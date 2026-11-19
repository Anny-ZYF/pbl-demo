import { normalizeRole } from './roles'

/** Only teacher/student accounts may be deleted; admin accounts stay untouched. */
export function canDeleteAccount(row) {
  const role = normalizeRole(row?.role)
  return Boolean(row && row.id != null && row.deletedAt == null
    && (role === 'ROLE_TEACHER' || role === 'ROLE_STUDENT'))
}

export function organizationType(row) {
  const value = String(row?.type ?? row?.typeLabel ?? '').trim()
  const normalized = value.toUpperCase()
  if (value === '院系' || normalized === 'DEPARTMENT') return 'department'
  if (value === '专业' || normalized === 'MAJOR') return 'major'
  if (value === '班级' || normalized === 'CLASS') return 'class'
  return null
}

export function organizationMemberCount(row) {
  const raw = row?.memberCount ?? row?.studentCount ?? row?.accountCount ?? 0
  const count = Number(raw)
  return Number.isFinite(count) && count > 0 ? count : 0
}

/** Empty organization nodes can be offered for deletion; the backend re-checks references. */
export function canDeleteOrganization(row) {
  return Boolean(row && row.id != null && organizationType(row) && organizationMemberCount(row) === 0)
}

export function organizationDeleteTarget(row) {
  if (!canDeleteOrganization(row)) return null
  return { type: organizationType(row), id: row.id, name: row.name }
}
