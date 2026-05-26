import { useAuthStore } from '../stores/auth'

export const getAuthToken = () => {
  const userStr = localStorage.getItem('user')
  if (!userStr) return ''
  try {
    const token = JSON.parse(userStr).token
    return token || ''
  } catch (e) {
    return ''
  }
}

export const getAuthHeaders = () => {
  const token = getAuthToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export const withFileAccessToken = (url) => {
  if (!url || !url.startsWith('/files/')) {
    return url
  }
  const token = getAuthToken()
  if (!token) {
    return url
  }
  const separator = url.includes('?') ? '&' : '?'
  return `${url}${separator}token=${encodeURIComponent(token)}`
}
