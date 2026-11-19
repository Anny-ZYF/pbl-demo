import { describe, expect, it } from 'vitest'
import { buildSubmissionPayload, canRemoveGroupAttachment, groupSubmitterName, isGroupSubmissionReturned, isGroupSubmitted } from './submission'

describe('group-level submission state', () => {
  it('treats the backend submitted flag as the single source of truth for every member', () => {
    expect(isGroupSubmitted({ submitted: true, submitterId: 7 })).toBe(true)
    expect(isGroupSubmitted({ submitted: false, submissionStatus: 'SUBMITTED' })).toBe(false)
    expect(isGroupSubmitted({ submitted: 1 })).toBe(true)
    expect(isGroupSubmitted({ submitted: 0 })).toBe(false)
  })

  it('does not call a staged DRAFT submission "submitted"', () => {
    expect(isGroupSubmitted({ submissionStatus: 'DRAFT' })).toBe(false)
    expect(isGroupSubmitted({ submissionStatus: null })).toBe(false)
    expect(isGroupSubmitted(null)).toBe(false)
  })

  it('shares the submitted state even when another member clicked submit', () => {
    expect(isGroupSubmitted({ submissionStatus: 'SUBMITTED', submitterId: 9 })).toBe(true)
    expect(isGroupSubmitted({ submissionStatus: 'GRADED', submitterId: 9 })).toBe(true)
    expect(isGroupSubmitted({ submissionStatus: 'RETURNED', submitterId: 9 })).toBe(true)
  })

  it('flags a returned submission and names the submitting member', () => {
    const detail = {
      submissionStatus: 'RETURNED',
      submitterId: 9,
      members: [{ studentId: 9, name: '李雷' }, { studentId: 10, name: '韩梅梅' }]
    }
    expect(isGroupSubmissionReturned(detail)).toBe(true)
    expect(isGroupSubmissionReturned({ submissionStatus: 'SUBMITTED' })).toBe(false)
    expect(groupSubmitterName(detail)).toBe('李雷')
    expect(groupSubmitterName({ submitterId: 9, members: [] })).toBe('')
    expect(groupSubmitterName({ members: [{ studentId: 9, name: '李雷' }] })).toBe('')
  })

  it('keeps attachments removable in the re-submission editor', () => {
    const file = { id: 4, storageKey: '2026/11/student/9/a.pdf' }
    expect(canRemoveGroupAttachment(file)).toBe(true)
    expect(canRemoveGroupAttachment(file, { graded: false })).toBe(true)
    expect(canRemoveGroupAttachment({ storageKey: file.storageKey })).toBe(false)
    expect(canRemoveGroupAttachment(null)).toBe(false)
  })

  it('freezes attachments once the submission was graded', () => {
    expect(canRemoveGroupAttachment({ id: 4 }, { graded: true })).toBe(false)
  })

  it('submits the note as the content, with attachments optional', () => {
    const payload = buildSubmissionPayload('本次完成 KNN 实验')
    expect(payload).toEqual({ content: '本次完成 KNN 实验', note: '本次完成 KNN 实验', attachments: [] })
    expect(buildSubmissionPayload('')).toEqual({ content: '', note: '', attachments: [] })
    expect(buildSubmissionPayload(null)).toEqual({ content: '', note: '', attachments: [] })
  })
})
