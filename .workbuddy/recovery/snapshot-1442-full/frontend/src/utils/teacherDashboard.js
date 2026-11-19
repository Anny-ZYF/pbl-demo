// 教师课程工作台的统计口径：只认当前有效提交（is_latest=1）。
// 后端 teacherAssignments 返回 submittedGroupCount / pendingReviewCount；
// submissionCount 是累计提交次数（含同一团队历次重交），只能用于展示份数。

export function countOf(value) {
  const number = Number(value)
  return Number.isFinite(number) && number > 0 ? Math.floor(number) : 0
}

export function submittedGroupCount(assignment) {
  return countOf(assignment?.submittedGroupCount)
}

export function pendingReviewCount(assignment) {
  return countOf(assignment?.pendingReviewCount)
}

export function groupCount(assignment) {
  return countOf(assignment?.groupCount)
}

export function assignmentProgress(assignment) {
  const total = groupCount(assignment)
  if (!total) return 0
  return Math.min(100, Math.round(submittedGroupCount(assignment) / total * 100))
}

export function submissionTotals(assignments) {
  const list = Array.isArray(assignments) ? assignments : []
  const submitted = list.reduce((sum, item) => sum + submittedGroupCount(item), 0)
  const total = list.reduce((sum, item) => sum + groupCount(item), 0)
  return { submitted, total, waiting: Math.max(0, total - submitted) }
}

export function pendingReviewTotal(assignments) {
  const list = Array.isArray(assignments) ? assignments : []
  return list.reduce((sum, item) => sum + pendingReviewCount(item), 0)
}

function deadlineOrder(item) {
  const value = item?.deadline
  if (!value) return Number.MAX_SAFE_INTEGER
  const time = new Date(value).getTime()
  return Number.isFinite(time) ? time : Number.MAX_SAFE_INTEGER
}

export function pendingReviewAssignments(assignments, limit = 3) {
  const list = Array.isArray(assignments) ? assignments : []
  return list
    .filter((item) => pendingReviewCount(item) > 0)
    .sort((a, b) => deadlineOrder(a) - deadlineOrder(b))
    .slice(0, Math.max(0, limit))
}

export function gradedTeamScores(submissions) {
  const list = Array.isArray(submissions) ? submissions : []
  return list.reduce((scores, item) => {
    const raw = item?.teacherScore
    if (raw === null || raw === undefined || raw === '') return scores
    const score = Number(raw)
    if (Number.isFinite(score) && score >= 0 && score <= 100) scores.push(score)
    return scores
  }, [])
}

export function scoreBuckets(scores) {
  const list = Array.isArray(scores) ? scores : []
  return [
    list.filter((score) => score < 60).length,
    list.filter((score) => score >= 60 && score < 75).length,
    list.filter((score) => score >= 75 && score < 90).length,
    list.filter((score) => score >= 90).length
  ]
}
