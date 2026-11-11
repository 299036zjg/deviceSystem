const auth = require('../../utils/auth');
const equip = require('../../utils/equipment');
const format = require('../../utils/format');
const request = require('../../utils/request');

/**
 * 扫码页（EM010/EM012）
 * - 扫码模式（报修人/默认）：wx.scanCode -> GET /equipments/by-qr，展示设备与 canReport
 * - 核对模式（维修人员，带 orderId）：wx.scanCode -> GET /equipments/verify，展示 matched
 * - 相机授权被拒绝时给出说明，并始终保留"手动输入设备编号"入口
 */
Page({
  data: {
    mode: 'scan', // scan | verify
    orderId: '',
    scanning: false,
    lookupLoading: false,
    manualCode: '',
    equipment: null,
    canReport: false,
    message: '',
    error: '',
    cameraDenied: false,
    verifyResult: null,
    relatedOrders: []
  },

  onLoad(options) {
    if (!auth.ensureLogin()) {
      return;
    }
    const opts = options || {};
    const orderId = opts.orderId ? String(opts.orderId) : '';
    const mode = opts.mode === 'verify' || orderId ? 'verify' : 'scan';
    this.setData({ mode: mode, orderId: orderId });
    wx.setNavigationBarTitle({ title: mode === 'verify' ? '扫码核对' : '扫码识别' });
  },

  onManualInput(e) {
    this.setData({ manualCode: e.detail.value });
  },

  /**
   * 调起微信扫码
   */
  doScan() {
    if (this.data.scanning) {
      return;
    }
    this.setData({ scanning: true, error: '', cameraDenied: false });
    equip
      .scanQr()
      .then((content) => {
        this.setData({ scanning: false });
        this.handleContent(content);
      })
      .catch((err) => {
        this.setData({ scanning: false });
        if (equip.isCancel(err)) {
          return; // 用户主动取消，不提示
        }
        if (equip.isCameraDenied(err)) {
          this.setData({ cameraDenied: true });
          return;
        }
        this.setData({ error: equip.describeScanError(err) });
      });
  },

  /**
   * 打开设置页以开启相机权限
   */
  openSetting() {
    wx.openSetting({
      success: () => {
        this.setData({ cameraDenied: false });
      },
      fail: () => {
        request.toast('请在微信「···」→「设置」中开启相机权限');
      }
    });
  },

  /**
   * 手动输入设备编号查询（自动补 EQ: 前缀）
   */
  doManualQuery() {
    if (this.data.lookupLoading) {
      return;
    }
    const code = (this.data.manualCode || '').trim();
    if (!code) {
      request.toast('请输入设备编号');
      return;
    }
    this.handleContent(equip.normalizeManualContent(code));
  },

  /**
   * 按当前模式处理二维码内容 / 手动编号
   */
  handleContent(content) {
    if (this.data.mode === 'verify' && this.data.orderId) {
      this.doVerify(content);
      return;
    }
    this.doLookup(content);
  },

  /**
   * 扫码识别设备（GET /equipments/by-qr）
   */
  doLookup(content) {
    this.setData({ lookupLoading: true, error: '', equipment: null, verifyResult: null, relatedOrders: [] });
    equip
      .fetchByQr(content)
      .then((data) => {
        const equipment = (data && data.equipment) || null;
        this.setData({
          lookupLoading: false,
          equipment: equipment,
          canReport: !!(data && data.canReport),
          message: (data && data.message) || ''
        });
        if (equipment && auth.isTechnician()) {
          this.loadRelatedOrders(equipment.equipmentId);
        }
      })
      .catch((err) => {
        this.setData({
          lookupLoading: false,
          error: (err && err.message) || '设备识别失败，请确认二维码或设备编号是否正确。'
        });
      });
  },

  /**
   * 到场核对工单设备（GET /equipments/verify）
   */
  doVerify(content) {
    this.setData({ lookupLoading: true, error: '', verifyResult: null, relatedOrders: [] });
    equip
      .verifyOrderEquipment(this.data.orderId, content)
      .then((data) => {
        const result = {
          matched: !!(data && data.matched),
          message: (data && data.message) || '',
          orderEquipment: (data && data.orderEquipment) || null,
          scannedEquipment: (data && data.scannedEquipment) || null
        };
        this.setData({
          lookupLoading: false,
          equipment: result.scannedEquipment,
          canReport: false,
          message: result.message,
          verifyResult: result
        });
        // 核对结果回传给工单详情页（页面间回传复杂对象走 globalData）
        getApp().globalData.lastVerify = {
          orderId: this.data.orderId,
          matched: result.matched,
          message: result.message,
          scannedCode: result.scannedEquipment ? result.scannedEquipment.equipmentCode : '',
          scannedEquipment: result.scannedEquipment
        };
      })
      .catch((err) => {
        this.setData({
          lookupLoading: false,
          error: (err && err.message) || '核对失败，请确认工单与设备二维码是否正确。'
        });
      });
  },

  /**
   * 维修人员：查询该设备下我的未完成工单
   */
  loadRelatedOrders(equipmentId) {
    equip.findMyOrdersForEquipment(equipmentId).then((items) => {
      const list = (items || []).map((item) => {
        return {
          orderId: item.orderId,
          orderNo: item.orderNo,
          status: item.status,
          statusClass: format.statusTag(item.statusCode, item.status),
          typeLabel: item.orderTypeLabel || '',
          equipmentName: item.equipmentName,
          equipmentCode: item.equipmentCode
        };
      });
      this.setData({ relatedOrders: list });
    });
  },

  goOrderDetail(e) {
    const id = e.currentTarget.dataset.id;
    if (!id) {
      return;
    }
    wx.navigateTo({ url: '/pages/order/detail?id=' + id });
  },

  goOrderList() {
    wx.navigateTo({ url: '/pages/order/list' });
  },

  /**
   * 带着已识别设备进入报修表单
   */
  goCreateRepair() {
    const eq = this.data.equipment;
    if (!eq) {
      request.toast('请先识别设备');
      return;
    }
    if (!this.data.canReport) {
      request.toast('该设备当前不可报修');
      return;
    }
    const query = [
      'equipmentId=' + encodeURIComponent(eq.equipmentId),
      'equipmentCode=' + encodeURIComponent(eq.equipmentCode),
      'equipmentName=' + encodeURIComponent(eq.equipmentName || ''),
      'equipmentLocation=' + encodeURIComponent(eq.location || '')
    ].join('&');
    wx.navigateTo({ url: '/pages/repair/create?' + query });
  },

  /**
   * 核对完成后返回工单详情
   */
  backToOrder() {
    wx.navigateBack({ delta: 1 });
  }
});
