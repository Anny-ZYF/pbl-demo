import axios from 'axios'
import { ApiError, unwrapApiResponse } from './response'
import { clearStoredSession, readStoredToken } from '@/utils/session'

let unauthorizedHandler = null

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = typeof handler === 'function' ? handler : null
}

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json'
  }
})

http.interceptors.request.use((config) => {
  const isPublicAuthRequest = config.url === '/auth/login' || config.url === '/auth/refresh'
  const token = isPublicAuthRequest ? null : readStoredToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    // 文件下载要保留响应头，Content-Disposition 里带的是库中的权威文件名
    if (response.config?.responseType === 'blob') {
      return { blob: response.data, headers: response.headers }
    }
    return unwrapApiResponse(response.data)
  },
  async (error) => {
    const status = error.response?.status ?? null
    let payload = error.response?.data

    // blob 下载失败时响应体是 Blob，需读回统一响应体才能显示真实错误
    if (typeof Blob !== 'undefined' && payload instanceof Blob) {
      try {
        payload = JSON.parse(await payload.text())
      } catch {
        payload = null
      }
    }

    const message = payload?.message || error.message || '网络请求失败'

    if (status === 401) {
      clearStoredSession()
      unauthorizedHandler?.()
    }

    throw new ApiError(message, {
      status,
      code: payload?.code,
      details: payload?.data
    })
  }
)

export default http
