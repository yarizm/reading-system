<template>
  <div class="book-detail-container">
    <el-card class="info-card" shadow="never">
      <div class="info-wrapper">
        <div class="cover-box">
          <img :src="bookInfo.coverUrl || defaultCover" class="book-cover" />
        </div>

        <div class="details-box">
          <h1 class="book-title">{{ bookInfo.title || bookInfo.name }}</h1>
          <div class="book-meta">
            <span class="meta-item"><el-icon><User /></el-icon> 作者：{{ bookInfo.author }}</span>
            <span class="meta-item"><el-icon><collection-tag /></el-icon> 分类：{{ bookInfo.category }}</span>
            <span class="meta-item">
               <el-rate
                   v-model="bookInfo.avgRating"
                   disabled
                   show-score
                   text-color="#ff9900"
                   score-template="{value} 分"
               />
            </span>
          </div>

          <div class="book-desc">
            <h3>简介</h3>
            <p>{{ bookInfo.description || '暂无简介...' }}</p>
          </div>

          <div class="action-btns">
            <el-button type="primary" size="large" round @click="startReading">
              <el-icon><Reading /></el-icon> 立即阅读
            </el-button>
            <el-button type="warning" size="large" round @click="addToShelf" :disabled="inShelf">
              <el-icon><Collection /></el-icon> {{ inShelf ? '已在书架' : '加入书架' }}
            </el-button>
            <el-button plain size="large" round @click="goBack">返回</el-button>
          </div>
        </div>
      </div>
    </el-card>

    <div class="comment-section">
      <div class="section-header">
        <h3>书友评论 ({{ totalComments }})</h3>
        <el-button type="primary" link @click="resetAndScrollToComment">我要评论</el-button>
      </div>

      <div class="comment-list">
        <el-empty v-if="commentList.length === 0" description="暂无评论，快来抢沙发！" />

        <div class="comment-item" v-for="item in commentList" :key="item.id">
          <el-avatar :size="40" :src="item.avatar || defaultAvatar" class="user-avatar clickable-user" @click="goToUserProfile(item.userId)" />

          <div class="comment-content-wrapper">
            <div class="comment-header">
              <span class="nickname clickable-user" @click="goToUserProfile(item.userId)">{{ item.nickname || '匿名书友' }}</span>
              <el-rate v-if="item.rating" v-model="item.rating" disabled size="small" />
              <span class="time">{{ formatTime(item.createTime) }}</span>
            </div>

            <p class="text">{{ item.content }}</p>

            <div class="comment-actions">
              <div class="action-item" :class="{ 'is-active': item.isLiked }" @click="toggleLike(item)">
                <el-icon>
                  <StarFilled v-if="item.isLiked" />
                  <Star v-else />
                </el-icon>
                {{ item.likeCount || 0 }}
              </div>
              <div class="action-item" @click="replyTo(item, item)">
                <el-icon><ChatDotSquare /></el-icon> 回复
              </div>
              <div class="action-item delete-btn" v-if="userInfo.id === item.userId || userInfo.role === 1" @click="deleteComment(item.id)">
                <el-icon><Delete /></el-icon> 删除
              </div>
            </div>

            <div class="sub-comments" v-if="item.children && item.children.length > 0">
              <div class="sub-item" v-for="sub in item.children" :key="sub.id">
                <el-avatar :size="24" :src="sub.avatar || defaultAvatar" class="clickable-user" @click="goToUserProfile(sub.userId)" />
                <div class="sub-content">
                  <div class="sub-header">
                    <span class="sub-nickname clickable-user" @click="goToUserProfile(sub.userId)">{{ sub.nickname }}</span>
                    <span v-if="sub.replyUserId && sub.replyUserId !== item.userId" class="reply-tag">
                      回复 <span class="at-name">@{{ sub.replyNickname }}</span>
                    </span>
                    <span class="sub-time">{{ formatTime(sub.createTime) }}</span>
                  </div>
                  <p class="sub-text">{{ sub.content }}</p>

                  <div class="comment-actions sub-actions">
                    <div class="action-item" :class="{ 'is-active': sub.isLiked }" @click="toggleLike(sub)">
                      <el-icon>
                        <StarFilled v-if="sub.isLiked" />
                        <Star v-else />
                      </el-icon>
                      {{ sub.likeCount || 0 }}
                    </div>
                    <div class="action-item" @click="replyTo(item, sub)">
                      <el-icon><ChatDotSquare /></el-icon> 回复
                    </div>
                    <div class="action-item delete-btn" v-if="userInfo.id === sub.userId || userInfo.role === 1" @click="deleteComment(sub.id)">
                      <el-icon><Delete /></el-icon>
                    </div>
                  </div>
                </div>
              </div>
            </div>

          </div>
        </div>
      </div>

      <div class="post-comment-box" id="commentInputBox">
        <div class="reply-indicator" v-if="replyTarget">
          回复 @{{ replyTarget.nickname }}
          <el-icon class="close-reply" @click="cancelReply"><Close /></el-icon>
        </div>

        <div class="input-row">
          <div class="rate-select" v-if="!replyTarget">
            <span class="label">评分</span>
            <el-rate v-model="myRating" />
          </div>

          <el-input
              v-model="myComment"
              type="textarea"
              :rows="3"
              :placeholder="replyTarget ? `回复 @${replyTarget.nickname}：` : '写下你的读书心得...'"
              maxlength="200"
              show-word-limit
          />
        </div>
        <div class="submit-row">
          <el-button type="primary" @click="submitComment" :loading="submitting">
            {{ replyTarget ? '发送回复' : '发表书评' }}
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  User, CollectionTag, Reading, Collection, Delete,
  ChatDotSquare, Close, Star, StarFilled
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const bookId = route.params.id

// 数据
const bookInfo = ref({})
const commentList = ref([])
const userInfo = ref({})
const inShelf = ref(false)
const defaultCover = 'https://via.placeholder.com/150'
const defaultAvatar = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'

// 评论表单
const myComment = ref('')
const myRating = ref(5) // 默认评分
const submitting = ref(false)

// 回复状态
const replyTarget = ref(null)
const replyParent = ref(null)

const totalComments = computed(() => {
  let count = commentList.value.length
  commentList.value.forEach(c => {
    if(c.children) count += c.children.length
  })
  return count
})

onMounted(() => {
  const userStr = localStorage.getItem('user')
  if (userStr) userInfo.value = JSON.parse(userStr)

  loadBookDetail()
  loadComments()
  checkShelf()
})

const loadBookDetail = async () => {
  try {
    const res = await axios.get(`/api/sysBook/${bookId}`)
    if (res.data.code === '200') bookInfo.value = res.data.data
  } catch (e) { console.error(e) }
}

const loadComments = async () => {
  try {
    const res = await axios.get(`/api/comment/list/${bookId}`, {
      params: { userId: userInfo.value.id }
    })
    if (res.data.code === '200') {
      commentList.value = res.data.data
    }
  } catch (e) { console.error(e) }
}

const checkShelf = async () => {
  if (!userInfo.value.id) return
  const res = await axios.get(`/api/bookshelf/list/${userInfo.value.id}`)
  if (res.data.code === '200') {
    inShelf.value = res.data.data.some(b => b.bookId == bookId)
  }
}

const replyTo = (parent, target) => {
  if (!userInfo.value.id) return ElMessage.warning('请先登录')
  replyParent.value = parent
  replyTarget.value = target
  // 进入回复模式时，虽然 UI 隐藏了评分，但数据层面不做操作，依靠 submitComment 处理
  scrollToCommentBox()
}

const cancelReply = () => {
  replyTarget.value = null
  replyParent.value = null
  // 不清空 myComment，方便用户转为普通书评
}

// 修改点3: 点击“我要评论”时，重置回复状态并滚动
const resetAndScrollToComment = () => {
  cancelReply() // 退出回复模式
  scrollToCommentBox()
}

const scrollToCommentBox = () => {
  document.getElementById('commentInputBox').scrollIntoView({ behavior: 'smooth' })
}

// === 修改后的 submitComment ===
const submitComment = async () => {
  if (!userInfo.value.id) return ElMessage.warning('请先登录')
  if (!myComment.value.trim()) return ElMessage.warning('请输入内容')

  submitting.value = true
  try {
    const isReply = !!replyTarget.value

    const payload = {
      bookId: bookId,
      userId: userInfo.value.id,
      content: myComment.value,
      // === 核心修改：如果是回复，显式传 0 ===
      rating: isReply ? 0 : myRating.value,
      parentId: isReply ? (replyParent.value ? replyParent.value.id : 0) : 0,
      replyUserId: isReply ? (replyTarget.value ? replyTarget.value.userId : null) : null
    }

    await axios.post('/api/comment/add', payload)

    ElMessage.success(isReply ? '回复成功' : '发表成功')
    myComment.value = ''
    cancelReply()
    loadComments()

    // 只有顶级评论（带评分）才需要刷新书籍分值
    if (!isReply) {
      loadBookDetail()
    }
  } catch (e) {
    ElMessage.error('发表失败')
  } finally {
    submitting.value = false
  }
}

const toggleLike = async (item) => {
  if (!userInfo.value.id) return ElMessage.warning('请先登录')
  try {
    const res = await axios.post('/api/comment/like', {
      commentId: item.id,
      userId: userInfo.value.id
    })
    if (res.data.code === '200') {
      item.likeCount = res.data.data.likeCount
      item.isLiked = res.data.data.isLiked
    }
  } catch (e) {}
}

const deleteComment = async (id) => {
  ElMessageBox.confirm('确认删除吗？', '提示', { type: 'warning' }).then(async () => {
    await axios.delete(`/api/comment/${id}`)
    ElMessage.success('已删除')
    loadComments()
  })
}

const addToShelf = async () => {
  if (!userInfo.value.id) return ElMessage.warning('请先登录')
  await axios.post('/api/bookshelf/add', { userId: userInfo.value.id, bookId: bookId })
  ElMessage.success('加入成功')
  inShelf.value = true
}

const startReading = () => router.push(`/read/${bookId}`)
const goBack = () => router.back()
const formatTime = (str) => str ? str.replace('T', ' ').substring(0, 16) : ''
const goToUserProfile = (userId) => {
  if (userId) router.push(`/user/${userId}`)
}
</script>

<style scoped>
.book-detail-container { max-width: 960px; margin: 24px auto; padding: 0 24px; }

/* === 书籍卡片 === */
.info-card { border-radius: 6px; margin-bottom: 28px; border: 1px solid #e8e0d6; }
.info-card :deep(.el-card__body) { padding: 28px 32px; }
.info-wrapper { display: flex; gap: 32px; }
.cover-box { width: 180px; flex-shrink: 0; }
.book-cover { width: 100%; border-radius: 4px; box-shadow: 0 2px 8px rgba(60, 40, 20, 0.1); }
.details-box { flex: 1; }
.book-title { font-size: 24px; color: #2e2520; margin: 0 0 12px 0; font-family: 'Noto Serif SC', serif; font-weight: 600; }
.book-meta { display: flex; gap: 18px; color: #7a6e63; font-size: 13px; align-items: center; margin-bottom: 18px; }
.meta-item { display: flex; align-items: center; gap: 5px; }
.book-desc {
  color: #5a5048;
  line-height: 1.7;
  margin-bottom: 24px;
  background: #faf5ed;
  padding: 16px 18px;
  border-radius: 4px;
  border-left: 3px solid #d4c4a8;
}
.book-desc h3 { font-size: 14px; margin: 0 0 8px; color: #6b5e53; font-weight: 600; }
.book-desc p { margin: 0; font-size: 14px; }
.action-btns { display: flex; gap: 12px; }
.action-btns :deep(.el-button--primary) { background-color: #5a4435; border-color: #5a4435; }
.action-btns :deep(.el-button--primary:hover) { background-color: #6b5040; border-color: #6b5040; }
.action-btns :deep(.el-button--warning) { background-color: #b8944e; border-color: #b8944e; }
.action-btns :deep(.el-button--warning:hover) { background-color: #c09a5c; border-color: #c09a5c; }

/* === 评论区 === */
.comment-section {
  background: #fffdf9;
  padding: 28px 32px;
  border-radius: 6px;
  border: 1px solid #e8e0d6;
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
  border-bottom: 1px solid #ede7de;
  padding-bottom: 12px;
}
.section-header h3 {
  font-family: 'Noto Serif SC', serif;
  color: #3d3632;
  font-weight: 600;
}

/* === 评论列表 === */
.comment-list { margin-bottom: 36px; }
.comment-item { display: flex; gap: 14px; padding: 18px 0; border-bottom: 1px solid #f0ece4; }
.comment-content-wrapper { flex: 1; }
.comment-header { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
.nickname { font-weight: 600; font-size: 14px; color: #4a3828; }
.time { color: #b5a99c; font-size: 12px; margin-left: auto; }
.text { font-size: 14px; color: #3d3632; line-height: 1.7; margin-bottom: 10px; }

/* === 操作栏 === */
.comment-actions { display: flex; gap: 18px; font-size: 13px; color: #9b8e82; }
.action-item { cursor: pointer; display: flex; align-items: center; gap: 4px; transition: color 0.2s; }
.action-item:hover { color: #6b5040; }
.action-item.is-active { color: #a34040; }
.delete-btn:hover { color: #a34040; }

/* === 楼中楼 === */
.sub-comments { margin-top: 14px; background: #faf5ed; padding: 14px; border-radius: 4px; }
.sub-item { display: flex; gap: 10px; margin-bottom: 14px; }
.sub-item:last-child { margin-bottom: 0; }
.sub-content { flex: 1; }
.sub-header { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; font-size: 13px; }
.sub-nickname { font-weight: 600; color: #5a5048; }
.reply-tag { color: #9b8e82; }
.at-name { color: #8b6f52; cursor: pointer; }
.sub-time { font-size: 12px; color: #c4b9ab; margin-left: auto; }
.sub-text { font-size: 14px; color: #4a4440; margin-bottom: 6px; }
.sub-actions { font-size: 12px; }

/* === 底部输入框 === */
.post-comment-box { background: #fffdf9; padding: 18px; border-radius: 4px; border: 1px solid #e8e0d6; }
.reply-indicator { background: #f5f0e8; color: #8b6f52; padding: 5px 10px; border-radius: 3px; display: inline-flex; align-items: center; gap: 5px; margin-bottom: 10px; font-size: 13px; }
.close-reply { cursor: pointer; }
.input-row { display: flex; gap: 14px; flex-direction: column; }
.rate-select { display: flex; align-items: center; gap: 10px; font-size: 14px; color: #6b5e53; }
.submit-row { text-align: right; margin-top: 14px; }

/* === 可点击用户 === */
.clickable-user { cursor: pointer; transition: opacity 0.15s; }
.clickable-user:hover { opacity: 0.75; }
</style>