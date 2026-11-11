const auth = require('../../utils/auth');
const config = require('../../utils/config');
const dict = require('../../utils/dict');
const equip = require('../../utils/equipment');
const format = require('../../utils/format');
const request = require('../../utils/request');
const uploader = require('../../utils/upload');
const uuid = require('../../utils/uuid');

/**
 * 报修提交页（EM011）
 * 流程：选设备（扫码/手动编号）-> 填写故障信息 -> 逐张上传照片 -> POST /repair-requests
 * 照片先 POST /files 拿到 attachmentId，再随报修单一起提交（bizType=REPAIR）
 */
Page({
  data: {
    equipment: null,
    canReport: false,
    equipmentMessage: '',
    scanning: false,
    lookupLoading: false,
    manualVisible: false,
    manualCode: '',
    description: '',
    faultTypes: [],
    faultTypeIndex: -1,
    faultTypeText: '',
    urgencies: [],
    urgencyIndex: -1,
    urgencyText: '',
    discoveredDate: '',
    discoveredTime: '',
    maxDate: '',
    photos: [],
    maxPhotoCount: config.maxPhotoCount,
    submitting: false
  },

  onLoad(options) {
    if (!auth.ensureLogin()) {
      return;
    }
    // submitToken 只生成一次，重试提交时复用，保证后端幂等（BR03）
    this.submitToken = uuid.uuid();
    this.photoSeed = 0;

    // 发现时间默认当前时间，日期选择上限为今天（不可选未来）
    this.setData({
      discoveredDate: format.today(),
      discoveredTime: format.currentTime(),
      maxDate: format.today()
    });

    this.loadDictionaries();

    const opts = options || {};
    if (opts.equipmentId) {
      // 从扫码页带入的设备：仅用于首屏展示，随后用 by-qr 复核 canReport
      const equipment = {
        equipmentId: Number(opts.equipmentId),
        equipmentCode: format.safeDecode(opts.equipmentCode),
        equipmentName: format.safeDecode(opts.equipmentName),
        department: '',
        location: format.safeDecode(opts.equipmentLocation)
      };
      this.setData({ equipment: equipment });
      if (equipment.equipmentCode) {
        this.fetchEquipmentByContent(equip.normalizeManualContent(equipment.equipmentCode), true);
      }
    }
  },

  loadDictionaries() {
    dict.load().then((data) => {
      this.setData({
        faultTypes: dict.faultTypes(data),
        urgencies: dict.urgencies(data)
      });
    });
  },

  /* ---------------- 设备选择 ---------------- */

  toggleManual() {
    this.setData({ manualVisible: !this.data.manualVisible });
  },

  onManualInput(e) {
    this.setData({ manualCode: e.detail.value });
  },

  onScanEquipment() {
    if (this.data.scanning) {
      return;
    }
    this.setData({ scanning: true });
    equip
      .scanQr()
      .then((content) => {
        this.setData({ scanning: false });
        this.fetchEquipmentByContent(content);
      })
      .catch((err) => {
        this.setData({ scanning: false });
        if (equip.isCancel(err)) {
          return;
        }
        if (equip.isCameraDenied(err)) {
          this.setData({ manualVisible: true });
          wx.showModal({
            title: '相机权限被拒绝',
            content: '无法扫码识别设备。可在右上角「···」→「设置」中开启相机权限，或使用手动输入设备编号。',
            showCancel: false
          });
          return;
        }
        this.setData({ manualVisible: true });
        request.toast('扫码失败，请使用手动输入设备编号');
      });
  },

  onManualQuery() {
    if (this.data.lookupLoading) {
      return;
    }
    const code = (this.data.manualCode || '').trim();
    if (!code) {
      request.toast('请输入设备编号');
      return;
    }
    this.fetchEquipmentByContent(equip.normalizeManualContent(code));
  },

  /**
   * 用二维码内容（EQ:编号）识别设备，得到 canReport 与提示语
   */
  fetchEquipmentByContent(content, silent) {
    this.setData({ lookupLoading: true });
    return equip
      .fetchByQr(content)
      .then((data) => {
        const equipment = (data && data.equipment) || null;
        const canReport = !!(data && data.canReport);
        this.setData({
          lookupLoading: false,
          equipment: equipment,
          canReport: canReport,
          equipmentMessage: (data && data.message) || '',
          manualVisible: equipment ? this.data.manualVisible : true
        });
        if (equipment && !silent) {
          wx.showToast({ title: canReport ? '设备已识别' : '该设备不可报修', icon: 'none' });
        }
      })
      .catch((err) => {
        this.setData({ lookupLoading: false, equipment: null, canReport: false });
        if (!silent) {
          request.toast((err && err.message) || '设备识别失败');
        } else {
          this.setData({ equipmentMessage: (err && err.message) || '设备信息校验失败' });
        }
      });
  },

  /* ---------------- 表单字段 ---------------- */

  onDescriptionInput(e) {
    this.setData({ description: e.detail.value });
  },

  onFaultTypeChange(e) {
    const index = Number(e.detail.value);
    this.setData({
      faultTypeIndex: index,
      faultTypeText: this.data.faultTypes[index] || ''
    });
  },

  onUrgencyChange(e) {
    const index = Number(e.detail.value);
    this.setData({
      urgencyIndex: index,
      urgencyText: this.data.urgencies[index] || ''
    });
  },

  onDateChange(e) {
    this.applyDiscovered(e.detail.value, this.data.discoveredTime);
  },

  onTimeChange(e) {
    this.applyDiscovered(this.data.discoveredDate, e.detail.value);
  },

  /**
   * 发现时间不可晚于当前时间：超出的选择回退到当前时间
   */
  applyDiscovered(date, time) {
    if (format.isFutureDateTime(date, time)) {
      request.toast('发现时间不能晚于当前时间');
      this.setData({
        discoveredDate: format.today(),
        discoveredTime: format.currentTime()
      });
      return;
    }
    this.setData({ discoveredDate: date, discoveredTime: time });
  },

  /* ---------------- 现场照片 ---------------- */

  choosePhoto() {
    const remain = this.data.maxPhotoCount - this.data.photos.length;
    if (remain <= 0) {
      request.toast('最多上传 ' + this.data.maxPhotoCount + ' 张照片');
      return;
    }
    wx.chooseMedia({
      count: remain,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      sizeType: ['compressed', 'original'],
      success: (res) => {
        const files = (res && res.tempFiles) || [];
        const photos = this.data.photos.slice();
        let rejected = '';
        files.forEach((file) => {
          if (photos.length >= this.data.maxPhotoCount) {
            return;
          }
          const error = uploader.checkImage(file.tempFilePath, file.size);
          if (error) {
            rejected = error;
            return;
          }
          this.photoSeed += 1;
          photos.push({
            key: 'p' + this.photoSeed,
            tempFilePath: file.tempFilePath,
            size: file.size,
            sizeText: format.formatFileSize(file.size),
            status: 'pending',
            progress: 0,
            attachmentId: null,
            error: ''
          });
        });
        this.setData({ photos: photos });
        if (rejected) {
          request.toast(rejected);
        }
      },
      fail: (err) => {
        if (/cancel/i.test((err && err.errMsg) || '')) {
          return;
        }
        request.toast('选择照片失败，请检查相机/相册权限');
      }
    });
  },

  removePhoto(e) {
    const index = Number(e.currentTarget.dataset.index);
    const photos = this.data.photos.slice();
    photos.splice(index, 1);
    this.setData({ photos: photos });
  },

  previewPhoto(e) {
    const index = Number(e.currentTarget.dataset.index);
    const urls = this.data.photos.map((item) => item.tempFilePath);
    if (!urls.length) {
      return;
    }
    wx.previewImage({ urls: urls, current: urls[index] });
  },

  /**
   * 单张重传：不清空其它已填内容与已上传结果
   */
  retryPhoto(e) {
    const index = Number(e.currentTarget.dataset.index);
    const photo = this.data.photos[index];
    if (!photo || photo.status === 'uploading') {
      return;
    }
    this.uploadOne(index).catch(() => {
      // 失败状态已在 uploadOne 中标记，提示由提交时统一给出
    });
  },

  /**
   * 上传单张照片（POST /files，bizType=REPAIR）
   */
  uploadOne(index) {
    const photo = this.data.photos[index];
    if (!photo) {
      return Promise.reject({ message: '照片不存在' });
    }
    const key = 'photos[' + index + ']';
    const patch = {};
    patch[key + '.status'] = 'uploading';
    patch[key + '.progress'] = 0;
    patch[key + '.error'] = '';
    this.setData(patch);

    return uploader
      .uploadImage({
        filePath: photo.tempFilePath,
        bizType: 'REPAIR',
        sortNo: index + 1,
        onProgress: (progress) => {
          const update = {};
          update[key + '.progress'] = progress;
          this.setData(update);
        }
      })
      .then((attachment) => {
        const done = {};
        done[key + '.status'] = 'done';
        done[key + '.progress'] = 100;
        done[key + '.attachmentId'] = attachment.attachmentId;
        this.setData(done);
        return attachment;
      })
      .catch((err) => {
        const failed = {};
        failed[key + '.status'] = 'failed';
        failed[key + '.error'] = (err && err.message) || '上传失败';
        this.setData(failed);
        throw err;
      });
  },

  /**
   * 串行上传所有未完成的照片，返回按顺序排列的 attachmentIds
   */
  uploadAll() {
    const photos = this.data.photos;
    const ids = [];
    let chain = Promise.resolve();
    photos.forEach((photo, index) => {
      chain = chain.then(() => {
        if (photo.status === 'done' && photo.attachmentId) {
          ids.push(photo.attachmentId);
          return null;
        }
        return this.uploadOne(index).then((attachment) => {
          ids.push(attachment.attachmentId);
        });
      });
    });
    return chain.then(() => ids);
  },

  /* ---------------- 提交 ---------------- */

  validate() {
    if (!this.data.equipment) {
      request.toast('请先扫码或手动输入设备编号选择设备');
      return false;
    }
    if (!this.data.canReport) {
      request.toast(this.data.equipmentMessage || '该设备当前不可报修');
      return false;
    }
    const description = (this.data.description || '').trim();
    if (!description) {
      request.toast('请填写故障描述');
      return false;
    }
    if (description.length > 1000) {
      request.toast('故障描述不能超过 1000 字');
      return false;
    }
    if (this.data.faultTypeIndex < 0) {
      request.toast('请选择故障类型');
      return false;
    }
    if (this.data.urgencyIndex < 0) {
      request.toast('请选择紧急程度');
      return false;
    }
    if (!this.data.discoveredDate || !this.data.discoveredTime) {
      request.toast('请选择发现时间');
      return false;
    }
    if (format.isFutureDateTime(this.data.discoveredDate, this.data.discoveredTime)) {
      request.toast('发现时间不能晚于当前时间');
      return false;
    }
    return true;
  },

  onSubmit() {
    if (this.data.submitting) {
      return;
    }
    if (!this.validate()) {
      return;
    }

    this.setData({ submitting: true });
    wx.showLoading({ title: '提交中', mask: true });

    this.uploadAll()
      .then((attachmentIds) => {
        const body = {
          equipmentId: this.data.equipment.equipmentId,
          equipmentCode: this.data.equipment.equipmentCode,
          description: (this.data.description || '').trim(),
          faultType: this.data.faultTypeText,
          urgency: this.data.urgencyText,
          discoveredAt: format.toServerDateTime(this.data.discoveredDate, this.data.discoveredTime),
          attachmentIds: attachmentIds,
          // 一次性提交标识：本次表单复用同一个 token，重复提交不会产生重复单据
          submitToken: this.submitToken,
          source: config.sourceMini
        };
        return request.post('/repair-requests', body, { showError: false });
      })
      .then((detail) => {
        wx.hideLoading();
        const requestNo = detail && detail.summary ? detail.summary.requestNo : '';
        wx.showToast({
          title: requestNo ? '报修成功 ' + requestNo : '报修成功',
          icon: 'none',
          duration: 2000
        });
        setTimeout(function () {
          wx.redirectTo({ url: '/pages/repair/list' });
        }, 1600);
      })
      .catch((err) => {
        wx.hideLoading();
        this.setData({ submitting: false });
        const message = (err && err.message) || '提交失败，请稍后重试';
        wx.showModal({ title: '提交失败', content: message, showCancel: false });
      });
  }
});
