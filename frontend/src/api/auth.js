import http from './http'

export const login = (credentials) => http.post('/auth/login', credentials)

export const getCurrentUser = () => http.get('/auth/me')

export const changePassword = (payload) => http.put('/auth/change-password', payload)

export const logout = () => http.post('/auth/logout')
