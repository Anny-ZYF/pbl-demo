import { describe, expect, it } from 'vitest'
import { homeForRole, normalizeRole, roleLabel } from './roles'

describe('role helpers', () => {
  it('normalizes prefixed and unprefixed roles', () => {
    expect(normalizeRole('admin')).toBe('ROLE_ADMIN')
    expect(normalizeRole('ROLE_TEACHER')).toBe('ROLE_TEACHER')
    expect(normalizeRole('unknown')).toBeNull()
  })

  it('maps roles to their isolated home routes', () => {
    expect(homeForRole('ROLE_STUDENT')).toBe('/student/dashboard')
    expect(homeForRole('teacher')).toBe('/teacher/courses')
    expect(roleLabel('ROLE_ADMIN')).toBe('管理员')
  })
})
