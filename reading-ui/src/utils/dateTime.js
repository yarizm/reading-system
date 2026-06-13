const pad = (value) => String(value).padStart(2, '0')

export const formatDatePart = (timeStr, fallback = '') => {
  if (!timeStr) return fallback
  return String(timeStr).split('T')[0]
}

export const formatDateTimeMinute = (timeStr, fallback = '') => {
  if (!timeStr) return fallback
  return String(timeStr).replace('T', ' ').substring(0, 16)
}

export const formatChatTime = (timeStr, now = new Date()) => {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  if (Number.isNaN(date.getTime())) return ''
  if (date.toDateString() === now.toDateString()) {
    return `${pad(date.getHours())}:${pad(date.getMinutes())}`
  }
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}
