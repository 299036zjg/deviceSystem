const auth = require('../../utils/auth');
const config = require('../../utils/config');
const dict = require('../../utils/dict');
const format = require('../../utils/format');
const request = require('../../utils/request');

/**
 * 我的工单（EM008/EM012）
 * GET /work-orders/mine?status=&page=&size=10
 * 列表字段（已实测）：orderId、orderNo、orderType、orderTypeLabel、equipmentCode、equipmentName、location、
 * ownerName、taskContent、plannedDate、status、statusCode、totalCost、createdAt、startedAt、completedAt
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

  onLoad(options) {
    if (!auth.ensureLogin()) {
      return;
    }
    this._shown = false;
    const initialTab = (options && options.tab) || '';
    // 先用缓存/兜底字典渲染 tab，再等字典就绪后拉列表
    this.setData({ tabs: dict.orderTabs(dict.getCached()) });
    dict.load().then((data) => {
      const tabs = dict.orderTabs(data);
      let index = 0;
      if (initialTab) {
        tabs.forEach(function (tab, i) {
          if (tab.code === initialTab) {
            index = i;
          }
        });
      }
      this.setData({ tabs: tabs, activeIndex: index });
      this.reload();
    });
  },

  onShow() {
    if (!auth.ensureLogin()) {
      return;
    }
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

  reload(fromPull) {
    this._pull = !!fromPull;
    this.fetch(1);
  },

  fetch(page) {
    if (this._requesting) {
      if (this._pull) {
        wx.stopPullDownRefresh();
        this._pull = false;
      }
      return;
    }
    this._requesting = true;
    const tab = this.data.tabs[this.data.activeIndex];
    const params = { page: page, size: config.pageSize };
    if (tab && tab.code) {
      params.status = tab.code;
    }

    this.setData({ loading: true, error: page === 1 ? '' : this.data.error });
    request
      .get('/work-orders/mine', params, { showError: false })
      .then((data) => {
        const items = (data && data.items) || [];
        const decorated = items.map(function (item) {
          const isMaintenance = item.orderType === 'MAINTENANCE';
          return {
            orderId: item.orderId,
            orderNo: item.orderNo,
            orderType: item.orderType,
            typeLabel: item.orderTypeLabel || (isMaintenance ? '保养' : '维修'),
            typeClass: isMaintenance ? 'tag-purple' : 'tag-blue',
            equipmentName: item.equipmentName,
            equipmentCode: item.equipmentCode,
            location: item.location || '—',
            taskContent: format.shortText(isMaintenance ? item.taskContent : (item.taskContent || ''), 44),
            plannedDateText: format.formatDate(item.plannedDate),
            costText: item.totalCost === null || item.totalCost === undefined ? '' : format.formatMoney(item.totalCost),
            status: item.status,
            statusClass: format.statusTag(item.statusCode, item.status)
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
          error: page === 1 ? '工单列表加载失败' : '加载更多失败'
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
    wx.navigateTo({ url: '/pages/order/detail?id=' + id });
  }
});
