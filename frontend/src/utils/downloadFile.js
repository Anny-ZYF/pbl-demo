import { downloadStudentFile } from '@/api/student'

export async function downloadAuthenticatedFile(file) {
  const blob = await downloadStudentFile(file.storageKey)
  const objectUrl = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.download = file.originalName || 'attachment'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(objectUrl)
}
