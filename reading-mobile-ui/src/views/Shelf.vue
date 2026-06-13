<script setup>
import request from '../utils/request'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'

import { useAuthStore } from '../stores/auth'
import { formatDatePart } from '../utils/dateTime'

const router = useRouter()
const authStore = useAuthStore()

const userInfo = computed(() => authStore.user || {})
const shelfList = ref([])
const shelfVisible = ref(1)
const booklists = ref([])
const loading = ref(false)
const showCreateSheet = ref(false)
const showBooklistPopup = ref(false)
const showDetailPopup = ref(false)
const detailBooklist = ref(null)
const newBooklist = ref({ name: '', description: '' })

const finishedCount = computed(() => shelfList.value.filter((item) => calcPercent(item) >= 100).length)

onMounted(() => {
  if (!authStore.isLoggedIn) {
    showToast('请先登录')
    router.push('/login')
    return
  }
  shelfVisible.value = userInfo.value.shelfVisible ?? 1
  loadShelf()
  loadBooklists()
})

const loadShelf = async () => {
  loading.value = true
  try {
    const res = await request.get(`/api/bookshelf/list/${userInfo.value.id}`)
    if (res.data.code === '200') {
      shelfList.value = res.data.data || []
    }
  } finally {
    loading.value = false
  }
}

const loadBooklists = async () => {
  const res = await request.get(`/api/booklist/list/${userInfo.value.id}`)
  if (res.data.code === '200') {
    booklists.value = res.data.data || []
  }
}

const continueRead = (bookId) => {
  router.push(`/read/${bookId}`)
}

const removeFromShelf = async (id) => {
  await showConfirmDialog({ message: '确定将这本书移出书架吗？' })
  await request.delete(`/api/bookshelf/remove/${id}`)
  showSuccessToast('已移出书架')
  loadShelf()
}

const calcPercent = (item) => {
  const total = item.totalChapters || 0
  const current = item.currentChapterIndex || 0
  if (!total) return 0
  return Math.min(100, Math.round(((current + 1) / total) * 100))
}

const formatTime = (time) => formatDatePart(time, '刚刚更新')

const formatProgress = (item) => {
  const chapterIndex = Number(item.currentChapterIndex || 0) + 1
  const total = Number(item.totalChapters || 0)
  if (!total) return '尚未记录阅读进度'
  return `读到第 ${chapterIndex} 章，共 ${total} 章`
}

const toggleVisibility = async (value) => {
  await request.post('/api/sysUser/update', {
    id: userInfo.value.id,
    shelfVisible: value
  })
  userInfo.value.shelfVisible = value
  localStorage.setItem('user', JSON.stringify(userInfo.value))
  showSuccessToast(value === 1 ? '书架已设为公开' : '书架已设为私密')
}

const createBooklist = async () => {
  if (!newBooklist.value.name.trim()) return
  const res = await request.post('/api/booklist/create', {
    userId: userInfo.value.id,
    name: newBooklist.value.name.trim(),
    description: newBooklist.value.description.trim()
  })
  if (res.data.code === '200') {
    showSuccessToast('书单创建成功')
    showCreateSheet.value = false
    newBooklist.value = { name: '', description: '' }
    loadBooklists()
  }
}

const deleteBooklist = async (id) => {
  await showConfirmDialog({ message: '确定删除这个书单吗？' })
  await request.delete(`/api/booklist/delete/${id}`)
  showSuccessToast('书单已删除')
  loadBooklists()
}

const viewDetail = async (id) => {
  const res = await request.get(`/api/booklist/detail/${id}`)
  if (res.data.code === '200') {
    detailBooklist.value = res.data.data
    showDetailPopup.value = true
  }
}

const removeBookFromList = async (booklistId, bookId) => {
  await request.delete(`/api/booklist/removeBook?booklistId=${booklistId}&bookId=${bookId}`)
  showSuccessToast('已从书单移除')
  viewDetail(booklistId)
  loadBooklists()
}

const copyShareLink = (booklist) => {
  const link = `${window.location.origin}/shelf/import/${booklist.shareCode}`
  navigator.clipboard?.writeText(link)
    .then(() => showSuccessToast('分享链接已复制'))
    .catch(() => showToast(link))
}
</script>

<template>
  <div class="shelf-page">
    <section class="hero-card glass-panel fade-in-up" style="animation-delay: 0.1s">
      <div class="hero-copy">
        <div class="hero-eyebrow">我的阅读空间</div>
        <h1 class="hero-title">书架与书单</h1>
        <p class="hero-desc">把正在阅读、收藏已久、想分享给朋友的书都整理在这里。</p>
      </div>
      <div class="hero-side">
        <div class="hero-switch">
          <span class="switch-label">书架可见性</span>
          <van-switch
            v-model="shelfVisible"
            :active-value="1"
            :inactive-value="0"
            size="22"
            @change="toggleVisibility"
          />
        </div>
        <div class="switch-status">{{ shelfVisible === 1 ? '当前为公开书架' : '当前为私密书架' }}</div>
      </div>
    </section>

    <section class="stats-row fade-in-up" style="animation-delay: 0.2s">
      <div class="stat-card glass-panel">
        <div class="stat-value">{{ shelfList.length }}</div>
        <div class="stat-label">书架藏书</div>
      </div>
      <div class="stat-card glass-panel">
        <div class="stat-value">{{ finishedCount }}</div>
        <div class="stat-label">已读完成</div>
      </div>
      <div class="stat-card glass-panel">
        <div class="stat-value">{{ booklists.length }}</div>
        <div class="stat-label">我的书单</div>
      </div>
    </section>

    <section class="tool-row fade-in-up" style="animation-delay: 0.3s">
      <van-button round plain icon="bars" class="tool-btn" @click="showBooklistPopup = true">查看书单</van-button>
      <van-button round plain icon="plus" class="tool-btn" @click="showCreateSheet = true">新建书单</van-button>
    </section>

    <van-empty v-if="shelfList.length === 0 && !loading" description="书架还是空的，去首页挑一本到心仪的书吧" image="search">
      <van-button type="primary" round size="small" @click="$router.push('/')">去首页看看</van-button>
    </van-empty>

    <section v-else class="shelf-grid fade-in-up" style="animation-delay: 0.4s">
      <template v-if="loading">
        <div v-for="i in 4" :key="i" class="shelf-card glass-panel" style="padding: 12px">
          <van-skeleton title :row="3" />
        </div>
      </template>
      <template v-else>
        <article
          v-for="item in shelfList"
          :key="item.id"
          class="shelf-card glass-panel hover-float"
          @click="continueRead(item.bookId)"
        >
        <div class="cover-box">
          <img :src="item.coverUrl || 'https://via.placeholder.com/120x160'" class="card-cover" alt="" />
          <div class="cover-badge">{{ calcPercent(item) >= 100 ? '已读完' : `${calcPercent(item)}%` }}</div>
        </div>
        <div class="card-body">
          <div class="card-title">{{ item.bookName }}</div>
          <div class="card-author">{{ item.author || '作者未知' }}</div>
          <div class="card-progress">{{ formatProgress(item) }}</div>
          <van-progress
            :percentage="calcPercent(item)"
            :stroke-width="5"
            color="linear-gradient(90deg, #a36b46, #c29b7a)"
            track-color="rgba(163, 107, 70, 0.12)"
            :show-pivot="false"
          />
          <div class="card-meta">
            <span class="card-time">{{ formatTime(item.lastReadTime) }}</span>
            <van-icon name="delete-o" size="18" color="var(--danger-color)" @click.stop="removeFromShelf(item.id)" />
          </div>
        </div>
        </article>
      </template>
    </section>

    <van-popup v-model:show="showCreateSheet" position="bottom" round>
      <div class="sheet-panel">
        <div class="panel-title">创建书单</div>
        <div class="panel-desc">给这组书起个名字，方便以后整理和分享。</div>
        <van-field v-model="newBooklist.name" label="名称" placeholder="例如：春日书单" maxlength="30" />
        <van-field
          v-model="newBooklist.description"
          label="简介"
          type="textarea"
          rows="3"
          placeholder="补充一点书单说明，可不填"
          maxlength="200"
        />
        <van-button
          type="primary"
          block
          round
          class="panel-submit"
          :disabled="!newBooklist.name.trim()"
          @click="createBooklist"
        >
          创建书单
        </van-button>
      </div>
    </van-popup>

    <van-popup v-model:show="showBooklistPopup" position="right" :style="{ width: '84%', height: '100%' }">
      <div class="drawer-panel">
        <div class="drawer-header">
          <div>
            <div class="panel-title">我的书单</div>
            <div class="panel-desc">整理主题阅读、朋友分享和长期收藏。</div>
          </div>
          <van-icon name="cross" size="20" @click="showBooklistPopup = false" />
        </div>
        <van-empty v-if="booklists.length === 0" description="还没有创建书单" />
        <div v-else class="booklist-list">
          <article v-for="booklist in booklists" :key="booklist.id" class="booklist-card">
            <div class="booklist-main" @click="viewDetail(booklist.id)">
              <div class="booklist-name">{{ booklist.name }}</div>
              <div v-if="booklist.description" class="booklist-desc">{{ booklist.description }}</div>
            </div>
            <div class="booklist-actions">
              <van-button size="mini" plain round @click="viewDetail(booklist.id)">查看</van-button>
              <van-button size="mini" plain round @click="copyShareLink(booklist)">分享</van-button>
              <van-button size="mini" plain round type="danger" @click="deleteBooklist(booklist.id)">删除</van-button>
            </div>
          </article>
        </div>
      </div>
    </van-popup>

    <van-popup v-model:show="showDetailPopup" position="bottom" round :style="{ maxHeight: '72%' }">
      <div v-if="detailBooklist" class="sheet-panel">
        <div class="panel-title">{{ detailBooklist.name }}</div>
        <div v-if="detailBooklist.description" class="panel-desc">{{ detailBooklist.description }}</div>
        <van-empty v-if="!detailBooklist.books?.length" description="这个书单里还没有书" />
        <div v-else class="detail-list">
          <div v-for="book in detailBooklist.books" :key="book.id" class="detail-row">
            <img :src="book.coverUrl || 'https://via.placeholder.com/40x55'" class="detail-cover" alt="" />
            <div class="detail-info">
              <div class="detail-title">{{ book.title }}</div>
              <div class="detail-author">{{ book.author || '作者未知' }}</div>
            </div>
            <van-icon
              name="delete-o"
              color="#d35645"
              @click="removeBookFromList(detailBooklist.id, book.id)"
            />
          </div>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.shelf-page {
  min-height: 100vh;
  padding: 18px 16px calc(88px + var(--safe-bottom));
  background: var(--bg-color);
}

.hero-card {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 18px;
}

.hero-copy {
  flex: 1;
}

.hero-eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.16em;
  color: #a17752;
  text-transform: uppercase;
}

.hero-title {
  margin: 8px 0 6px;
  font-family: var(--font-serif), serif;
  font-size: 26px;
  line-height: 1.2;
  color: var(--text-primary);
}

.hero-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.75;
  color: var(--text-secondary);
}

.hero-side {
  min-width: 108px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  align-items: flex-end;
}

.hero-switch {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.switch-label {
  font-size: 11px;
  color: #8a725d;
}

.switch-status {
  font-size: 12px;
  color: var(--text-secondary);
  text-align: right;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-top: 14px;
}

.stat-card {
  padding: 14px 12px;
  text-align: center;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
}

.stat-label {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}

.tool-row {
  display: flex;
  gap: 10px;
  margin: 16px 0 18px;
}

.tool-btn {
  flex: 1;
  border-color: var(--color-border);
  background: var(--color-bg-card);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  color: var(--color-primary);
}

.shelf-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.shelf-card {
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.cover-box {
  position: relative;
}

.card-cover {
  width: 100%;
  height: 188px;
  object-fit: cover;
  display: block;
}

.cover-badge {
  position: absolute;
  top: 10px;
  right: 10px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(61, 44, 31, 0.74);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
}

.card-body {
  padding: 12px 12px 14px;
}

.card-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-author,
.card-progress,
.card-time {
  font-size: 12px;
  color: var(--text-secondary);
}

.card-author {
  margin-top: 4px;
}

.card-progress {
  margin: 8px 0 10px;
  line-height: 1.5;
}

.card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
}

.sheet-panel,
.drawer-panel {
  padding: 22px 18px 28px;
}

.drawer-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.panel-title {
  font-family: var(--font-serif), serif;
  font-size: 22px;
  color: #3d2c1f;
}

.panel-desc {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.7;
  color: #85705d;
}

.panel-submit {
  margin-top: 16px;
}

.booklist-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.booklist-card {
  padding: 14px;
  border-radius: 16px;
  background: var(--color-bg-card);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid var(--color-border);
  box-shadow: 0 10px 24px rgba(93, 67, 43, 0.04);
}

.booklist-main {
  cursor: pointer;
}

.booklist-name {
  font-size: 15px;
  font-weight: 700;
  color: #3d2c1f;
}

.booklist-desc {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.7;
  color: #7d6754;
}

.booklist-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.detail-list {
  margin-top: 14px;
}

.detail-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid rgba(143, 117, 87, 0.12);
}

.detail-row:last-child {
  border-bottom: none;
}

.detail-cover {
  width: 42px;
  height: 58px;
  object-fit: cover;
  border-radius: 8px;
  flex-shrink: 0;
}

.detail-info {
  flex: 1;
  min-width: 0;
}

.detail-title {
  font-size: 14px;
  font-weight: 700;
  color: #3d2c1f;
}

.detail-author {
  margin-top: 4px;
  font-size: 12px;
  color: #85705d;
}
</style>
