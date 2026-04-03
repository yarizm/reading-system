<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showNotify } from 'vant'

const router = useRouter()
const route = useRoute()

const activeTab = ref(0)
const hideTabBar = computed(() => route.meta.hideTabBar)

// Notification badge
const unreadCount = ref(0)
const userInfo = ref({})
let ws = null

const tabMap = ['/', '/shelf', '/friends', '/profile']

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
    const res = await fetch(`/api/chat/unread/${userInfo.value.id}`)
    const data = await res.json()
    unreadCount.value = data.data || 0
  } catch (e) { /* ignore */ }
}

const connectWs = () => {
  if (!userInfo.value.id) return
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${location.host}/ws/notification?userId=${userInfo.value.id}`
  ws = new WebSocket(wsUrl)

  ws.onmessage = (event) => {
    try {
      const msg = JSON.parse(event.data)
      if (msg.type === 'chat') {
        loadUnread()
        showNotify({ type: 'primary', message: '收到新消息' })
      } else if (msg.type === 'friend_request') {
        showNotify({ type: 'primary', message: `${msg.data?.nickname || '某人'} 想加你为好友` })
      } else if (msg.type === 'book_share') {
        showNotify({ type: 'primary', message: `收到书籍分享：${msg.data?.bookTitle || ''}` })
      }
    } catch (e) { console.error('WS parse error', e) }
  }

  ws.onclose = () => {
    if (userInfo.value.id) setTimeout(connectWs, 5000)
  }
}

onMounted(() => {
  const u = localStorage.getItem('user')
  if (u) {
    userInfo.value = JSON.parse(u)
    loadUnread()
    connectWs()
  }
})

onUnmounted(() => {
  if (ws) ws.close()
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
      active-color="#8b6f52"
      inactive-color="#9b8e82"
      :border="false"
      :safe-area-inset-bottom="true"
      :placeholder="true"
      class="app-tabbar"
    >
      <van-tabbar-item icon="wap-home-o">首页</van-tabbar-item>
      <van-tabbar-item icon="bookmark-o">书架</van-tabbar-item>
      <van-tabbar-item icon="friends-o" :badge="unreadCount > 0 ? unreadCount : ''">好友</van-tabbar-item>
      <van-tabbar-item icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<style>
.app-container {
  min-height: 100vh;
  background: var(--color-bg);
}

.app-tabbar {
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  box-shadow: 0 -1px 12px rgba(60, 40, 20, 0.06);
}

.app-tabbar .van-tabbar-item__icon {
  font-size: 22px;
  margin-bottom: 2px;
}
</style>
