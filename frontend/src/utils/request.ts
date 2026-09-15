import axios, { type AxiosRequestConfig } from 'axios'
import router from '@/router'
import { toastError } from '@/utils/feedback'
import {
  clearStoredAuth,
  normalizeAuthTokenResponse,
  notifyAuthSessionCleared,
  notifyAuthSessionUpdated,
  readAuthSession,
  saveAuthSession
} from '@/utils/authSession'

interface RequestConfig extends AxiosRequestConfig {
  _authRetry?: boolean
}

interface RefreshResult {
  success: boolean
  message?: string
}

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

const instance = axios.create({
  baseURL,
  timeout: 30000
})

const refreshClient = axios.create({
  baseURL,
  timeout: 30000
})

let refreshPromise: Promise<RefreshResult> | null = null

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function responseMessage(value: unknown): string | null {
  if (!isRecord(value) || typeof value.msg !== 'string' || !value.msg.trim()) return null
  const message = value.msg.trim()
  // Backend messages are intended for users, but never surface a credential-like value if
  // an unexpected error response contains one.
  if (/(?:access[_ -]?token|refresh[_ -]?token|bearer\s+[^\s]+|authorization\s*:)/i.test(message)) {
    return null
  }
  return message
}

function responseErrorMessage(error: unknown): string | null {
  if (!isRecord(error) || !isRecord(error.response)) return null
  return responseMessage(error.response.data)
}

function applyResponseErrorMessage(error: unknown): string | null {
  const message = responseErrorMessage(error)
  if (message && isRecord(error)) error.message = message
  return message
}

function requestPath(url?: string): string {
  if (!url) return ''
  try {
    return new URL(url, window.location.origin).pathname
  } catch {
    return url.split('?')[0]
  }
}

function isAuthenticationEndpoint(url?: string): boolean {
  const path = requestPath(url)
  return path === '/auth/login' || path === '/auth/logout' || path === '/oauth/email-code'
}

function requiresAuthRoute(): boolean {
  return router.currentRoute.value.matched.some(record => record.meta.requiresAuth)
}

function setBearerHeader(config: RequestConfig, accessToken: string): void {
  if (!config.headers) config.headers = {}
  if (typeof config.headers.set === 'function') {
    config.headers.set('Authorization', `Bearer ${accessToken}`)
  } else {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
}

function sentAccessToken(config?: RequestConfig): string | null {
  const headers = config?.headers
  if (!headers) return null
  const authorization = typeof headers.get === 'function'
    ? headers.get('Authorization')
    : headers.Authorization
  return typeof authorization === 'string' && authorization.startsWith('Bearer ')
    ? authorization.slice('Bearer '.length)
    : null
}

function retryWithCurrentToken(config: RequestConfig): Promise<unknown> | null {
  const currentToken = readAuthSession().accessToken
  const requestToken = sentAccessToken(config)
  if (!currentToken || !requestToken || currentToken === requestToken) return null

  const retryConfig = { ...config, _authRetry: true } as RequestConfig
  setBearerHeader(retryConfig, currentToken)
  return instance(retryConfig)
}

function unwrapApiResponse(value: unknown): unknown {
  if (!isRecord(value) || !('code' in value)) return value
  if (value.code === 1) return value.data
  throw new Error(responseMessage(value) || '请求失败')
}

async function performRefresh(): Promise<RefreshResult> {
  const session = readAuthSession()
  if (!session.refreshToken || !session.clientId) return { success: false }

  try {
    const response = await refreshClient.post('/auth/login', {
      client_id: session.clientId,
      grant_type: 'refresh_token',
      refresh_token: session.refreshToken
    })
    const tokens = normalizeAuthTokenResponse(unwrapApiResponse(response.data))
    const updatedSession = saveAuthSession(tokens, session.clientId)
    notifyAuthSessionUpdated(updatedSession)
    sessionExpiryNoticeShown = false
    return { success: true }
  } catch (error) {
    return { success: false, message: responseErrorMessage(error) || undefined }
  }
}

function refreshAccessToken(): Promise<RefreshResult> {
  if (!refreshPromise) {
    refreshPromise = performRefresh().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

let sessionExpiryNoticeShown = false

function expireSession(message = '登录状态已失效，请重新登录'): void {
  clearStoredAuth()
  notifyAuthSessionCleared()
  if (!sessionExpiryNoticeShown) {
    toastError(message)
    sessionExpiryNoticeShown = true
  }
  if (router.currentRoute.value.path !== '/login') {
    void router.push('/login')
  }
}

function sanitizeRequestError(error: unknown): unknown {
  if (!isRecord(error)) return error

  const sanitizeConfig = (config: unknown) => {
    if (!isRecord(config)) return
    // Callers may log rejected errors; never retain request bodies or bearer headers there.
    config.data = undefined
    const headers = config.headers
    if (!isRecord(headers)) return
    if (typeof headers.delete === 'function') {
      headers.delete('Authorization')
    } else {
      delete headers.Authorization
    }
  }

  sanitizeConfig(error.config)
  if (isRecord(error.response)) sanitizeConfig(error.response.config)
  return error
}

instance.interceptors.request.use(
  (config) => {
    const token = readAuthSession().accessToken
    if (token && !config.headers?.Authorization) {
      setBearerHeader(config, token)
    }
    return config
  },
  error => Promise.reject(error)
)

instance.interceptors.response.use(
  (response) => {
    const res = response.data
    // 兼容两种返回格式：1) { code: 1, data: ... } 2) 直接返回数据对象
    if (isRecord(res) && 'code' in res) {
      if (res.code === 1) {
        if (requestPath(response.config?.url) === '/auth/login') sessionExpiryNoticeShown = false
        return res.data
      }
      const message = responseMessage(res) || '请求失败'
      if (!isAuthenticationEndpoint(response.config?.url)) toastError(message)
      return Promise.reject(new Error(message))
    }
    return res
  },
  async (error) => {
    const status = error.response?.status
    const config = error.config as RequestConfig | undefined
    const authEndpoint = isAuthenticationEndpoint(config?.url)
    const serverMessage = applyResponseErrorMessage(error)

    if (status === 401 && requiresAuthRoute() && !authEndpoint && !config?._authRetry) {
      const retry = config && retryWithCurrentToken(config)
      if (retry) return retry

      const refreshed = await refreshAccessToken()
      if (refreshed.success && config) {
        const retryConfig = { ...config, _authRetry: true } as RequestConfig
        const accessToken = readAuthSession().accessToken
        if (accessToken) setBearerHeader(retryConfig, accessToken)
        return instance(retryConfig)
      }
      expireSession(refreshed.message || '登录状态已失效，请重新登录')
    } else if (status === 401 && requiresAuthRoute() && !authEndpoint) {
      expireSession(serverMessage || '登录状态已失效，请重新登录')
    } else if (status === 403) {
      toastError(serverMessage || '无权访问')
    } else if (!authEndpoint) {
      toastError(serverMessage || error.message || '网络错误')
    }
    return Promise.reject(sanitizeRequestError(error))
  }
)

function request<T = unknown>(config: RequestConfig): Promise<T> {
  return instance(config) as unknown as Promise<T>
}

request.get = <T = unknown>(url: string, config?: RequestConfig): Promise<T> =>
  instance.get(url, config) as unknown as Promise<T>

request.post = <T = unknown>(url: string, data?: unknown, config?: RequestConfig): Promise<T> =>
  instance.post(url, data, config) as unknown as Promise<T>

request.put = <T = unknown>(url: string, data?: unknown, config?: RequestConfig): Promise<T> =>
  instance.put(url, data, config) as unknown as Promise<T>

request.delete = <T = unknown>(url: string, config?: RequestConfig): Promise<T> =>
  instance.delete(url, config) as unknown as Promise<T>

export default request
