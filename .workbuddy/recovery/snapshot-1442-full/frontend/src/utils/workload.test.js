import { describe, expect, it } from 'vitest'
import { allRowsComplete, buildAllocationPayload, buildContributions, buildRows, cellKey } from './workload'

const parts = [
  { id: 101, name: '数据处理和特征工程', weight: 0.4 },
  { id: 102, name: '模型的选择和评估', weight: 0.35 },
  { id: 103, name: '撰写文档', weight: 0.15 },
  { id: 104, name: '汇报演示', weight: 0.1 }
]
const members = [
  { studentId: 1, name: '罗姿' },
  { studentId: 2, name: '陆盈' },
  { studentId: 3, name: '周钰桦' }
]

const draftFrom = (matrix) => {
  const draft = {}
  for (const [partId, shares] of Object.entries(matrix)) {
    members.forEach((member, index) => { draft[cellKey(partId, member.studentId)] = shares[index] })
  }
  return draft
}

const completeDraft = () => draftFrom({
  101: [60, 30, 10],
  102: [0, 40, 60],
  103: [20, 30, 50],
  104: [34, 33, 33]
})

describe('buildRows', () => {
  it('leaves every row fully unallocated for an empty draft', () => {
    const rows = buildRows(parts, members, {})
    expect(rows).toHaveLength(4)
    expect(rows.every((row) => row.remaining === 100)).toBe(true)
    expect(allRowsComplete(rows, members.length)).toBe(false)
  })

  it('shows zero remaining for a finished row and a negative value for over-allocation', () => {
    const draft = draftFrom({ 101: [60, 30, 10] })
    const rows = buildRows(parts, members, draft)
    expect(rows[0].remaining).toBe(0)
    expect(rows[1].remaining).toBe(100)
    expect(allRowsComplete(rows, members.length)).toBe(false)

    const over = draftFrom({ 101: [60, 30, 20] })
    expect(buildRows(parts, members, over)[0].remaining).toBe(-10)
  })

  it('only passes when every evaluation branch sums to 100', () => {
    expect(allRowsComplete(buildRows(parts, members, completeDraft()), members.length)).toBe(true)
  })

  it('aligns with the backend 1e-6 share tolerance instead of accepting 99.99', () => {
    const almost = draftFrom({
      101: [33.33, 33.33, 33.33],
      102: [60, 30, 10],
      103: [20, 30, 50],
      104: [34, 33, 33]
    })
    const rows = buildRows(parts, members, almost)
    expect(rows[0].remaining).toBeGreaterThan(0.0001)
    expect(allRowsComplete(rows, members.length)).toBe(false)
  })

  it('treats blank and non-numeric cells as zero', () => {
    const draft = { [cellKey(101, 1)]: '', [cellKey(101, 2)]: 'abc', [cellKey(101, 3)]: null }
    expect(buildRows(parts, members, draft)[0].remaining).toBe(100)
  })

  it('rejects an empty branch list or an empty member list', () => {
    expect(allRowsComplete(buildRows([], members, completeDraft()), members.length)).toBe(false)
    expect(allRowsComplete(buildRows(parts, [], completeDraft()), 0)).toBe(false)
  })
})

describe('buildContributions', () => {
  it('computes C = sum of W times Q and keeps the group total at 100', () => {
    const result = buildContributions(parts, members, completeDraft())
    expect(result[1]).toBeCloseTo(30.4, 6)
    expect(result[2]).toBeCloseTo(33.8, 6)
    expect(result[3]).toBeCloseTo(35.8, 6)
    expect(Object.values(result).reduce((sum, value) => sum + value, 0)).toBeCloseTo(100, 6)
  })
})

describe('buildAllocationPayload', () => {
  it('covers every branch and every member with shares converted to 0-1', () => {
    const payload = buildAllocationPayload(parts, members, completeDraft())
    expect(payload.parts.map((part) => part.assignmentPartId)).toEqual([101, 102, 103, 104])
    for (const part of payload.parts) {
      expect(part.members.map((member) => member.studentId)).toEqual([1, 2, 3])
      expect(part.members.reduce((sum, member) => sum + member.share, 0)).toBeCloseTo(1, 10)
    }
    expect(payload.parts[0].members[0].share).toBeCloseTo(0.6, 10)
  })
})
