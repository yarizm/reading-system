import axios from 'axios'
import { showFailToast } from 'vant'
import { useAuthStore } from '../stores/auth'

const request = axios.create()

request.interceptors.request.use((config) => {
  const authStore = useAuthStore()
  if (authStore.token) {
    config.headers.Authorization = `Bearer ${authStore.token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const authStore = useAuthStore()
      authStore.logout()
      window.location.href = '/login'
      showFailToast('登录已过期，请重新登录')
    } else if (error.response?.status === 403) {
      showFailToast('权限不足')
    }
    return Promise.reject(error)
  }
)

export default request
