const request = require('../../utils/request');
const auth = require('../../utils/auth');

/**
 * 登录页（EM009）：账号密码登录，不提供微信一键登录与自助注册。
 * 登录返回字段（已实测）：token / userId / account / name / department / role / roleLabel / expireAt
 */
Page({
  data: {
    account: '',
    password: '',
    submitting: false
  },

  onLoad() {
    // 本地已有登录态时直接进入首页（令牌是否过期由服务端 40101 统一处理）
    if (auth.isLoggedIn()) {
      wx.reLaunch({ url: '/pages/index/index' });
    }
  },

  onAccountInput(e) {
    this.setData({ account: e.detail.value });
  },

  onPasswordInput(e) {
    this.setData({ password: e.detail.value });
  },

  onSubmit() {
    if (this.data.submitting) {
      return;
    }
    const account = (this.data.account || '').trim();
    const password = this.data.password || '';
    if (!account) {
      request.toast('请输入账号');
      return;
    }
    if (!password) {
      request.toast('请输入密码');
      return;
    }

    this.setData({ submitting: true });
    request
      .post('/auth/login', { account: account, password: password }, { loading: true, loadingText: '登录中' })
      .then((data) => {
        auth.setSession(data);
        wx.showToast({ title: '登录成功', icon: 'success', duration: 600 });
        setTimeout(function () {
          wx.reLaunch({ url: '/pages/index/index' });
        }, 600);
      })
      .catch(() => {
        // 失败提示已由 utils/request.js 统一 toast，这里不再重复提示
      })
      .then(() => {
        this.setData({ submitting: false });
      });
  }
});
