// 教师端作业编辑表单的纯函数：后端 weight 用 0..1，界面用百分数。

export function toDateTimeLocal(value) {
  if (!value) return ''
  const text = String(value).replace(' ', 'T')
  const match = /^(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2})/.exec(text)
  return match ? `${match[1]}T${match[2]}:${match[3]}` : ''
}

export function fromDateTimeLocal(value) {
  if (!value) return ''
  const text = String(value).replace(' ', 'T')
  return text.length === 16 ? `${text}:00` : text
}

export function normalizeParts(parts) {
  if (!Array.isArray(parts) || !parts.length) {
    return [{ name: '', description: '', weightPercent: 0 }]
  }
  return parts.map((part) => ({
    name: String(part?.name ?? ''),
    description: String(part?.description ?? ''),
    weightPercent: Math.round(Number(part?.weight ?? 0) * 10000) / 100
  }))
}

export function buildAssignmentPayload(form) {
  return {
    title: String(form.title ?? '').trim(),
    description: form.description ?? '',
    deadline: fromDateTimeLocal(form.deadline),
    standardGroupSize: Number(form.standardGroupSize),
    totalScore: Number(form.totalScore),
    branches: (form.branches ?? []).map((branch) => ({
      name: String(branch.name ?? '').trim(),
      description: branch.description ?? '',
      weight: Number(branch.weightPercent ?? 0) / 100
    }))
  }
}

export function validateAssignmentForm(form) {
  const branches = Array.isArray(form?.branches) ? form.branches : []
  const weightTotal = Math.round(branches.reduce(
    (sum, branch) => sum + (Number(branch.weightPercent) || 0), 0) * 1000) / 1000

  if (!String(form?.title ?? '').trim()) return { valid: false, message: '请填写作业名称', weightTotal }
  if (!form?.deadline) return { valid: false, message: '请选择截止时间', weightTotal }
  if (!branches.length) return { valid: false, message: '至少需要一个评价分支', weightTotal }
  if (branches.some((branch) => !String(branch.name ?? '').trim())) {
    return { valid: false, message: '请填写每个评价分支的名称', weightTotal }
  }
  const names = branches.map((branch) => String(branch.name).trim().toLowerCase())
  if (new Set(names).size !== names.length) {
    return { valid: false, message: '评价分支名称不能重复', weightTotal }
  }
  if (Math.abs(weightTotal - 100) > 0.001) {
    return { valid: false, message: `权重总和需为 100%，当前 ${weightTotal}%`, weightTotal }
  }
  const totalScore = Number(form?.totalScore)
  if (!Number.isFinite(totalScore) || totalScore < 1 || totalScore > 100) {
    return { valid: false, message: '作业总分需在 1 至 100 之间', weightTotal }
  }
  return { valid: true, message: '', weightTotal }
}
