<template>
  <div class="shelf-container">
    <div class="page-header">
      <div class="header-left">
        <el-button link class="back-btn" @click="$router.push('/')">
          <el-icon><ArrowLeft /></el-icon> 返回首页
        </el-button>
        <el-divider direction="vertical" />
        <div class="header-title-box">
          <h2>我的书架 📚</h2>
          <p class="subtitle">记录你的阅读足迹</p>
        </div>
      </div>
      <div class="header-right">
        <el-switch
            v-model="shelfVisible"
            active-text="书架公开"
            inactive-text="书架私密"
            :active-value="1"
            :inactive-value="0"
            @change="toggleVisibility"
            style="margin-right: 14px;"
        />
        <el-button type="primary" @click="showUploadDialog = true">
          <el-icon style="margin-right:5px"><Upload /></el-icon>
          上传书籍
        </el-button>
        <el-button plain @click="openMyUploads">
          <el-icon style="margin-right:5px"><Document /></el-icon>
          我的上传
        </el-button>
        <el-button plain @click="showBooklistDrawer = true">
          <el-icon style="margin-right:5px"><Collection /></el-icon>
          我的书单
        </el-button>
        <el-button plain @click="showCreateDialog = true">
          <el-icon style="margin-right:5px"><Plus /></el-icon>
          创建书单
        </el-button>
      </div>
    </div>

    <el-empty v-if="shelfList.length === 0" description="书架空空如也，快去书城看看吧">
      <el-button type="primary" @click="$router.push('/')">去逛逛</el-button>
    </el-empty>

    <div class="book-grid" v-else>
      <div
          class="shelf-card"
          v-for="item in shelfList"
          :key="item.id"
      >
        <div class="cover-box" @click="continueRead(item.bookId)">
          <img :src="item.coverUrl || 'https://via.placeholder.com/150'" class="book-cover" />
          <div class="hover-mask">
            <el-icon size="30"><VideoPlay /></el-icon>
            <span>继续阅读</span>
          </div>
          <div class="read-badge" v-if="calculatePercentage(item) >= 100">已读完</div>
        </div>

        <div class="book-info">
          <div class="book-title" :title="item.bookName">{{ item.bookName }}</div>
          <div class="book-author">{{ item.author }}</div>

          <div class="progress-box">
            <div class="chapter-info">
              <span class="chapter-label">上次看到：</span>
              <span class="chapter-name" :title="item.currentChapterTitle">
                {{ formatChapterText(item) }}
              </span>
            </div>

            <el-progress
                :percentage="calculatePercentage(item)"
                :stroke-width="8"
                :status="calculatePercentage(item) >= 100 ? 'success' : ''"
                :format="progressFormat"
            />
          </div>

          <div class="action-bar">
            <span class="time-text">{{ formatTime(item.lastReadTime) }}</span>
            <div class="action-btns">
              <!-- 加入书单 dropdown -->
              <el-dropdown trigger="click" @command="(cmd) => addBookToList(cmd, item.bookId)" v-if="booklists.length > 0">
                <el-button link type="primary" size="small">
                  <el-icon><FolderAdd /></el-icon> 加入书单
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-for="bl in booklists" :key="bl.id" :command="bl.id">
                      {{ bl.name }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>

              <el-popconfirm title="确定移出书架吗？" @confirm="removeFromShelf(item.id)">
                <template #reference>
                  <el-button link type="danger" size="small">
                    <el-icon><Delete /></el-icon> 移出
                  </el-button>
                </template>
              </el-popconfirm>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ====== 创建书单 Dialog ====== -->
    <el-dialog v-model="showCreateDialog" title="创建书单" width="420px">
      <el-form :model="newBooklist" label-width="70px">
        <el-form-item label="书单名称">
          <el-input v-model="newBooklist.name" placeholder="给书单起个名字" maxlength="30" show-word-limit />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="newBooklist.description" type="textarea" :rows="3" placeholder="可选，描述一下这个书单" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="createBooklist" :disabled="!newBooklist.name.trim()">创建</el-button>
      </template>
    </el-dialog>

    <!-- ====== 我的书单 Drawer ====== -->
    <el-drawer v-model="showBooklistDrawer" title="我的书单" direction="rtl" size="380px">
      <div v-if="booklists.length === 0" class="empty-list-tip">
        <p>还没有书单，去创建一个吧</p>
        <el-button size="small" @click="showBooklistDrawer = false; showCreateDialog = true">创建书单</el-button>
      </div>

      <div class="booklist-list" v-else>
        <div class="booklist-item" v-for="bl in booklists" :key="bl.id">
          <div class="bl-header" @click="viewBooklistDetail(bl.id)">
            <div class="bl-name">📋 {{ bl.name }}</div>
            <div class="bl-desc" v-if="bl.description">{{ bl.description }}</div>
          </div>
          <div class="bl-actions">
            <el-button link size="small" @click="viewBooklistDetail(bl.id)">
              <el-icon><View /></el-icon> 查看
            </el-button>
            <el-button link size="small" @click="copyShareLink(bl)">
              <el-icon><Share /></el-icon> 分享
            </el-button>
            <el-popconfirm title="确定删除此书单吗？" @confirm="deleteBooklist(bl.id)">
              <template #reference>
                <el-button link type="danger" size="small">
                  <el-icon><Delete /></el-icon> 删除
                </el-button>
              </template>
            </el-popconfirm>
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- ====== 书单详情 Dialog ====== -->
    <el-dialog v-model="showDetailDialog" :title="detailBooklist?.name || '书单详情'" width="500px">
      <div v-if="detailBooklist">
        <p class="detail-desc" v-if="detailBooklist.description">{{ detailBooklist.description }}</p>
        <div class="detail-book-list">
          <div class="detail-book-item" v-for="book in detailBooklist.books" :key="book.id">
            <img :src="book.coverUrl || 'https://via.placeholder.com/40x55'" class="detail-cover" />
            <div class="detail-info">
              <div class="detail-title">{{ book.title }}</div>
              <div class="detail-author">{{ book.author }}</div>
            </div>
            <el-button link type="danger" size="small" @click="removeBookFromList(detailBooklist.id, book.id)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
          <el-empty v-if="!detailBooklist.books || detailBooklist.books.length === 0" description="书单还是空的" :image-size="60" />
        </div>
      </div>
    </el-dialog>

    <!-- ====== 上传书籍 Dialog ====== -->
    <el-dialog v-model="showUploadDialog" title="上传书籍" width="500px">
      <el-form :model="uploadForm" label-width="80px">
        <el-form-item label="书名" required>
          <el-input v-model="uploadForm.title" placeholder="请输入书名" maxlength="100" />
        </el-form-item>
        <el-form-item label="作者">
          <el-input v-model="uploadForm.author" placeholder="请输入作者名" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="uploadForm.category" style="width: 100%" placeholder="选择分类">
            <el-option value="科幻" label="科幻"/>
            <el-option value="文学" label="文学"/>
            <el-option value="历史" label="历史"/>
            <el-option value="技术" label="技术"/>
            <el-option value="悬疑" label="悬疑"/>
            <el-option value="其他" label="其他"/>
          </el-select>
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="uploadForm.description" type="textarea" :rows="3" placeholder="简单描述一下这本书" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="封面图">
          <el-upload
              action="/api/sysBook/upload"
              :on-success="(res) => uploadForm.coverUrl = res.data"
              :show-file-list="false"
              class="avatar-uploader"
          >
            <img v-if="uploadForm.coverUrl" :src="uploadForm.coverUrl" class="upload-img-preview"/>
            <el-icon v-else class="avatar-uploader-icon"><Plus /></el-icon>
          </el-upload>
        </el-form-item>
        <el-form-item label="电子书" required>
          <el-upload
              action="/api/sysBook/upload"
              :on-success="(res) => uploadForm.filePath = res.data"
              :show-file-list="false"
          >
            <el-button size="small" type="primary">
              {{ uploadForm.filePath ? '文件已上传 (点击替换)' : '上传 TXT 文件' }}
            </el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" @click="submitUserUpload" :disabled="!uploadForm.title || !uploadForm.filePath">提交上传</el-button>
      </template>
    </el-dialog>

    <!-- ====== 我的上传 Drawer ====== -->
    <el-drawer v-model="showMyUploadsDrawer" title="我的上传" direction="rtl" size="420px">
      <div v-if="myUploads.length === 0" class="empty-list-tip">
        <p>你还没有上传过书籍</p>
      </div>
      <div class="booklist-list" v-else>
        <div class="upload-item" v-for="book in myUploads" :key="book.id">
          <div class="upload-item-left">
            <img :src="book.coverUrl || 'https://via.placeholder.com/40x55'" class="detail-cover" />
            <div class="detail-info">
              <div class="detail-title">{{ book.title }}</div>
              <div class="detail-author">{{ book.author || '未知作者' }}</div>
            </div>
          </div>
          <div class="upload-item-right">
            <el-tag size="small" :type="statusTagType(book.status)" effect="dark">{{ statusText(book.status) }}</el-tag>
            <el-button
                v-if="book.status === 0 || book.status === 3"
                type="primary" size="small" plain
                @click="applyPublic(book.id)"
            >申请公开</el-button>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { VideoPlay, Delete, HomeFilled, Plus, Collection, FolderAdd, Share, View, Upload, Document } from '@element-plus/icons-vue'

const router = useRouter()
const shelfList = ref([])
const userInfo = ref({})
const shelfVisible = ref(1)

// 书单相关
const booklists = ref([])
const showCreateDialog = ref(false)
const showBooklistDrawer = ref(false)
const showDetailDialog = ref(false)
const newBooklist = ref({ name: '', description: '' })
const detailBooklist = ref(null)

// 上传相关
const showUploadDialog = ref(false)
const showMyUploadsDrawer = ref(false)
const myUploads = ref([])
const uploadForm = ref({ title: '', author: '', category: '', description: '', coverUrl: '', filePath: '' })

onMounted(() => {
  const userStr = localStorage.getItem('user')
  if (userStr) {
    userInfo.value = JSON.parse(userStr)
    shelfVisible.value = userInfo.value.shelfVisible ?? 1
    loadShelf()
    loadBooklists()
  } else {
    ElMessage.warning('请先登录')
    router.push('/login')
  }
})

// ===== 书架 =====
const loadShelf = async () => {
  try {
    const res = await axios.get(`/api/bookshelf/list/${userInfo.value.id}`)
    if (res.data.code === '200') {
      shelfList.value = res.data.data
    }
  } catch (error) {
    console.error('书架加载失败', error)
  }
}

const calculatePercentage = (item) => {
  const total = item.totalChapters || 0
  const current = item.currentChapterIndex || 0
  if (total === 0) return 0
  let percent = Math.round(((current + 1) / total) * 100)
  if (percent > 100) percent = 100
  return percent
}

const formatChapterText = (item) => {
  if (item.currentChapterTitle) return item.currentChapterTitle
  const idx = item.currentChapterIndex || 0
  return `第 ${idx + 1} 章`
}

const progressFormat = (percentage) => {
  return percentage === 100 ? '已完结' : `${percentage}%`
}

const continueRead = (bookId) => {
  if (!bookId) { ElMessage.error('书籍数据异常'); return }
  router.push(`/read/${bookId}`)
}

const removeFromShelf = async (id) => {
  await axios.delete(`/api/bookshelf/remove/${id}`)
  ElMessage.success('已移出')
  loadShelf()
}

const formatTime = (timeStr) => {
  if (!timeStr) return '刚刚'
  return timeStr.split('T')[0]
}

// ===== 书架可见性 =====
const toggleVisibility = async (val) => {
  try {
    await axios.post('/api/sysUser/update', {
      id: userInfo.value.id,
      shelfVisible: val
    })
    userInfo.value.shelfVisible = val
    localStorage.setItem('user', JSON.stringify(userInfo.value))
    ElMessage.success(val === 1 ? '书架已设为公开' : '书架已设为私密')
  } catch (e) {
    ElMessage.error('设置失败')
    shelfVisible.value = val === 1 ? 0 : 1
  }
}

// ===== 书单 CRUD =====
const loadBooklists = async () => {
  try {
    const res = await axios.get(`/api/booklist/list/${userInfo.value.id}`)
    if (res.data.code === '200') {
      booklists.value = res.data.data
    }
  } catch (e) {
    console.error('书单加载失败', e)
  }
}

const createBooklist = async () => {
  if (!newBooklist.value.name.trim()) return
  try {
    const res = await axios.post('/api/booklist/create', {
      userId: userInfo.value.id,
      name: newBooklist.value.name.trim(),
      description: newBooklist.value.description.trim()
    })
    if (res.data.code === '200') {
      ElMessage.success('书单创建成功')
      showCreateDialog.value = false
      newBooklist.value = { name: '', description: '' }
      loadBooklists()
    }
  } catch (e) {
    ElMessage.error('创建失败')
  }
}

const deleteBooklist = async (id) => {
  try {
    await axios.delete(`/api/booklist/delete/${id}`)
    ElMessage.success('书单已删除')
    loadBooklists()
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

const addBookToList = async (booklistId, bookId) => {
  try {
    const res = await axios.post('/api/booklist/addBook', {
      booklistId,
      bookId
    })
    if (res.data.code === '200') {
      ElMessage.success('已添加到书单')
    } else {
      ElMessage.warning(res.data.msg)
    }
  } catch (e) {
    ElMessage.error('添加失败')
  }
}

const viewBooklistDetail = async (id) => {
  try {
    const res = await axios.get(`/api/booklist/detail/${id}`)
    if (res.data.code === '200') {
      detailBooklist.value = res.data.data
      showDetailDialog.value = true
    }
  } catch (e) {
    ElMessage.error('加载失败')
  }
}

const removeBookFromList = async (booklistId, bookId) => {
  try {
    await axios.delete(`/api/booklist/removeBook?booklistId=${booklistId}&bookId=${bookId}`)
    ElMessage.success('已从书单移除')
    viewBooklistDetail(booklistId) // 刷新
  } catch (e) {
    ElMessage.error('移除失败')
  }
}

const copyShareLink = (bl) => {
  const link = `${window.location.origin}/shelf/import/${bl.shareCode}`
  navigator.clipboard.writeText(link).then(() => {
    ElMessage.success('分享链接已复制到剪贴板')
  }).catch(() => {
    // fallback
    ElMessage({ message: `分享链接：${link}`, type: 'info', duration: 8000 })
  })
}

// ===== 用户上传书籍 =====
const submitUserUpload = async () => {
  if (!uploadForm.value.title || !uploadForm.value.filePath) return
  try {
    const res = await axios.post('/api/sysBook/userUpload', {
      ...uploadForm.value,
      uploaderId: userInfo.value.id
    })
    if (res.data.code === '200') {
      ElMessage.success('上传成功！书籍已加入你的书架')
      showUploadDialog.value = false
      uploadForm.value = { title: '', author: '', category: '', description: '', coverUrl: '', filePath: '' }
      loadShelf()

      // 自动解析章节
      const bookId = res.data.data?.id
      if (bookId) {
        try { await axios.post(`/api/sysBook/analyze/${bookId}`) } catch (e) { /* 解析失败不影响主流程 */ }
      }
    } else {
      ElMessage.error(res.data.msg || '上传失败')
    }
  } catch (e) {
    ElMessage.error('上传失败')
  }
}

const openMyUploads = async () => {
  try {
    const res = await axios.get(`/api/sysBook/myUploads/${userInfo.value.id}`)
    if (res.data.code === '200') {
      myUploads.value = res.data.data
    }
  } catch (e) {
    console.error('加载失败', e)
  }
  showMyUploadsDrawer.value = true
}

const applyPublic = async (bookId) => {
  try {
    const res = await axios.post(`/api/sysBook/applyPublic/${bookId}`)
    if (res.data.code === '200') {
      ElMessage.success('已提交公开申请，等待管理员审核')
      openMyUploads()
    } else {
      ElMessage.error(res.data.msg)
    }
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

const statusText = (status) => {
  const map = { 0: '私有', 1: '审核中', 2: '已公开', 3: '已驳回' }
  return map[status] || '未知'
}
const statusTagType = (status) => {
  const map = { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }
  return map[status] || 'info'
}
</script>

<style scoped>
.shelf-container {
  max-width: 1100px;
  margin: 0 auto;
  padding: 18px 24px;
}

/* === 上传弹窗 === */
.avatar-uploader {
  border: 1px dashed #d4c8ba;
  border-radius: 4px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  width: 80px;
  height: 110px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: border-color 0.2s;
}
.avatar-uploader:hover { border-color: #8b6f52; }
.upload-img-preview { width: 100%; height: 100%; object-fit: cover; }
.avatar-uploader-icon { font-size: 22px; color: #b5a99c; }

/* === 我的上传 Drawer === */
.upload-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid #f0ece4;
  gap: 12px;
}
.upload-item:hover { background: #faf5ed; }
.upload-item-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}
.upload-item-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}



/* === 书籍网格 === */
.book-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 22px;
}

.shelf-card {
  background: #fffdf9;
  border-radius: 6px;
  overflow: hidden;
  transition: border-color 0.25s, box-shadow 0.25s;
  display: flex;
  flex-direction: column;
  border: 1px solid #e8e0d6;
}
.shelf-card:hover {
  border-color: #c4b09a;
  box-shadow: 0 4px 14px rgba(60, 40, 20, 0.08);
}

/* === 封面区域 === */
.cover-box {
  height: 270px;
  position: relative;
  cursor: pointer;
  overflow: hidden;
  background-color: #f0ece4;
}
.book-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.4s;
}
.cover-box:hover .book-cover {
  transform: scale(1.03);
}
.hover-mask {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(40, 28, 16, 0.35);
  backdrop-filter: blur(1px);
  color: #f0ece4;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.25s;
  gap: 6px;
  font-weight: 600;
  font-size: 14px;
}
.cover-box:hover .hover-mask {
  opacity: 1;
}
.read-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  background: #6a8c5a;
  color: #fff;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 3px;
  font-weight: 600;
}

/* === 信息区域 === */
.book-info {
  padding: 14px 16px;
  flex: 1;
  display: flex;
  flex-direction: column;
}
.book-title {
  font-weight: 600;
  font-size: 15px;
  color: #3d3632;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.book-author {
  font-size: 12px;
  color: #9b8e82;
  margin-bottom: 14px;
}

/* === 进度条区域 === */
.progress-box {
  margin-top: auto;
  margin-bottom: 10px;
  background: #faf5ed;
  padding: 8px 10px;
  border-radius: 4px;
  border: 1px solid #ede7de;
}
.chapter-info {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  margin-bottom: 5px;
}
.chapter-label {
  color: #9b8e82;
  flex-shrink: 0;
}
.chapter-name {
  color: #8b6f52;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 120px;
}

/* === 底部操作栏 === */
.action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px solid #f0ece4;
  padding-top: 10px;
}
.action-btns {
  display: flex;
  gap: 6px;
  align-items: center;
}
.time-text {
  font-size: 12px;
  color: #c4b9ab;
}

/* === 书单抽屉 === */
.empty-list-tip {
  text-align: center;
  color: #9b8e82;
  padding: 40px 0;
}
.booklist-list {
  padding: 4px 0;
}
.booklist-item {
  padding: 14px 16px;
  border-bottom: 1px solid #f0ece4;
  transition: background 0.15s;
}
.booklist-item:hover {
  background: #faf5ed;
}
.bl-header {
  cursor: pointer;
  margin-bottom: 8px;
}
.bl-name {
  font-weight: 600;
  color: #3d3632;
  font-size: 15px;
  margin-bottom: 4px;
}
.bl-desc {
  font-size: 13px;
  color: #9b8e82;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bl-actions {
  display: flex;
  gap: 10px;
}

/* === 书单详情 === */
.detail-desc {
  color: #7a6e63;
  font-size: 14px;
  margin-bottom: 18px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0ece4;
}
.detail-book-list {
  max-height: 400px;
  overflow-y: auto;
}
.detail-book-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid #f5f0e8;
}
.detail-book-item:last-child { border-bottom: none; }
.detail-cover {
  width: 40px;
  height: 55px;
  object-fit: cover;
  border-radius: 3px;
  flex-shrink: 0;
  background: #f0ece4;
}
.detail-info { flex: 1; }
.detail-title {
  font-weight: 600;
  font-size: 14px;
  color: #3d3632;
  margin-bottom: 2px;
}
.detail-author {
  font-size: 12px;
  color: #9b8e82;
}
/* === 标准统一头部 === */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  border-bottom: 1px solid #e8e0d6;
  padding-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.back-btn {
  font-size: 15px;
  color: #6b5e53;
}
.back-btn:hover { color: #8b6f52; }
.header-title-box {
  display: flex;
  flex-direction: column;
}
.header-title-box h2 {
  margin: 0;
  font-family: 'Noto Serif SC', serif;
  color: #2e2520;
  font-size: 22px;
  font-weight: 600;
}
.subtitle {
  color: #9b8e82;
  font-size: 13px;
  margin: 4px 0 0 0;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
/* === 标准统一头部 === */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  border-bottom: 1px solid #e8e0d6;
  padding-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.back-btn {
  font-size: 15px;
  color: #6b5e53;
}
.back-btn:hover { color: #8b6f52; }
.header-title-box {
  display: flex;
  flex-direction: column;
}
.header-title-box h2 {
  margin: 0;
  font-family: 'Noto Serif SC', serif;
  color: #2e2520;
  font-size: 22px;
  font-weight: 600;
}
.subtitle {
  color: #9b8e82;
  font-size: 13px;
  margin: 4px 0 0 0;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
</style>