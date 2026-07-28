export const ROLE_HOME = Object.freeze({
  ROLE_ADMIN: '/admin/dashboard',
  ROLE_TEACHER: '/teacher/courses',
  ROLE_STUDENT: '/student/dashboard'
})

export function normalizeRole(role) {
  if (!role) return null
  const value = String(role).trim().toUpperCase()
  const normalized = value.startsWith('ROLE_') ? value : `ROLE_${value}`
  return Object.prototype.hasOwnProperty.call(ROLE_HOME, normalized) ? normalized : null
}

export function roleLabel(role) {
  return {
    ROLE_ADMIN: '管理员',
    ROLE_TEACHER: '教师',
    ROLE_STUDENT: '学生'
  }[normalizeRole(role)] || '用户'
}

export function homeForRole(role) {
  return ROLE_HOME[normalizeRole(role)] || '/login'
}
