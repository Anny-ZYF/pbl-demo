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
  const token = readStoredToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => unwrapApiResponse(response.data),
  (error) => {
    const status = error.response?.status ?? null
    const payload = error.response?.data
    const message = payload?.message || error.message || '网络请求失败'

    if (status === 401) {
      clearStoredSession()
      unauthorizedHandler?.()
    }

    return Promise.reject(new ApiError(message, {
      status,
      code: payload?.code,
      details: payload?.data
    }))
  }
)

export default http
