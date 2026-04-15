<script setup>
import { ref, onMounted } from 'vue'
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
  } catch (e) { console.error(e) }
}

const loadRankBooks = async () => {
  try {
    const res = await axios.get('/api/sysBook/rank')
    rankBooks.value = (res.data.data || []).slice(0, 8)
  } catch (e) { console.error(e) }
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
  } catch (e) { console.error(e) }
  finally { recommendLoading.value = false }
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
  } catch (e) { console.error(e) }
  finally {
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
</script>

<template>
  <div class="home-page">
    <!-- Header -->
    <div class="home-header">
      <div class="header-brand">📚 智慧阅读</div>
      <div class="header-right" v-if="!userInfo.id" @click="goToLogin">
        <van-button size="small" round type="primary" class="login-btn">登录</van-button>
      </div>
      <div class="header-right" v-else>
        <van-icon name="user-circle-o" size="24" color="#8b6f52" @click="$router.push('/profile')" />
      </div>
    </div>

    <!-- Search -->
    <div class="search-section">
      <van-search
        v-model="searchKeyword"
        placeholder="搜索书籍 / 作者 / 简介..."
        shape="round"
        background="transparent"
        @search="onSearch"
        @clear="clearSearch"
      />
    </div>

    <!-- Category Tabs -->
    <div class="category-scroll">
      <div class="category-list">
        <span
          v-for="cat in categories"
          :key="cat"
          :class="['cat-chip', currentCategory === cat ? 'active' : '']"
          @click="onCategoryChange(cat)"
        >{{ cat }}</span>
      </div>
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh" success-text="刷新成功">
      <!-- Hot Carousel -->
      <div v-if="!isSearchMode && hotBooks.length > 0" class="carousel-section">
        <van-swipe :autoplay="4000" :loop="true" indicator-color="#8b6f52" class="hot-swipe">
          <van-swipe-item v-for="book in hotBooks" :key="book.id" @click="goToDetail(book.id)">
            <div class="swipe-card">
              <img :src="book.coverUrl || defaultCover" class="swipe-cover" @error="(e) => e.target.src = defaultCover"  alt=""/>
              <div class="swipe-overlay">
                <div class="swipe-title">{{ book.title }}</div>
                <div class="swipe-meta">
                  <span>{{ book.author }}</span>
                  <span class="hot-badge">🔥 {{ book.heat || 0 }} 人在读</span>
                </div>
              </div>
            </div>
          </van-swipe-item>
        </van-swipe>
      </div>

      <!-- Rank Section -->
      <div v-if="!isSearchMode && rankBooks.length > 0" class="rank-section m-card" style="margin: 0 16px 12px;">
        <div class="rank-header">
          <span>🔥 热门阅读榜</span>
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
            <span class="rank-fire">🔥</span>
          </div>
        </div>
      </div>

      <!-- Recommend Section -->
      <div v-if="!isSearchMode" class="recommend-section">
        <div class="m-section-title">
          <span>✨ 猜你喜欢</span>
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
            <img :src="book.coverUrl || defaultCover" class="recommend-cover cover-aspect" @error="(e) => e.target.src = defaultCover"  alt=""/>
            <div class="recommend-title">{{ book.title }}</div>
            <div class="recommend-author">{{ book.author }}</div>
          </div>
        </div>
        <van-empty v-else-if="!recommendLoading" description="暂无推荐" image="search" />
      </div>

      <!-- Search Result Title -->
      <div v-if="isSearchMode" class="search-result-header">
        <span>🔍 "{{ searchKeyword }}" 共 {{ total }} 条结果</span>
        <van-button size="mini" plain round @click="clearSearch">返回</van-button>
      </div>

      <!-- Book Grid / Explore -->
      <div class="m-section-title" v-if="!isSearchMode">📖 探索书库</div>

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
            <img :src="book.coverUrl || defaultCover" class="grid-cover cover-aspect" @error="(e) => e.target.src = defaultCover"  alt=""/>
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
  padding-bottom: 60px;
}

/* Header */
.home-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px 0;
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(245, 240, 232, 0.92);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}
.header-brand {
  font-size: 20px;
  font-weight: 800;
  color: var(--color-text);
  font-family: var(--font-serif);
}
.login-btn {
  font-size: 13px;
}

/* Search */
.search-section {
  padding: 8px 4px 0;
}

/* Categories */
.category-scroll {
  overflow-x: auto;
  padding: 8px 16px 12px;
  -webkit-overflow-scrolling: touch;
}
.category-list {
  display: flex;
  gap: 8px;
  white-space: nowrap;
}
.cat-chip {
  display: inline-block;
  padding: 6px 18px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  transition: all 0.25s;
  flex-shrink: 0;
}

/* Carousel */
.carousel-section {
  padding: 0 16px 12px;
}
.hot-swipe {
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 6px 20px rgba(60, 40, 20, 0.1);
}
.swipe-card {
  position: relative;
  height: 200px;
}
.swipe-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.swipe-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: linear-gradient(to top, rgba(0,0,0,0.75) 0%, transparent 100%);
  color: #fff;
  padding: 30px 16px 14px;
}
.swipe-title {
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 4px;
  font-family: var(--font-serif),serif;
}
.swipe-meta {
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  opacity: 0.9;
}
.hot-badge {
  background: linear-gradient(135deg, #ff3b30, #ff9500);
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 600;
}

/* Rank */
.rank-header {
  font-size: 16px;
  font-weight: 700;
  margin-bottom: 10px;
  color: var(--color-text);
}
.rank-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.rank-item {
  display: flex;
  align-items: center;
  padding: 8px 4px;
  border-radius: 6px;
  transition: background 0.2s;
}
.rank-item:active {
  background: var(--color-bg-warm);
}
.rank-num {
  width: 22px;
  height: 22px;
  line-height: 22px;
  text-align: center;
  border-radius: 5px;
  font-size: 12px;
  font-weight: 700;
  margin-right: 10px;
  background: var(--color-border-light);
  color: var(--color-text-muted);
  flex-shrink: 0;
}

.rank-name {
  flex: 1;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rank-fire { font-size: 14px; }

/* Recommend */
.recommend-scroll {
  display: flex;
  overflow-x: auto;
  gap: 12px;
  padding: 0 16px 16px;
  -webkit-overflow-scrolling: touch;
  scroll-snap-type: x mandatory;
}
.recommend-card {
  flex-shrink: 0;
  width: 110px;
  scroll-snap-align: start;
}
.recommend-cover {
  width: 110px;
  height: 150px;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(60, 40, 20, 0.1);
}
.recommend-title {
  margin-top: 6px;
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.recommend-author {
  font-size: 11px;
  color: var(--color-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Search result header */
.search-result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px 6px;
  font-size: 14px;
  font-weight: 600;
}

/* Book Grid */
.book-list {
  padding: 0 16px;
}
.book-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.book-grid-item {
  overflow: hidden;
}
.grid-cover {
  width: 100%;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(60, 40, 20, 0.08);
}
.grid-info {
  padding: 6px 2px;
}
.grid-title {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.grid-author {
  font-size: 11px;
  color: var(--color-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
