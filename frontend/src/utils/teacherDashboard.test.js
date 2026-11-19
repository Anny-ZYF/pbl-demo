import { describe, expect, it } from 'vitest'
import {
  assignmentProgress,
  countOf,
  gradedTeamScores,
  pendingReviewAssignments,
  pendingReviewTotal,
  scoreBuckets,
  submissionTotals
} from './teacherDashboard'

describe('countOf', () => {
  it('normalizes missing and invalid counts to zero', () => {
    expect(countOf(undefined)).toBe(0)
    expect(countOf(null)).toBe(0)
    expect(countOf('')).toBe(0)
    expect(countOf(Number.NaN)).toBe(0)
    expect(countOf(-2)).toBe(0)
    expect(countOf('3')).toBe(3)
    expect(countOf(2.9)).toBe(2)
  })
})

describe('assignmentProgress', () => {
  it('uses current valid submissions instead of cumulative attempts', () => {
    expect(assignmentProgress({ groupCount: 3, submissionCount: 9, submittedGroupCount: 1 })).toBe(33)
    expect(assignmentProgress({ groupCount: 4, submissionCount: 2, submittedGroupCount: 4 })).toBe(100)
  })

  it('handles missing teams and over-counting', () => {
    expect(assignmentProgress({ groupCount: 0, submittedGroupCount: 0 })).toBe(0)
    expect(assignmentProgress({ groupCount: 2, submittedGroupCount: 5 })).toBe(100)
    expect(assignmentProgress(null)).toBe(0)
  })
})

describe('submissionTotals', () => {
  it('sums submitted teams and remaining teams across assignments', () => {
    const list = [
      { groupCount: 3, submittedGroupCount: 2 },
      { groupCount: 2, submittedGroupCount: 0 }
    ]
    expect(submissionTotals(list)).toEqual({ submitted: 2, total: 5, waiting: 3 })
  })

  it('never returns a negative waiting count', () => {
    expect(submissionTotals([{ groupCount: 1, submittedGroupCount: 4 }])).toEqual({ submitted: 4, total: 1, waiting: 0 })
    expect(submissionTotals(null)).toEqual({ submitted: 0, total: 0, waiting: 0 })
  })
})

describe('pending review', () => {
  const list = [
    { id: 1, title: '无截止', pendingReviewCount: 2 },
    { id: 2, title: '较晚', deadline: '2026-12-01', pendingReviewCount: 1 },
    { id: 3, title: '最紧急', deadline: '2026-11-20', pendingReviewCount: 3 },
    { id: 4, title: '无待办', deadline: '2026-11-01', pendingReviewCount: 0 }
  ]

  it('counts only ungraded current submissions', () => {
    expect(pendingReviewTotal(list)).toBe(6)
    expect(pendingReviewTotal(null)).toBe(0)
  })

  it('sorts by deadline and drops assignments without pending work', () => {
    expect(pendingReviewAssignments(list).map((item) => item.id)).toEqual([3, 2, 1])
    expect(pendingReviewAssignments(list, 2).map((item) => item.id)).toEqual([3, 2])
  })
})

describe('gradedTeamScores', () => {
  it('accepts only numeric teacherScore within 0..100', () => {
    const rows = [
      { teacherScore: 82 },
      { teacherScore: null },
      {},
      { teacherScore: '59' },
      { teacherScore: 120 },
      { teacher_score: 70 },
      { score: 95 }
    ]
    expect(gradedTeamScores(rows)).toEqual([82, 59])
    expect(gradedTeamScores(null)).toEqual([])
  })
})

describe('scoreBuckets', () => {
  it('places boundary values into the right bucket', () => {
    expect(scoreBuckets([10, 59, 60, 74, 75, 89, 90, 100])).toEqual([2, 2, 2, 2])
    expect(scoreBuckets(null)).toEqual([0, 0, 0, 0])
  })
})
