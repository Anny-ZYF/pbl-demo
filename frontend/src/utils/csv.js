// 表格导出统一入口。
// 两个坑必须一起处理：Excel 打开中文 CSV 需要 BOM；以 = + - @ 开头的单元格会被当成公式执行。
const escapeCell = (value) => {
  const text = value == null ? '' : String(value)
  const safe = /^[\s]*[=+@-]/.test(text) ? `'${text}` : text
  return `"${safe.replaceAll('"', '""')}"`
}

export function toCsv(headers, rows) {
  return `\uFEFF${[headers, ...rows].map((row) => row.map(escapeCell).join(',')).join('\r\n')}`
}

/** 文件名里去掉 Windows / macOS 都不接受的字符，空白名给一个兜底。 */
export const safeFileName = (name, fallback = '导出') => {
  const text = String(name ?? '').replace(/[\\/:*?"<>|]/g, '_').trim()
  return text || fallback
}

/** 导出文件名用的日期戳，如 20261128。 */
export const dateStamp = (date = new Date()) => {
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}`
}

export function downloadCsv(filename, headers, rows) {
  const blob = new Blob([toCsv(headers, rows)], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.append(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
