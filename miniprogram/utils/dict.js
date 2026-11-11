/**
 * 字典数据（GET /dictionaries）读取与缓存
 *
 * 接口返回结构（已实测）：
 * {
 *   faultTypes: ["机械故障", ...],
 *   urgencies: ["一般","较高","紧急"],
 *   requestStatuses: [{code:"PENDING",label:"待受理"}, ...],
 *   orderStatuses: [{code:"PENDING",label:"待处理"}, ...],
 *   orderTypes / acceptanceResults / sources: [{code,label}]
 * }
 * 字典属于低频变化数据，可缓存；业务单据状态一律以服务端实时返回为准。
 */
const request = require('./request');

const CACHE_KEY = 'sems_dictionaries';

// 接口不可用时的兜底（取值与线上接口一致，仅用于让筛选/选项仍可展示）
const FALLBACK = {
  faultTypes: ['机械故障', '电气故障', '控制系统故障', '液压气动故障', '其他'],
  urgencies: ['一般', '较高', '紧急'],
  requestStatuses: [
    { code: 'PENDING', label: '待受理' },
    { code: 'REJECTED', label: '已驳回' },
    { code: 'ACCEPTED', label: '已受理' },
    { code: 'COMPLETED', label: '已完成' }
  ],
  orderStatuses: [
    { code: 'PENDING', label: '待处理' },
    { code: 'PROCESSING', label: '处理中' },
    { code: 'PENDING_ACCEPTANCE', label: '待验收' },
    { code: 'COMPLETED', label: '已完成' }
  ],
  orderTypes: [
    { code: 'REPAIR', label: '维修' },
    { code: 'MAINTENANCE', label: '保养' }
  ],
  acceptanceResults: [
    { code: 'PASS', label: '通过' },
    { code: 'RETURN', label: '退回' }
  ],
  sources: [
    { code: 'WEB', label: 'Web 后台' },
    { code: 'MINI', label: '微信小程序' }
  ]
};

let memory = null;

function getCached() {
  if (memory) {
    return memory;
  }
  const stored = wx.getStorageSync(CACHE_KEY);
  if (stored && stored.faultTypes) {
    memory = stored;
  }
  return memory;
}

/**
 * 获取字典；force 为 true 时强制刷新
 * 请求失败时不打扰用户，返回缓存或兜底数据
 */
function load(force) {
  const cached = getCached();
  if (cached && !force) {
    return Promise.resolve(cached);
  }
  return request
    .get('/dictionaries', null, { showError: false })
    .then(function (data) {
      memory = data;
      wx.setStorageSync(CACHE_KEY, data);
      return data;
    })
    .catch(function () {
      return cached || FALLBACK;
    });
}

/**
 * 报修状态筛选 tab（含"全部"）
 */
function requestTabs(dict) {
  const statuses = (dict && dict.requestStatuses) || FALLBACK.requestStatuses;
  return [{ code: '', label: '全部' }].concat(statuses);
}

/**
 * 工单状态筛选 tab（含"全部"）
 */
function orderTabs(dict) {
  const statuses = (dict && dict.orderStatuses) || FALLBACK.orderStatuses;
  return [{ code: '', label: '全部' }].concat(statuses);
}

function faultTypes(dict) {
  return (dict && dict.faultTypes && dict.faultTypes.length ? dict.faultTypes : FALLBACK.faultTypes) || [];
}

function urgencies(dict) {
  return (dict && dict.urgencies && dict.urgencies.length ? dict.urgencies : FALLBACK.urgencies) || [];
}

module.exports = {
  load: load,
  getCached: getCached,
  requestTabs: requestTabs,
  orderTabs: orderTabs,
  faultTypes: faultTypes,
  urgencies: urgencies,
  FALLBACK: FALLBACK
};
