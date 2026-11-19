/**
 * There is no separate team-formation phase. From publication until the assignment deadline a
 * student may join/create a group, fill in the workload allocation and submit. At the deadline
 * the whole assignment freezes for students and any unconfirmed workload version is locked by
 * the backend. These helpers are kept pure so the same rules can be unit-tested and reused.
 */

export const parseDateValue = (value) => {
  if (!value) return null
  const time = new Date(value).getTime()
  return Number.isFinite(time) ? time : null
}

export const isSubmissionOpen = (detail, now = Date.now()) => {
  const end = parseDateValue(detail?.deadline)
  return end != null && now < end
}

export const isAutoLocked = (value) => value === true || value === 1 || value === '1'

export const groupSubmissionStatus = ({
  reviewed = false,
  returned = false,
  submitted = false,
  groupName = null,
  submissionOpen = false
} = {}) => {
  if (reviewed) return { text: '已批阅', tone: 'reviewed' }
  if (returned) return { text: '已退回·待重交', tone: 'pending' }
  if (submitted) return { text: '已提交', tone: 'submitted' }
  if (submissionOpen) {
    return groupName
      ? { text: '待提交', tone: 'pending' }
      : { text: '待入组', tone: 'pending' }
  }
  return { text: '已截止', tone: 'closed' }
}

export const workloadChecklistHint = (status) => {
  if (!status) return '加载中'
  if (status.locked) {
    return isAutoLocked(status.autoLocked)
      ? '截止自动锁定（含未确认成员）'
      : '已全员确认并锁定'
  }
  if (status.objected) {
    return status.myConfirmation === 'OBJECTED' ? '已提异议，待组长重提' : '有成员异议，待组长重提'
  }
  if (!status.hasVersion) return '尚未填写分工'
  if (status.myConfirmation === 'CONFIRMED') {
    const pending = status.pendingMembers || []
    return pending.length ? `我已确认，等待 ${pending.join('、')}` : '我已确认，等待其他成员'
  }
  if (status.myConfirmation === 'OBJECTED') return '我已提出异议'
  const pending = status.pendingMembers || []
  return pending.length
    ? `等待我确认（共 ${pending.length} 人待确认：${pending.join('、')}）`
    : '等待我确认'
}
