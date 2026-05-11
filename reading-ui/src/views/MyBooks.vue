<template>
  <div class="my-books-container page-glass-container">
    <div class="page-header">
      <div class="header-left">
        <el-button plain round class="back-btn glass-btn" @click="goHome">
          <el-icon><ArrowLeft /></el-icon> 返回首页
        </el-button>
        <el-divider direction="vertical" />
        <div class="header-title-box">
          <h2>我的书籍</h2>
        </div>
      </div>
      <el-button type="primary" @click="openUploadDialog">
        <el-icon><Plus /></el-icon> 上传新书
      </el-button>
    </div>

    <el-tabs v-model="activeTab" type="border-card" @tab-change="onTabChange">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane label="草稿" name="0" />
      <el-tab-pane label="待审核" name="1" />
      <el-tab-pane label="已上线" name="2" />
      <el-tab-pane label="已驳回" name="3" />
      <el-tab-pane label="已下架" name="4" />

      <el-empty v-if="filteredBooks.length === 0" description="暂无书籍" />

      <div v-else class="book-grid">
        <el-card v-for="book in filteredBooks" :key="book.id" class="book-card" shadow="hover">
          <div class="book-card-body">
            <div class="book-cover">
              <img v-if="book.coverUrl" :src="book.coverUrl" alt="" />
              <div v-else class="no-cover">暂无封面</div>
            </div>
            <div class="book-info">
              <div class="book-title">{{ book.title }}</div>
              <div class="book-meta">
                <span class="book-author">{{ book.author || '未知作者' }}</span>
                <el-tag v-if="book.category" size="small" type="info">{{ book.category }}</el-tag>
              </div>
              <div class="book-status">
                <el-tag :type="statusTagType(book.status)" size="small">
                  {{ statusText(book.status) }}
                </el-tag>
              </div>
              <div class="book-actions">
                <template v-if="book.status === 0 || book.status === 3 || book.status === 4">
                  <el-button type="primary" size="small" @click="openEditDialog(book)">编辑</el-button>
                  <el-button type="success" size="small" @click="submitForReview(book.id)">提交审核</el-button>
                </template>
                <template v-else-if="book.status === 1">
                  <span class="pending-hint">审核中，请耐心等待</span>
                </template>
                <template v-else-if="book.status === 2">
                  <el-button type="primary" size="small" @click="openEditDialog(book)">编辑信息</el-button>
                  <el-button type="warning" size="small" @click="requestDelist(book.id)">申请下架</el-button>
                </template>
              </div>
            </div>
          </div>
        </el-card>
      </div>
    </el-tabs>

    <!-- 审核历史 -->
    <div class="review-history">
      <h3>审核记录</h3>
      <el-empty v-if="reviewRequests.length === 0" description="暂无审核记录" />
      <el-timeline v-else>
        <el-timeline-item
          v-for="req in reviewRequests"
          :key="req.id"
          :timestamp="formatTime(req.createTime)"
          :type="timelineType(req.status)"
          placement="top"
        >
          <el-card shadow="never" class="timeline-card">
            <div class="timeline-content">
              <span class="req-book">《{{ req.bookTitle || '未知书籍' }}》</span>
              <el-tag :type="reqTypeTag(req.requestType)" size="small" style="margin-left: 8px;">
                {{ reqTypeText(req.requestType) }}
              </el-tag>
              <el-tag :type="reqStatusTag(req.status)" size="small" style="margin-left: 4px;">
                {{ reqStatusText(req.status) }}
              </el-tag>
            </div>
            <div v-if="req.status === 2 && req.rejectReason" class="reject-reason">
              驳回原因：{{ req.rejectReason }}
            </div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
    </div>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="showEditDialog" :title="isUpload ? '上传新书' : '编辑书籍'" width="520px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="书名"><el-input v-model="editForm.title" /></el-form-item>
        <el-form-item label="作者"><el-input v-model="editForm.author" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="editForm.category" style="width: 100%">
            <el-option value="科幻" label="科幻" />
            <el-option value="文学" label="文学" />
            <el-option value="历史" label="历史" />
            <el-option value="技术" label="技术" />
            <el-option value="悬疑" label="悬疑" />
          </el-select>
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="editForm.description" type="textarea" :rows="4" placeholder="请输入书籍的内容简介..." />
        </el-form-item>
        <el-form-item label="封面图">
          <el-upload
            action="/api/sysBook/upload"
            :headers="uploadHeaders"
            :on-success="(res) => editForm.coverUrl = res.data"
            :show-file-list="false"
            class="avatar-uploader"
          >
            <img v-if="editForm.coverUrl" :src="editForm.coverUrl" class="upload-img-preview" alt="" />
            <el-icon v-else class="avatar-uploader-icon"><Plus /></el-icon>
            <div v-if="!editForm.coverUrl" class="el-upload__text">点击上传</div>
          </el-upload>
        </el-form-item>
        <el-form-item label="电子书">
          <el-upload
            action="/api/sysBook/upload"
            :headers="uploadHeaders"
            :on-success="(res) => editForm.filePath = res.data"
            :show-file-list="false"
          >
            <el-button size="small" type="primary">
              {{ editForm.filePath ? '文件已上传 (点击替换)' : '上传 TXT' }}
            </el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button v-if="!isUpload && editForm.status !== 2" type="primary" @click="saveBook">保存</el-button>
        <el-button v-if="!isUpload && editForm.status === 2" type="warning" @click="saveBook">
          保存并提交编辑审核
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../utils/request'
import { getAuthHeaders } from '../utils/authHeaders'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Plus } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const uploadHeaders = getAuthHeaders()

const currentUser = authStore.user

const books = ref([])
const reviewRequests = ref([])
const activeTab = ref('all')
const showEditDialog = ref(false)
const isUpload = ref(false)

const editForm = ref({
  id: null, title: '', author: '', category: '', description: '',
  coverUrl: '', filePath: '', status: null
})

const goHome = () => router.push('/')

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
  const map = { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger', 4: '' }
  return map[s] || 'info'
}

const reqTypeText = (t) => ({ new: '新书公开', edit: '编辑审核', delist: '下架审核' }[t] || t)
const reqTypeTag = (t) => ({ new: 'success', edit: 'warning', delist: 'danger' }[t] || 'info')
const reqStatusText = (s) => ({ 0: '待审核', 1: '已通过', 2: '已驳回' }[s] || '未知')
const reqStatusTag = (s) => ({ 0: 'warning', 1: 'success', 2: 'danger' }[s] || 'info')
const timelineType = (s) => ({ 0: 'warning', 1: 'success', 2: 'danger' }[s] || '')

const formatTime = (t) => {
  if (!t) return ''
  return new Date(t).toLocaleString()
}

const loadBooks = async () => {
  if (!currentUser) return
  try {
    const res = await request.get(`/api/sysBook/myUploads/${currentUser.id}`)
    if (res.data.code === '200') {
      books.value = res.data.data
    }
  } catch (e) {
    console.error(e)
  }
}

const loadReviewRequests = async () => {
  if (!currentUser) return
  try {
    const res = await request.get(`/api/sysBook/reviewRequests/${currentUser.id}`)
    if (res.data.code === '200') {
      reviewRequests.value = res.data.data
    }
  } catch (e) {
    console.error(e)
  }
}

const onTabChange = () => {}

const openUploadDialog = () => {
  isUpload.value = true
  editForm.value = { id: null, title: '', author: '', category: '', description: '', coverUrl: '', filePath: '', status: null }
  showEditDialog.value = true
}

const openEditDialog = (book) => {
  isUpload.value = false
  editForm.value = { ...book }
  showEditDialog.value = true
}

const saveBook = async () => {
  if (!editForm.value.title) return ElMessage.warning('书名不能为空')
  try {
    if (isUpload.value) {
      const payload = { ...editForm.value, uploaderId: currentUser.id }
      await request.post('/api/sysBook/userUpload', payload)
      ElMessage.success('上传成功')
    } else if (editForm.value.status === 2) {
      // 已上线书籍走编辑审核
      await request.post('/api/sysBook/applyEdit', editForm.value)
      ElMessage.success('编辑审核已提交')
    } else {
      // 草稿/驳回/下架 直接更新
      await request.put('/api/sysBook/userEdit', editForm.value)
      ElMessage.success('保存成功')
    }
    showEditDialog.value = false
    loadBooks()
    loadReviewRequests()
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || '操作失败')
  }
}

const submitForReview = async (bookId) => {
  try {
    await ElMessageBox.confirm('确定提交审核吗？审核通过后书籍将公开上线。', '提交审核')
    const res = await request.post(`/api/sysBook/applyPublic/${bookId}`)
    if (res.data.code === '200') {
      ElMessage.success('已提交审核')
      loadBooks()
      loadReviewRequests()
    } else {
      ElMessage.error(res.data.msg)
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.response?.data?.msg || '操作失败')
  }
}

const requestDelist = async (bookId) => {
  try {
    await ElMessageBox.confirm('确定申请下架吗？需要管理员审核通过后才会下架。', '申请下架', { type: 'warning' })
    const res = await request.post(`/api/sysBook/applyDelist/${bookId}`)
    if (res.data.code === '200') {
      ElMessage.success('下架审核已提交')
      loadBooks()
      loadReviewRequests()
    } else {
      ElMessage.error(res.data.msg)
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.response?.data?.msg || '操作失败')
  }
}

onMounted(() => {
  if (!currentUser) {
    router.push('/login')
    return
  }
  loadBooks()
  loadReviewRequests()
})
</script>

<style scoped>
.my-books-container {
  padding: 18px 24px;
}

/* === header title fix === */
.header-title-box h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #2e2520;
}

/* === 玻璃拟态 tabs === */
:deep(.el-tabs--border-card) {
  background: rgba(255, 255, 255, 0.45) !important;
  backdrop-filter: blur(24px) !important;
  -webkit-backdrop-filter: blur(24px) !important;
  border-radius: 12px !important;
  border: 1px solid rgba(255, 255, 255, 0.6) !important;
  box-shadow: 0 8px 32px rgba(60, 40, 20, 0.05) !important;
  overflow: hidden;
}
:deep(.el-tabs--border-card > .el-tabs__header) {
  background: rgba(255, 255, 255, 0.3) !important;
  border-bottom: 1px solid rgba(255, 255, 255, 0.5) !important;
}
:deep(.el-tabs--border-card > .el-tabs__header .el-tabs__item.is-active) {
  background: rgba(255, 255, 255, 0.6) !important;
  border-right-color: rgba(255, 255, 255, 0.5) !important;
  border-left-color: rgba(255, 255, 255, 0.5) !important;
}

/* === 书籍卡片网格 === */
.book-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 16px;
  margin-top: 16px;
}
.book-card {
  background: rgba(255, 255, 255, 0.5) !important;
  backdrop-filter: blur(16px) !important;
  border-radius: 12px !important;
  border: 1px solid rgba(255, 255, 255, 0.6) !important;
  transition: all 0.2s;
}
.book-card:hover {
  box-shadow: 0 8px 24px rgba(60, 40, 20, 0.1) !important;
  transform: translateY(-2px);
}
.book-card-body {
  display: flex;
  gap: 14px;
}
.book-cover {
  flex-shrink: 0;
  width: 80px;
  height: 110px;
  border-radius: 6px;
  overflow: hidden;
  background: #f5f0eb;
  display: flex;
  align-items: center;
  justify-content: center;
}
.book-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.no-cover {
  font-size: 12px;
  color: #b5a99c;
}
.book-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.book-title {
  font-size: 16px;
  font-weight: 600;
  color: #2e2520;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.book-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #4a3e35;
}
.book-actions {
  margin-top: auto;
  display: flex;
  gap: 8px;
  align-items: center;
}
.pending-hint {
  font-size: 12px;
  color: #6b5e53;
  font-style: italic;
}

/* === 审核历史 === */
.review-history {
  margin-top: 32px;
  padding: 0 4px;
}
.review-history h3 {
  font-size: 18px;
  font-weight: 600;
  color: #2e2520;
  margin-bottom: 16px;
}
.timeline-card {
  background: rgba(255, 255, 255, 0.4) !important;
  border-radius: 8px !important;
}
.timeline-content {
  display: flex;
  align-items: center;
  gap: 4px;
}
.req-book {
  font-weight: 500;
  color: #2e2520;
}
.reject-reason {
  margin-top: 8px;
  font-size: 13px;
  color: #e6a23c;
  background: rgba(230, 162, 60, 0.08);
  padding: 6px 10px;
  border-radius: 4px;
  border-left: 3px solid #e6a23c;
}

/* === 弹窗样式 === */
:deep(.el-dialog) {
  background: rgba(255, 255, 255, 0.85) !important;
  backdrop-filter: blur(30px) !important;
  border-radius: 16px !important;
}

/* === 上传图片 === */
.avatar-uploader {
  border: 1px dashed #d4c8ba;
  border-radius: 4px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  width: 100px;
  height: 140px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: border-color 0.2s;
}
.avatar-uploader:hover {
  border-color: #8b6f52;
}
.upload-img-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.avatar-uploader-icon {
  font-size: 24px;
  color: #b5a99c;
}
</style>
