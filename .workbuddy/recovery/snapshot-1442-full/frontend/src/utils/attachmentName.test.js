import { describe, expect, it } from 'vitest'
import { attachmentDisplayName, filenameFromContentDisposition, isStorageFileName } from './attachmentName'

const uuidKey = '2026/11/student/63/7fb0c3f0-1b2a-4c3d-8e5f-6a7b8c9d0e1f.docx'

describe('isStorageFileName', () => {
  it('recognises legacy UUID storage names', () => {
    expect(isStorageFileName('7fb0c3f0-1b2a-4c3d-8e5f-6a7b8c9d0e1f.docx')).toBe(true)
    expect(isStorageFileName('7FB0C3F0-1B2A-4C3D-8E5F-6A7B8C9D0E1F.PDF')).toBe(true)
  })

  it('keeps real file names', () => {
    expect(isStorageFileName('团队报告-终稿.pdf')).toBe(false)
    expect(isStorageFileName('report.docx')).toBe(false)
    expect(isStorageFileName('')).toBe(false)
    expect(isStorageFileName(null)).toBe(false)
  })
})

describe('attachmentDisplayName', () => {
  it('shows the real uploaded name', () => {
    expect(attachmentDisplayName({ originalName: '团队报告-终稿.pdf', storageKey: uuidKey }, 0))
      .toBe('团队报告-终稿.pdf')
  })

  it('falls back to a friendly name for legacy UUID records', () => {
    expect(attachmentDisplayName({
      originalName: '7fb0c3f0-1b2a-4c3d-8e5f-6a7b8c9d0e1f.docx',
      storageKey: uuidKey
    }, 0)).toBe('附件 1.docx')
  })

  it('numbers multiple legacy attachments and keeps the extension from the storage key', () => {
    expect(attachmentDisplayName({ originalName: '', storageKey: uuidKey }, 2)).toBe('附件 3.docx')
  })

  it('handles missing metadata', () => {
    expect(attachmentDisplayName(null, 0)).toBe('附件 1')
    expect(attachmentDisplayName({}, 0)).toBe('附件 1')
  })
})

describe('filenameFromContentDisposition', () => {
  const chinese = encodeURIComponent('团队报告-终稿.docx')

  it('prefers the RFC 5987 UTF-8 name over Spring ASCII approximation', () => {
    expect(filenameFromContentDisposition(`attachment; filename="??.docx"; filename*=UTF-8''${chinese}`))
      .toBe('团队报告-终稿.docx')
  })

  it('reads a plain ASCII filename', () => {
    expect(filenameFromContentDisposition('attachment; filename="report.pdf"')).toBe('report.pdf')
    expect(filenameFromContentDisposition('inline; filename=notes.txt')).toBe('notes.txt')
  })

  it('strips path separators and control characters', () => {
    expect(filenameFromContentDisposition(`attachment; filename*=UTF-8''${encodeURIComponent('C:/tmp/report.pdf')}`))
      .toBe('report.pdf')
  })

  it('rejects missing or placeholder headers', () => {
    expect(filenameFromContentDisposition('')).toBe('')
    expect(filenameFromContentDisposition(null)).toBe('')
    expect(filenameFromContentDisposition('attachment; filename="??.docx"')).toBe('')
    expect(filenameFromContentDisposition("attachment; filename*=UTF-8''%E0%A4%A")).toBe('')
  })
})
