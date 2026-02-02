<template>
  <div class="home-container">
    <div class="nav-header">
      <div class="logo">📚 智慧阅读</div>
      <div class="nav-right">
        <el-button link class="nav-item" @click="goToShelf">
          <el-icon><Collection /></el-icon> 我的书架
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
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
// === 修改点 2: 引入 Refresh 图标 ===
import { Search, StarFilled, Reading, Collection, CaretBottom, Refresh } from '@element-plus/icons-vue'
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

onMounted(() => {
  const userStr = localStorage.getItem('user')
  if (userStr) {
    userInfo.value = JSON.parse(userStr)
  }
  loadHotBooks()
  loadRankBooks()
  loadRecommendBooks() // 页面加载时触发一次推荐
  loadBooks()
})

const goToShelf = () => {
  if (!userInfo.value.id) return ElMessage.warning('请先登录')
  router.push('/shelf')
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
    ElMessage.success('已退出登录')
    // 登出后刷新一下推荐（变回随机推荐）
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
  padding: 20px;
}

/* 顶部搜索栏 */
.header-section {
  text-align: center;
  margin-bottom: 30px;
}
.search-box {
  width: 600px;
  margin: 0 auto 20px;
}
.category-tags {
  display: flex;
  justify-content: center;
  gap: 20px;
}
.tag-item {
  cursor: pointer;
  padding: 6px 16px;
  border-radius: 20px;
  color: #606266;
  transition: all 0.3s;
}
.tag-item:hover, .tag-item.active {
  background-color: #409EFF;
  color: white;
  font-weight: bold;
}

/* 轮播图 */
.core-section {
  margin-bottom: 40px;
}
.promo-carousel {
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}
.carousel-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  background-color: #f0f0f0; /* 图片加载前的背景色 */
}
.carousel-info {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: linear-gradient(to top, rgba(0,0,0,0.85) 0%, rgba(0,0,0,0.4) 100%); /* 渐变背景更清晰 */
  color: white;
  padding: 20px 30px;
  text-align: left;
}
.carousel-info h3 {
  margin: 0 0 8px 0;
  font-size: 24px;
  font-weight: bold;
}
.author-text {
  font-size: 14px;
  margin-bottom: 8px;
  opacity: 0.9;
}

/* 热度标签样式 */
.heat-tag {
  display: inline-block;
  background-color: #ff4d4f; /* 醒目的红色 */
  color: white;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: bold;
  margin-bottom: 10px;
}

.desc-text {
  font-size: 13px;
  opacity: 0.8;
  line-height: 1.5;
  margin: 0;
}
/* 排行榜 - 核心修复区 */
.rank-card {
  height: 360px;
  display: flex;
  flex-direction: column;
}
/* 必须给容器一个 overflow 才能让内部滚动生效 */
.rank-list {
  overflow-y: auto;
  flex: 1;
  padding: 0 10px;
}
.rank-item {
  display: flex;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px dashed #eee;
  cursor: pointer;
  width: 100%; /* 强制宽度 */
}
.rank-item:last-child {
  border-bottom: none;
}
.rank-num {
  width: 20px;
  height: 20px;
  line-height: 20px;
  text-align: center;
  background: #eee;
  border-radius: 4px;
  font-size: 12px;
  margin-right: 10px;
  color: #666;
  flex-shrink: 0;
}
.rank-num.top-three {
  background: #ff9900;
  color: white;
  font-weight: bold;
}

/* 修复2: 强制书名截断 */
.rank-name {
  flex: 1;
  width: 0; /* 这是 Flex 布局中实现截断的魔法属性 */
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-right: 5px;
  color: black;
}

.rank-hot {
  font-size: 12px;
  flex-shrink: 0;
}

/* 通用标题 */
.section-title {
  font-size: 20px;
  font-weight: bold;
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  gap: 8px;
  border-left: 4px solid #409EFF;
  padding-left: 12px;
}

/* 推荐专栏 */
.recommend-section {
  margin-bottom: 40px;
}
.book-card-simple {
  cursor: pointer;
  background: white;
  border-radius: 8px;
  overflow: hidden;
  transition: transform 0.3s;
}
.book-card-simple:hover {
  transform: translateY(-5px);
}
.cover-wrapper {
  position: relative;
  height: 200px;
  background-color: #f5f7fa;
}
.simple-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.hover-mask {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.5);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s;
}
.book-card-simple:hover .hover-mask {
  opacity: 1;
}
.simple-info {
  padding: 10px;
  text-align: center;
}
.simple-name {
  font-weight: bold;
  font-size: 14px;
  margin-bottom: 5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.simple-author {
  color: #999;
  font-size: 12px;
}

/* 书库网格 */
.book-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 20px;
}
.book-card {
  cursor: pointer;
  transition: all 0.3s;
}
.book-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 8px 16px rgba(0,0,0,0.15);
}
.book-cover {
  width: 100%;
  height: 220px;
  object-fit: cover;
  background-color: #f5f7fa;
}
.book-info {
  padding: 12px;
}
.book-title {
  font-weight: bold;
  margin-bottom: 5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.book-author {
  font-size: 12px;
  color: #666;
  margin-bottom: 5px;
}
.book-desc {
  font-size: 12px;
  color: #999;
}
.pagination-box {
  margin-top: 30px;
  display: flex;
  justify-content: center;
}
.nav-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 60px;
  border-bottom: 1px solid #eee;
  margin-bottom: 20px;
  padding: 0 10px;
}
.logo {
  font-size: 20px;
  font-weight: bold;
  color: #409EFF;
}
.nav-right {
  display: flex;
  align-items: center;
  gap: 20px;
}
.nav-item {
  font-size: 16px;
  color: #606266;
}
.user-avatar-box {
  display: flex;
  align-items: center;
  cursor: pointer;
  gap: 8px;
}
.username {
  font-size: 14px;
  color: #333;
}
</style>