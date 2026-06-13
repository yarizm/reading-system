import { useAuthStore } from '../stores/auth'

export const getAuthToken = () => {
  const authStore = useAuthStore()
  return authStore.token || ''
}

export const getAuthHeaders = () => {
  const token = getAuthToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export const withFileAccessToken = (url) => {
  if (!url || !url.startsWith('/files/')) {
    return url
  }
  if (/[?&]token=/.test(url)) {
    return url
  }
  const token = getAuthToken()
  if (!token) {
    return url
  }
  const separator = url.includes('?') ? '&' : '?'
  return `${url}${separator}token=${encodeURIComponent(token)}`
}
