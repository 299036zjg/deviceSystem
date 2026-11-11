import http from './http'

/** 鉴权与会话 */
export const authApi = {
  login: (data) => http.post('/auth/login', data),
  logout: () => http.post('/auth/logout'),
  profile: () => http.get('/auth/profile'),
  changePassword: (data) => http.post('/auth/password', data)
}

/** 账号管理（管理员） */
export const userApi = {
  list: (params) => http.get('/users', { params }),
  technicians: () => http.get('/users/technicians'),
  create: (data) => http.post('/users', data),
  disable: (id) => http.post(`/users/${id}/disable`),
  enable: (id) => http.post(`/users/${id}/enable`),
  resetPassword: (id, password) => http.post(`/users/${id}/reset-password`, { password })
}

/** 设备台账与二维码 */
export const equipmentApi = {
  list: (params) => http.get('/equipments', { params }),
  get: (id) => http.get(`/equipments/${id}`),
  history: (id) => http.get(`/equipments/${id}/history`),
  create: (data) => http.post('/equipments', data),
  update: (id, data) => http.put(`/equipments/${id}`, data),
  disable: (id) => http.post(`/equipments/${id}/disable`),
  enable: (id) => http.post(`/equipments/${id}/enable`),
  byQr: (content) => http.get('/equipments/by-qr', { params: { content } }),
  verify: (orderId, content) => http.get('/equipments/verify', { params: { orderId, content } }),
  qrcodeUrl: (id) => `/api/v1/equipments/${id}/qrcode`,
  batchQrcode: (equipmentIds) => http.post('/equipments/qrcodes', { equipmentIds })
}

/** 故障报修 */
export const repairApi = {
  create: (data) => http.post('/repair-requests', data),
  list: (params) => http.get('/repair-requests', { params }),
  mine: (params) => http.get('/repair-requests/mine', { params }),
  detail: (id) => http.get(`/repair-requests/${id}`),
  attachments: (id) => http.get(`/repair-requests/${id}/attachments`),
  accept: (id, data) => http.post(`/repair-requests/${id}/accept`, data),
  reject: (id, data) => http.post(`/repair-requests/${id}/reject`, data)
}

/** 工单、保养、完工与验收 */
export const orderApi = {
  list: (params) => http.get('/work-orders', { params }),
  mine: (params) => http.get('/work-orders/mine', { params }),
  detail: (id) => http.get(`/work-orders/${id}`),
  attachments: (id) => http.get(`/work-orders/${id}/attachments`),
  createMaintenance: (data) => http.post('/work-orders', data),
  start: (id, data) => http.post(`/work-orders/${id}/start`, data || {}),
  submit: (id, data) => http.post(`/work-orders/${id}/submit`, data),
  acceptance: (id, data) => http.post(`/work-orders/${id}/acceptance`, data),
  pendingAcceptances: (params) => http.get('/acceptances/pending', { params })
}

/** 图片上传 */
export const fileApi = {
  upload: (file, bizType, sortNo) => {
    const form = new FormData()
    form.append('file', file)
    form.append('bizType', bizType)
    if (sortNo) form.append('sortNo', sortNo)
    return http.post('/files', form, { headers: { 'Content-Type': 'multipart/form-data' } })
  }
}

/** 统计与字典 */
export const statApi = {
  statistics: (params) => http.get('/statistics', { params }),
  dashboard: () => http.get('/dashboard'),
  dictionaries: () => http.get('/dictionaries')
}
