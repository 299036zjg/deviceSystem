const auth = require('../../utils/auth');
const config = require('../../utils/config');
const format = require('../../utils/format');
const media = require('../../utils/media');
const request = require('../../utils/request');
const uploader = require('../../utils/upload');
const uuid = require('../../utils/uuid');

/**
 * 工单详情与处理（EM012）
 * GET /work-orders/{id} 返回：
 *   { summary, description, discoveredAt, decisionReason, records[], acceptances[], repairPhotos[], workPhotos[] }
 * 状态与按钮严格按 summary.statusCode 控制：
 *   PENDING -> 开始维修；PROCESSING -> 填写并提交完工；PENDING_ACCEPTANCE / COMPLETED -> 只读
 */
Page({
  data: {
    id: '',
    loading: true,
    error: '',
    summary: null,
    statusClass: 'tag-gray',
    typeClass: 'tag-blue',
    isMaintenance: false,
    taskContent: '',
    discoveredAtText: '',
    createdAtText: '',
    startedAtText: '',
    completedAtText: '',
    plannedDateText: '',
    costText: '',
    repairPhotos: [],
    records: [],
    acceptances: [],
    returnOpinionText: '',
    canStart: false,
    canSubmit: false,
    readOnly: false,
    statusHint: '',
    verified: 0, // 0 未核对 / 1 一致 / 2 不一致
    verifyMessage: '',
    verifiedCode: '',
    starting: false,
    submitting: false,
    form: {
      cause: '',
      process: '',
      result: '',
      replacedParts: '',
      totalCost: ''
    },
    photos: [],
    maxPhotoCount: config.maxPhotoCount
  },

  onLoad(options) {
    if (!auth.ensureLogin()) {
      return;
    }
    // 一次性提交标识：同一表单重试复用；提交成功后重新生成（整改再次提交）
    this.submitToken = uuid.uuid();
    this.photoSeed = 0;
    this.setData({ id: (options && options.id) || '' });
    this.load();
  },

  onShow() {
    if (!auth.isLoggedIn()) {
      return;
    }
    // 扫码核对结果由 pages/scan 写入 globalData
    const app = getApp();
    const verify = app.globalData && app.globalData.lastVerify;
    if (verify && String(verify.orderId) === String(this.data.id)) {
      app.globalData.lastVerify = null;
      this.applyVerify(verify);
    }
    if (this._loaded) {
      this.load(true);
    }
    this._loaded = true;
  },

  onPullDownRefresh() {
    this.load(true, true);
  },

  onRetry() {
    this.load();
  },

  /**
   * 加载工单详情
   * @param {boolean} silent 静默刷新（不显示骨架文案）
   * @param {boolean} fromPull 是否来自下拉刷新
   */
  load(silent, fromPull) {
    if (!this.data.id) {
      this.setData({ loading: false, error: '缺少工单标识' });
      return;
    }
    if (!silent) {
      this.setData({ loading: true });
    }
    request
      .get('/work-orders/' + this.data.id, null, { showError: false })
      .then((detail) => {
        const summary = (detail && detail.summary) || {};
        const statusCode = summary.statusCode || '';
        const isMaintenance = summary.orderType === 'MAINTENANCE';
        const records = ((detail && detail.records) || []).map(function (record) {
          return {
            recordId: record.recordId,
            operatorName: record.operatorName,
            cause: record.cause,
            process: record.process,
            result: record.result,
            replacedParts: record.replacedParts,
            submittedAtText: format.formatDateTime(record.submittedAt),
            costText: record.totalCostSnapshot === null || record.totalCostSnapshot === undefined
              ? '—'
              : format.formatMoney(record.totalCostSnapshot),
            photos: [],
            attachmentsRaw: record.attachments || []
          };
        });
        const acceptances = ((detail && detail.acceptances) || []).map(function (item) {
          return {
            acceptanceId: item.acceptanceId,
            inspectorName: item.inspectorName,
            result: item.result,
            resultLabel: item.resultLabel,
            opinion: item.opinion,
            acceptedAtText: format.formatDateTime(item.acceptedAt)
          };
        });

        // 最近一次退回意见（作为整改依据）
        let returnOpinionText = '';
        for (let i = acceptances.length - 1; i >= 0; i -= 1) {
          if (acceptances[i].result === 'RETURN') {
            returnOpinionText = acceptances[i].opinion || '验收退回，请按要求整改后重新提交';
            break;
          }
        }

        const canStart = statusCode === 'PENDING';
        const canSubmit = statusCode === 'PROCESSING';
        const readOnly = statusCode === 'PENDING_ACCEPTANCE' || statusCode === 'COMPLETED';
        let statusHint = '';
        if (statusCode === 'PENDING_ACCEPTANCE') {
          statusHint = '工单已提交，等待管理员验收，期间不能修改维修记录。';
        } else if (statusCode === 'COMPLETED') {
          statusHint = '工单已验收完成，只读查看。';
        }

        this.setData({
          loading: false,
          error: '',
          summary: summary,
          statusClass: format.statusTag(statusCode, summary.status),
          typeClass: isMaintenance ? 'tag-purple' : 'tag-blue',
          isMaintenance: isMaintenance,
          taskContent: isMaintenance ? (summary.taskContent || '') : ((detail && detail.description) || summary.taskContent || ''),
          discoveredAtText: format.formatDateTime((detail && detail.discoveredAt) || summary.createdAt),
          createdAtText: format.formatDateTime(summary.createdAt),
          startedAtText: summary.startedAt ? format.formatDateTime(summary.startedAt) : '',
          completedAtText: summary.completedAt ? format.formatDateTime(summary.completedAt) : '',
          plannedDateText: format.formatDate(summary.plannedDate),
          // 待处理/未提交过的处理中工单尚无费用，展示"待提交"而不是 0.00
          costText: (statusCode === 'PENDING' || (statusCode === 'PROCESSING' && !records.length))
            ? '待提交'
            : (summary.totalCost === null || summary.totalCost === undefined
              ? '—'
              : format.formatMoney(summary.totalCost)),
          records: records,
          acceptances: acceptances,
          returnOpinionText: canSubmit ? returnOpinionText : '',
          canStart: canStart,
          canSubmit: canSubmit,
          readOnly: readOnly,
          statusHint: statusHint,
          repairPhotos: []
        });

        // 现场照片 + 处理记录照片均需带令牌下载；详情页取原图，便于放大查看
        const repairTasks = media.resolveList((detail && detail.repairPhotos) || []);
        const recordTasks = records.map(function (record) {
          return media.resolveList(record.attachmentsRaw).then(function (photos) {
            return Object.assign({}, record, { photos: photos });
          });
        });
        return Promise.all([repairTasks, Promise.all(recordTasks)]).then((results) => {
          // 去掉仅用于下载的原始附件数组，避免把大对象塞进页面数据
          const recordsWithPhotos = results[1].map(function (record) {
            const copy = Object.assign({}, record);
            delete copy.attachmentsRaw;
            return copy;
          });
          this.setData({
            repairPhotos: results[0],
            records: recordsWithPhotos
          });
        });
      })
      .catch((err) => {
        this.setData({
          loading: false,
          error: (err && err.message) || '工单详情加载失败'
        });
      })
      .then(() => {
        if (fromPull) {
          wx.stopPullDownRefresh();
        }
      });
  },

  /* ---------------- 到场核对 ---------------- */

  onScanVerify() {
    wx.navigateTo({ url: '/pages/scan/scan?mode=verify&orderId=' + this.data.id });
  },

  applyVerify(verify) {
    this.setData({
      verified: verify.matched ? 1 : 2,
      verifyMessage: verify.message || '',
      verifiedCode: verify.scannedCode || ''
    });
    if (verify.matched) {
      wx.showToast({ title: '设备核对一致', icon: 'success' });
    } else {
      wx.showModal({
        title: '设备核对不一致',
        content: (verify.message || '扫码设备与工单设备不一致') + '\n核对不一致时不能提交完工。',
        showCancel: false
      });
    }
  },

  /* ---------------- 开工 ---------------- */

  onStart() {
    if (this.data.starting) {
      return;
    }
    wx.showModal({
      title: '开始维修',
      content: '确认已到场并开始维修该工单？',
      success: (res) => {
        if (res.confirm) {
          this.doStart();
        }
      }
    });
  },

  doStart() {
    this.setData({ starting: true });
    const body = {};
    // 已核对一致时带上设备编号，后端会再次校验（POST /work-orders/{id}/start）
    if (this.data.verified === 1 && this.data.verifiedCode) {
      body.equipmentCode = this.data.verifiedCode;
    }
    request
      .post('/work-orders/' + this.data.id + '/start', body, { showError: false, loading: true, loadingText: '开工中' })
      .then(() => {
        wx.showToast({ title: '已开始维修', icon: 'success' });
        this.setData({ starting: false });
        this.load(true);
      })
      .catch((err) => {
        this.setData({ starting: false });
        wx.showModal({
          title: '开工失败',
          content: (err && err.message) || '请稍后重试',
          showCancel: false
        });
      });
  },

  /* ---------------- 完工表单 ---------------- */

  onCauseInput(e) {
    this.setData({ 'form.cause': e.detail.value });
  },

  onProcessInput(e) {
    this.setData({ 'form.process': e.detail.value });
  },

  onResultInput(e) {
    this.setData({ 'form.result': e.detail.value });
  },

  onPartsInput(e) {
    this.setData({ 'form.replacedParts': e.detail.value });
  },

  onCostInput(e) {
    // 仅允许数字与一个小数点，减少非法输入
    let value = (e.detail.value || '').replace(/[^\d.]/g, '');
    const firstDot = value.indexOf('.');
    if (firstDot >= 0) {
      value = value.slice(0, firstDot + 1) + value.slice(firstDot + 1).replace(/\./g, '');
    }
    this.setData({ 'form.totalCost': value });
    return value;
  },

  /* ---------------- 完工照片 ---------------- */

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
            key: 'w' + this.photoSeed,
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

  previewRepairPhoto(e) {
    const index = Number(e.currentTarget.dataset.index);
    media.previewLoaded(this.data.repairPhotos, index);
  },

  previewRecordPhoto(e) {
    const recordIndex = Number(e.currentTarget.dataset.record);
    const photoIndex = Number(e.currentTarget.dataset.index);
    const record = this.data.records[recordIndex];
    if (!record) {
      return;
    }
    media.previewLoaded(record.photos, photoIndex);
  },

  retryPhoto(e) {
    const index = Number(e.currentTarget.dataset.index);
    const photo = this.data.photos[index];
    if (!photo || photo.status === 'uploading') {
      return;
    }
    this.uploadOne(index).catch(() => {
      // 失败状态已在 uploadOne 中标记
    });
  },

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
        bizType: 'WORK',
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

  /* ---------------- 提交完工 ---------------- */

  validateForm() {
    const form = this.data.form;
    const cause = (form.cause || '').trim();
    const process = (form.process || '').trim();
    const result = (form.result || '').trim();
    const replacedParts = (form.replacedParts || '').trim();
    const costText = (form.totalCost || '').trim();

    if (cause.length > 500) {
      request.toast('故障原因不能超过 500 字');
      return false;
    }
    if (process.length > 1000) {
      request.toast('维修过程不能超过 1000 字');
      return false;
    }
    if (!result) {
      request.toast('请填写处理结果');
      return false;
    }
    if (result.length > 1500) {
      request.toast('处理结果不能超过 1500 字');
      return false;
    }
    if (replacedParts.length > 500) {
      request.toast('更换零部件说明不能超过 500 字');
      return false;
    }
    if (!costText) {
      request.toast('请填写本单总费用');
      return false;
    }
    if (!/^\d{1,8}(\.\d{1,2})?$/.test(costText)) {
      request.toast('总费用须为不小于 0 的数字，最多两位小数且不超过 99 999 999.99');
      return false;
    }
    if (!this.data.photos.length) {
      request.toast('请上传至少 1 张维修完成照片');
      return false;
    }
    if (this.data.photos.length > this.data.maxPhotoCount) {
      request.toast('最多上传 ' + this.data.maxPhotoCount + ' 张维修完成照片');
      return false;
    }
    if (this.data.verified === 2) {
      request.toast('设备核对不一致，请重新扫码核对后再提交完工');
      return false;
    }
    return true;
  },

  onSubmitComplete() {
    if (this.data.submitting) {
      return;
    }
    if (!this.data.canSubmit) {
      request.toast('当前状态不能提交完工');
      return;
    }
    if (!this.validateForm()) {
      return;
    }

    const form = this.data.form;
    const costText = (form.totalCost || '').trim();
    this.setData({ submitting: true });
    wx.showLoading({ title: '提交中', mask: true });

    this.uploadAll()
      .then((attachmentIds) => {
        if (!attachmentIds.length) {
          throw { message: '请上传至少 1 张维修完成照片' };
        }
        const body = {
          cause: (form.cause || '').trim(),
          process: (form.process || '').trim(),
          result: (form.result || '').trim(),
          replacedParts: (form.replacedParts || '').trim(),
          totalCost: Number(costText),
          attachmentIds: attachmentIds,
          submitToken: this.submitToken
        };
        return request.post('/work-orders/' + this.data.id + '/submit', body, { showError: false });
      })
      .then(() => {
        wx.hideLoading();
        wx.showToast({ title: '已提交完工，等待验收', icon: 'none', duration: 2000 });
        // 提交成功后重置表单并重新生成提交标识（退回整改时可再次提交）
        this.submitToken = uuid.uuid();
        this.setData({
          submitting: false,
          photos: [],
          form: { cause: '', process: '', result: '', replacedParts: '', totalCost: '' }
        });
        // 记录已关联附件，清空本地照片后刷新详情
        this.load(true);
      })
      .catch((err) => {
        wx.hideLoading();
        this.setData({ submitting: false });
        wx.showModal({
          title: '提交失败',
          content: (err && err.message) || '请稍后重试',
          showCancel: false
        });
      });
  }
});
