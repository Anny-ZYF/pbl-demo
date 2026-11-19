import { downloadFileByKey } from '@/api/files'
import { attachmentDisplayName, isStorageFileName } from './attachmentName'

export { attachmentDisplayName, isStorageFileName, filenameFromContentDisposition } from './attachmentName'

export async function downloadAuthenticatedFile(file, index = 0) {
  if (!file?.storageKey) throw new Error('附件缺少存储标识，无法下载')

  const { blob, filename } = await downloadFileByKey(file.storageKey)
  if (typeof Blob !== 'undefined' && !(blob instanceof Blob)) {
    throw new Error('服务端返回的文件内容无效')
  }

  // 优先用服务端返回的名字；历史 UUID 记录仍由前端兜底成「附件 N.ext」
  const serverName = filename && !isStorageFileName(filename) ? filename : ''
  const objectUrl = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.download = serverName || attachmentDisplayName(file, index)
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  // 立即释放会让部分浏览器来不及启动下载
  setTimeout(() => URL.revokeObjectURL(objectUrl), 1000)
}
