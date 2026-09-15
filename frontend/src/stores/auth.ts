import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'
import type { User } from '@/types'
import router from '@/router'

type AuthScope = 'user' | 'admin'

function isUnauthorizedError(error: unknown): boolean {
  if (!error || typeof error !== 'object') return false
  const response = (error as { response?: { status?: number } }).response
  return response?.status === 401
}

function readAuthScope(): AuthScope | null {
  const scope = localStorage.getItem('authScope')
  return scope === 'user' || scope === 'admin' ? scope : null
}

function getRouteAuthScope(): AuthScope | null {
  const path = router.currentRoute.value.path
  if (path.startsWith('/admin')) return 'admin'
  if (path.startsWith('/user')) return 'user'
  return null
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const authScope = ref<AuthScope | null>(readAuthScope())
  const user = ref<User | null>(null)

  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => (user.value ? user.value.roleId <= 2 : false))

  function setToken(newToken: string) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function setAuthScope(scope: AuthScope) {
    authScope.value = scope
    localStorage.setItem('authScope', scope)
  }

  function setUser(newUser: User) {
    user.value = newUser
  }

  async function login(credentials: { username: string; password: string }) {
    const res = await authApi.login(credentials)
    setToken(res as unknown as string)
    setAuthScope('user')
    await fetchUserInfo()
    return user.value
  }

  async function adminLogin(credentials: { username: string; password: string }) {
    const res = await authApi.adminLogin(credentials)
    setToken(res as unknown as string)
    setAuthScope('admin')
    await fetchAdminUserInfo()
    return user.value
  }

  async function register(data: { username: string; password: string; confirmPassword: string; email: string }) {
    return authApi.register(data)
  }

  async function resendActivation(account: string) {
    return authApi.resendActivation({ account })
  }

  async function verifyActivationCode(code: string) {
    return authApi.verifyActivationCode({ code })
  }

  async function fetchUserInfo() {
    if (!token.value) return null
    try {
      const userInfo = await authApi.getCurrentUser()
      setAuthScope('user')
      setUser(userInfo)
      return userInfo
    } catch (error) {
      if (isUnauthorizedError(error)) {
        logout({ withServer: false })
        return null
      }
      throw error
    }
  }

  async function fetchAdminUserInfo() {
    if (!token.value) return null
    if (getRouteAuthScope() === 'user') {
      return fetchUserInfo()
    }
    try {
      const userInfo = await authApi.getCurrentAdminUser()
      setAuthScope('admin')
      setUser(userInfo)
      return userInfo
    } catch (error) {
      if (isUnauthorizedError(error)) {
        logout({ withServer: false })
        return null
      }
      throw error
    }
  }

  function clearSession() {
    token.value = null
    authScope.value = null
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('authScope')
    router.push('/login')
  }

  function adminLogout(options?: { withServer?: boolean }) {
    const withServer = options?.withServer ?? true
    if (withServer) {
      void authApi.adminLogout().catch(() => {})
    }
    clearSession()
  }

  function logout(options?: { withServer?: boolean }) {
    const withServer = options?.withServer ?? true
    if (withServer) {
      void authApi.logout().catch(() => {})
    }
    clearSession()
  }

  async function updateProfile(data: { nickname?: string; email?: string; password?: string; newPassword?: string; confirmPassword?: string }) {
    return authApi.updateProfile(data)
  }

  async function refreshCurrentUserInfo(scope?: AuthScope) {
    if (scope === 'user') {
      return fetchUserInfo()
    }
    if (scope === 'admin') {
      return fetchAdminUserInfo()
    }
    const resolvedScope = getRouteAuthScope() ?? authScope.value ?? 'user'
    if (resolvedScope === 'admin') {
      return fetchAdminUserInfo()
    }
    return fetchUserInfo()
  }

  return {
    token,
    authScope,
    user,
    isAuthenticated,
    isAdmin,
    login,
    adminLogin,
    register,
    resendActivation,
    verifyActivationCode,
    fetchUserInfo,
    fetchAdminUserInfo,
    updateProfile,
    refreshCurrentUserInfo,
    adminLogout,
    logout
  }
})
