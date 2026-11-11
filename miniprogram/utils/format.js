/**
 * 格式化工具：时间、金额、文本、状态颜色映射
 *
 * 后端时间格式为 "yyyy-MM-dd HH:mm:ss"，这里统一转成界面展示格式；
 * 状态既展示中文文字（接口 status / statusLabel 字段），又用颜色标签辅助区分。
 */

function pad2(value) {
  const num = Number(value);
  if (isNaN(num)) {
    return '00';
  }
  return num < 10 ? '0' + num : '' + num;
}

/**
 * "2026-11-11 14:54:32" -> "2026-11-11 14:54"（withSeconds 为 true 时保留秒）
 */
function formatDateTime(value, withSeconds) {
  if (!value) {
    return '—';
  }
  const text = String(value).replace('T', ' ');
  const parts = text.split(' ');
  if (parts.length < 2) {
    return text;
  }
  const time = parts[1].split(':');
  const hhmm = pad2(time[0]) + ':' + pad2(time[1]);
  return parts[0] + ' ' + (withSeconds ? hhmm + ':' + pad2(time[2] || 0) : hhmm);
}

/**
 * 取日期部分 "2026-11-11"
 */
function formatDate(value) {
  if (!value) {
    return '—';
  }
  return String(value).replace('T', ' ').split(' ')[0];
}

/**
 * 金额展示："120.5" -> "¥120.50"；无值返回 "—"
 */
function formatMoney(value) {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  const num = Number(value);
  if (isNaN(num)) {
    return '—';
  }
  return '¥' + num.toFixed(2);
}

/**
 * 金额纯数字（两位小数），用于输入框回填
 */
function formatAmount(value) {
  if (value === null || value === undefined || value === '') {
    return '';
  }
  const num = Number(value);
  if (isNaN(num)) {
    return '';
  }
  return num.toFixed(2);
}

/**
 * 超长文本截断
 */
function shortText(text, limit) {
  if (!text) {
    return '';
  }
  const max = limit || 48;
  const str = String(text);
  return str.length > max ? str.slice(0, max) + '…' : str;
}

/**
 * 状态码 -> 标签颜色类（app.wxss 中定义）
 * 接口状态码：报修 PENDING/REJECTED/ACCEPTED/COMPLETED；工单 PENDING/PROCESSING/PENDING_ACCEPTANCE/COMPLETED
 */
function statusTag(statusCode, statusText) {
  const code = statusCode || '';
  if (code === 'REJECTED') {
    return 'tag-red';
  }
  if (code === 'PENDING') {
    return 'tag-orange';
  }
  if (code === 'ACCEPTED' || code === 'PROCESSING') {
    return 'tag-blue';
  }
  if (code === 'PENDING_ACCEPTANCE') {
    return 'tag-purple';
  }
  if (code === 'COMPLETED') {
    return 'tag-green';
  }
  // 仅有中文状态时的兜底（接口同时返回 status 与 statusCode，此处仅作容错）
  if (statusText === '已驳回') {
    return 'tag-red';
  }
  if (statusText === '待受理' || statusText === '待处理') {
    return 'tag-orange';
  }
  if (statusText === '已受理' || statusText === '处理中') {
    return 'tag-blue';
  }
  if (statusText === '待验收') {
    return 'tag-purple';
  }
  if (statusText === '已完成') {
    return 'tag-green';
  }
  return 'tag-gray';
}

/**
 * 紧急程度 -> 标签颜色类（接口取值：一般 / 较高 / 紧急）
 */
function urgencyTag(text) {
  if (text === '紧急') {
    return 'tag-red';
  }
  if (text === '较高') {
    return 'tag-orange';
  }
  return 'tag-gray';
}

/**
 * 当前日期 "yyyy-MM-dd"
 */
function today() {
  const now = new Date();
  return now.getFullYear() + '-' + pad2(now.getMonth() + 1) + '-' + pad2(now.getDate());
}

/**
 * 当前时间 "HH:mm"
 */
function currentTime() {
  const now = new Date();
  return pad2(now.getHours()) + ':' + pad2(now.getMinutes());
}

/**
 * 当前时间戳字符串 "yyyy-MM-dd HH:mm:ss"
 */
function nowDateTime() {
  const now = new Date();
  return today() + ' ' + pad2(now.getHours()) + ':' + pad2(now.getMinutes()) + ':' + pad2(now.getSeconds());
}

/**
 * 日期 + 时间 -> 后端可接收的 "yyyy-MM-dd HH:mm:ss"
 */
function toServerDateTime(date, time) {
  const d = date || today();
  const t = time || currentTime();
  return d + ' ' + t + ':00';
}

/**
 * 判断 "yyyy-MM-dd" + "HH:mm" 是否晚于当前时间
 */
function isFutureDateTime(date, time) {
  if (!date) {
    return false;
  }
  const target = new Date((date + ' ' + (time || '00:00')).replace(/-/g, '/') + ':00');
  return target.getTime() > Date.now();
}

/**
 * 安全解码页面参数（小程序不保证对 query 解码，且重复解码可能抛错）
 */
function safeDecode(value) {
  if (!value) {
    return '';
  }
  try {
    return decodeURIComponent(value);
  } catch (e) {
    return value;
  }
}

/**
 * 文件大小展示："12455" -> "12.2 KB"
 */
function formatFileSize(bytes) {
  const size = Number(bytes || 0);
  if (!size) {
    return '—';
  }
  if (size < 1024) {
    return size + ' B';
  }
  if (size < 1024 * 1024) {
    return (size / 1024).toFixed(1) + ' KB';
  }
  return (size / 1024 / 1024).toFixed(1) + ' MB';
}

module.exports = {
  pad2: pad2,
  formatDateTime: formatDateTime,
  formatDate: formatDate,
  formatMoney: formatMoney,
  formatAmount: formatAmount,
  shortText: shortText,
  statusTag: statusTag,
  urgencyTag: urgencyTag,
  today: today,
  currentTime: currentTime,
  nowDateTime: nowDateTime,
  toServerDateTime: toServerDateTime,
  isFutureDateTime: isFutureDateTime,
  safeDecode: safeDecode,
  formatFileSize: formatFileSize
};
