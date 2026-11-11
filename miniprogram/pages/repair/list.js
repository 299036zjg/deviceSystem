const auth = require('../../utils/auth');
const config = require('../../utils/config');
const dict = require('../../utils/dict');
const format = require('../../utils/format');
const request = require('../../utils/request');

/**
 * 我的报修（EM008/EM011）
 * GET /repair-requests/mine?status=&page=&size=10
 * 列表字段（已实测）：requestId、requestNo、equipmentCode、equipmentName、location、description、
 * faultType、urgency、discoveredAt、submittedAt、status、statusCode、orderNo、ownerName、photoCount
 */
Page({
  data: {
    tabs: [],
    activeIndex: 0,
    list: [],
    page: 1,
    hasMore: true,
    total: 0,
    loading: false,
    loaded: false,
    error: ''
  },

  onLoad() {
    if (!auth.ensureLogin()) {
      return;
    }
    this._shown = false;
    // 先用缓存/兜底字典渲染 tab，再等字典就绪后拉列表
    this.setData({ tabs: dict.requestTabs(dict.getCached()) });
    dict.load().then((data) => {
      this.setData({ tabs: dict.requestTabs(data) });
      this.reload();
    });
  },

  onShow() {
    if (!auth.ensureLogin()) {
      return;
    }
    // 首次进入由 onLoad 拉取；从详情/新增返回时重新取服务端最新数据
    if (this._shown) {
      this.reload();
    }
    this._shown = true;
  },

  onPullDownRefresh() {
    this.reload(true);
  },

  onReachBottom() {
    if (!this.data.hasMore || this.data.loading) {
      return;
    }
    this.fetch(this.data.page + 1);
  },

  onTabChange(e) {
    const index = Number(e.currentTarget.dataset.index);
    if (index === this.data.activeIndex) {
      return;
    }
    this.setData({ activeIndex: index });
    this.reload();
  },

  onRetry() {
    this.reload();
  },

  /**
   * 重新加载第一页
   */
  reload(fromPull) {
    this._pull = !!fromPull;
    this.fetch(1);
  },

  /**
   * 拉取列表数据
   * @param {number} page 页码（从 1 开始）
   */
  fetch(page) {
    if (this._requesting) {
      if (this._pull) {
        wx.stopPullDownRefresh();
        this._pull = false;
      }
      return;
    }
    this._requesting = true;
    const tabs = this.data.tabs;
    const tab = tabs[this.data.activeIndex];
    const params = {
      page: page,
      size: config.pageSize
    };
    if (tab && tab.code) {
      params.status = tab.code;
    }

    this.setData({ loading: true, error: page === 1 ? '' : this.data.error });
    request
      .get('/repair-requests/mine', params, { showError: false })
      .then((data) => {
        const items = (data && data.items) || [];
        const decorated = items.map(function (item) {
          return {
            requestId: item.requestId,
            requestNo: item.requestNo,
            equipmentName: item.equipmentName,
            equipmentCode: item.equipmentCode,
            location: item.location || '—',
            description: format.shortText(item.description, 44),
            urgency: item.urgency,
            urgencyClass: format.urgencyTag(item.urgency),
            discoveredAtText: format.formatDateTime(item.discoveredAt),
            status: item.status,
            statusClass: format.statusTag(item.statusCode, item.status),
            ownerName: item.ownerName || '',
            orderNo: item.orderNo || '',
            photoCount: item.photoCount || 0
          };
        });
        const total = (data && data.total) || 0;
        const list = page === 1 ? decorated : this.data.list.concat(decorated);
        this.setData({
          list: list,
          page: page,
          total: total,
          hasMore: list.length < total,
          loading: false,
          loaded: true,
          error: ''
        });
      })
      .catch(() => {
        this.setData({
          loading: false,
          loaded: true,
          error: page === 1 ? '报修列表加载失败' : '加载更多失败'
        });
      })
      .then(() => {
        this._requesting = false;
        if (this._pull) {
          wx.stopPullDownRefresh();
          this._pull = false;
        }
      });
  },

  goDetail(e) {
    const id = e.currentTarget.dataset.id;
    if (!id) {
      return;
    }
    wx.navigateTo({ url: '/pages/repair/detail?id=' + id });
  }
});
