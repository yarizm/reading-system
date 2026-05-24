<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../utils/request'
import { showToast, showSuccessToast, showFailToast, showConfirmDialog } from 'vant'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const currentUser = authStore.user

const books = ref([])
const reviewRequests = ref([])
const activeTab = ref('all')
const showEditPopup = ref(false)
const isUpload = ref(false)
const loading = ref(false)
const refreshing = ref(false)
const showCategoryPicker = ref(false)

const categories = [
  { text: '科幻', value: '科幻' },
  { text: '文学', value: '文学' },
  { text: '历史', value: '历史' },
  { text: '技术', value: '技术' },
  { text: '悬疑', value: '悬疑' }
]

const editForm = ref({
  id: null,
  title: '',
  author: '',
  category: '',
  description: '',
  coverUrl: '',
  filePath: '',
  status: null
})

const goBack = () => router.back()

const filteredBooks = computed(() => {
  if (activeTab.value === 'all') return books.value
  const status = parseInt(activeTab.value)
  return books.value.filter(b => b.status === status)
})

const statusText = (s) => {
  const map = { 0: '草稿', 1: '待审核', 2: '已上线', 3: '已驳回', 4: '已下架' }
  return map[s] || '未知'
}

const statusTagType = (s) => {
  const map = { 0: 'default', 1: 'warning', 2: 'success', 3: 'danger', 4: 'danger' }
  return map[s] || 'default'
}

const reqTypeText = (t) => ({ new: '新书公开', edit: '编辑审核', delist: '下架审核' }[t] || t)
const reqTypeTag = (t) => ({ new: 'primary', edit: 'warning', delist: 'danger' }[t] || 'default')
const reqStatusText = (s) => ({ 0: '待审核', 1: '已通过', 2: '已驳回' }[s] || '未知')
const reqStatusTag = (s) => ({ 0: 'warning', 1: 'success', 2: 'danger' }[s] || 'default')

const timelineType = (s) => ({ 0: 'warning', 1: 'success', 2: 'danger' }[s] || '')

const formatTime = (t) => {
  if (!t) return ''
  return new Date(t).toLocaleString()
}

const loadData = async () => {
  if (!currentUser) return
  loading.value = true
  try {
    const [booksRes, reviewRes] = await Promise.all([
      request.get(`/api/sysBook/myUploads/${currentUser.id}`),
      request.get(`/api/sysBook/reviewRequests/${currentUser.id}`)
    ])
    if (booksRes.data.code === '200') {
      books.value = booksRes.data.data
    }
    if (reviewRes.data.code === '200') {
      reviewRequests.value = reviewRes.data.data
    }
  } catch (e) {
    showFailToast('数据加载失败')
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

const onRefresh = () => {
  refreshing.value = true
  loadData()
}

const openUploadPopup = () => {
  isUpload.value = true
  editForm.value = {
    id: null,
    title: '',
    author: '',
    category: '',
    description: '',
    coverUrl: '',
    filePath: '',
    status: null
  }
  showEditPopup.value = true
}

const openEditPopup = (book) => {
  isUpload.value = false
  editForm.value = { ...book }
  showEditPopup.value = true
}

const onConfirmCategory = ({ selectedOptions }) => {
  editForm.value.category = selectedOptions[0]?.value || ''
  showCategoryPicker.value = false
}

const uploadCover = async (file) => {
  const formData = new FormData()
  formData.append('file', file.file)
  try {
    const res = await request.post('/api/sysBook/upload', formData)
    editForm.value.coverUrl = res.data.data
    showSuccessToast('封面上传成功')
  } catch (e) {
    showFailToast('封面上传失败')
  }
}

const uploadEbook = async (file) => {
  const formData = new FormData()
  formData.append('file', file.file)
  try {
    const res = await request.post('/api/sysBook/upload', formData)
    editForm.value.filePath = res.data.data
    showSuccessToast('电子书上传成功')
  } catch (e) {
    showFailToast('电子书上传失败')
  }
}

const saveBook = async () => {
  if (!editForm.value.title) return showToast('书名不能为空')
  try {
    if (isUpload.value) {
      const payload = { ...editForm.value, uploaderId: currentUser.id }
      await request.post('/api/sysBook/userUpload', payload)
      showSuccessToast('上传成功')
    } else if (editForm.value.status === 2) {
      await request.post('/api/sysBook/applyEdit', editForm.value)
      showSuccessToast('编辑审核已提交')
    } else {
      await request.put('/api/sysBook/userEdit', editForm.value)
      showSuccessToast('保存成功')
    }
    showEditPopup.value = false
    loadData()
  } catch (e) {
    showFailToast(e.response?.data?.msg || '操作失败')
  }
}

const submitForReview = async (bookId) => {
  try {
    await showConfirmDialog({
      title: '提交审核',
      message: '确定提交审核吗？审核通过后书籍将公开上线。'
    })
    const res = await request.post(`/api/sysBook/applyPublic/${bookId}`)
    if (res.data.code === '200') {
      showSuccessToast('已提交审核')
      loadData()
    } else {
      showFailToast(res.data.msg)
    }
  } catch (e) {
    if (e !== 'cancel') showFailToast(e.response?.data?.msg || '操作失败')
  }
}

const requestDelist = async (bookId) => {
  try {
    await showConfirmDialog({
      title: '申请下架',
      message: '确定申请下架吗？需要管理员审核通过后才会下架。'
    })
    const res = await request.post(`/api/sysBook/applyDelist/${bookId}`)
    if (res.data.code === '200') {
      showSuccessToast('下架审核已提交')
      loadData()
    } else {
      showFailToast(res.data.msg)
    }
  } catch (e) {
    if (e !== 'cancel') showFailToast(e.response?.data?.msg || '操作失败')
  }
}

onMounted(() => {
  if (!currentUser) {
    router.push('/login')
    return
  }
  loadData()
})
</script>

<template>
  <div class="my-books-page">
    <van-nav-bar
      title="我的书籍"
      fixed
      placeholder
      z-index="10"
      class="nav-bar"
    >
      <template #right>
        <van-icon name="plus" size="18" @click="openUploadPopup" class="nav-plus-icon" />
      </template>
    </van-nav-bar>

    <van-tabs v-model:active="activeTab" sticky offset-top="46px" animated swipeable color="#a36b46">
      <van-tab title="全部" name="all" />
      <van-tab title="草稿" name="0" />
      <van-tab title="待审核" name="1" />
      <van-tab title="已上线" name="2" />
      <van-tab title="已驳回" name="3" />
      <van-tab title="已下架" name="4" />
    </van-tabs>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh" class="content-refresh">
      <van-empty v-if="filteredBooks.length === 0 && !loading" description="暂无书籍" />

      <div class="book-list">
        <div v-for="book in filteredBooks" :key="book.id" class="book-card mobile-glass-panel">
          <div class="card-content">
            <div class="book-cover">
              <img v-if="book.coverUrl" :src="book.coverUrl" alt="" class="cover-aspect" />
              <div v-else class="no-cover cover-aspect">暂无封面</div>
            </div>
            <div class="book-info">
              <div class="book-title">{{ book.title }}</div>
              <div class="book-author">{{ book.author || '未知作者' }}</div>
              <div class="book-tags">
                <van-tag v-if="book.category" plain type="primary" color="#a36b46">{{ book.category }}</van-tag>
                <van-tag :type="statusTagType(book.status)">{{ statusText(book.status) }}</van-tag>
              </div>
            </div>
          </div>

          <div class="card-actions">
            <template v-if="book.status === 0 || book.status === 3 || book.status === 4">
              <van-button size="small" type="primary" plain round @click="openEditPopup(book)">编辑</van-button>
              <van-button size="small" type="primary" round @click="submitForReview(book.id)">提交审核</van-button>
            </template>
            <template v-else-if="book.status === 1">
              <van-button size="small" type="primary" plain round @click="openEditPopup(book)">修改提交信息</van-button>
            </template>
            <template v-else-if="book.status === 2">
              <van-button size="small" type="primary" plain round @click="openEditPopup(book)">编辑信息</van-button>
              <van-button size="small" type="warning" plain round @click="requestDelist(book.id)">申请下架</van-button>
            </template>
          </div>
        </div>
      </div>

      <!-- 审核记录时间线 -->
      <div class="review-history" v-if="reviewRequests.length > 0">
        <div class="section-title">审核记录</div>
        <div class="timeline">
          <div v-for="req in reviewRequests" :key="req.id" class="timeline-item">
            <div class="timeline-badge" :class="'badge-' + timelineType(req.status)"></div>
            <div class="timeline-card mobile-glass-panel">
              <div class="timeline-header">
                <span class="req-book">《{{ req.bookTitle || '未知书籍' }}》</span>
                <div class="timeline-tags">
                  <van-tag :type="reqTypeTag(req.requestType)">{{ reqTypeText(req.requestType) }}</van-tag>
                  <van-tag :type="reqStatusTag(req.status)">{{ reqStatusText(req.status) }}</van-tag>
                </div>
              </div>
              <div class="timeline-time">{{ formatTime(req.createTime) }}</div>
              <div v-if="req.status === 2 && req.rejectReason" class="reject-reason">
                驳回原因：{{ req.rejectReason }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </van-pull-refresh>

    <!-- 编辑/上传 弹出层 -->
    <van-popup
      v-model:show="showEditPopup"
      position="bottom"
      round
      class="edit-popup mobile-glass-panel"
      :style="{ height: '80%' }"
    >
      <div class="popup-header">
        <span class="popup-title">{{ isUpload ? '上传新书' : '编辑书籍' }}</span>
        <van-icon name="cross" size="20" @click="showEditPopup = false" class="close-icon" />
      </div>

      <div class="popup-content">
        <van-form @submit="saveBook">
          <van-field v-model="editForm.title" label="书名" placeholder="请输入书名" required />
          <van-field v-model="editForm.author" label="作者" placeholder="请输入作者" />
          <van-field
            v-model="editForm.category"
            is-link
            readonly
            label="分类"
            placeholder="选择分类"
            @click="showCategoryPicker = true"
          />
          <van-field
            v-model="editForm.description"
            rows="3"
            autosize
            label="简介"
            type="textarea"
            placeholder="请输入书籍简介"
          />

          <van-cell title="图书封面">
            <template #right-icon>
              <van-uploader :after-read="uploadCover" :max-count="1" :preview-image="false">
                <div class="popup-cover-uploader">
                  <img v-if="editForm.coverUrl" :src="editForm.coverUrl" alt="" />
                  <van-icon v-else name="plus" size="24" />
                </div>
              </van-uploader>
            </template>
          </van-cell>

          <van-cell title="电子书 (.txt)">
            <template #right-icon>
              <van-uploader :after-read="uploadEbook" accept=".txt" :max-count="1">
                <van-button size="small" type="primary" plain round>
                  {{ editForm.filePath ? '文件已上传 (点击替换)' : '上传 TXT' }}
                </van-button>
              </van-uploader>
            </template>
          </van-cell>

          <div class="popup-actions">
            <van-button type="primary" block round native-type="submit">
              {{ !isUpload && editForm.status === 2 ? '保存并提交编辑审核' : '保存' }}
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>

    <!-- 分类选择器 -->
    <van-popup v-model:show="showCategoryPicker" position="bottom" round>
      <van-picker
        :columns="categories"
        @confirm="onConfirmCategory"
        @cancel="showCategoryPicker = false"
      />
    </van-popup>
  </div>
</template>

<style scoped>
.my-books-page {
  min-height: 100vh;
  background:
    radial-gradient(circle at top right, rgba(214, 191, 165, 0.15), transparent 35%),
    linear-gradient(180deg, #f8fafc 0%, #f5eee4 100%);
  padding-bottom: 80px; /* 预留底部导航 TabBar 空间 */
}

.nav-bar {
  background: rgba(255, 255, 255, 0.85) !important;
  backdrop-filter: blur(10px);
}

.nav-plus-icon {
  color: #a36b46;
}

.content-refresh {
  min-height: calc(100vh - 90px);
}

.book-list {
  padding: 16px;
}

.book-card {
  margin-bottom: 16px;
  padding: 16px;
  display: flex;
  flex-direction: column;
}

.card-content {
  display: flex;
  gap: 16px;
}

.book-cover {
  width: 75px;
  height: 100px;
  flex-shrink: 0;
  border-radius: 6px;
  overflow: hidden;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.08);
}

.book-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.no-cover {
  width: 100%;
  height: 100%;
  background: rgba(139, 111, 82, 0.1);
  color: #8b6f52;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
}

.book-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
}

.book-title {
  font-family: var(--font-sans);
  font-size: 16px;
  font-weight: 700;
  color: #2e2520;
}

.book-author {
  font-size: 13px;
  color: #6b5e53;
}

.book-tags {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}

.card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 14px;
  border-top: 1px solid rgba(139, 111, 82, 0.08);
  padding-top: 12px;
}

/* 审核记录时间线 */
.review-history {
  padding: 8px 16px 20px;
}

.section-title {
  font-family: var(--font-sans);
  font-size: 17px;
  font-weight: 700;
  color: #2e2520;
  margin-bottom: 16px;
}

.timeline {
  position: relative;
  padding-left: 20px;
}

.timeline::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 8px;
  bottom: 8px;
  width: 2px;
  background: rgba(163, 107, 70, 0.2);
}

.timeline-item {
  position: relative;
  margin-bottom: 16px;
}

.timeline-badge {
  position: absolute;
  left: -20px;
  top: 12px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #a36b46;
  border: 2px solid #fff;
  box-shadow: 0 0 6px rgba(163, 107, 70, 0.4);
}

.timeline-card {
  padding: 12px;
  border-radius: 12px;
}

.timeline-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.req-book {
  font-weight: 700;
  font-size: 14px;
  color: #2e2520;
}

.timeline-tags {
  display: flex;
  gap: 4px;
}

.timeline-time {
  font-size: 12px;
  color: #8b6f52;
}

.reject-reason {
  margin-top: 8px;
  font-size: 12px;
  color: #ee4d38;
  background: rgba(238, 77, 56, 0.05);
  padding: 6px 10px;
  border-radius: 6px;
  border-left: 3px solid #ee4d38;
}

/* 抽屉弹出层样式 */
.edit-popup {
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.95) !important;
  backdrop-filter: blur(30px);
}

.popup-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid rgba(139, 111, 82, 0.08);
}

.popup-title {
  font-size: 17px;
  font-weight: 700;
  color: #2e2520;
}

.close-icon {
  color: #8b6f52;
}

.popup-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px 16px 30px;
}

.popup-cover-uploader {
  width: 60px;
  height: 80px;
  border: 1px dashed rgba(139, 111, 82, 0.3);
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #8b6f52;
  overflow: hidden;
}

.popup-cover-uploader img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.popup-actions {
  margin-top: 24px;
  padding: 0 16px;
}
</style>
