import { describe, expect, it } from 'vitest'
import { ApiError, normalizePage, unwrapApiResponse } from './response'

describe('unwrapApiResponse', () => {
  it('returns data for a successful unified response', () => {
    expect(unwrapApiResponse({ code: 200, message: 'success', data: { id: 1 } })).toEqual({ id: 1 })
    expect(unwrapApiResponse({ code: 201, message: 'created', data: { id: 2 } })).toEqual({ id: 2 })
    expect(unwrapApiResponse({ code: 0, data: ['ok'] })).toEqual(['ok'])
  })

  it('throws ApiError for a failed business response', () => {
    expect(() => unwrapApiResponse({ code: 4001, message: '无权限', data: null }))
      .toThrowError(ApiError)
  })
})

describe('normalizePage', () => {
  it('normalizes supported list field names', () => {
    expect(normalizePage({ rows: [{ id: 1 }], total: 1, pageNum: 2, pageSize: 20 })).toEqual({
      items: [{ id: 1 }],
      total: 1,
      page: 2,
      pageSize: 20,
      summary: null
    })
  })

  it('returns an empty page for missing data', () => {
    expect(normalizePage(null, { page: 3, pageSize: 10 })).toMatchObject({
      items: [],
      total: 0,
      page: 3,
      pageSize: 10
    })
  })
})
