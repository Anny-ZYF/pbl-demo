import { describe, expect, it } from 'vitest'
import {
  MAX_CLASS_IDS,
  MAX_STUDENT_NOS,
  buildImportPayload,
  classCounts,
  classImportEstimate,
  filterClasses,
  formatImportResult,
  parseStudentNos,
  validateImportPayload
} from './studentImport'

describe('parseStudentNos', () => {
  it('splits pasted rows, commas and full-width separators', () => {
    expect(parseStudentNos('20260001\n20260002, 20260003；20260004\t20260005'))
      .toEqual(['20260001', '20260002', '20260003', '20260004', '20260005'])
  })

  it('deduplicates, drops CSV headers, quotes and BOM', () => {
    expect(parseStudentNos('\uFEFF学号,studentNo, grade\n"20260001"\n20260001\n20260002\n'))
      .toEqual(['20260001', '20260002'])
  })

  it('returns an empty list for non-string input', () => {
    expect(parseStudentNos(null)).toEqual([])
    expect(parseStudentNos(undefined)).toEqual([])
  })
})

describe('filterClasses', () => {
  const classes = [
    { id: 1, name: '临床 1 班', departmentName: '医学院', grade: '2024' },
    { id: 2, name: '生医 2 班', departmentName: '工学院', grade: '2025' }
  ]

  it('matches name, department and grade case-insensitively', () => {
    expect(filterClasses(classes, ' 医学 ')).toEqual([classes[0]])
    expect(filterClasses(classes, '2025')).toEqual([classes[1]])
    expect(filterClasses(classes, '生医')).toEqual([classes[1]])
    expect(filterClasses(classes, '')).toEqual(classes)
  })

  it('tolerates missing fields and non-array input', () => {
    expect(filterClasses(null, 'x')).toEqual([])
    expect(filterClasses([{}, null], 'x')).toEqual([])
  })
})

describe('classCounts and classImportEstimate', () => {
  it('clamps invalid counts', () => {
    expect(classCounts({ studentCount: 30, enrolledCount: 12 }))
      .toEqual({ studentCount: 30, enrolledCount: 12, pendingCount: 18 })
    expect(classCounts({ studentCount: 'x', enrolledCount: -4 }))
      .toEqual({ studentCount: 0, enrolledCount: 0, pendingCount: 0 })
    expect(classCounts({ studentCount: 3, enrolledCount: 5 }))
      .toEqual({ studentCount: 3, enrolledCount: 5, pendingCount: 0 })
  })

  it('sums only selected classes and ignores duplicate selections', () => {
    const classes = [
      { id: 1, studentCount: 30, enrolledCount: 10 },
      { id: 2, studentCount: 40, enrolledCount: 40 },
      { id: '3', studentCount: 25, enrolledCount: 0 }
    ]
    expect(classImportEstimate(classes, ['1', 1, 3])).toEqual(45)
    expect(classImportEstimate(classes, [])).toBe(0)
  })
})

describe('buildImportPayload', () => {
  it('combines class selection and student numbers, dropping invalid ids', () => {
    expect(buildImportPayload({ classIds: ['2', 2, 0, null, 'x'], studentNos: [' S1 ', 'S1', ''] }))
      .toEqual({ classIds: [2], studentNos: ['S1'] })
  })

  it('returns null when nothing is selected', () => {
    expect(buildImportPayload()).toBeNull()
    expect(buildImportPayload({ classIds: [], studentNos: [] })).toBeNull()
  })

  it('omits empty channels so the server contract stays clean', () => {
    expect(buildImportPayload({ classIds: [7], studentNos: [] })).toEqual({ classIds: [7] })
    expect(buildImportPayload({ classIds: [], studentNos: ['S1'] })).toEqual({ studentNos: ['S1'] })
  })
})

describe('validateImportPayload', () => {
  it('requires at least one channel', () => {
    expect(validateImportPayload(null)).toContain('请选择')
  })

  it('rejects oversized batches', () => {
    expect(validateImportPayload({ classIds: Array.from({ length: MAX_CLASS_IDS + 1 }, (_, i) => i + 1) }))
      .toContain(String(MAX_CLASS_IDS))
    expect(validateImportPayload({ studentNos: Array.from({ length: MAX_STUDENT_NOS + 1 }, (_, i) => `S${i}`) }))
      .toContain(String(MAX_STUDENT_NOS))
    expect(validateImportPayload({ classIds: [1], studentNos: ['S1'] })).toBe('')
  })
})

describe('formatImportResult', () => {
  it('reports success, skipped and missing numbers', () => {
    expect(formatImportResult({ success: 12, skipped: 3, notFound: [] }))
      .toBe('成功导入 12 人，已在课程 3 人')
    expect(formatImportResult({ success: 0, skipped: 0, notFound: ['S9'] }))
      .toContain('未找到 1 个学号')
    expect(formatImportResult(null)).toBe('成功导入 0 人，已在课程 0 人')
  })
})
