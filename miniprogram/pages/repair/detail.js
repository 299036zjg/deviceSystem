const auth = require('../../utils/auth');
const format = require('../../utils/format');
const media = require('../../utils/media');
const request = require('../../utils/request');

/**
 * 报修详情（EM011）
 * GET /repair-requests/{id} 返回：
 *   { summary, decisionReason, decidedAt, decidedByName, attachments[], order, records[], acceptances[], acceptanceConclusion }
 * 注意：接口对报修人已隐藏费用字段（summary.totalCost 恒为 null），此处不展示任何费用信息。
 */
Page({
  data: {
    id: '',
    loading: true,
    error: '',
    summary: null,
    statusClass: '',
    urgencyClass: 'tag-gray',
    discoveredAtText: '',
    submittedAtText: '',
    decidedAtText: '',
    photos: [],
    records: [],
    acceptances: [],
    conclusion: '',
    decisionReason: '',
    decidedByName: '',
    order: null,
    orderStatusClass: ''
  },

  onLoad(options) {
    if (!auth.ensureLogin()) {
      return;
    }
    const id = (options && options.id) || '';
    this.setData({ id: id });
    this.load();
  },

  onShow() {
    // 状态可能被 Web 后台改变（受理/驳回/验收），每次显示都重新取服务端数据
    if (this._loaded && auth.isLoggedIn()) {
      this.load();
    }
    this._loaded = true;
  },

  onPullDownRefresh() {
    this.load(true);
  },

  load(fromPull) {
    if (!this.data.id) {
      this.setData({ loading: false, error: '缺少报修单标识' });
      return;
    }
    this.setData({ loading: true });
    request
      .get('/repair-requests/' + this.data.id, null, { showError: false })
      .then((detail) => {
        const summary = (detail && detail.summary) || null;
        const records = ((detail && detail.records) || []).map(function (record) {
          return {
            recordId: record.recordId,
            operatorName: record.operatorName,
            cause: record.cause,
            process: record.process,
            result: record.result,
            replacedParts: record.replacedParts,
            submittedAtText: format.formatDateTime(record.submittedAt)
          };
        });
        const acceptances = ((detail && detail.acceptances) || []).map(function (item) {
          return {
            acceptanceId: item.acceptanceId,
            inspectorName: item.inspectorName,
            result: item.result,
            resultLabel: item.resultLabel,
            opinion: item.opinion,
            acceptedAtText: format.formatDateTime(item.acceptedAt)
          };
        });
        const order = (detail && detail.order) || null;

        this.setData({
          loading: false,
          error: '',
          summary: summary,
          statusClass: summary ? format.statusTag(summary.statusCode, summary.status) : 'tag-gray',
          urgencyClass: summary ? format.urgencyTag(summary.urgency) : 'tag-gray',
          discoveredAtText: summary ? format.formatDateTime(summary.discoveredAt) : '',
          submittedAtText: summary ? format.formatDateTime(summary.submittedAt) : '',
          decidedAtText: detail ? format.formatDateTime(detail.decidedAt) : '',
          decidedByName: (detail && detail.decidedByName) || '',
          decisionReason: (detail && detail.decisionReason) || '',
          conclusion: (detail && detail.acceptanceConclusion) || '',
          records: records,
          acceptances: acceptances,
          photos: [],
          order: order,
          orderStatusClass: order ? format.statusTag(order.statusCode, order.status) : 'tag-gray'
        });

        // 现场照片需要带令牌下载，用临时文件路径展示；详情取原图，点击可放大查看
        return media.resolveList((detail && detail.attachments) || []).then((photos) => {
          this.setData({ photos: photos });
        });
      })
      .catch((err) => {
        this.setData({
          loading: false,
          error: (err && err.message) || '报修详情加载失败'
        });
      })
      .then(() => {
        if (fromPull) {
          wx.stopPullDownRefresh();
        }
      });
  },

  previewPhoto(e) {
    const index = Number(e.currentTarget.dataset.index);
    media.previewLoaded(this.data.photos, index);
  }
});
