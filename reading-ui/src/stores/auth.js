import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import axios from 'axios'
import { parseJsonSafely } from '../utils/jsonUtils'

export const useAuthStore = defineStore('auth', () => {
  const user = ref(parseJsonSafely(localStorage.getItem('user'), null, () => localStorage.removeItem('user')))

  const isLoggedIn = computed(() => user.value !== null && !!user.value.token)
  const isAdmin = computed(() => user.value?.role === 1)
  const token = computed(() => user.value?.token || '')

  function login(userData) {
    user.value = userData
    localStorage.setItem('user', JSON.stringify(userData))
  }

  function logout() {
    user.value = null
    localStorage.removeItem('user')
  }

  async function fetchAndVerify() {
    try {
      const currentToken = user.value?.token || ''
      // 故意用裸 axios 而非共享 request 实例：request 的 401 拦截器会触发跳转 /login，
      // 而初始化校验时 token 失效应自行 logout 而非跳转，故手动注入 header 并自行处理 401。
      const res = await axios.get('/api/sysUser/me', {
        headers: currentToken ? { Authorization: `Bearer ${currentToken}` } : {}
      })
      if (res.data.code === '200') {
        const serverData = res.data.data
        user.value = { ...user.value, ...serverData }
        localStorage.setItem('user', JSON.stringify(user.value))
        return true
      }
    } catch (e) {
      if (e.response?.status === 401) {
        logout()
      }
    }
    return false
  }

  return { user, isLoggedIn, isAdmin, token, login, logout, fetchAndVerify }
})
