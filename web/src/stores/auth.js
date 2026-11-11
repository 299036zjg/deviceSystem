import { defineStore } from 'pinia'
import { authApi } from '@/api/modules'

const TOKEN_KEY = 'sems_token'
const USER_KEY = 'sems_user'

/**
 * 登录状态：令牌与用户信息保存在 localStorage，刷新页面后仍可继续使用。
 * 退出、改密、账号停用后服务端令牌失效，任意请求返回 40101 即回到登录页。
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  }),
  getters: {
    isLogin: (state) => !!state.token,
    role: (state) => state.user?.role || '',
    roleLabel: (state) => state.user?.roleLabel || '',
    displayName: (state) => state.user?.name || '',
    isAdmin: (state) => state.user?.role === 'ADMIN',
    isReporter: (state) => state.user?.role === 'REPORTER',
    isTechnician: (state) => state.user?.role === 'TECHNICIAN'
  },
  actions: {
    async login(account, password) {
      const data = await authApi.login({ account, password })
      this.token = data.token
      this.user = {
        userId: data.userId,
        account: data.account,
        name: data.name,
        department: data.department,
        role: data.role,
        roleLabel: data.roleLabel,
        mustChangePassword: !!data.mustChangePassword
      }
      localStorage.setItem(TOKEN_KEY, this.token)
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
      return data
    },
    async refreshProfile() {
      const profile = await authApi.profile()
      this.user = {
        userId: profile.userId,
        account: profile.account,
        name: profile.name,
        department: profile.department,
        role: profile.role,
        roleLabel: profile.roleLabel,
        mustChangePassword: !!profile.mustChangePassword
      }
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
      return profile
    },
    async logout() {
      try {
        await authApi.logout()
      } catch (e) {
        // 令牌可能已失效，忽略退出接口失败
      }
      this.clear()
    },
    clear() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})
