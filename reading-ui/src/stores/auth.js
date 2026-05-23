import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import axios from 'axios'

export const useAuthStore = defineStore('auth', () => {
  const safeParseJSON = (str) => {
    if (!str || str === 'null') return null
    try {
      return JSON.parse(str)
    } catch (e) {
      localStorage.removeItem('user')
      return null
    }
  }
  const user = ref(safeParseJSON(localStorage.getItem('user')))

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
      const res = await axios.get('/api/sysUser/me')
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
