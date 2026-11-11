const request = require('../../utils/request');
const auth = require('../../utils/auth');

/**
 * 首页（EM009）：按角色展示入口与首页数字。
 * GET /dashboard 返回字段（已实测，与角色无关的字段为 null）：
 *   role、pendingRepairCount、pendingAcceptanceCount（管理员）、
 *   myPendingRepairCount（报修人）、
 *   myPendingOrderCount、myProcessingOrderCount、myPendingAcceptanceOrderCount、myUnfinishedOrderCount（维修人员）、
 *   equipmentCount
 */
Page({
  data: {
    user: null,
    role: '',
    roleLabel: '',
    isReporter: false,
    isTechnician: false,
    isAdmin: false,
    stats: [],
    notice: '',
    loading: false,
    error: ''
  },

  onShow() {
    if (!auth.ensureLogin()) {
      return;
    }
    const user = auth.getUser() || {};
    const role = user.role || '';
    this.setData({
      user: user,
      role: role,
      roleLabel: user.roleLabel || '',
      isReporter: role === 'REPORTER',
      isTechnician: role === 'TECHNICIAN',
      isAdmin: role === 'ADMIN'
    });
    this.loadDashboard();
  },

  onPullDownRefresh() {
    this.loadDashboard(true);
  },

  /**
   * 拉取首页数字：每次进入/刷新都取服务端最新数据，不做本地缓存
   */
  loadDashboard(fromPull) {
    if (this._loading) {
      if (fromPull) {
        wx.stopPullDownRefresh();
      }
      return;
    }
    this._loading = true;
    this.setData({ loading: true, error: '' });
    request
      .get('/dashboard', null, { showError: false })
      .then((data) => {
        this.renderOverview(data || {});
        this.setData({ loading: false });
      })
      .catch(() => {
        this.setData({ loading: false, error: '首页数据加载失败' });
      })
      .then(() => {
        this._loading = false;
        if (fromPull) {
          wx.stopPullDownRefresh();
        }
      });
  },

  /**
   * 把接口数字转成展示用数组：只展示当前角色有意义的字段（其余为 null，不展示）
   */
  renderOverview(data) {
    const role = data.role || this.data.role;
    const stats = [];
    let notice = '';

    const pick = function (value) {
      return value === null || value === undefined ? '—' : value;
    };

    if (role === 'REPORTER') {
      stats.push({ key: 'myPendingRepairCount', label: '我的待受理', value: pick(data.myPendingRepairCount) });
    } else if (role === 'TECHNICIAN') {
      stats.push({ key: 'myPendingOrderCount', label: '待处理', value: pick(data.myPendingOrderCount) });
      stats.push({ key: 'myProcessingOrderCount', label: '处理中', value: pick(data.myProcessingOrderCount) });
      stats.push({ key: 'myPendingAcceptanceOrderCount', label: '待验收', value: pick(data.myPendingAcceptanceOrderCount) });
      stats.push({ key: 'myUnfinishedOrderCount', label: '未完成', value: pick(data.myUnfinishedOrderCount) });
    } else if (role === 'ADMIN') {
      notice = '管理员功能请在 Web 管理后台操作：小程序端不提供设备台账、派工受理、验收与统计入口。';
    }

    this.setData({
      role: role,
      isReporter: role === 'REPORTER',
      isTechnician: role === 'TECHNICIAN',
      isAdmin: role === 'ADMIN',
      stats: stats,
      notice: notice
    });
  },

  goScan() {
    wx.navigateTo({ url: '/pages/scan/scan' });
  },

  goRepairList() {
    wx.navigateTo({ url: '/pages/repair/list' });
  },

  goOrderList() {
    wx.navigateTo({ url: '/pages/order/list' });
  },

  goVerifyScan() {
    wx.navigateTo({ url: '/pages/scan/scan?mode=verify' });
  },

  goProfile() {
    wx.navigateTo({ url: '/pages/profile/profile' });
  }
});
