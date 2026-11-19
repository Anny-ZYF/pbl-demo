import { describe, expect, it } from 'vitest'
import {
  groupSubmissionStatus,
  isAutoLocked,
  isSubmissionOpen,
  workloadChecklistHint
} from './assignmentWindow'

const NOW = new Date('2026-11-19T10:00:00+08:00').getTime()

describe('isSubmissionOpen', () => {
  it('stays open from publication until the deadline, without a formation phase', () => {
    const detail = {
      publishedAt: '2026-11-19T09:00:00+08:00',
      deadline: '2026-11-21T23:59:00+08:00'
    }
    expect(isSubmissionOpen(detail, NOW)).toBe(true)
  })

  it('is open one minute after publication (old code would still be in the formation window)', () => {
    const published = new Date(NOW - 60_000).toISOString()
    const deadline = new Date(NOW + 3_600_000).toISOString()
    expect(isSubmissionOpen({ publishedAt: published, deadline }, NOW)).toBe(true)
  })

  it('closes at the deadline regardless of publishedAt', () => {
    expect(isSubmissionOpen({
      publishedAt: '2026-11-19T09:00:00+08:00',
      deadline: new Date(NOW - 1).toISOString()
    }, NOW)).toBe(false)
  })

  it('treats a missing deadline as closed', () => {
    expect(isSubmissionOpen({ publishedAt: new Date(NOW).toISOString() }, NOW)).toBe(false)
  })
})

describe('groupSubmissionStatus', () => {
  it('asks an ungrouped student to join a group while the window is open', () => {
    expect(groupSubmissionStatus({ submissionOpen: true, groupName: null }))
      .toEqual({ text: '待入组', tone: 'pending' })
  })

  it('allows submission immediately after joining a group', () => {
    expect(groupSubmissionStatus({ submissionOpen: true, groupName: '第3组' }))
      .toEqual({ text: '待提交', tone: 'pending' })
  })

  it('never reports a 组队中 state', () => {
    const states = [
      groupSubmissionStatus({ submissionOpen: true, groupName: null }),
      groupSubmissionStatus({ submissionOpen: true, groupName: '第3组' }),
      groupSubmissionStatus({ submissionOpen: false })
    ]
    expect(states.every((state) => state.text !== '组队中')).toBe(true)
  })

  it('keeps submitted / returned / reviewed precedence over the deadline', () => {
    expect(groupSubmissionStatus({ submitted: true, submissionOpen: false }).text).toBe('已提交')
    expect(groupSubmissionStatus({ returned: true, submitted: true, submissionOpen: true }).text).toBe('已退回·待重交')
    expect(groupSubmissionStatus({ reviewed: true, returned: true }).text).toBe('已批阅')
  })

  it('shows 已截止 after the deadline', () => {
    expect(groupSubmissionStatus({ submissionOpen: false }).text).toBe('已截止')
  })
})

describe('workloadChecklistHint', () => {
  it('distinguishes a deadline auto-lock from a full confirmation', () => {
    expect(workloadChecklistHint({ locked: true, autoLocked: true }))
      .toBe('截止自动锁定（含未确认成员）')
    expect(workloadChecklistHint({ locked: true, autoLocked: false }))
      .toBe('已全员确认并锁定')
  })

  it('normalises MySQL boolean values', () => {
    expect(isAutoLocked(1)).toBe(true)
    expect(isAutoLocked(true)).toBe(true)
    expect(isAutoLocked(0)).toBe(false)
    expect(workloadChecklistHint({ locked: true, autoLocked: 1 })).toBe('截止自动锁定（含未确认成员）')
  })

  it('keeps the pending / objection hints unchanged', () => {
    expect(workloadChecklistHint({ hasVersion: true, myConfirmation: 'PENDING', pendingMembers: ['张三'] }))
      .toBe('等待我确认（共 1 人待确认：张三）')
    expect(workloadChecklistHint({ objected: true, myConfirmation: 'OBJECTED' }))
      .toBe('已提异议，待组长重提')
    expect(workloadChecklistHint(null)).toBe('加载中')
  })
})
