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

export function allRowsComplete(rows, memberCount) {
  return rows.length > 0 && memberCount > 0
    && rows.every((row) => Math.abs(row.remaining) < ROW_TOLERANCE)
}

export function buildContributions(parts, members, draft) {
  const result = {}
  for (const member of members || []) {
    let value = 0
    for (const part of parts || []) {
      value += (Number(part.weight) || 0) * toNumber(draft[cellKey(part.id, member.studentId)])
    }
    result[member.studentId] = value
  }
  return result
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
