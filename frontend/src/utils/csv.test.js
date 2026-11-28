import { describe, expect, it } from 'vitest'
import { dateStamp, safeFileName, toCsv } from './csv'

describe('toCsv', () => {
  it('starts with a BOM so Excel reads Chinese as UTF-8', () => {
    expect(toCsv(['姓名'], [['张三']]).charCodeAt(0)).toBe(0xfeff)
  })

  it('quotes every cell and doubles inner quotes', () => {
    expect(toCsv(['a'], [['他说"好"']])).toBe('\uFEFF"a"\r\n"他说""好"""')
  })

  it('keeps a formula-looking cell from being executed', () => {
    expect(toCsv(['x'], [['=1+1']])).toContain(`"'=1+1"`)
    expect(toCsv(['x'], [['  @SUM(A1)']])).toContain(`"'  @SUM(A1)"`)
  })

  it('renders null and undefined as empty strings', () => {
    expect(toCsv(['a', 'b'], [[null, undefined]])).toBe('\uFEFF"a","b"\r\n"",""')
  })
})

describe('safeFileName', () => {
  it('replaces characters that the file system rejects', () => {
    expect(safeFileName('需求分析:初稿/终稿?')).toBe('需求分析_初稿_终稿_')
  })

  it('falls back when the title is blank', () => {
    expect(safeFileName('   ', '作业')).toBe('作业')
  })
})

describe('dateStamp', () => {
  it('formats as yyyymmdd with zero padding', () => {
    expect(dateStamp(new Date(2026, 0, 5))).toBe('20260105')
  })
})
