<template>
  <div class="chat-container">
    <!-- 顶部栏 -->
    <div class="chat-header">
      <el-button link @click="$router.push('/friends')">
        <el-icon><ArrowLeft /></el-icon> 返回好友
      </el-button>
      <div class="chat-friend-info">
        <el-avatar :size="32" :src="friendInfo.avatar || defaultAvatar" />
        <span class="chat-friend-name">{{ friendInfo.nickname || '加载中...' }}</span>
      </div>
      <el-button link @click="$router.push(`/user/${friendId}`)">
        查看资料 <el-icon><ArrowRight /></el-icon>
      </el-button>
    </div>

    <!-- 消息区域 -->
    <div class="messages-area" ref="messagesArea">
      <el-empty v-if="messages.length === 0" description="开始聊天吧！" :image-size="80" />
      <div v-for="msg in messages" :key="msg.id"
           :class="['message-bubble', msg.senderId === userInfo.id ? 'mine' : 'theirs']">
        <el-avatar v-if="msg.senderId !== userInfo.id" :size="32"
                   :src="friendInfo.avatar || defaultAvatar" class="msg-avatar" />
        <div class="bubble-content">
          <div class="bubble-text">{{ msg.content }}</div>
          <div class="bubble-time">{{ formatTime(msg.createTime) }}</div>
        </div>
        <el-avatar v-if="msg.senderId === userInfo.id" :size="32"
                   :src="userInfo.avatar || defaultAvatar" class="msg-avatar" />
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="chat-input-area">
      <el-button plain @click="openShareDialog" class="share-btn">
        <el-icon><Share /></el-icon>
      </el-button>
      <el-input v-model="inputMessage" placeholder="输入消息..." @keyup.enter="sendMessage"
                class="msg-input" maxlength="500" />
      <el-button type="primary" @click="sendMessage" :disabled="!inputMessage.trim()">
        发送
      </el-button>
    </div>

    <!-- 分享书籍弹窗 -->
    <el-dialog v-model="shareDialogVisible" title="分享书籍" width="480px">
      <el-empty v-if="myShelf.length === 0" description="书架为空" :image-size="60" />
      <div v-else class="share-book-grid">
        <div v-for="book in myShelf" :key="book.bookId" class="share-book-card"
             :class="{ selected: selectedBookId === book.bookId }"
             @click="selectedBookId = book.bookId">
          <img :src="book.coverUrl || defaultCover" class="share-cover" />
          <div class="share-name">{{ book.bookName }}</div>
        </div>
      </div>
      <el-input v-model="shareMsg" placeholder="附上一句话 (可选)" maxlength="200" style="margin-top: 14px;" />
      <template #footer>
        <el-button @click="shareDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmShare" :disabled="!selectedBookId">分享</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight, Share } from '@element-plus/icons-vue'
import axios from 'axios'

const route = useRoute()
const router = useRouter()
const friendId = Number(route.params.friendId)
const defaultAvatar = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
const defaultCover = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII='

const userInfo = ref({})
const friendInfo = ref({})
const messages = ref([])
const inputMessage = ref('')
const messagesArea = ref(null)
let pollTimer = null

// 分享书籍
const shareDialogVisible = ref(false)
const myShelf = ref([])
const selectedBookId = ref(null)
const shareMsg = ref('')

onMounted(async () => {
  const userStr = localStorage.getItem('user')
  if (!userStr) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  userInfo.value = JSON.parse(userStr)

  // 获取好友信息
  try {
    const res = await axios.get(`/api/sysUser/profile/${friendId}`)
    if (res.data.code === '200') {
      friendInfo.value = res.data.data
    }
  } catch (e) {
    console.error(e)
  }

  // 加载聊天记录 & 开始轮询
  await loadMessages()
  markAsRead()
  pollTimer = setInterval(async () => {
    await loadMessages()
  }, 3000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})

const loadMessages = async () => {
  try {
    const res = await axios.get('/api/chat/history', {
      params: { userId: userInfo.value.id, friendId }
    })
    const newMsgs = res.data.data || []
    if (newMsgs.length !== messages.value.length) {
      messages.value = newMsgs
      await nextTick()
      scrollToBottom()
      // 如果有新消息到来（对方发的），标记已读
      markAsRead()
    }
  } catch (e) {
    console.error(e)
  }
}

const markAsRead = async () => {
  try {
    await axios.post('/api/chat/read', null, {
      params: { userId: userInfo.value.id, senderId: friendId }
    })
  } catch (e) {
    // 忽略
  }
}

const sendMessage = async () => {
  const text = inputMessage.value.trim()
  if (!text) return
  try {
    await axios.post('/api/chat/send', {
      senderId: userInfo.value.id,
      receiverId: friendId,
      content: text
    })
    inputMessage.value = ''
    await loadMessages()
  } catch (e) {
    ElMessage.error('发送失败')
  }
}

const scrollToBottom = () => {
  if (messagesArea.value) {
    messagesArea.value.scrollTop = messagesArea.value.scrollHeight
  }
}

const openShareDialog = async () => {
  selectedBookId.value = null
  shareMsg.value = ''
  try {
    const res = await axios.get(`/api/bookshelf/list/${userInfo.value.id}`)
    myShelf.value = res.data.data || []
  } catch (e) {
    myShelf.value = []
  }
  shareDialogVisible.value = true
}

const confirmShare = async () => {
  try {
    await axios.post('/api/bookShare/send', {
      senderId: userInfo.value.id,
      receiverId: friendId,
      bookId: selectedBookId.value,
      message: shareMsg.value
    })
    ElMessage.success('书籍分享成功！')
    shareDialogVisible.value = false
  } catch (e) {
    ElMessage.error('分享失败')
  }
}

const formatTime = (timeStr) => {
  if (!timeStr) return ''
  const d = new Date(timeStr)
  const pad = (n) => String(n).padStart(2, '0')
  const today = new Date()
  if (d.toDateString() === today.toDateString()) {
    return `${pad(d.getHours())}:${pad(d.getMinutes())}`
  }
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<style scoped>
.chat-container {
  max-width: 800px;
  margin: 0 auto;
  height: calc(100vh - 40px);
  display: flex;
  flex-direction: column;
  padding: 20px 24px 0;
}

/* 顶部 */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid #e8e0d6;
  margin-bottom: 0;
  flex-shrink: 0;
}
.chat-friend-info {
  display: flex;
  align-items: center;
  gap: 10px;
}
.chat-friend-name {
  font-weight: 600;
  font-size: 16px;
  color: #2e2520;
  font-family: 'Noto Serif SC', serif;
}

/* 消息区域 */
.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 20px 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message-bubble {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  max-width: 75%;
}
.message-bubble.mine {
  align-self: flex-end;
  flex-direction: row-reverse;
}
.message-bubble.theirs {
  align-self: flex-start;
}

.msg-avatar {
  flex-shrink: 0;
  margin-top: 2px;
}

.bubble-content {
  display: flex;
  flex-direction: column;
}
.bubble-text {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
}
.mine .bubble-text {
  background: linear-gradient(135deg, #8b6f52, #a68968);
  color: #fff;
  border-bottom-right-radius: 4px;
}
.theirs .bubble-text {
  background: #f5f0e8;
  color: #3d3632;
  border-bottom-left-radius: 4px;
}
.bubble-time {
  font-size: 11px;
  color: #c4b9ab;
  margin-top: 4px;
}
.mine .bubble-time {
  text-align: right;
}

/* 输入区域 */
.chat-input-area {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 0;
  border-top: 1px solid #e8e0d6;
  flex-shrink: 0;
}
.msg-input {
  flex: 1;
}
.share-btn {
  flex-shrink: 0;
}

/* 分享弹窗 */
.share-book-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(85px, 1fr));
  gap: 10px;
  max-height: 280px;
  overflow-y: auto;
}
.share-book-card {
  cursor: pointer;
  border: 2px solid transparent;
  border-radius: 6px;
  padding: 4px;
  text-align: center;
  transition: border-color 0.2s;
}
.share-book-card:hover {
  background: #faf7f2;
}
.share-book-card.selected {
  border-color: #8b6f52;
  background: #faf5ed;
}
.share-cover {
  width: 100%;
  height: 90px;
  object-fit: cover;
  border-radius: 4px;
  background: #f0ece4;
  display: block;
}
.share-name {
  font-size: 11px;
  margin-top: 4px;
  color: #3d3632;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
