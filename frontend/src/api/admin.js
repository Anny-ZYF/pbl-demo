import http from './http'
import { normalizePage } from './response'

const getPage = async (url, params) => normalizePage(
  await http.get(url, { params }),
  params
)

export const getDashboard = () => http.get('/admin/dashboard')

export const getOrganizationOverview = () => http.get('/admin/organization/overview')

export const getAccounts = (params) => getPage('/admin/accounts', params)

export const createAccount = (payload) => http.post('/admin/accounts', payload)

export const importAccounts = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return http.post('/admin/accounts/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const updateAccount = (id, payload) => http.put(`/admin/accounts/${id}`, {
  ...payload,
  role: payload.role
})

export const updateAccountStatus = (id, role, status) => http.put(`/admin/accounts/${id}/status`, {
  role,
  status
})

export const resetAccountPassword = (id, role, newPassword) => http.post(`/admin/accounts/${id}/reset-password`, {
  role,
  newPassword
})

export const getCourses = (params) => getPage('/admin/courses', params)

export const getModelVersions = (params) => getPage('/admin/model-versions', params)

export const registerModelVersion = (payload) => http.post('/admin/model-versions', payload)

export const activateModelVersion = (id) => http.post(`/admin/model-versions/${id}/activate`)

export const getScoringRuns = (params) => getPage('/admin/scoring-runs', params)

export const getAnomalies = (params) => getPage('/admin/anomalies', params)

export const resolveAnomaly = (id, payload) => http.put(`/admin/anomalies/${id}`, payload)

export const getAuditEvents = (params) => getPage('/admin/audit-events', params)
