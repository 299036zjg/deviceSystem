/**
 * 登录态管理：令牌与用户信息的本地读写、角色判断、登录校验。
 * 存储键：sems_token / sems_user
 */
const config = require('./config');

const TOKEN_KEY = 'sems_token';
const USER_KEY = 'sems_user';

/**
 * 保存登录结果（POST /auth/login 的 data）。
 * 返回字段：token、userId、account、name、department、role、roleLabel、expireAt、mustChangePassword
 */
function setSession(loginData) {
  if (!loginData || !loginData.token) {
    return;
  }
  wx.setStorageSync(TOKEN_KEY, loginData.token);
  const user = {
    userId: loginData.userId,
    account: loginData.account,
    name: loginData.name,
    department: loginData.department,
    role: loginData.role,
    roleLabel: loginData.roleLabel || config.roleLabels[loginData.role] || '',
    expireAt: loginData.expireAt,
    mustChangePassword: !!loginData.mustChangePassword
  };
  wx.setStorageSync(USER_KEY, user);
}

function getToken() {
  return wx.getStorageSync(TOKEN_KEY) || '';
}

function getUser() {
  const user = wx.getStorageSync(USER_KEY);
  return user && user.userId ? user : null;
}

/**
 * 用 GET /auth/profile 的最新数据刷新本地用户信息
 */
function updateUser(patch) {
  const current = getUser();
  if (!current || !patch) {
    return;
  }
  const next = Object.assign({}, current, patch);
  next.roleLabel = patch.roleLabel || config.roleLabels[next.role] || current.roleLabel || '';
  wx.setStorageSync(USER_KEY, next);
}

function clearSession() {
  wx.removeStorageSync(TOKEN_KEY);
  wx.removeStorageSync(USER_KEY);
}

function isLoggedIn() {
  return !!getToken();
}

function getRole() {
  const user = getUser();
  return user ? user.role : '';
}

function hasRole(role) {
  return getRole() === role;
}

function isReporter() {
  return hasRole(config.roles.REPORTER);
}

function isTechnician() {
  return hasRole(config.roles.TECHNICIAN);
}

function isAdmin() {
  return hasRole(config.roles.ADMIN);
}

/**
 * 页面进入前的登录校验：未登录时回到登录页并返回 false
 */
function ensureLogin() {
  if (isLoggedIn()) {
    return true;
  }
  wx.reLaunch({ url: '/pages/login/login' });
  return false;
}

module.exports = {
  TOKEN_KEY: TOKEN_KEY,
  USER_KEY: USER_KEY,
  setSession: setSession,
  getToken: getToken,
  getUser: getUser,
  updateUser: updateUser,
  clearSession: clearSession,
  isLoggedIn: isLoggedIn,
  getRole: getRole,
  hasRole: hasRole,
  isReporter: isReporter,
  isTechnician: isTechnician,
  isAdmin: isAdmin,
  ensureLogin: ensureLogin
};
