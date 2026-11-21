/**
 * Group names are numbered ("第10组"), but students usually only know the number.
 * A numeric keyword is matched against the group number; anything else falls back
 * to matching the displayed name, so "10" and "第10组" both work.
 */
export function groupNumber(group) {
  if (group?.groupNo != null && group.groupNo !== '') {
    const parsed = Number(group.groupNo)
    if (Number.isFinite(parsed)) return parsed
  }
  const match = String(group?.name ?? '').match(/\d+/)
  return match ? Number(match[0]) : null
}

export function isNumericGroupKeyword(keyword) {
  return /^\d+$/.test(String(keyword ?? '').trim())
}

export function matchesGroupKeyword(group, keyword) {
  const query = String(keyword ?? '').trim()
  if (!query) return true
  const name = String(group?.name ?? '')
  if (/^\d+$/.test(query)) {
    const no = groupNumber(group)
    // Prefix matching keeps narrowing as the student types: "1" -> 1, 10, 11...
    // Leading zeros are ignored so "07" still finds 第7组.
    return no != null && String(no).startsWith(String(Number(query)))
  }
  return name.toLocaleLowerCase('zh-CN').includes(query.toLocaleLowerCase('zh-CN'))
}

export function filterAvailableGroups(groups, keyword) {
  const rows = Array.isArray(groups) ? groups : []
  const query = String(keyword ?? '').trim()
  const matches = rows.filter((group) => matchesGroupKeyword(group, query))
  if (!/^\d+$/.test(query)) return matches
  const exact = Number(query)
  // Exact match first, then ascending group number (第1组 before 第10组).
  return matches.sort((a, b) => {
    const an = groupNumber(a) ?? Number.MAX_SAFE_INTEGER
    const bn = groupNumber(b) ?? Number.MAX_SAFE_INTEGER
    if (an === exact && bn !== exact) return -1
    if (bn === exact && an !== exact) return 1
    return an - bn
  })
}

export function findExactGroup(groups, keyword) {
  const query = String(keyword ?? '').trim()
  if (!/^\d+$/.test(query)) return null
  const exact = Number(query)
  return (Array.isArray(groups) ? groups : []).find((group) => groupNumber(group) === exact) ?? null
}
