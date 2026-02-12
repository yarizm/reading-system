<template>
  <div class="shelf-container">
    <div class="shelf-header">
      <div class="header-left">
        <h2>我的书架 📚</h2>
        <p class="subtitle">记录你的阅读足迹</p>
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
        <el-button plain @click="showBooklistDrawer = true">
          <el-icon style="margin-right:5px"><Collection /></el-icon>
          我的书单
        </el-button>
        <el-button plain @click="showCreateDialog = true">
          <el-icon style="margin-right:5px"><Plus /></el-icon>
          创建书单
        </el-button>
        <el-button type="primary" plain @click="$router.push('/')">
          <el-icon style="margin-right: 5px"><HomeFilled /></el-icon>
          返回首页
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
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { VideoPlay, Delete, HomeFilled, Plus, Collection, FolderAdd, Share, View } from '@element-plus/icons-vue'

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
</script>

<style scoped>
.shelf-container {
  max-width: 1100px;
  margin: 0 auto;
  padding: 18px 24px;
}

/* === 头部 === */
.shelf-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 28px;
  border-bottom: 1px solid #e8e0d6;
  padding-bottom: 14px;
  flex-wrap: wrap;
  gap: 12px;
}
.header-left h2 {
  margin: 0 0 4px 0;
  color: #2e2520;
  font-family: 'Noto Serif SC', serif;
  font-size: 22px;
}
.subtitle {
  color: #9b8e82;
  font-size: 13px;
  margin: 0;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
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
</style>