<template>
  <div class="home-container">
    <div class="nav-header">
      <div class="logo">📚 智能书籍管理系统</div>
      <div class="nav-right">
        <el-button link class="nav-item" @click="goToShelf">
          <el-icon><Collection /></el-icon> 我的书架
        </el-button>
        <el-button link class="nav-item" @click="goToFriends">
          <el-icon><User /></el-icon> 好友
          <el-badge v-if="unreadCount > 0" :value="unreadCount" :max="99" class="unread-badge" />
        </el-button>
        <el-button link class="nav-item notification-bell" :class="{ blinking: isBlinking }" @click="toggleNotifyPanel" v-if="userInfo.id">
          <el-icon><Bell /></el-icon>
          <el-badge v-if="notifications.length > 0" :value="notifications.length" :max="99" class="unread-badge" />
        </el-button>
        <el-dropdown v-if="userInfo.id" trigger="click" @command="handleUserCommand">
          <div class="user-avatar-box">
            <el-avatar :size="32" :src="userInfo.avatar || defaultAvatar" />
            <span class="username">{{ userInfo.nickname || userInfo.username }}</span>
            <el-icon><CaretBottom /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item command="admin" v-if="userInfo.role === 1" divided>
                <span style="color: #F56C6C; font-weight: bold;">后台管理</span>
              </el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <div v-else class="login-btn-box">
          <el-button type="primary" round @click="goToLogin">登录 / 注册</el-button>
        </div>
      </div>
    </div>

    <div class="header-section">
      <div class="search-box">
        <el-input v-model="searchKeyword" placeholder="搜索书名 / 作者..." size="large" class="search-input" @keyup.enter="handleSearch">
          <template #append><el-button :icon="Search" @click="handleSearch" /></template>
        </el-input>
      </div>
      <div class="category-tags">
        <span :class="['tag-item', currentCategory === '全部' ? 'active' : '']" @click="changeCategory('全部')">全部</span>
        <span v-for="cat in categories" :key="cat" :class="['tag-item', currentCategory === cat ? 'active' : '']" @click="changeCategory(cat)">
          {{ cat }}
        </span>
      </div>
    </div>

    <el-row :gutter="20" class="core-section">
      <el-col :span="16">
        <el-carousel trigger="click" height="360px" class="promo-carousel">
          <el-carousel-item v-for="book in hotBooks" :key="book.id" @click="goToDetail(book.id)">
            <img :src="book.coverUrl || defaultCover" class="carousel-img" @error="(e) => e.target.src = defaultCover" />
            <div class="carousel-info">
              <h3>{{ book.title }}</h3>
              <p class="author-text">{{ book.author }}</p>
              <div class="heat-tag">🔥 {{ book.heat || 0 }} 人在读</div>
              <p class="desc-text">{{ book.description ? book.description.substring(0, 50) + '...' : '暂无简介' }}</p>
            </div>
          </el-carousel-item>
        </el-carousel>
      </el-col>
      <el-col :span="8">
        <el-card class="rank-card" shadow="hover">
          <template #header><div class="card-header"><span>🔥 热门阅读榜</span></div></template>
          <div class="rank-list">
            <div v-for="(book, index) in rankBooks" :key="book.id" class="rank-item" @click="goToDetail(book.id)">
              <span :class="['rank-num', index < 3 ? 'top-three' : '']">{{ index + 1 }}</span>
              <span class="rank-name" :title="book.title">{{ book.title }}</span>
              <span class="rank-hot">🔥</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <div class="section-title">
      <el-icon><StarFilled /></el-icon> 猜你喜欢
      <el-button link type="primary" size="small" @click="loadRecommendBooks" style="margin-left: 10px;" :loading="recommendLoading">
        <el-icon><Refresh /></el-icon> 换一批
      </el-button>
    </div>

    <el-row :gutter="20" class="recommend-section" v-loading="recommendLoading" element-loading-text="AI 正在分析你的阅读口味...">
      <el-col :span="6" v-for="book in recommendBooks" :key="book.id">
        <div class="book-card-simple" @click="goToDetail(book.id)">
          <div class="cover-wrapper">
            <img :src="book.coverUrl || defaultCover" class="simple-cover" @error="(e) => e.target.src = defaultCover" />
            <div class="hover-mask">点击阅读</div>
          </div>
          <div class="simple-info">
            <div class="simple-name" :title="book.title">{{ book.title }}</div>
            <div class="simple-author">{{ book.author }}</div>
          </div>
        </div>
      </el-col>
      <el-empty v-if="recommendBooks.length === 0 && !recommendLoading" description="暂无推荐书籍" />
    </el-row>

    <div class="section-title"><el-icon><Reading /></el-icon> 书库</div>
    <div class="book-grid">
      <el-card v-for="book in tableData" :key="book.id" class="book-card" shadow="hover" :body-style="{ padding: '0px' }" @click="goToDetail(book.id)">
        <img :src="book.coverUrl || defaultCover" class="book-cover" @error="(e) => e.target.src = defaultCover" />
        <div class="book-info">
          <div class="book-title" :title="book.title">{{ book.title }}</div>
          <div class="book-author">{{ book.author }}</div>
          <div class="book-desc">{{ book.description?.substring(0, 30) }}...</div>
        </div>
      </el-card>
    </div>
    <div class="pagination-box">
      <el-pagination background layout="prev, pager, next" :total="total" :page-size="pageSize" @current-change="handleCurrentChange" />
    </div>

    <!-- 通知面板 -->
    <transition name="slide-notify">
      <div v-if="showNotifyPanel" class="notify-panel">
        <div class="notify-panel-header">
          <span>🔔 消息通知</span>
          <el-button link size="small" @click="clearNotifications">清空</el-button>
          <el-button link size="small" @click="showNotifyPanel = false">关闭</el-button>
        </div>
        <div class="notify-panel-body">
          <el-empty v-if="notifications.length === 0" description="暂无新通知" :image-size="60" />
          <div v-for="(n, idx) in notifications" :key="idx" class="notify-item" @click="handleNotifyClick(n, idx)">
            <div class="notify-icon">
              <span v-if="n.type === 'chat'">💬</span>
              <span v-else-if="n.type === 'friend_request'">👥</span>
              <span v-else-if="n.type === 'book_share'">📚</span>
            </div>
            <div class="notify-text">
              <div class="notify-title">{{ getNotifyTitle(n) }}</div>
              <div class="notify-desc">{{ getNotifyDesc(n) }}</div>
            </div>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
// === 修改点 2: 引入 Refresh 图标 ===
import { Search, StarFilled, Reading, Collection, CaretBottom, Refresh, User, Bell } from '@element-plus/icons-vue'
import axios from 'axios'

const router = useRouter()
const defaultCover = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII='
const defaultAvatar = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'

// 数据状态
const searchKeyword = ref('')
const currentCategory = ref('全部')
const categories = ['科幻', '文学', '历史', '技术', '悬疑']

const hotBooks = ref([])
const rankBooks = ref([])
const recommendBooks = ref([])
const recommendLoading = ref(false) // === 修改点 3: 新增 loading 状态 ===
const tableData = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(12)
const userInfo = ref({})
const unreadCount = ref(0)

// 通知系统
const notifications = ref([])
const showNotifyPanel = ref(false)
const isBlinking = ref(false)
let blinkTimer = null
let ws = null

onMounted(() => {
  const userStr = localStorage.getItem('user')
  if (userStr) {
    userInfo.value = JSON.parse(userStr)
  }
  loadHotBooks()
  loadRankBooks()
  loadRecommendBooks()
  loadBooks()
  loadUnreadCount()
  connectWebSocket()
})

onUnmounted(() => {
  if (ws) ws.close()
  if (blinkTimer) clearTimeout(blinkTimer)
})

const connectWebSocket = () => {
  if (!userInfo.value.id) return
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${location.host}/ws/notification?userId=${userInfo.value.id}`
  ws = new WebSocket(wsUrl)

  ws.onmessage = (event) => {
    try {
      const msg = JSON.parse(event.data)
      notifications.value.unshift(msg)
      // 触发闪烁动画
      triggerBlink()
      // 刷新未读数
      loadUnreadCount()
    } catch (e) {
      console.error('WS parse error', e)
    }
  }

  ws.onclose = () => {
    // 自动重连（5秒后）
    if (userInfo.value.id) {
      setTimeout(connectWebSocket, 5000)
    }
  }
}

const triggerBlink = () => {
  isBlinking.value = true
  if (blinkTimer) clearTimeout(blinkTimer)
  blinkTimer = setTimeout(() => { isBlinking.value = false }, 3000)
}

const toggleNotifyPanel = () => {
  showNotifyPanel.value = !showNotifyPanel.value
  if (showNotifyPanel.value) {
    isBlinking.value = false
  }
}

const clearNotifications = () => {
  notifications.value = []
}

const getNotifyTitle = (n) => {
  if (n.type === 'chat') return '💬 新消息'
  if (n.type === 'friend_request') return '👥 好友请求'
  if (n.type === 'book_share') return '📚 书籍分享'
  return '通知'
}

const getNotifyDesc = (n) => {
  const d = n.data || {}
  if (n.type === 'chat') return d.content ? d.content.substring(0, 30) : '收到一条新消息'
  if (n.type === 'friend_request') return `${d.nickname || '某人'} 想加你为好友`
  if (n.type === 'book_share') return `收到一本书籍分享：${d.bookTitle || ''}`
  return ''
}

const handleNotifyClick = (n, idx) => {
  notifications.value.splice(idx, 1)
  if (n.type === 'chat') {
    router.push(`/chat/${n.data?.senderId}`)
  } else if (n.type === 'friend_request') {
    router.push('/friends')
  } else if (n.type === 'book_share') {
    router.push('/friends')
  }
  showNotifyPanel.value = false
}

const goToShelf = () => {
  if (!userInfo.value.id) return ElMessage.warning('请先登录')
  router.push('/shelf')
}

const goToFriends = () => {
  if (!userInfo.value.id) return ElMessage.warning('请先登录')
  router.push('/friends')
}

const loadUnreadCount = async () => {
  if (!userInfo.value.id) return
  try {
    const res = await axios.get(`/api/chat/unread/${userInfo.value.id}`)
    unreadCount.value = res.data.data || 0
  } catch (e) {
    // 忽略
  }
}

const goToLogin = () => router.push('/login')

const loadHotBooks = async () => {
  const res = await axios.get('/api/sysBook/hot')
  hotBooks.value = res.data.data || []
}

const loadRankBooks = async () => {
  const res = await axios.get('/api/sysBook/rank')
  rankBooks.value = res.data.data || []
}

// === 修改点 4: 升级版推荐获取逻辑 ===
const loadRecommendBooks = async () => {
  recommendLoading.value = true
  try {
    // 传入 userId，让后端决定是走 AI 还是走随机
    // 如果没登录，userId 为 undefined，后端会自动处理
    const res = await axios.get('/api/sysBook/recommend', {
      params: { userId: userInfo.value.id }
    })

    if (res.data.code === '200') {
      recommendBooks.value = res.data.data || []
    } else {
      ElMessage.warning('获取推荐失败，请稍后重试')
    }
  } catch (e) {
    console.error('推荐接口异常', e)
  } finally {
    recommendLoading.value = false
  }
}

const loadBooks = async () => {
  const res = await axios.get('/api/sysBook/list', {
    params: {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: searchKeyword.value,
      category: currentCategory.value
    }
  })
  tableData.value = res.data.data.records
  total.value = res.data.data.total
}

const handleUserCommand = (cmd) => {
  if (cmd === 'logout') {
    localStorage.removeItem('user')
    userInfo.value = {}
    unreadCount.value = 0
    notifications.value = []
    if (ws) ws.close()
    ElMessage.success('已退出登录')
    loadRecommendBooks()
  } else if (cmd === 'profile') {
    router.push('/profile')
  } else if (cmd === 'admin') {
    router.push('/admin')
  }
}

const handleSearch = () => { pageNum.value = 1; loadBooks() }
const changeCategory = (cat) => { currentCategory.value = cat; pageNum.value = 1; loadBooks() }
const handleCurrentChange = (val) => { pageNum.value = val; loadBooks() }
const goToDetail = (id) => { router.push(`/book/${id}`) }
</script>

<style scoped>
.home-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 18px 24px;
}

/* === 导航栏 === */
.nav-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 56px;
  border-bottom: 1px solid #e8e0d6;
  margin-bottom: 24px;
  padding: 0 8px;
}
.logo {
  font-size: 22px;
  font-weight: 700;
  color: #4a3828;
  font-family: 'Noto Serif SC', serif;
  letter-spacing: 2px;
}
.nav-right {
  display: flex;
  align-items: center;
  gap: 18px;
}
.nav-item {
  font-size: 14px;
  color: #6b5e53;
  font-weight: 500;
}
.user-avatar-box {
  display: flex;
  align-items: center;
  cursor: pointer;
  gap: 8px;
}
.username {
  font-size: 14px;
  color: #4a3828;
  font-weight: 500;
}
.unread-badge {
  margin-left: 4px;
}

/* === 通知铃铛 === */
.notification-bell {
  position: relative;
}
.notification-bell.blinking :deep(.el-icon) {
  animation: bell-blink 0.5s ease-in-out infinite alternate;
}
@keyframes bell-blink {
  0% { color: #6b5e53; transform: scale(1); }
  100% { color: #e6a23c; transform: scale(1.3) rotate(15deg); }
}

/* === 通知面板 === */
.notify-panel {
  position: fixed;
  top: 60px;
  right: 24px;
  width: 340px;
  max-height: 460px;
  background: #fffdf9;
  border: 1px solid #e8e0d6;
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(60, 40, 20, 0.15);
  z-index: 2000;
  display: flex;
  flex-direction: column;
}
.notify-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #f0ece4;
  font-weight: 600;
  font-size: 14px;
  color: #3d3632;
  gap: 8px;
}
.notify-panel-header span:first-child {
  flex: 1;
}
.notify-panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}
.notify-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 16px;
  cursor: pointer;
  transition: background 0.15s;
}
.notify-item:hover {
  background: #faf7f2;
}
.notify-icon {
  font-size: 20px;
  flex-shrink: 0;
  margin-top: 2px;
}
.notify-text {
  flex: 1;
  min-width: 0;
}
.notify-title {
  font-size: 13px;
  font-weight: 600;
  color: #3d3632;
  margin-bottom: 2px;
}
.notify-desc {
  font-size: 12px;
  color: #9b8e82;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 通知面板动画 */
.slide-notify-enter-active,
.slide-notify-leave-active {
  transition: opacity 0.25s, transform 0.25s;
}
.slide-notify-enter-from,
.slide-notify-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

/* === 搜索与分类 === */
.header-section {
  text-align: center;
  margin-bottom: 32px;
}
.search-box {
  width: 560px;
  margin: 0 auto 18px;
}
.search-box :deep(.el-input__wrapper) {
  border-radius: 4px;
  box-shadow: 0 0 0 1px #ddd5ca inset;
}
.search-box :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #b8a898 inset;
}
.search-box :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #8b6f52 inset;
}
.category-tags {
  display: flex;
  justify-content: center;
  gap: 10px;
  flex-wrap: wrap;
}
.tag-item {
  cursor: pointer;
  padding: 5px 18px;
  border-radius: 3px;
  color: #7a6e63;
  font-size: 14px;
  transition: all 0.2s;
  border: 1px solid transparent;
}
.tag-item:hover {
  color: #4a3828;
  border-color: #d4c8ba;
  background: #f5f0e8;
}
.tag-item.active {
  background-color: #5a4435;
  color: #fff;
  font-weight: 600;
  border-color: #5a4435;
}

/* === 轮播 + 排行区 === */
.core-section {
  margin-bottom: 36px;
}
.promo-carousel {
  border-radius: 6px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(60, 40, 20, 0.08);
  border: 1px solid #e8e0d6;
}
.carousel-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  background-color: #efe9e0;
}
.carousel-info {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: linear-gradient(to top, rgba(30,20,10,0.88) 0%, rgba(30,20,10,0.2) 100%);
  color: #f0ece4;
  padding: 22px 28px;
  text-align: left;
}
.carousel-info h3 {
  margin: 0 0 6px 0;
  font-size: 22px;
  font-weight: 700;
  font-family: 'Noto Serif SC', serif;
}
.author-text {
  font-size: 13px;
  margin-bottom: 6px;
  opacity: 0.85;
}
.heat-tag {
  display: inline-block;
  background-color: #a34040;
  color: #fff;
  padding: 3px 10px;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 600;
  margin-bottom: 8px;
}
.desc-text {
  font-size: 13px;
  opacity: 0.75;
  line-height: 1.5;
  margin: 0;
}

/* === 排行榜 === */
.rank-card {
  height: 360px;
  display: flex;
  flex-direction: column;
  border: 1px solid #e8e0d6;
}
.rank-card :deep(.el-card__header) {
  border-bottom: 1px solid #e8e0d6;
  padding: 14px 18px;
}
.card-header span {
  font-family: 'Noto Serif SC', serif;
  font-weight: 600;
  color: #4a3828;
}
.rank-list {
  overflow-y: auto;
  flex: 1;
  padding: 4px 14px;
}
.rank-item {
  display: flex;
  align-items: center;
  padding: 9px 0;
  border-bottom: 1px solid #f0ece4;
  cursor: pointer;
  width: 100%;
  transition: background 0.15s;
}
.rank-item:hover {
  background: #faf5ed;
}
.rank-item:last-child {
  border-bottom: none;
}
.rank-num {
  width: 22px;
  height: 22px;
  line-height: 22px;
  text-align: center;
  background: #ede7de;
  border-radius: 3px;
  font-size: 12px;
  margin-right: 10px;
  color: #8a7d72;
  flex-shrink: 0;
  font-weight: 600;
}
.rank-num.top-three {
  background: #c09a5c;
  color: #fff;
  font-weight: 700;
}
.rank-name {
  flex: 1;
  width: 0;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-right: 5px;
  color: #3d3632;
}
.rank-hot {
  font-size: 12px;
  flex-shrink: 0;
}

/* === 通用标题 === */
.section-title {
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 18px;
  display: flex;
  align-items: center;
  gap: 8px;
  border-left: 3px solid #8b6f52;
  padding-left: 12px;
  color: #3d3632;
  font-family: 'Noto Serif SC', serif;
}

/* === 推荐区 === */
.recommend-section {
  margin-bottom: 36px;
}
.book-card-simple {
  cursor: pointer;
  background: #fffdf9;
  border-radius: 6px;
  overflow: hidden;
  transition: box-shadow 0.25s, transform 0.25s;
  border: 1px solid #e8e0d6;
}
.book-card-simple:hover {
  transform: translateY(-3px);
  box-shadow: 0 6px 18px rgba(60, 40, 20, 0.1);
}
.cover-wrapper {
  position: relative;
  height: 200px;
  background-color: #f0ece4;
}
.simple-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.hover-mask {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(40, 28, 16, 0.45);
  color: #f0ece4;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.25s;
  font-weight: 600;
  font-size: 14px;
  letter-spacing: 1px;
}
.book-card-simple:hover .hover-mask {
  opacity: 1;
}
.simple-info {
  padding: 10px 12px;
  text-align: left;
}
.simple-name {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #3d3632;
}
.simple-author {
  color: #9b8e82;
  font-size: 12px;
}

/* === 书库网格 === */
.book-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(176px, 1fr));
  gap: 18px;
}
.book-card {
  cursor: pointer;
  transition: box-shadow 0.25s, transform 0.2s;
  border: 1px solid #e8e0d6;
  border-radius: 6px;
}
.book-card:hover {
  transform: scale(1.02);
  box-shadow: 0 4px 14px rgba(60, 40, 20, 0.1);
}
.book-cover {
  width: 100%;
  height: 220px;
  object-fit: cover;
  background-color: #f0ece4;
}
.book-info {
  padding: 12px;
}
.book-title {
  font-weight: 600;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: #3d3632;
  font-size: 14px;
}
.book-author {
  font-size: 12px;
  color: #9b8e82;
  margin-bottom: 4px;
}
.book-desc {
  font-size: 12px;
  color: #b5a99c;
  line-height: 1.4;
}
.pagination-box {
  margin-top: 32px;
  display: flex;
  justify-content: center;
}
</style>