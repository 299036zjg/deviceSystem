/**
 * 设备与扫码相关封装
 *
 * 二维码内容格式：EQ:<设备编号>（后端 /equipments/by-qr 只接受该格式）
 * 接口返回（已实测）：
 *   GET /equipments/by-qr?content=EQ:SB-001
 *     -> { equipment: {...}, canReport: true/false, message: "..." }
 *   GET /equipments/verify?orderId=1&content=EQ:SB-001
 *     -> { matched: true/false, message: "...", orderId, orderEquipment, scannedEquipment }
 */
const request = require('./request');

const QR_PREFIX = 'EQ:';

/**
 * 调起微信扫码
 * @returns {Promise<string>} 二维码原始内容
 */
function scanQr() {
  return new Promise(function (resolve, reject) {
    wx.scanCode({
      onlyFromCamera: false,
      scanType: ['qrCode'],
      success: function (res) {
        resolve((res.result || '').trim());
      },
      fail: function (err) {
        reject(err || { errMsg: 'scanCode:fail' });
      }
    });
  });
}

function isCancel(err) {
  return /cancel/i.test((err && err.errMsg) || '');
}

/**
 * 判断是否为微信扫码服务的登录态问题（非本系统令牌）
 * 典型 errMsg：scanCode:fail Error: INVALID_LOGIN,access_token expired [20261111 16:56:27]
 * 原因：微信开发者工具/手机微信的登录态过期，或游客模式、测试号不支持扫码能力。
 */
function isScanLoginInvalid(err) {
  return /INVALID_LOGIN|access_token|invalid session|login expired/i.test((err && err.errMsg) || '');
}

/**
 * 把扫码失败转换为可直接展示的中文提示
 */
function describeScanError(err) {
  const msg = (err && err.errMsg) || '';
  if (isScanLoginInvalid(err)) {
    return '微信扫码服务的登录态已失效（不是本系统账号问题）：请在微信开发者工具右上角重新登录微信账号，'
      + '或重启开发者工具后再扫；游客模式/测试号可能不支持扫码，建议改用真实 AppID。'
      + '临时可手动输入设备编号查询。';
  }
  if (/not support|unsupported|system error|invalid parameter/i.test(msg)) {
    return '当前环境无法调起扫码（部分模拟器或游客模式不支持）。请改用真机预览，或手动输入设备编号。';
  }
  return '扫码未完成：' + (msg || '未知错误') + '。可重新扫码或手动输入设备编号。';
}

/**
 * 判断是否为相机授权被拒绝（不同基础库/系统 errMsg 不一致，这里做关键字兜底）
 */
function isCameraDenied(err) {
  const msg = (err && err.errMsg) || '';
  return /auth|authorize|permission|deny|denied|scope\.camera|隐私/i.test(msg);
}

/**
 * 手动输入设备编号时的内容规范化：补上 EQ: 前缀
 */
function normalizeManualContent(input) {
  const text = (input || '').trim();
  if (!text) {
    return '';
  }
  if (text.indexOf(QR_PREFIX) === 0) {
    return text;
  }
  return QR_PREFIX + text;
}

/**
 * 扫码内容是否为本系统约定格式
 */
function isSystemQr(content) {
  return (content || '').indexOf(QR_PREFIX) === 0;
}

/**
 * 扫码识别设备（报修人返回 canReport 与提示语，维修人员用于核对）
 */
function fetchByQr(content) {
  return request.get('/equipments/by-qr', { content: content }, { showError: false });
}

/**
 * 维修人员到场核对工单设备
 */
function verifyOrderEquipment(orderId, content) {
  return request.get(
    '/equipments/verify',
    { orderId: orderId, content: content },
    { showError: false }
  );
}

/**
 * 设备详情
 */
function loadEquipment(equipmentId) {
  return request.get('/equipments/' + equipmentId, null, { showError: false });
}

/**
 * 查询"我的未完成工单"中属于该设备（维修人员扫码核对入口使用）
 * 后端 /work-orders/mine 支持 status 过滤，状态取枚举名
 */
function findMyOrdersForEquipment(equipmentId) {
  const statuses = ['PENDING', 'PROCESSING', 'PENDING_ACCEPTANCE'];
  const tasks = statuses.map(function (status) {
    return request
      .get('/work-orders/mine', { status: status, page: 1, size: 50 }, { showError: false })
      .then(function (data) {
        return (data && data.items) || [];
      })
      .catch(function () {
        return [];
      });
  });
  return Promise.all(tasks).then(function (groups) {
    const merged = [];
    groups.forEach(function (items) {
      items.forEach(function (item) {
        if (String(item.equipmentId) === String(equipmentId)) {
          merged.push(item);
        }
      });
    });
    return merged;
  });
}

module.exports = {
  QR_PREFIX: QR_PREFIX,
  scanQr: scanQr,
  isCancel: isCancel,
  isScanLoginInvalid: isScanLoginInvalid,
  describeScanError: describeScanError,
  isCameraDenied: isCameraDenied,
  normalizeManualContent: normalizeManualContent,
  isSystemQr: isSystemQr,
  fetchByQr: fetchByQr,
  verifyOrderEquipment: verifyOrderEquipment,
  loadEquipment: loadEquipment,
  findMyOrdersForEquipment: findMyOrdersForEquipment
};
