/**
 * wx.request 统一封装
 *
 * - 自动携带 Authorization: Bearer <token>
 * - 统一解析后端响应结构 {code, message, data}：code === 0 视为成功，resolve(data)
 * - code === 40101（未登录/令牌失效）：清除本地令牌并 wx.reLaunch 到登录页
 * - 其他错误码：wx.showToast 提示 message（可由调用方传 showError:false 关闭），并 reject({code, message})
 *
 * 注意：后端错误响应同时带 HTTP 状态码（如 400/403/409），wx.request 仍会走 success，
 * 因此这里以响应体 code 字段为准。
 */
const config = require('./config');
const auth = require('./auth');

const CODE_OK = 0;
const CODE_UNAUTHORIZED = 40101;

let redirecting = false;

function toast(title) {
  wx.showToast({ title: title || '操作失败', icon: 'none', duration: 2200 });
}

/**
 * 40101 统一处理：清空本地登录态并回到登录页
 */
function handleUnauthorized() {
  if (redirecting) {
    return;
  }
  redirecting = true;
  auth.clearSession();
  const pages = getCurrentPages();
  const current = pages.length ? pages[pages.length - 1].route : '';
  if (current && current.indexOf('pages/login') === 0) {
    redirecting = false;
    return;
  }
  wx.showToast({ title: '登录已失效，请重新登录', icon: 'none', duration: 1500 });
  setTimeout(function () {
    wx.reLaunch({ url: '/pages/login/login' });
    redirecting = false;
  }, 500);
}

function buildQuery(params) {
  if (!params) {
    return '';
  }
  const parts = [];
  Object.keys(params).forEach(function (key) {
    const value = params[key];
    if (value === undefined || value === null || value === '') {
      return;
    }
    parts.push(encodeURIComponent(key) + '=' + encodeURIComponent(value));
  });
  return parts.length ? '?' + parts.join('&') : '';
}

/**
 * 发起请求
 * @param {Object} options
 *   url      接口路径（相对 baseUrl），如 '/dashboard'
 *   method   请求方法，默认 GET
 *   data     请求体 / 查询参数（GET 时由调用方拼接，推荐用 get()）
 *   header   额外请求头
 *   loading  是否显示 loading（默认 false）
 *   loadingText loading 文案
 *   showError 失败时是否自动 toast（默认 true）
 * @returns {Promise<any>} resolve 后端 data 字段
 */
function request(options) {
  const opts = options || {};
  const method = (opts.method || 'GET').toUpperCase();
  const showError = opts.showError !== false;
  const useLoading = !!opts.loading;

  return new Promise(function (resolve, reject) {
    const header = Object.assign({ 'Content-Type': 'application/json' }, opts.header || {});
    const token = auth.getToken();
    if (token) {
      header.Authorization = 'Bearer ' + token;
    }
    if (useLoading) {
      wx.showLoading({ title: opts.loadingText || '加载中', mask: true });
    }

    wx.request({
      url: config.baseUrl + opts.url,
      method: method,
      data: opts.data,
      header: header,
      timeout: 20000,
      success: function (res) {
        if (useLoading) {
          wx.hideLoading();
        }
        const body = res.data;
        if (body && typeof body === 'object' && typeof body.code === 'number') {
          if (body.code === CODE_OK) {
            resolve(body.data);
            return;
          }
          if (body.code === CODE_UNAUTHORIZED) {
            handleUnauthorized();
            reject({ code: body.code, message: body.message || '登录已失效' });
            return;
          }
          if (showError) {
            toast(body.message || '请求失败');
          }
          reject({ code: body.code, message: body.message || '请求失败' });
          return;
        }
        // 非标准响应（例如网关返回的 HTML 或空响应）
        if (showError) {
          toast('服务响应异常（HTTP ' + res.statusCode + '）');
        }
        reject({ code: -1, message: '服务响应异常（HTTP ' + res.statusCode + '）' });
      },
      fail: function (err) {
        if (useLoading) {
          wx.hideLoading();
        }
        if (showError) {
          toast('网络异常，请确认后端服务（localhost:8080）已启动');
        }
        reject({ code: -2, message: (err && err.errMsg) || '网络异常' });
      }
    });
  });
}

/**
 * GET 请求
 * @param {string} url 接口路径
 * @param {Object} [params] 查询参数
 * @param {Object} [options] 其他选项（showError/loading 等）
 */
function get(url, params, options) {
  return request(Object.assign({ url: url + buildQuery(params), method: 'GET' }, options || {}));
}

/**
 * POST 请求
 * @param {string} url 接口路径
 * @param {Object} [data] 请求体
 * @param {Object} [options] 其他选项
 */
function post(url, data, options) {
  return request(Object.assign({ url: url, method: 'POST', data: data || {} }, options || {}));
}

module.exports = {
  request: request,
  get: get,
  post: post,
  toast: toast,
  handleUnauthorized: handleUnauthorized,
  buildQuery: buildQuery
};
