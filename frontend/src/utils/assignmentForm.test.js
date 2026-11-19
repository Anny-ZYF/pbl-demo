import { describe, expect, it } from 'vitest'
import {
  buildAssignmentPayload,
  fromDateTimeLocal,
  normalizeParts,
  toDateTimeLocal,
  validateAssignmentForm
} from './assignmentForm'

const validForm = () => ({
  title: '需求分析',
  description: '说明',
  deadline: '2026-12-01T18:00',
  standardGroupSize: 4,
  totalScore: 100,
  branches: [
    { name: '需求完整性', description: '', weightPercent: 60 },
    { name: '团队协作', description: '', weightPercent: 40 }
  ]
})

describe('date conversion', () => {
  it('converts backend datetimes to datetime-local input values', () => {
    expect(toDateTimeLocal('2026-12-01T18:00:00')).toBe('2026-12-01T18:00')
    expect(toDateTimeLocal('2026-12-01 18:00:00.000')).toBe('2026-12-01T18:00')
    expect(toDateTimeLocal('')).toBe('')
  })

  it('produces a second-precision ISO value for the backend', () => {
    expect(fromDateTimeLocal('2026-12-01T18:00')).toBe('2026-12-01T18:00:00')
    expect(fromDateTimeLocal('2026-12-01T18:00:00')).toBe('2026-12-01T18:00:00')
  })
})

describe('normalizeParts', () => {
  it('turns decimal weights into percentages', () => {
    expect(normalizeParts([{ name: 'A', description: 'x', weight: 0.355 }]))
      .toEqual([{ name: 'A', description: 'x', weightPercent: 35.5 }])
  })

  it('gives an editable blank row when there is no branch', () => {
    expect(normalizeParts([])).toEqual([{ name: '', description: '', weightPercent: 0 }])
  })
})

describe('validateAssignmentForm', () => {
  it('accepts a complete form', () => {
    expect(validateAssignmentForm(validForm())).toEqual({ valid: true, message: '', weightTotal: 100 })
  })

  it('rejects weights that do not sum to 100', () => {
    const form = validForm()
    form.branches[1].weightPercent = 30
    expect(validateAssignmentForm(form).valid).toBe(false)
    expect(validateAssignmentForm(form).message).toContain('100%')
  })

  it('rejects duplicate and blank branch names', () => {
    const duplicate = validForm()
    duplicate.branches[1].name = ' 需求完整性 '
    expect(validateAssignmentForm(duplicate).message).toBe('评价分支名称不能重复')
    const blank = validForm()
    blank.branches[0].name = ''
    expect(validateAssignmentForm(blank).message).toBe('请填写每个评价分支的名称')
  })

  it('rejects missing basics and an out-of-range total score', () => {
    const noTitle = validForm()
    noTitle.title = '  '
    expect(validateAssignmentForm(noTitle).message).toBe('请填写作业名称')
    const badScore = validForm()
    badScore.totalScore = 120
    expect(validateAssignmentForm(badScore).message).toContain('1 至 100')
  })
})

describe('buildAssignmentPayload', () => {
  it('converts percentages back to decimal weights', () => {
    expect(buildAssignmentPayload(validForm())).toEqual({
      title: '需求分析',
      description: '说明',
      deadline: '2026-12-01T18:00:00',
      standardGroupSize: 4,
      totalScore: 100,
      branches: [
        { name: '需求完整性', description: '', weight: 0.6 },
        { name: '团队协作', description: '', weight: 0.4 }
      ]
    })
  })
})
