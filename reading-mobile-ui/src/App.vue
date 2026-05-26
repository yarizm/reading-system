<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showNotify } from 'vant'
import axios from 'axios'

import { useAuthStore } from './stores/auth'
import MobileAgentGuide from './components/MobileAgentGuide.vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const activeTab = ref(0)
const hideTabBar = computed(() => route.meta.hideTabBar)

// Notification badge
const unreadCount = ref(0)
const userInfo = computed(() => authStore.user || {})
let ws = null

const tabMap = ['/', '/shelf', '/my-books', '/friends', '/profile']

watch(route, (r) => {
  const idx = tabMap.indexOf(r.path)
  if (idx >= 0) activeTab.value = idx
}, { immediate: true })

const onTabChange = (index) => {
  router.push(tabMap[index])
}

const loadUnread = async () => {
  if (!userInfo.value.id) return
  try {
    const res = await axios.get(`/api/chat/unread/${userInfo.value.id}`)
    unreadCount.value = res.data.data || 0
  } catch (e) { /* ignore */ }
}

const connectWs = () => {
  if (!userInfo.value.id) return
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${location.host}/ws/notification?userId=${userInfo.value.id}&token=${encodeURIComponent(userInfo.value.token || '')}`
  ws = new WebSocket(wsUrl)

  ws.onmessage = (event) => {
    try {
      const msg = JSON.parse(event.data)
      if (msg.type === 'chat') {
        loadUnread()
        if (msg.data?.shareType === 'book') return
        showNotify({ type: 'primary', message: '收到新消息' })
      } else if (msg.type === 'friend_request') {
        showNotify({ type: 'primary', message: `${msg.data?.nickname || '某人'} 想加你为好友` })
      } else if (msg.type === 'book_share') {
        showNotify({ type: 'primary', message: `收到书籍分享：${msg.data?.bookTitle || ''}` })
      }
    } catch (e) { console.error('WS parse error', e) }
  }

  ws.onclose = () => {
    if (userInfo.value.id && ws) {
      setTimeout(() => {
        if (userInfo.value.id) connectWs()
      }, 5000)
    }
  }
}

watch(() => authStore.user, (newUser) => {
  if (ws) {
    const oldWs = ws
    ws = null
    oldWs.close()
  }
  if (newUser && newUser.id) {
    loadUnread()
    connectWs()
  } else {
    unreadCount.value = 0
  }
}, { deep: true, immediate: true })

onUnmounted(() => {
  if (ws) {
    ws.close()
  }
})
</script>

<template>
  <div class="app-container">
    <router-view v-slot="{ Component }">
      <transition name="page-slide" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>

    <van-tabbar
      v-if="!hideTabBar"
      v-model="activeTab"
      @change="onTabChange"
      active-color="#a36b46"
      inactive-color="#8b6f52"
      :border="false"
      :safe-area-inset-bottom="true"
      :placeholder="true"
      class="app-tabbar"
    >
      <van-tabbar-item icon="wap-home-o">首页</van-tabbar-item>
      <van-tabbar-item icon="bookmark-o">书架</van-tabbar-item>
      <van-tabbar-item icon="records-o">我的书籍</van-tabbar-item>
      <van-tabbar-item icon="friends-o" :badge="unreadCount > 0 ? unreadCount : ''">好友</van-tabbar-item>
      <van-tabbar-item icon="user-o">我的</van-tabbar-item>
    </van-tabbar>

    <!-- 仅在移动端使用此底部向导 -->
    <MobileAgentGuide />
  </div>
</template>

<style>
.app-container {
  min-height: 100vh;
  background: var(--color-bg);
}

.app-tabbar {
  position: fixed !important;
  bottom: 0 !important;
  left: 0 !important;
  right: 0 !important;
  z-index: 100 !important;
  background: rgba(255, 255, 255, 0.75) !important;
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  box-shadow: 0 -1px 12px rgba(139, 111, 82, 0.1);
  border-top: 1px solid rgba(139, 111, 82, 0.1);
}

.app-tabbar .van-tabbar-item {
  background: transparent !important;
}

.app-tabbar .van-tabbar-item__icon {
  font-size: 22px;
  margin-bottom: 2px;
}
</style>
