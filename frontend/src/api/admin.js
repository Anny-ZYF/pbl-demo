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

export const importAccounts = (file, role) => {
  const formData = new FormData()
  formData.append('file', file)
  // 角色由导入入口选择，CSV 里不再写 role 列。
  if (role) formData.append('role', role)
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

// 账号删除是逻辑删除：账号从列表消失、立即无法登录，教学与成绩历史保留。
export const deleteAccount = (id, role) => http.delete(`/admin/accounts/${id}`, { params: { role } })

// 仅允许删除无成员、且无课程/小组等业务引用的组织节点，后端会再次校验。
export const deleteOrganization = (type, id) => http.delete(`/admin/organization/${type}/${id}`)

export const getCourses = (params) => getPage('/admin/courses', params)

export const getModelVersions = (params) => getPage('/admin/model-versions', params)

export const registerModelVersion = (payload) => http.post('/admin/model-versions', payload)

export const activateModelVersion = (id) => http.post(`/admin/model-versions/${id}/activate`)

export const getScoringRuns = (params) => getPage('/admin/scoring-runs', params)

export const getAnomalies = (params) => getPage('/admin/anomalies', params)

export const resolveAnomaly = (id, payload) => http.put(`/admin/anomalies/${id}`, payload)

export const getAuditEvents = (params) => getPage('/admin/audit-events', params)
