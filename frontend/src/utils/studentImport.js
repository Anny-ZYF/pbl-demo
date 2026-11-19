// 教师端「从系统班级库导入学生」的纯计算逻辑，方便单测覆盖。
export const MAX_CLASS_IDS = 50
export const MAX_STUDENT_NOS = 500

// CSV / 表格粘贴时常见的表头，不能当成学号提交给后端。
const HEADER_PATTERN = /^(学号|编号|账号|姓名|年级|班级|专业|name|grade|class|major|student[_ ]?no|student[_ ]?id|student[_ ]?number|username)$/i

export function parseStudentNos(text) {
  const raw = typeof text === 'string' ? text.replace(/^\uFEFF/, '') : ''
  const seen = new Set()
  const result = []
  for (const token of raw.split(/[,\n\r\t ;，、；;|]+/)) {
    const value = token.trim().replace(/^["']|["']$/g, '')
    if (!value || HEADER_PATTERN.test(value) || seen.has(value)) continue
    seen.add(value)
    result.push(value)
  }
  return result
}

export function filterClasses(classes, keyword) {
  const list = Array.isArray(classes) ? classes : []
  const query = typeof keyword === 'string' ? keyword.trim().toLowerCase() : ''
  if (!query) return list
  return list.filter((item) => ['name', 'departmentName', 'grade']
    .some((key) => String(item?.[key] ?? '').toLowerCase().includes(query)))
}

export function classCounts(item) {
  const total = Number(item?.studentCount)
  const enrolled = Number(item?.enrolledCount)
  const studentCount = Number.isFinite(total) ? Math.max(0, total) : 0
  const enrolledCount = Number.isFinite(enrolled) ? Math.max(0, enrolled) : 0
  return { studentCount, enrolledCount, pendingCount: Math.max(0, studentCount - enrolledCount) }
}

export function classImportEstimate(classes, selectedIds) {
  const selected = new Set((Array.isArray(selectedIds) ? selectedIds : []).map(String))
  return (Array.isArray(classes) ? classes : [])
    .filter((item) => selected.has(String(item?.id)))
    .reduce((total, item) => total + classCounts(item).pendingCount, 0)
}

export function buildImportPayload({ classIds = [], studentNos = [] } = {}) {
  const ids = [...new Set((Array.isArray(classIds) ? classIds : [])
    .map((id) => Number(id)).filter((id) => Number.isInteger(id) && id > 0))]
  const nos = [...new Set((Array.isArray(studentNos) ? studentNos : [])
    .map((no) => String(no ?? '').trim()).filter(Boolean))]
  if (!ids.length && !nos.length) return null
  return {
    ...(ids.length ? { classIds: ids } : {}),
    ...(nos.length ? { studentNos: nos } : {})
  }
}

export function validateImportPayload(payload) {
  if (!payload) return '请选择要导入的班级或填写学号'
  if ((payload.classIds?.length ?? 0) > MAX_CLASS_IDS) return `单次最多选择 ${MAX_CLASS_IDS} 个班级`
  if ((payload.studentNos?.length ?? 0) > MAX_STUDENT_NOS) return `单次最多填写 ${MAX_STUDENT_NOS} 个学号`
  return ''
}

export function formatImportResult(result) {
  const source = result && typeof result === 'object' ? result : {}
  const success = Number(source.success) || 0
  const skipped = Number(source.skipped) || 0
  const notFound = Array.isArray(source.notFound) ? source.notFound : []
  const parts = [`成功导入 ${success} 人`, `已在课程 ${skipped} 人`]
  if (notFound.length) parts.push(`未找到 ${notFound.length} 个学号`)
  return parts.join('，')
}
