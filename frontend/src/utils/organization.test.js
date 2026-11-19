import { describe, expect, it } from 'vitest'
import {
  canDeleteAccount,
  canDeleteOrganization,
  organizationDeleteTarget,
  organizationMemberCount,
  organizationType
} from './organization'

describe('account deletion guards', () => {
  it('only allows teacher and student accounts', () => {
    expect(canDeleteAccount({ id: 7, role: 'TEACHER' })).toBe(true)
    expect(canDeleteAccount({ id: 66, role: 'ROLE_STUDENT' })).toBe(true)
    expect(canDeleteAccount({ id: 1, role: 'ADMIN' })).toBe(false)
    expect(canDeleteAccount({ id: 1, role: 'ROLE_ADMIN' })).toBe(false)
    expect(canDeleteAccount({ role: 'STUDENT' })).toBe(false)
    expect(canDeleteAccount(null)).toBe(false)
  })

  it('never offers deleted accounts again', () => {
    expect(canDeleteAccount({ id: 66, role: 'STUDENT', deletedAt: '2026-11-19' })).toBe(false)
  })
})

describe('organization deletion guards', () => {
  it('resolves tree node types', () => {
    expect(organizationType({ type: 'class' })).toBe('class')
    expect(organizationType({ typeLabel: '班级' })).toBe('class')
    expect(organizationType({ typeLabel: '专业' })).toBe('major')
    expect(organizationType({ type: 'DEPARTMENT' })).toBe('department')
    expect(organizationType({})).toBeNull()
  })

  it('counts members from the overview payload', () => {
    expect(organizationMemberCount({ memberCount: 7 })).toBe(7)
    expect(organizationMemberCount({ studentCount: 12 })).toBe(12)
    expect(organizationMemberCount({})).toBe(0)
    expect(organizationMemberCount({ memberCount: '-' })).toBe(0)
  })

  it('only allows empty nodes with an id', () => {
    expect(canDeleteOrganization({ id: 6, type: 'class', typeLabel: '班级', memberCount: 0 })).toBe(true)
    expect(canDeleteOrganization({ id: 76, type: 'class', memberCount: 7 })).toBe(false)
    expect(canDeleteOrganization({ id: 1, type: 'department', accountCount: 116 })).toBe(false)
    expect(canDeleteOrganization({ type: 'class', memberCount: 0 })).toBe(false)
    expect(canDeleteOrganization({ id: 2, memberCount: 0 })).toBe(false)
  })

  it('builds the delete request target', () => {
    expect(organizationDeleteTarget({ id: 6, type: 'class', name: '2', memberCount: 0 }))
      .toEqual({ type: 'class', id: 6, name: '2' })
    expect(organizationDeleteTarget({ id: 76, type: 'class', memberCount: 7 })).toBeNull()
  })
})
