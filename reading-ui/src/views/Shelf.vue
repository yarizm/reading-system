<template>
  <div class="shelf-container">
    <div class="shelf-header">
      <div class="header-left">
        <h2>我的书架 📚</h2>
        <p class="subtitle">记录你的阅读足迹</p>
      </div>
      <div class="header-right">
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
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { VideoPlay, Delete, HomeFilled } from '@element-plus/icons-vue' // 引入 HomeFilled

const router = useRouter()
const shelfList = ref([])
const userInfo = ref({})

onMounted(() => {
  const userStr = localStorage.getItem('user')
  if (userStr) {
    userInfo.value = JSON.parse(userStr)
    loadShelf()
  } else {
    ElMessage.warning('请先登录')
    router.push('/login')
  }
})

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

// === 计算进度百分比 ===
const calculatePercentage = (item) => {
  const total = item.totalChapters || 0
  const current = item.currentChapterIndex || 0

  // 如果没有章节信息（比如没解析过），默认给 0
  if (total === 0) return 0

  // 计算百分比：(当前章索引 + 1) / 总章数
  // 例如：共10章，读到第0章(索引)，进度 10%
  let percent = Math.round(((current + 1) / total) * 100)

  // 边界处理
  if (percent > 100) percent = 100
  return percent
}

// === 格式化章节文字 ===
const formatChapterText = (item) => {
  if (item.currentChapterTitle) {
    return item.currentChapterTitle
  }
  // 如果没有标题，回退显示第几章
  const idx = item.currentChapterIndex || 0
  return `第 ${idx + 1} 章`
}

// 进度条文字格式化
const progressFormat = (percentage) => {
  return percentage === 100 ? '已完结' : `${percentage}%`
}

const continueRead = (bookId) => {
  if (!bookId) {
    ElMessage.error('书籍数据异常')
    return
  }
  router.push(`/read/${bookId}`)
}

const removeFromShelf = async (id) => {
  await axios.delete(`/api/bookshelf/remove/${id}`)
  ElMessage.success('已移出')
  loadShelf()
}

const formatTime = (timeStr) => {
  if (!timeStr) return '刚刚'
  // 简单美化时间显示：2023-10-01
  return timeStr.split('T')[0]
}
</script>

<style scoped>
.shelf-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

/* 头部样式优化 */
.shelf-header {
  display: flex;
  justify-content: space-between;
  align-items: center; /* 垂直居中 */
  margin-bottom: 30px;
  border-bottom: 1px solid #eee;
  padding-bottom: 15px;
}
.header-left h2 {
  margin: 0 0 5px 0;
  color: #303133;
}
.subtitle {
  color: #909399;
  font-size: 14px;
  margin: 0;
}

.book-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); /* 稍微加宽一点卡片 */
  gap: 25px;
}

.shelf-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
  overflow: hidden;
  transition: all 0.3s;
  display: flex;
  flex-direction: column;
  border: 1px solid #ebeef5;
}
.shelf-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 12px 24px rgba(0,0,0,0.1);
}

.cover-box {
  height: 280px; /* 增加封面高度 */
  position: relative;
  cursor: pointer;
  overflow: hidden;
  background-color: #f5f7fa;
}
.book-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.5s;
}
.cover-box:hover .book-cover {
  transform: scale(1.05); /* 悬浮微放大 */
}
.hover-mask {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.3);
  backdrop-filter: blur(2px); /* 毛玻璃效果 */
  color: white;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s;
  gap: 8px;
  font-weight: bold;
}
.cover-box:hover .hover-mask {
  opacity: 1;
}
.read-badge {
  position: absolute;
  top: 10px;
  right: 10px;
  background: #67C23A;
  color: white;
  font-size: 12px;
  padding: 2px 6px;
  border-radius: 4px;
}

.book-info {
  padding: 16px;
  flex: 1;
  display: flex;
  flex-direction: column;
}
.book-title {
  font-weight: bold;
  font-size: 16px;
  color: #303133;
  margin-bottom: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.book-author {
  font-size: 13px;
  color: #909399;
  margin-bottom: 15px;
}

/* 进度条区域 */
.progress-box {
  margin-top: auto;
  margin-bottom: 12px;
  background: #f9fafe;
  padding: 8px;
  border-radius: 6px;
}
.chapter-info {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  margin-bottom: 6px;
}
.chapter-label {
  color: #909399;
  flex-shrink: 0;
}
.chapter-name {
  color: #409EFF;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 120px;
}

.action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px solid #f0f2f5;
  padding-top: 12px;
}
.time-text {
  font-size: 12px;
  color: #c0c4cc;
}
</style>