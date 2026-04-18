<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()
const defaultCover = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII='

const searchKeyword = ref('')
const currentCategory = ref('全部')
const categories = ['全部', '科幻', '文学', '历史', '技术', '悬疑']

const hotBooks = ref([])
const rankBooks = ref([])
const recommendBooks = ref([])
const recommendLoading = ref(false)
const tableData = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const isSearchMode = ref(false)
const userInfo = ref({})
const loading = ref(false)
const refreshing = ref(false)
const finished = ref(false)

const welcomeTitle = computed(() => {
  if (!userInfo.value.id) return '今天想读点什么？'
  return `欢迎回来，${userInfo.value.nickname || userInfo.value.username}`
})

onMounted(() => {
  const u = localStorage.getItem('user')
  if (u) userInfo.value = JSON.parse(u)
  loadHotBooks()
  loadRankBooks()
  loadRecommendBooks()
  loadBooks()
})

const loadHotBooks = async () => {
  try {
    const res = await axios.get('/api/sysBook/hot')
    hotBooks.value = res.data.data || []
  } catch (e) {
    console.error(e)
  }
}

const loadRankBooks = async () => {
  try {
    const res = await axios.get('/api/sysBook/rank')
    rankBooks.value = (res.data.data || []).slice(0, 8)
  } catch (e) {
    console.error(e)
  }
}

const loadRecommendBooks = async (refresh = false) => {
  recommendLoading.value = true
  try {
    const res = await axios.get('/api/sysBook/recommend', {
      params: { userId: userInfo.value.id, refresh }
    })
    if (res.data.code === '200') {
      recommendBooks.value = res.data.data || []
    }
  } catch (e) {
    console.error(e)
  } finally {
    recommendLoading.value = false
  }
}

const loadBooks = async () => {
  const isSearch = searchKeyword.value.trim().length > 0
  isSearchMode.value = isSearch
  const url = isSearch ? '/api/search' : '/api/sysBook/list'

  try {
    const res = await axios.get(url, {
      params: {
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        keyword: searchKeyword.value,
        category: currentCategory.value
      }
    })
    const records = res.data.data.records || []
    if (pageNum.value === 1) {
      tableData.value = records
    } else {
      tableData.value.push(...records)
    }
    total.value = res.data.data.total
    finished.value = tableData.value.length >= total.value
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

const onSearch = () => {
  pageNum.value = 1
  finished.value = false
  loadBooks()
}

const clearSearch = () => {
  searchKeyword.value = ''
  isSearchMode.value = false
  pageNum.value = 1
  finished.value = false
  loadBooks()
}

const onCategoryChange = (cat) => {
  currentCategory.value = cat
  pageNum.value = 1
  finished.value = false
  tableData.value = []
  loadBooks()
}

const onLoadMore = () => {
  pageNum.value++
  loadBooks()
}

const onRefresh = () => {
  pageNum.value = 1
  finished.value = false
  loadHotBooks()
  loadRankBooks()
  loadRecommendBooks()
  loadBooks()
}

const goToDetail = (id) => router.push(`/book/${id}`)
const goToLogin = () => router.push('/login')
const goToProfile = () => router.push('/profile')
const goToShelf = () => router.push(userInfo.value.id ? '/shelf' : '/login')
const goToFriends = () => router.push(userInfo.value.id ? '/friends' : '/login')
</script>

<template>
  <div class="home-page">
    <div class="home-header">
      <div class="header-copy">
        <div class="header-brand">智能阅读</div>
        <div class="header-subtitle">发现好书、延续阅读、和书友分享阅读瞬间</div>
      </div>
      <div class="header-right" v-if="!userInfo.id">
        <van-button size="small" round type="primary" class="login-btn" @click="goToLogin">登录</van-button>
      </div>
      <div class="header-right" v-else>
        <van-icon name="manager-o" size="24" color="#8b6f52" @click="goToProfile" />
      </div>
    </div>

    <div class="hero-card">
      <div class="hero-title">{{ welcomeTitle }}</div>
      <div class="hero-desc">从热门推荐、智能推荐和书库探索里，快速找到下一本适合你的书。</div>
      <div class="hero-actions">
        <div class="quick-action" @click="goToShelf">
          <van-icon name="star-o" size="18" />
          <span>我的书架</span>
        </div>
        <div class="quick-action" @click="goToFriends">
          <van-icon name="friends-o" size="18" />
          <span>好友中心</span>
        </div>
      </div>
      <div class="search-shell">
        <van-search
          v-model="searchKeyword"
          placeholder="搜索书籍 / 作者 / 简介..."
          shape="round"
          background="transparent"
          @search="onSearch"
          @clear="clearSearch"
        />
      </div>
    </div>

    <div class="category-scroll">
      <div class="category-list">
        <span
          v-for="cat in categories"
          :key="cat"
          :class="['cat-chip', currentCategory === cat ? 'active' : '']"
          @click="onCategoryChange(cat)"
        >
          {{ cat }}
        </span>
      </div>
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh" success-text="刷新成功">
      <div v-if="!isSearchMode && hotBooks.length > 0" class="section-block">
        <div class="m-section-title">
          <span>热门推荐</span>
          <span class="section-tip">正在被更多读者阅读</span>
        </div>
        <div class="carousel-section">
          <van-swipe :autoplay="4000" :loop="true" indicator-color="#8b6f52" class="hot-swipe">
            <van-swipe-item v-for="book in hotBooks" :key="book.id" @click="goToDetail(book.id)">
              <div class="swipe-card">
                <img :src="book.coverUrl || defaultCover" class="swipe-cover" alt="" />
                <div class="swipe-overlay">
                  <div class="swipe-title">{{ book.title }}</div>
                  <div class="swipe-meta">
                    <span>{{ book.author }}</span>
                    <span class="hot-badge">热度 {{ book.heat || 0 }}</span>
                  </div>
                </div>
              </div>
            </van-swipe-item>
          </van-swipe>
        </div>
      </div>

      <div v-if="!isSearchMode && rankBooks.length > 0" class="section-block">
        <div class="rank-section m-card">
          <div class="rank-header">
            <span>热门阅读榜</span>
            <span class="section-tip">看看大家最近都在读什么</span>
          </div>
          <div class="rank-list">
            <div
              v-for="(book, idx) in rankBooks"
              :key="book.id"
              class="rank-item"
              @click="goToDetail(book.id)"
            >
              <span :class="['rank-num', idx < 3 ? 'top' : '']">{{ idx + 1 }}</span>
              <span class="rank-name">{{ book.title }}</span>
              <span class="rank-fire">热</span>
            </div>
          </div>
        </div>
      </div>

      <div v-if="!isSearchMode" class="section-block">
        <div class="m-section-title">
          <span>猜你喜欢</span>
          <van-button size="mini" plain round @click="loadRecommendBooks(true)" :loading="recommendLoading" style="margin-left: auto;">
            换一批
          </van-button>
        </div>
        <div class="recommend-scroll" v-if="recommendBooks.length > 0">
          <div
            v-for="book in recommendBooks"
            :key="book.id"
            class="recommend-card"
            @click="goToDetail(book.id)"
          >
            <img :src="book.coverUrl || defaultCover" class="recommend-cover cover-aspect" alt="" />
            <div class="recommend-title">{{ book.title }}</div>
            <div class="recommend-author">{{ book.author }}</div>
          </div>
        </div>
        <van-empty v-else-if="!recommendLoading" description="暂无推荐" image="search" />
      </div>

      <div v-if="isSearchMode" class="search-result-header">
        <span>“{{ searchKeyword }}” 共 {{ total }} 条结果</span>
        <van-button size="mini" plain round @click="clearSearch">返回</van-button>
      </div>

      <div class="m-section-title" v-if="!isSearchMode">
        <span>探索书库</span>
        <span class="section-tip">按分类继续发现新书</span>
      </div>

      <van-list
        v-model:loading="loading"
        :finished="finished"
        finished-text="没有更多了"
        @load="onLoadMore"
        class="book-list"
      >
        <div class="book-grid">
          <div
            v-for="book in tableData"
            :key="book.id"
            class="book-grid-item"
            @click="goToDetail(book.id)"
          >
            <img :src="book.coverUrl || defaultCover" class="grid-cover cover-aspect" alt="" />
            <div class="grid-info">
              <div class="grid-title">{{ book.title }}</div>
              <div class="grid-author">{{ book.author }}</div>
              <van-tag v-if="isSearchMode && book.score" plain type="primary" size="mini">
                匹配 {{ Number(book.score).toFixed(1) }}
              </van-tag>
            </div>
          </div>
        </div>
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<style scoped>
.home-page {
  min-height: 100vh;
  padding-bottom: 60px;
  background:
    radial-gradient(circle at top, rgba(139, 111, 82, 0.12), transparent 34%),
    linear-gradient(180deg, #f7f2e8 0%, #fdfcf8 38%, #f5efe5 100%);
}

.home-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 18px 16px 8px;
}

.header-copy {
  flex: 1;
  min-width: 0;
}

.header-brand {
  font-size: 22px;
  font-weight: 800;
  color: var(--color-text);
  font-family: var(--font-serif);
}

.header-subtitle {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-muted);
}

.hero-card {
  margin: 8px 16px 12px;
  padding: 18px 16px 14px;
  border-radius: 18px;
  background: rgba(255, 253, 249, 0.88);
  border: 1px solid rgba(139, 111, 82, 0.08);
  box-shadow: 0 10px 28px rgba(60, 40, 20, 0.07);
  backdrop-filter: blur(12px);
}

.hero-title {
  font-size: 20px;
  font-weight: 800;
  line-height: 1.3;
  color: var(--color-text);
  font-family: var(--font-serif);
}

.hero-desc {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-text-secondary);
}

.hero-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 14px;
}

.quick-action {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px;
  border-radius: 14px;
  background: linear-gradient(180deg, #fffdf9 0%, #f5eee4 100%);
  border: 1px solid rgba(139, 111, 82, 0.08);
  color: #6b5440;
  font-size: 13px;
  font-weight: 700;
}

.search-shell {
  margin-top: 14px;
  border-radius: 18px;
  background: #fff;
  box-shadow: inset 0 0 0 1px rgba(139, 111, 82, 0.08);
}

.category-scroll {
  overflow-x: auto;
  padding: 2px 16px 12px;
  -webkit-overflow-scrolling: touch;
}

.category-list {
  display: flex;
  gap: 8px;
  white-space: nowrap;
}

.cat-chip {
  display: inline-block;
  padding: 7px 18px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
  background: rgba(255, 253, 249, 0.92);
  border: 1px solid rgba(139, 111, 82, 0.08);
  flex-shrink: 0;
  transition: all 0.25s;
}

.cat-chip.active {
  background: linear-gradient(135deg, #8b6f52, #a68968);
  color: #fff;
  box-shadow: 0 6px 16px rgba(139, 111, 82, 0.22);
}

.section-block {
  margin-bottom: 12px;
}

.section-tip {
  margin-left: 8px;
  font-size: 11px;
  font-weight: 500;
  color: var(--color-text-muted);
}

.carousel-section {
  padding: 0 16px;
}

.hot-swipe {
  border-radius: 18px;
  overflow: hidden;
  box-shadow: 0 12px 26px rgba(60, 40, 20, 0.12);
}

.swipe-card {
  position: relative;
  height: 210px;
}

.swipe-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.swipe-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: 18px 16px;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.78) 0%, rgba(0, 0, 0, 0.16) 58%, transparent 100%);
  color: #fff;
}

.swipe-title {
  font-size: 20px;
  font-weight: 800;
  line-height: 1.3;
  font-family: var(--font-serif), serif;
}

.swipe-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
  font-size: 12px;
}

.hot-badge {
  padding: 3px 8px;
  border-radius: 999px;
  background: linear-gradient(135deg, #ff7a18, #ff3d54);
  font-weight: 700;
}

.rank-section {
  margin: 0 16px;
  padding: 16px;
}

.rank-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
  font-size: 16px;
  font-weight: 800;
  color: var(--color-text);
}

.rank-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.rank-item {
  display: flex;
  align-items: center;
  padding: 10px 4px;
  border-radius: 10px;
}

.rank-num {
  width: 24px;
  height: 24px;
  line-height: 24px;
  text-align: center;
  border-radius: 7px;
  font-size: 12px;
  font-weight: 800;
  margin-right: 10px;
  background: #e8dfd2;
  color: #8c7a68;
}

.rank-num.top {
  background: linear-gradient(135deg, #ffb347, #ff7a18);
  color: #fff;
}

.rank-name {
  flex: 1;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-fire {
  font-size: 11px;
  color: #ff7a18;
  font-weight: 800;
}

.recommend-scroll {
  display: flex;
  overflow-x: auto;
  gap: 12px;
  padding: 0 16px 10px;
  -webkit-overflow-scrolling: touch;
}

.recommend-card {
  flex-shrink: 0;
  width: 118px;
}

.recommend-cover {
  width: 118px;
  height: 160px;
  border-radius: 12px;
  box-shadow: 0 8px 18px rgba(60, 40, 20, 0.12);
}

.recommend-title {
  margin-top: 8px;
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recommend-author {
  margin-top: 3px;
  font-size: 11px;
  color: var(--color-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px 6px;
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text);
}

.book-list {
  padding: 0 16px;
}

.book-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.book-grid-item {
  padding: 8px;
  border-radius: 14px;
  background: rgba(255, 253, 249, 0.92);
  box-shadow: 0 6px 18px rgba(60, 40, 20, 0.06);
}

.grid-cover {
  width: 100%;
  border-radius: 10px;
  box-shadow: 0 4px 12px rgba(60, 40, 20, 0.08);
}

.grid-info {
  padding: 8px 2px 2px;
}

.grid-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.grid-author {
  margin-top: 3px;
  font-size: 11px;
  color: var(--color-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
