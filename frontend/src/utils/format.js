export function displayValue(value, fallback = '—') {
  return value === null || value === undefined || value === '' ? fallback : value
}

export function formatDateTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
}

export function statusLabel(status) {
  const value = String(status ?? '').toUpperCase()
  return {
    ACTIVE: '启用',
    ENABLED: '启用',
    DISABLED: '停用',
    INACTIVE: '停用',
    PENDING: '待处理',
    RUNNING: '运行中',
    SUCCESS: '成功',
    COMPLETED: '已完成',
    FAILED: '失败',
    RESOLVED: '已处理',
    OPEN: '开放',
    CLOSED: '关闭',
    PUBLISHED: '已发布',
    DRAFT: '草稿'
  }[value] || (status ? String(status) : '—')
}

export function statusType(status) {
  const value = String(status ?? '').toUpperCase()
  if (['ACTIVE', 'ENABLED', 'SUCCESS', 'COMPLETED', 'RESOLVED', 'OPEN', 'PUBLISHED'].includes(value)) return 'success'
  if (['FAILED', 'DISABLED', 'INACTIVE', 'CLOSED'].includes(value)) return 'danger'
  if (['PENDING', 'RUNNING', 'DRAFT'].includes(value)) return 'warning'
  return 'info'
}
