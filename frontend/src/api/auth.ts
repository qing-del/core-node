import request from '@/utils/request'
import type { User } from '@/types'

export const authApi = {
  login(data: { username: string; password: string }): Promise<string> {
    return request.post('/user/user/login', data)
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
  adminLogin(data: { username: string; password: string }): Promise<string> {
    return request.post('/admin/user/login', data)
  },
  adminLogout() {
    const token = localStorage.getItem('token')
    const authToken = `Bearer ${token}`
    return request.post('/admin/user/logout', null, {
      headers: {
        Authorization: authToken
      }
    })
  },
  logout() {
    const token = localStorage.getItem('token')
    const authToken = `Bearer ${token}`
    return request.post('/user/user/logout', null, {
      headers: {
        Authorization: authToken
      }
    })
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
