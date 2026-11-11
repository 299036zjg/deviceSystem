const auth = require('./utils/auth');

/**
 * 小程序入口。
 * 全局只保存少量跨页数据（如扫码核对结果），登录态与用户信息统一由 utils/auth.js 管理。
 */
App({
  globalData: {
    // 工单扫码核对结果：{ orderId, matched, message, scannedCode, scannedEquipment }
    // 由 pages/scan 写入、pages/order/detail 读取（页面间回传复杂对象走 globalData）
    lastVerify: null
  },

  onLaunch() {
    // 启动时不做强校验：未登录停留在登录页（pages/login 为启动页），
    // 令牌是否过期由服务端返回 40101 时统一处理（见 utils/request.js）
    if (auth.isLoggedIn()) {
      console.log('[app] 检测到本地登录态，进入后将按角色加载首页数据');
    }
  },

  onError(err) {
    // 记录运行时异常，避免静默失败影响联调
    console.error('[app] 运行时异常', err);
  },

  onUnhandledRejection(res) {
    console.error('[app] 未处理的 Promise 拒绝', res && res.reason);
  }
});
