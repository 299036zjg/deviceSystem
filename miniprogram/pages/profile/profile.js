const auth = require('../../utils/auth');
const format = require('../../utils/format');
const request = require('../../utils/request');

/**
 * 个人中心（EM009）
 * GET /auth/profile -> { userId, account, name, department, role, roleLabel, enabled }
 * POST /auth/password -> { oldPassword, newPassword }，成功后该用户全部令牌失效
 * POST /auth/logout -> 使当前令牌失效
 */
Page({
  data: {
    user: {},
    expireAtText: '',
    oldPassword: '',
    newPassword: '',
    confirmPassword: '',
    submitting: false
  },

  onShow() {
    if (!auth.ensureLogin()) {
      return;
    }
    const local = auth.getUser() || {};
    this.setData({
      user: local,
      expireAtText: local.expireAt ? format.formatDateTime(local.expireAt) : ''
    });
    this.loadProfile();
  },

  loadProfile() {
    request
      .get('/auth/profile', null, { showError: false })
      .then((data) => {
        if (!data) {
          return;
        }
        auth.updateUser(data);
        this.setData({
          user: Object.assign({}, this.data.user, data),
          expireAtText: (auth.getUser() || {}).expireAt
            ? format.formatDateTime(auth.getUser().expireAt)
            : this.data.expireAtText
        });
      })
      .catch(() => {
        // 静默失败：保留本地缓存的用户信息
      });
  },

  onOldPasswordInput(e) {
    this.setData({ oldPassword: e.detail.value });
  },

  onNewPasswordInput(e) {
    this.setData({ newPassword: e.detail.value });
  },

  onConfirmPasswordInput(e) {
    this.setData({ confirmPassword: e.detail.value });
  },

  /**
   * 密码规则与后端一致：8~64 位且同时包含字母和数字（仅 ASCII）
   */
  checkPasswordRule(password) {
    if (!password || password.length < 8 || password.length > 64) {
      return '密码须为 8 至 64 位字符';
    }
    if (!/[A-Za-z]/.test(password) || !/\d/.test(password)) {
      return '密码须同时包含字母和数字';
    }
    return '';
  },

  onChangePassword() {
    if (this.data.submitting) {
      return;
    }
    const oldPassword = this.data.oldPassword;
    const newPassword = this.data.newPassword;
    const confirmPassword = this.data.confirmPassword;

    if (!oldPassword) {
      request.toast('请输入原密码');
      return;
    }
    const ruleError = this.checkPasswordRule(newPassword);
    if (ruleError) {
      request.toast(ruleError);
      return;
    }
    if (newPassword !== confirmPassword) {
      request.toast('两次输入的新密码不一致');
      return;
    }
    if (newPassword === oldPassword) {
      request.toast('新密码不能与原密码相同');
      return;
    }

    this.setData({ submitting: true });
    request
      .post('/auth/password', { oldPassword: oldPassword, newPassword: newPassword }, {
        showError: false,
        loading: true,
        loadingText: '提交中'
      })
      .then(() => {
        wx.showToast({ title: '密码已修改，请重新登录', icon: 'none', duration: 1800 });
        auth.clearSession();
        setTimeout(function () {
          wx.reLaunch({ url: '/pages/login/login' });
        }, 1600);
      })
      .catch((err) => {
        this.setData({ submitting: false });
        wx.showModal({
          title: '修改失败',
          content: (err && err.message) || '请稍后重试',
          showCancel: false
        });
      });
  },

  onLogout() {
    wx.showModal({
      title: '退出登录',
      content: '确认退出当前账号？',
      success: (res) => {
        if (!res.confirm) {
          return;
        }
        wx.showLoading({ title: '退出中', mask: true });
        request
          .post('/auth/logout', {}, { showError: false })
          .catch(function () {
            // 即使接口失败也清除本地登录态，避免卡在无效状态
          })
          .then(() => {
            wx.hideLoading();
            auth.clearSession();
            wx.reLaunch({ url: '/pages/login/login' });
          });
      }
    });
  }
});
