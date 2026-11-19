/**
 * A submission belongs to the whole group, not to the member who clicked submit.
 * Every ACTIVE member must resolve the same submitted flag, content and attachments.
 */
export function isGroupSubmitted(detail) {
  if (!detail) return false
  if (typeof detail.submitted === 'boolean') return detail.submitted
  if (detail.submitted != null) return Number(detail.submitted) !== 0
  // Compatibility with an older backend that only returned submissionStatus.
  return Boolean(detail.submissionStatus && detail.submissionStatus !== 'DRAFT')
}

export function isGroupSubmissionReturned(detail) {
  return detail?.submissionStatus === 'RETURNED'
}

export function groupSubmitterName(detail) {
  const id = detail?.submitterId
  if (id == null) return ''
  const member = (detail.members || []).find(
    (item) => String(item.studentId) === String(id)
  )
  return member?.name || ''
}

/**
 * Any attachment of the team's current submission is removable before grading, no matter
 * whether the team has already submitted once (the re-submission editor must show the
 * remove button too). Only a graded/published submission freezes the attachments.
 */
export function canRemoveGroupAttachment(attachment, { graded = false } = {}) {
  return !graded && attachment?.id != null
}

/**
 * The form has exactly one text field: the submit note. It is the submission content, so it is
 * sent as `content` (legacy alias) and `note` (submit_note). Attachments are optional - a
 * filled note alone is enough to submit.
 */
export function buildSubmissionPayload(submitNote = '') {
  const note = String(submitNote ?? '')
  return { content: note, note, attachments: [] }
}
