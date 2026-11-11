import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'

/**
 * 统一请求封装（设计文档 5.1、5.2）：
 * - 自动附加令牌
 * - 统一解析 {code, message, data}
 * - 40101 清除本地状态并回到登录页；其他错误码统一提示
 */
const http = axios.create({
  baseURL: '/api/v1',
  timeout: 20000,
  validateStatus: () => true
})

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const { config, data } = response
    if (config.responseType === 'blob') {
      const isJson = (data && data.type && data.type.indexOf('json') >= 0)
      if (isJson) {
        return data.text().then((text) => {
          let body = {}
          try { body = JSON.parse(text) } catch (e) { body = { code: 50001, message: '导出文件失败' } }
          handleError(body.code, body.message, config.url)
          return Promise.reject(new Error(body.message || '请求失败'))
        })
      }
      return data
    }
    const body = data || {}
    if (body.code === 0) {
      return body.data
    }
    handleError(body.code, body.message, config.url)
    return Promise.reject(Object.assign(new Error(body.message || '请求失败'), { code: body.code }))
  },
  (error) => {
    const message = error.code === 'ECONNABORTED'
      ? '请求超时，请稍后重试'
      : '网络异常，请检查后端服务是否已启动'
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

function handleError(code, message, url) {
  if (code === 40101) {
    const auth = useAuthStore()
    const isLoginRequest = (url || '').indexOf('/auth/login') >= 0
    if (!isLoginRequest) {
      auth.clear()
      if (router.currentRoute.value.path !== '/login') {
        router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
      }
      ElMessage.error('登录状态已失效，请重新登录')
      return
    }
  }
  ElMessage.error(message || '请求失败')
}

/** 以二进制方式获取受鉴权保护的图片，返回 blob URL */
export async function fetchImageBlob(url, width) {
  const auth = useAuthStore()
  const response = await http.get(url, {
    responseType: 'blob',
    params: width ? { width } : {},
    headers: { Authorization: `Bearer ${auth.token}` },
    baseURL: ''
  })
  return URL.createObjectURL(response)
}

export default http
