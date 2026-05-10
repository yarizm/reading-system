export const getAuthHeaders = () => {
  const userStr = localStorage.getItem('user')
  if (!userStr) return {}

  try {
    const token = JSON.parse(userStr).token
    return token ? { Authorization: `Bearer ${token}` } : {}
  } catch (e) {
    localStorage.removeItem('user')
    return {}
  }
}
