import axios, { type AxiosRequestConfig } from 'axios'
import router from '@/router'
import { toastError } from '@/utils/feedback'

const instance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000
})

instance.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token && !config.headers.Authorization) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

instance.interceptors.response.use(
  (response) => {
    const res = response.data
    // 兼容两种返回格式：1) { code: 1, data: ... } 2) 直接返回数据对象
    if (res && typeof res === 'object' && 'code' in res) {
      if (res.code === 1) {
        return res.data
      }
      toastError(res.msg || '请求失败')
      return Promise.reject(new Error(res.msg || '请求失败'))
    }
    // 直接返回数据（没有 code 字段包装的情况）
    return res
  },
  (error) => {
    const status = error.response?.status
    const requiresAuth = router.currentRoute.value.matched.some(record => record.meta.requiresAuth)
    if (status === 401 && requiresAuth) {
      localStorage.removeItem('token')
      // 只有受保护路由才跳转登录页，访客公开页保持原地展示错误。
      if (!router.currentRoute.value.path.startsWith('/login')) {
        router.push('/login')
      }
    } else if (status === 403) {
      toastError(error.response?.data?.msg || '无权访问')
    } else {
      toastError(error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

function request<T = unknown>(config: AxiosRequestConfig): Promise<T> {
  return instance(config) as unknown as Promise<T>
}

request.get = <T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> =>
  instance.get(url, config) as unknown as Promise<T>

request.post = <T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> =>
  instance.post(url, data, config) as unknown as Promise<T>

request.put = <T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> =>
  instance.put(url, data, config) as unknown as Promise<T>

request.delete = <T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> =>
  instance.delete(url, config) as unknown as Promise<T>

export default request
