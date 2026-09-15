import request from '@/utils/request'
import type { User } from '@/types'
import {
  type AuthClientId,
  type AuthTokenResponse,
  normalizeAuthTokenResponse
} from '@/utils/authSession'

export type InternalLoginRequest =
  | {
      client_id: AuthClientId
      grant_type: 'password'
      username: string
      password: string
    }
  | {
      client_id: AuthClientId
      grant_type: 'email-code'
      email: string
      code: string
    }
  | {
      client_id: AuthClientId
      grant_type: 'refresh_token'
      refresh_token: string
      scope?: string
    }

export const authApi = {
  login(data: InternalLoginRequest): Promise<AuthTokenResponse> {
    return request.post<unknown>('/auth/login', data).then(normalizeAuthTokenResponse)
  },
  register(data: { username: string; password: string; confirmPassword: string; email: string }): Promise<string> {
    return request.post('/user/user/register', data)
  },
  resendActivation(data: { account: string }): Promise<string> {
    return request.post('/user/user/resend-activation', data)
  },
  verifyActivationCode(data: { code: string }): Promise<string> {
    return request.post('/user/user/active-code', data)
  },
  requestEmailCode(data: { client_id: AuthClientId; email: string }): Promise<void> {
    return request.post<void>('/oauth/email-code', data)
  },
  logout(accessToken?: string): Promise<void> {
    return request.post<void>('/auth/logout', null, accessToken
      ? { headers: { Authorization: `Bearer ${accessToken}` } }
      : undefined)
  },
  getCurrentUser(): Promise<User> {
    return request.get('/user/user/me')
  },
  // 管理端获取当前用户信息
  getCurrentAdminUser(): Promise<User> {
    return request.get('/admin/user/me')
  },
  updateProfile(data: { nickname?: string; email?: string; password?: string; newPassword?: string; confirmPassword?: string }): Promise<string> {
    return request.put('/user/user/me', data)
  }
}
