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
        <el-autocomplete
          v-model="searchKeyword"
          :fetch-suggestions="querySearchAsync"
          placeholder="搜索书名 / 作者 / 简介 / 正文内容..."
          size="large"
          class="search-input custom-autocomplete"
          :prefix-icon="Search"
          @select="handleSelect"
          @keyup.enter="handleSearch"
          clearable>
          <template #default="{ item }">
            <div class="suggest-item">
              <img :src="item.coverUrl || defaultCover" class="suggest-cover" @error="(e) => e.target.src=defaultCover" />
              <div class="suggest-info">
                <div class="suggest-title" :title="item.title">{{ item.title }}</div>
                <div class="suggest-author">{{ item.author }}</div>
              </div>
            </div>
          </template>
        </el-autocomplete>
      </div>
      <div class="category-tags">
        <span :class="['tag-item', currentCategory === '全部' ? 'active' : '']" @click="changeCategory('全部')">全部</span>
        <span v-for="cat in categories" :key="cat" :class="['tag-item', currentCategory === cat ? 'active' : '']" @click="changeCategory(cat)">
          {{ cat }}
        </span>
      </div>
    </div>

    <el-row :gutter="20" class="core-section" v-if="!isSearchMode">
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

    <div class="section-title" v-if="!isSearchMode">
      <el-icon><StarFilled /></el-icon> 猜你喜欢
      <el-button link type="primary" size="small" @click="loadRecommendBooks" style="margin-left: 10px;" :loading="recommendLoading">
        <el-icon><Refresh /></el-icon> 换一批
      </el-button>
    </div>

    <el-row :gutter="20" class="recommend-section" v-loading="recommendLoading" element-loading-text="AI 正在分析你的阅读口味..." v-if="!isSearchMode">
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

    <div class="section-title search-result-title" v-if="isSearchMode">
      <span>🔍 搜索 "{{ searchKeyword }}" 的结果 (共 {{ total }} 条)</span>
      <el-button link type="primary" @click="clearSearch" class="clear-search-btn">
        <el-icon><ArrowLeft /></el-icon>返回主界面
      </el-button>
    </div>
    <div class="section-title" v-else><el-icon><Reading /></el-icon> 探索书库</div>
    <div class="book-grid">
      <el-card v-for="book in tableData" :key="book.id" class="book-card" shadow="hover" :body-style="{ padding: '0px' }" @click="goToDetail(book.id)">
        <img :src="book.coverUrl || defaultCover" class="book-cover" @error="(e) => e.target.src = defaultCover" />
        <div class="book-info">
          <div class="book-title" :title="book.title">{{ book.title }}</div>
          <div class="book-author">{{ book.author }}</div>
          <div class="book-desc">{{ book.description?.substring(0, 30) }}...</div>
          <div class="match-tag" v-if="isSearchMode && book.score">
            <span class="score-text">🔍 综合匹配度: {{ Number(book.score).toFixed(1) }}</span>
          </div>
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
const isSearchMode = ref(false)
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
  const isSearch = searchKeyword.value.trim().length > 0
  isSearchMode.value = isSearch
  
  const url = isSearch ? '/api/search' : '/api/sysBook/list'
  
  const res = await axios.get(url, {
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


const querySearchAsync = async (queryString, cb) => {
  if (!queryString || !queryString.trim()) {
    cb([]);
    return;
  }
  try {
    const res = await axios.get('/api/search', {
      params: { keyword: queryString, pageNum: 1, pageSize: 5 }
    });
    if (res.data.code === '200') {
      const suggestions = res.data.data.records.map(item => {
        return {
          ...item,
          value: item.title + ' - ' + item.author // 仅为了提供给原生的 input value 显示
        };
      });
      cb(suggestions);
    } else {
      cb([]);
    }
  } catch (e) {
    cb([]);
  }
};

const handleSelect = (item) => {
  goToDetail(item.id);
};

const clearSearch = () => {
  searchKeyword.value = '';
  isSearchMode.value = false;
  pageNum.value = 1;
  loadBooks();
};

const handleSearch = () => { pageNum.value = 1; loadBooks() }
const changeCategory = (cat) => { currentCategory.value = cat; pageNum.value = 1; loadBooks() }
const handleCurrentChange = (val) => { pageNum.value = val; loadBooks() }
const goToDetail = (id) => { router.push(`/book/${id}`) }
</script>

<style scoped>
/* ==================================================
   Modernized UI Styles for Home.vue
================================================== */
.home-container {
  max-width: 1240px;
  margin: 0 auto;
  padding: 0 24px 40px;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
  color: #3d3632;
  background-color: #f0ece4;
  min-height: 100vh;
  
  /* Override Element Plus Primary Color to warm brown */
  --el-color-primary: #8b6f52;
  --el-color-primary-light-3: #a38c75;
  --el-color-primary-light-5: #bdae9c;
  --el-color-primary-light-7: #d6ccc2;
  --el-color-primary-light-9: #f0ece4;
  --el-color-primary-dark-2: #6b5040;
}

/* === Navigation Bar (Glassmorphism & Sticky) === */
.nav-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 64px;
  margin: 0 -24px 32px;
  padding: 0 32px;
  position: sticky;
  top: 0;
  z-index: 1000;
  background: rgba(255, 253, 249, 0.85);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid rgba(60, 40, 20, 0.08);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.02);
}
.logo {
  font-size: 22px;
  font-weight: 800;
  color: #2e2520;
  letter-spacing: 1px;
}
.nav-right {
  display: flex;
  align-items: center;
  gap: 24px;
}
.nav-item {
  font-size: 15px;
  color: #5a5048;
  font-weight: 500;
  transition: color 0.2s ease;
}
.nav-item:hover {
  color: #8b6f52;
}
.user-avatar-box {
  display: flex;
  align-items: center;
  cursor: pointer;
  gap: 10px;
  padding: 4px 8px;
  border-radius: 20px;
  transition: background 0.2s;
}
.user-avatar-box:hover {
  background: rgba(60, 40, 20, 0.06);
}
.username {
  font-size: 14px;
  color: #2e2520;
  font-weight: 600;
}
.unread-badge {
  margin-left: 4px;
}

/* === Notification Bell === */
.notification-bell {
  position: relative;
}
.notification-bell.blinking :deep(.el-icon) {
  animation: bell-blink 0.5s ease-in-out infinite alternate;
}
@keyframes bell-blink {
  0% { color: #5a5048; transform: scale(1); }
  100% { color: #ff3b30; transform: scale(1.2) rotate(15deg); }
}

/* === Notification Panel === */
.notify-panel {
  position: absolute;
  top: 50px;
  right: 0;
  width: 360px;
  max-height: 480px;
  background: rgba(255, 253, 249, 0.95);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(0,0,0,0.08);
  border-radius: 16px;
  box-shadow: 0 12px 48px rgba(0, 0, 0, 0.12);
  z-index: 2000;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.notify-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(60, 40, 20, 0.08);
  font-weight: 700;
  font-size: 15px;
  color: #2e2520;
}
.notify-panel-header .el-button { margin-left: 8px; }
.notify-panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}
.notify-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 20px;
  cursor: pointer;
  transition: background 0.2s;
}
.notify-item:hover {
  background: rgba(139, 111, 82, 0.04);
}
.notify-icon {
  font-size: 22px;
  flex-shrink: 0;
}
.notify-text {
  flex: 1;
  min-width: 0;
}
.notify-title {
  font-size: 14px;
  font-weight: 600;
  color: #2e2520;
  margin-bottom: 4px;
}
.notify-desc {
  font-size: 13px;
  color: #9b8e82;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.slide-notify-enter-active,
.slide-notify-leave-active {
  transition: opacity 0.3s cubic-bezier(0.25, 0.8, 0.25, 1), transform 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
}
.slide-notify-enter-from,
.slide-notify-leave-to {
  opacity: 0;
  transform: translateY(-10px) scale(0.98);
}

/* === Search & Filters === */
.header-section {
  text-align: center;
  margin-bottom: 40px;
  padding-top: 20px;
}
.search-box {
  width: 600px;
  margin: 0 auto 24px;
}
.search-box :deep(.el-input__wrapper) {
  border-radius: 24px;
  box-shadow: 0 4px 12px rgba(60, 40, 20, 0.06);
  background: #fffdf9;
  padding: 4px 20px;
  transition: all 0.3s ease;
  border: 1px solid transparent;
}
.search-box :deep(.el-input__wrapper:hover) {
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.06);
}
.search-box :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px rgba(139, 111, 82, 0.2);
  border-color: #8b6f52;
}
.category-tags {
  display: flex;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
}
.tag-item {
  cursor: pointer;
  padding: 8px 24px;
  border-radius: 20px;
  color: #5a5048;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.25s ease;
  background: #fffdf9;
  border: 1px solid rgba(60, 40, 20, 0.08);
  box-shadow: 0 2px 6px rgba(60, 40, 20, 0.03);
}
.tag-item:hover {
  color: #8b6f52;
  border-color: rgba(139, 111, 82, 0.3);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(139, 111, 82, 0.08);
}
.tag-item.active {
  background-color: #8b6f52;
  color: #fffdf9;
  font-weight: 600;
  border-color: #8b6f52;
  box-shadow: 0 4px 12px rgba(139, 111, 82, 0.3);
}

/* === Carousel + Rank Section === */
.core-section {
  margin-bottom: 48px;
}
.promo-carousel {
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.08);
  border: 1px solid rgba(0,0,0,0.04);
}
::v-deep(.el-carousel__item:hover) .carousel-img {
  transform: scale(1.03);
}
.carousel-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  background-color: #ebe3d5;
  will-change: transform;
  transform: translateZ(0);
  transition: transform 0.6s cubic-bezier(0.25, 0.8, 0.25, 1);
}
.carousel-info {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: linear-gradient(to top, rgba(0,0,0,0.85) 0%, rgba(0,0,0,0.4) 60%, transparent 100%);
  color: #fffdf9;
  padding: 40px 32px 24px;
  text-align: left;
  backdrop-filter: blur(2px);
}
.carousel-info h3 {
  margin: 0 0 8px 0;
  font-size: 28px;
  font-weight: 800;
  letter-spacing: 0.5px;
  text-shadow: 0 2px 4px rgba(0,0,0,0.3);
}
.author-text {
  font-size: 14px;
  margin-bottom: 10px;
  color: rgba(255,255,255,0.9);
  font-weight: 500;
}
.heat-tag {
  display: inline-block;
  background: linear-gradient(135deg, #ff3b30, #ff9500);
  color: #fff;
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 700;
  margin-bottom: 12px;
  box-shadow: 0 2px 8px rgba(255, 59, 48, 0.3);
}
.desc-text {
  font-size: 14px;
  color: rgba(255,255,255,0.8);
  line-height: 1.6;
  margin: 0;
  max-width: 80%;
}

/* === Rank List === */
.rank-card {
  height: 360px;
  display: flex;
  flex-direction: column;
  border-radius: 16px;
  border: 1px solid rgba(60, 40, 20, 0.08);
  box-shadow: 0 8px 24px rgba(60, 40, 20, 0.04);
  background: #fffdf9;
}
.rank-card :deep(.el-card__header) {
  border-bottom: 1px solid rgba(60, 40, 20, 0.08);
  padding: 16px 20px;
  background: rgba(245,240,232,0.5);
}
.card-header span {
  font-weight: 700;
  font-size: 16px;
  color: #2e2520;
}
.rank-list {
  overflow-y: auto;
  flex: 1;
  padding: 8px 16px;
}
.rank-list::-webkit-scrollbar {
  width: 6px;
}
.rank-list::-webkit-scrollbar-thumb {
  background: rgba(0,0,0,0.1);
  border-radius: 3px;
}
.rank-item {
  display: flex;
  align-items: center;
  padding: 12px 8px;
  border-bottom: 1px solid rgba(60, 40, 20, 0.04);
  cursor: pointer;
  transition: all 0.2s ease;
  border-radius: 8px;
}
.rank-item:hover {
  background: #ebe3d5;
  transform: translateX(4px);
}
.rank-item:last-child {
  border-bottom: none;
}
.rank-num {
  width: 24px;
  height: 24px;
  line-height: 24px;
  text-align: center;
  background: #e0d8c8;
  border-radius: 6px;
  font-size: 12px;
  margin-right: 12px;
  color: #9b8e82;
  flex-shrink: 0;
  font-weight: 700;
}
.rank-num.top-three {
  background: linear-gradient(135deg, #f59e0b, #d97706);
  color: #fff;
  box-shadow: 0 2px 6px rgba(245, 158, 11, 0.3);
}
.rank-name {
  flex: 1;
  width: 0;
  font-size: 14px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-right: 8px;
  color: #2e2520;
}
.rank-hot {
  font-size: 14px;
}

/* === Section Titles === */
.section-title {
  font-size: 22px;
  font-weight: 800;
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  gap: 10px;
  color: #2e2520;
  letter-spacing: 0.5px;
}
.section-title i {
  color: #8b6f52;
}

/* === Recommend Section === */
.recommend-section {
  margin-bottom: 48px;
}
.book-card-simple {
  cursor: pointer;
  background: #fffdf9;
  border-radius: 12px;
  overflow: hidden;
  transition: all 0.4s cubic-bezier(0.25, 0.8, 0.25, 1);
  border: 1px solid rgba(0,0,0,0.04);
  box-shadow: 0 4px 12px rgba(60, 40, 20, 0.03);
}
.book-card-simple:hover {
  transform: translateY(-8px);
  box-shadow: 0 20px 40px rgba(0,0,0,0.1);
}
.cover-wrapper {
  position: relative;
  height: 220px;
  background-color: #ebe3d5;
  overflow: hidden;
}
.simple-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.6s cubic-bezier(0.25, 0.8, 0.25, 1);
}
.book-card-simple:hover .simple-cover {
  transform: scale(1.06);
}
.hover-mask {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background: linear-gradient(to top, rgba(139, 111, 82, 0.9), rgba(60, 40, 20, 0.2));
  color: #fffdf9;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.4s ease;
  font-weight: 700;
  font-size: 15px;
  letter-spacing: 1px;
}
.book-card-simple:hover .hover-mask {
  opacity: 1;
}
.simple-info {
  padding: 14px 16px;
  text-align: left;
}
.simple-name {
  font-weight: 700;
  font-size: 15px;
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #2e2520;
}
.simple-author {
  color: #9b8e82;
  font-size: 13px;
  font-weight: 500;
}

/* === Book Grid === */
.book-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 24px;
}
.book-card {
  cursor: pointer;
  background: #fffdf9;
  border-radius: 12px;
  overflow: hidden;
  transition: all 0.4s cubic-bezier(0.25, 0.8, 0.25, 1);
  border: 1px solid rgba(0,0,0,0.04);
  box-shadow: 0 4px 12px rgba(60, 40, 20, 0.03);
}
.book-card:hover {
  transform: translateY(-8px);
  box-shadow: 0 20px 40px rgba(0,0,0,0.1);
}
.book-cover {
  width: 100%;
  height: 240px;
  object-fit: cover;
  background-color: #ebe3d5;
  border-bottom: 1px solid rgba(60, 40, 20, 0.04);
}
.book-info {
  padding: 16px;
}
.book-title {
  font-weight: 700;
  margin-bottom: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: #2e2520;
  font-size: 15px;
}
.book-author {
  font-size: 13px;
  color: #9b8e82;
  margin-bottom: 8px;
  font-weight: 500;
}
.book-desc {
  font-size: 13px;
  color: #9b8e82;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.pagination-box {
  margin-top: 48px;
  display: flex;
  justify-content: center;
}
.match-tag {
  margin-top: 8px;
  font-size: 11px;
  color: #a38c75;
  background-color: #f7f4f0;
  padding: 2px 8px;
  border-radius: 4px;
  display: inline-block;
  border: 1px solid #ebdaca;
}
.score-text {
  font-family: monospace;
}

/* === Custom AutoComplete Styles === */
.custom-autocomplete {
  width: 100%;
}
::v-deep(.custom-autocomplete .el-input__wrapper) {
  border-radius: 20px;
  box-shadow: 0 4px 12px rgba(139, 111, 82, 0.08) !important;
  transition: all 0.3s ease;
  padding-left: 18px;
}
::v-deep(.custom-autocomplete .el-input__wrapper.is-focus) {
  box-shadow: 0 6px 16px rgba(139, 111, 82, 0.2) !important;
}
.suggest-item {
  display: flex;
  align-items: center;
  padding: 6px 0;
  gap: 12px;
}
.suggest-cover {
  width: 32px;
  height: 44px;
  object-fit: cover;
  border-radius: 4px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}
.suggest-info {
  display: flex;
  flex-direction: column;
  justify-content: center;
  overflow: hidden;
}
.suggest-title {
  font-size: 14px;
  color: #2e2520;
  font-weight: 600;
  line-height: 1.2;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.suggest-author {
  font-size: 12px;
  color: #a38c75;
}
.search-result-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.clear-search-btn {
  font-size: 14px;
  color: #8b6f52;
}

</style>