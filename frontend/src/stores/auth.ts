import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi } from '@/api/auth'
import type { User } from '@/types'
import router from '@/router'
import {
  clearStoredAuth,
  hasAllGrantedScopes,
  hasGrantedScope,
  readAuthSession,
  registerAuthSessionListener,
  saveAuthSession,
  type AuthClientId,
  type AuthTokenResponse,
  type StoredAuthSession
} from '@/utils/authSession'

export interface PasswordLoginCredentials {
  username: string
  password: string
}

export interface EmailCodeLoginCredentials {
  email: string
  code: string
}

function isUnauthorizedError(error: unknown): boolean {
  if (!error || typeof error !== 'object') return false
  const response = (error as { response?: { status?: number } }).response
  return response?.status === 401
}

export const useAuthStore = defineStore('auth', () => {
  const initialSession = readAuthSession()
  const token = ref<string | null>(initialSession.accessToken)
  const refreshToken = ref<string | null>(initialSession.refreshToken)
  const authClientId = ref<AuthClientId | null>(initialSession.clientId)
  const scopes = ref<string[]>(initialSession.scopes)
  const user = ref<User | null>(null)

  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => authClientId.value === 'admin')
  // Kept as a compatibility alias for existing consumers; it represents client boundary, not permission.
  const authScope = authClientId

  function applySession(session: StoredAuthSession) {
    token.value = session.accessToken
    refreshToken.value = session.refreshToken
    authClientId.value = session.clientId
    scopes.value = [...session.scopes]
  }

  function applyTokenResponse(response: AuthTokenResponse, clientId: AuthClientId) {
    applySession(saveAuthSession(response, clientId))
  }

  function setUser(newUser: User | null) {
    user.value = newUser
  }

  function hasScope(requiredScope: string): boolean {
    return hasGrantedScope(scopes.value, requiredScope)
  }

  function hasAllScopes(requiredScopes: readonly string[]): boolean {
    return hasAllGrantedScopes(scopes.value, requiredScopes)
  }

  async function authenticate(clientId: AuthClientId, credentials: PasswordLoginCredentials | EmailCodeLoginCredentials) {
    const response = 'username' in credentials
      ? await authApi.login({
          client_id: clientId,
          grant_type: 'password',
          username: credentials.username,
          password: credentials.password
        })
      : await authApi.login({
          client_id: clientId,
          grant_type: 'email-code',
          email: credentials.email,
          code: credentials.code
        })

    applyTokenResponse(response, clientId)
    if (clientId === 'admin') {
      await fetchAdminUserInfo()
    } else {
      await fetchUserInfo()
    }
    return user.value
  }

  async function login(credentials: PasswordLoginCredentials) {
    return authenticate('user', credentials)
  }

  async function loginWithEmailCode(credentials: EmailCodeLoginCredentials) {
    return authenticate('user', credentials)
  }

  async function adminLogin(credentials: PasswordLoginCredentials) {
    return authenticate('admin', credentials)
  }

  async function adminLoginWithEmailCode(credentials: EmailCodeLoginCredentials) {
    return authenticate('admin', credentials)
  }

  async function requestEmailCode(email: string, clientId: AuthClientId = 'user') {
    return authApi.requestEmailCode({ client_id: clientId, email })
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
      setUser(userInfo)
      return userInfo
    } catch (error) {
      if (isUnauthorizedError(error)) {
        clearSession({ withServer: false })
        return null
      }
      throw error
    }
  }

  async function fetchAdminUserInfo() {
    if (!token.value) return null
    try {
      const userInfo = await authApi.getCurrentAdminUser()
      setUser(userInfo)
      return userInfo
    } catch (error) {
      if (isUnauthorizedError(error)) {
        clearSession({ withServer: false })
        return null
      }
      throw error
    }
  }

  function clearSession(options?: { withServer?: boolean; redirect?: boolean; clearStorage?: boolean }) {
    const withServer = options?.withServer ?? false
    if (withServer && token.value) {
      // Capture the access token before clearing storage so the logout request cannot race the cleanup.
      void authApi.logout(token.value).catch(() => {})
    }
    if (options?.clearStorage !== false) clearStoredAuth()
    token.value = null
    refreshToken.value = null
    authClientId.value = null
    scopes.value = []
    setUser(null)
    if (options?.redirect !== false && router.currentRoute.value.path !== '/login') {
      void router.push('/login')
    }
  }

  function logout(options?: { withServer?: boolean }) {
    clearSession({ withServer: options?.withServer ?? true })
  }

  function adminLogout(options?: { withServer?: boolean }) {
    logout(options)
  }

  async function updateProfile(data: { nickname?: string; email?: string; password?: string; newPassword?: string; confirmPassword?: string }) {
    return authApi.updateProfile(data)
  }

  async function refreshCurrentUserInfo(scope?: AuthClientId) {
    const resolvedClientId = scope ?? authClientId.value ?? 'user'
    return resolvedClientId === 'admin' ? fetchAdminUserInfo() : fetchUserInfo()
  }

  registerAuthSessionListener({
    onUpdated: applySession,
    onCleared: () => clearSession({ withServer: false, clearStorage: false })
  })

  return {
    token,
    refreshToken,
    authClientId,
    authScope,
    scopes,
    user,
    isAuthenticated,
    isAdmin,
    hasScope,
    hasAllScopes,
    login,
    loginWithEmailCode,
    adminLogin,
    adminLoginWithEmailCode,
    requestEmailCode,
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
