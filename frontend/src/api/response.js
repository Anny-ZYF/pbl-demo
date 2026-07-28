export class ApiError extends Error {
  constructor(message, options = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = options.status ?? null
    this.code = options.code ?? null
    this.details = options.details ?? null
  }
}

export function isSuccessCode(code) {
  return code === 0 || code === 200 || code === 201
    || code === '0' || code === '200' || code === '201'
}

export function unwrapApiResponse(payload) {
  if (payload === null || payload === undefined) return null
  if (typeof payload !== 'object' || Array.isArray(payload)) return payload
  if (!Object.prototype.hasOwnProperty.call(payload, 'code')) return payload

  if (!isSuccessCode(payload.code)) {
    throw new ApiError(payload.message || '请求处理失败', {
      code: payload.code,
      details: payload.data
    })
  }

  return payload.data ?? null
}

export function normalizePage(payload, fallback = {}) {
  const source = payload && typeof payload === 'object' ? payload : {}
  const items = source.items ?? source.rows ?? source.records ?? source.list ?? []

  return {
    items: Array.isArray(items) ? items : [],
    total: Number(source.total ?? source.totalCount ?? 0),
    page: Number(source.page ?? source.pageNum ?? source.current ?? fallback.page ?? 1),
    pageSize: Number(source.pageSize ?? source.size ?? fallback.pageSize ?? 10),
    summary: source.summary ?? null
  }
}

export function getErrorMessage(error, fallback = '操作失败，请稍后重试') {
  return error?.message || fallback
}
