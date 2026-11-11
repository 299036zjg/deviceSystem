/**
 * 附件图片加载
 *
 * 后端图片接口 GET /api/v1/files/{attachmentId} 需要 Authorization 头，
 * 小程序的 <image src> 无法携带请求头，因此统一用 wx.downloadFile（带令牌）下载到本地临时文件，
 * 再用临时文件路径展示/预览原图，并按 attachmentId 做内存缓存避免重复下载。
 */
const config = require('./config');
const auth = require('./auth');

const cache = {};

/**
 * 拼接完整图片地址（接口返回形如 "/api/v1/files/8"）
 */
function fullUrl(url) {
  if (!url) {
    return '';
  }
  if (/^https?:\/\//i.test(url)) {
    return url;
  }
  return config.fileBaseUrl + (url.charAt(0) === '/' ? url : '/' + url);
}

/**
 * 下载单个附件，返回本地临时文件路径
 * @param {Object} attachment 附件对象 { attachmentId, url, ... }
 * @param {number} [width] 需要缩略图时传宽度（后端支持 ?width=200）
 */
function resolveAttachment(attachment, width) {
  if (!attachment || !attachment.url) {
    return Promise.reject({ code: -1, message: '附件地址为空' });
  }
  const key = String(attachment.attachmentId) + (width ? ('@' + width) : '');
  if (cache[key]) {
    return Promise.resolve(cache[key]);
  }
  const url = fullUrl(attachment.url) + (width ? (attachment.url.indexOf('?') >= 0 ? '&' : '?') + 'width=' + width : '');
  return new Promise(function (resolve, reject) {
    wx.downloadFile({
      url: url,
      header: { Authorization: 'Bearer ' + auth.getToken() },
      success: function (res) {
        if (res.statusCode === 200 && res.tempFilePath) {
          cache[key] = res.tempFilePath;
          resolve(res.tempFilePath);
          return;
        }
        reject({ code: res.statusCode, message: '图片加载失败（' + res.statusCode + '）' });
      },
      fail: function (err) {
        reject({ code: -2, message: (err && err.errMsg) || '图片加载失败' });
      }
    });
  });
}

/**
 * 批量加载附件，返回带 localPath / loadError 的新数组（不因单张失败影响整体）
 * @param {Array} attachments
 * @param {number} [width] 缩略图宽度
 */
function resolveList(attachments, width) {
  const list = attachments || [];
  if (!list.length) {
    return Promise.resolve([]);
  }
  const tasks = list.map(function (item) {
    return resolveAttachment(item, width).then(
      function (localPath) {
        return Object.assign({}, item, { localPath: localPath, loadError: false });
      },
      function () {
        return Object.assign({}, item, { localPath: '', loadError: true });
      }
    );
  });
  return Promise.all(tasks);
}

/**
 * 预览原图：只有已下载成功的本地文件可以预览
 */
function previewLoaded(list, index) {
  const urls = (list || [])
    .filter(function (item) {
      return item.localPath;
    })
    .map(function (item) {
      return item.localPath;
    });
  if (!urls.length) {
    wx.showToast({ title: '图片尚未加载完成', icon: 'none' });
    return;
  }
  const target = list[index];
  wx.previewImage({
    urls: urls,
    current: target && target.localPath ? target.localPath : urls[0]
  });
}

function clearCache() {
  Object.keys(cache).forEach(function (key) {
    delete cache[key];
  });
}

module.exports = {
  fullUrl: fullUrl,
  resolveAttachment: resolveAttachment,
  resolveList: resolveList,
  previewLoaded: previewLoaded,
  clearCache: clearCache
};
