import http from './http'
import { filenameFromContentDisposition } from '@/utils/attachmentName'

// 附件上传/下载是三个角色共用的 /api/v1/files，必须走带 JWT 的 axios 实例。
// 裸 <a href> 会被 SecurityConfig 的 authenticated() 拦成 401。
export const uploadFile = (file) => {
  const data = new FormData()
  data.append('file', file)
  // multipart 头里的中文文件名在部分容器下会被误解码，额外用纯 ASCII 传一份
  data.append('name', encodeURIComponent(file.name || ''))
  return http.post('/files', data, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}

export const downloadFileByKey = async (storageKey) => {
  const { blob, headers } = await http.get('/files', {
    params: { key: storageKey },
    responseType: 'blob',
    timeout: 60000
  })
  const disposition = headers?.get?.('content-disposition')
    ?? headers?.['content-disposition']
    ?? headers?.['Content-Disposition']
    ?? ''
  return { blob, filename: filenameFromContentDisposition(disposition) }
}
