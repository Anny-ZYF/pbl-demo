export const cellKey = (partId, studentId) => `${partId}:${studentId}`

const toNumber = (value) => {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

export function buildRows(parts, members, draft) {
  return (parts || []).map((part) => {
    const total = (members || []).reduce(
      (sum, member) => sum + toNumber(draft[cellKey(part.id, member.studentId)]), 0)
    return {
      id: part.id,
      name: part.name,
      description: part.description,
      weight: Number(part.weight) || 0,
      remaining: 100 - total
    }
  })
}

export const ROW_TOLERANCE = 0.0001

/** 每个评价分支的份额上限：一行 100 个百分点，先填的成员先占用额度。 */
export const SHARE_LIMIT = 100

/** 份额保留两位小数，避免 33.33 + 33.33 + 33.33 之类的浮点尾巴。 */
export const roundShare = (value) => Math.round(toNumber(value) * 100) / 100

/**
 * 单格钳位：一格最多能填「100 − 同行其他成员已填合计」，最少 0。
 * 先填 A=100，B/C 的可填额度自然变成 0；把 140 填进空格子会得到 100。
 */
export function clampCellValue(value, othersTotal, limit = SHARE_LIMIT) {
  if (value === '' || value === null || value === undefined) return 0
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) return 0
  const remaining = Math.max(0, Number(limit) - Math.max(0, toNumber(othersTotal)))
  return roundShare(Math.min(Math.max(0, parsed), remaining))
}

/**
 * 一行里某位成员当前最多还能填多少（含他自己已填的部分），用于 input 的 max 属性与提示。
 */
export function cellLimit(othersTotal, limit = SHARE_LIMIT) {
  return roundShare(Math.max(0, Number(limit) - Math.max(0, toNumber(othersTotal))))
}

export function allRowsComplete(rows, memberCount) {
  return rows.length > 0 && memberCount > 0
    && rows.every((row) => Math.abs(row.remaining) < ROW_TOLERANCE)
}

export function buildAllocationPayload(parts, members, draft) {
  return {
    parts: (parts || []).map((part) => ({
      assignmentPartId: Number(part.id),
      members: (members || []).map((member) => ({
        studentId: Number(member.studentId),
        share: toNumber(draft[cellKey(part.id, member.studentId)]) / 100
      }))
    }))
  }
}
