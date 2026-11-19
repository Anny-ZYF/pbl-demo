import { defineStore } from 'pinia'
import { ApiError } from '@/api/response'
import * as authApi from '@/api/auth'
import {
  clearStoredSession,
  readStoredToken,
  readStoredUser,
  writeStoredSession
} from '@/utils/session'
import { homeForRole, normalizeRole } from '@/utils/roles'

function normalizeUser(user) {
  if (!user || typeof user !== 'object') return null
  const role = normalizeRole(user.role)
  return role ? { ...user, role } : null
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: readStoredToken(),
    user: normalizeUser(readStoredUser()),
    initialized: false
  }),

  getters: {
    role: (state) => state.user?.role || null,
    isAuthenticated: (state) => Boolean(state.token && state.user?.role),
    homePath() {
      return homeForRole(this.role)
    },
    displayName: (state) => state.user?.displayName || state.user?.name || state.user?.username || '用户'
  },

  actions: {
    persistSession(token, user) {
      const normalizedUser = normalizeUser(user)
      if (!token || !normalizedUser) {
        throw new ApiError('登录响应缺少有效的令牌或角色信息')
      }
      this.token = token
      this.user = normalizedUser
      writeStoredSession(token, normalizedUser)
    },

    clearSession() {
      this.token = null
      this.user = null
      this.initialized = true
      clearStoredSession()
    },

    async login(credentials) {
      const result = await authApi.login(credentials)
      const token = result?.accessToken || result?.token
      const returnedUser = normalizeUser(result?.user || result?.account)

      if (returnedUser) {
        this.persistSession(token, returnedUser)
      } else if (token) {
        this.token = token
        writeStoredSession(token, {})
        const currentUserResult = await authApi.getCurrentUser()
        this.persistSession(token, currentUserResult?.user || currentUserResult?.account || currentUserResult)
      } else {
        throw new ApiError('登录响应缺少访问令牌')
      }

      this.initialized = true
      return this.homePath
    },

    async ensureSession() {
      if (this.initialized) return this.isAuthenticated

      const token = readStoredToken()
      if (!token) {
        this.clearSession()
        return false
      }

      this.token = token
      try {
        const currentUserResult = await authApi.getCurrentUser()
        const currentUser = normalizeUser(currentUserResult?.user || currentUserResult?.account || currentUserResult)
        if (!currentUser) throw new ApiError('无法识别当前用户角色')
        this.persistSession(token, currentUser)
        this.initialized = true
        return true
      } catch {
        this.clearSession()
        return false
      }
    },

    async changePassword(payload) {
      await authApi.changePassword(payload)
    },

    async logout() {
      try {
        if (this.token) await authApi.logout()
      } finally {
        this.clearSession()
      }
    }
  }
})
