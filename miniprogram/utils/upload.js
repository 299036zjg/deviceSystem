/**
 * 图片上传封装（POST /files，multipart/form-data）
 *
 * 接口字段（已实测）：file（文件）、bizType（REPAIR 故障现场 / WORK 维修完成）、sortNo
 * 返回：{ attachmentId, bizType, bizTypeLabel, fileName, url, fileSize, uploaderName, uploadedAt, sortNo }
 * 业务提交时把 attachmentId 放进 attachmentIds 与单据关联。
 */
const config = require('./config');
const auth = require('./auth');
const request = require('./request');

function getExt(filePath) {
  const matched = /\.([a-zA-Z0-9]+)$/.exec(filePath || '');
  return matched ? matched[1].toLowerCase() : '';
}

/**
 * 校验图片：格式 JPG/PNG，单张 ≤ 5 MB
 * @returns {string} 空字符串表示通过，否则为提示文案
 */
function checkImage(tempFilePath, size) {
  const ext = getExt(tempFilePath);
  if (ext && config.allowedImageExt.indexOf(ext) === -1) {
    return '仅支持 JPG、PNG 格式的图片';
  }
  if (size && size > config.maxPhotoBytes) {
    return '单张图片不能超过 5 MB，请压缩后再选择';
  }
  return '';
}

/**
 * 规范化文件路径后缀
 * wx.uploadFile 依据文件后缀推断 multipart 中 file 部分的 Content-Type，
 * 后端只接受 image/jpeg 与 image/png，因此后缀不是 jpg/jpeg/png 时先复制成 .jpg 再上传。
 */
function normalizeFilePath(filePath) {
  return new Promise(function (resolve) {
    const ext = getExt(filePath);
    if (config.allowedImageExt.indexOf(ext) >= 0) {
      resolve(filePath);
      return;
    }
    try {
      const fs = wx.getFileSystemManager();
      const target =
        wx.env.USER_DATA_PATH + '/upload_' + Date.now() + '_' + Math.floor(Math.random() * 10000) + '.jpg';
      fs.copyFile({
        srcPath: filePath,
        destPath: target,
        success: function () {
          resolve(target);
        },
        fail: function () {
          resolve(filePath);
        }
      });
    } catch (e) {
      resolve(filePath);
    }
  });
}

/**
 * 上传单张图片
 * @param {Object} options { filePath, bizType, sortNo, onProgress }
 * @returns {Promise<Object>} 附件信息（含 attachmentId）
 */
function uploadImage(options) {
  const opts = options || {};
  return normalizeFilePath(opts.filePath).then(function (filePath) {
    return new Promise(function (resolve, reject) {
      const task = wx.uploadFile({
        url: config.baseUrl + '/files',
        filePath: filePath,
        name: 'file',
        formData: {
          bizType: opts.bizType,
          sortNo: opts.sortNo || 1
        },
        header: {
          Authorization: 'Bearer ' + auth.getToken()
        },
        success: function (res) {
          let body = null;
          try {
            body = JSON.parse(res.data);
          } catch (e) {
            body = null;
          }
          if (!body || typeof body.code !== 'number') {
            reject({ code: -1, message: '上传响应异常（HTTP ' + res.statusCode + '）' });
            return;
          }
          if (body.code === 0) {
            resolve(body.data);
            return;
          }
          if (body.code === 40101) {
            request.handleUnauthorized();
          }
          reject({ code: body.code, message: body.message || '图片上传失败' });
        },
        fail: function (err) {
          reject({ code: -2, message: (err && err.errMsg) || '图片上传失败，请检查网络' });
        }
      });
      if (task && typeof task.onProgressUpdate === 'function' && typeof opts.onProgress === 'function') {
        task.onProgressUpdate(function (res) {
          opts.onProgress(res.progress);
        });
      }
    });
  });
}

module.exports = {
  checkImage: checkImage,
  uploadImage: uploadImage,
  getExt: getExt,
  normalizeFilePath: normalizeFilePath
};
