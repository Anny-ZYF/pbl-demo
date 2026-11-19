// 早期版本把存储用的 UUID 文件名写进了 original_name，这里识别出来做展示兜底，
// 避免教师/学生看到一串无意义的字符（历史上被当作「乱码」）。
const STORAGE_FILE_NAME = /^[0-9a-f-]{36}\.[A-Za-z0-9]{1,8}$/i

export function isStorageFileName(name) {
  return typeof name === 'string' && STORAGE_FILE_NAME.test(name.trim())
}

export function attachmentDisplayName(file, index = 0) {
  const original = typeof file?.originalName === 'string' ? file.originalName.trim() : ''
  if (original && !isStorageFileName(original)) return original
  const key = typeof file?.storageKey === 'string' ? file.storageKey : ''
  const dot = key.lastIndexOf('.')
  const extension = dot > key.lastIndexOf('/') ? key.slice(dot) : ''
  return `附件 ${index + 1}${extension}`
}

function cleanName(value) {
  const cleaned = String(value)
    .replace(/[\x00-\x1f\x7f]/g, '')
    .replace(/\\/g, '/')
    .split('/')
    .pop()
    .trim()
    .replace(/^[.\s]+/, '')
  return cleaned || ''
}

/**
 * 从下载响应头解析文件名。Spring 对中文名会同时给出 ASCII 近似的
 * filename 与 RFC 5987 的 filename*，必须优先取 filename* 才能拿到原文。
 */
export function filenameFromContentDisposition(disposition) {
  const header = typeof disposition === 'string' ? disposition : ''
  if (!header) return ''

  // Spring 对中文名会给出一个 ASCII 近似（如 ??.docx），那不是可用的名字
  const usable = (value) => Boolean(value)
    && !value.includes('\uFFFD')
    && !/^\?+(\.[A-Za-z0-9]+)?$/.test(value)

  const encoded = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(header)
  if (encoded) {
    try {
      const value = cleanName(decodeURIComponent(encoded[1].trim().replace(/^"|"$/g, '')))
      if (usable(value)) return value
    } catch {
      // 编码异常时退回普通 filename
    }
  }

  const plain = /filename\s*=\s*(?:"([^"]*)"|([^;]+))/i.exec(header)
  const value = plain ? cleanName(plain[1] ?? plain[2] ?? '') : ''
  return usable(value) ? value : ''
}
