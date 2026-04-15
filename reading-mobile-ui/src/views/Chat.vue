<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showSuccessToast, showFailToast } from 'vant'
import axios from 'axios'

const route = useRoute()
const router = useRouter()
const friendId = Number(route.params.friendId)
const defaultAvatar = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
const defaultCover = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII='

const PARAGRAPH_SHARE_PREFIX = '__PARAGRAPH_SHARE__'
const AUDIO_SHARE_PREFIX = '__AUDIO_SHARE__'

const userInfo = ref({})
const friendInfo = ref({})
const messages = ref([])
const inputMessage = ref('')
const messagesArea = ref(null)
const shareDialogVisible = ref(false)
const myShelf = ref([])
const selectedBookId = ref(null)
const shareMsg = ref('')

let pollTimer = null

onMounted(async () => {
  const userStr = localStorage.getItem('user')
  if (!userStr) {
    showToast('请先登录')
    router.push('/login')
    return
  }
  userInfo.value = JSON.parse(userStr)

  try {
    const res = await axios.get(`/api/sysUser/profile/${friendId}`)
    if (res.data.code === '200') {
      friendInfo.value = res.data.data
    }
  } catch (error) {
    console.error(error)
  }

  await loadMessages()
  await markAsRead()
  pollTimer = setInterval(loadMessages, 3000)
})

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
  }
})

const loadMessages = async () => {
  try {
    const res = await axios.get('/api/chat/history', {
      params: { userId: userInfo.value.id, friendId }
    })
    const nextMessages = res.data.data || []
    const changed =
      nextMessages.length !== messages.value.length ||
      nextMessages[nextMessages.length - 1]?.id !== messages.value[messages.value.length - 1]?.id

    if (changed) {
      messages.value = nextMessages
      await nextTick()
      scrollToBottom()
      await markAsRead()
    }
  } catch (error) {
    console.error(error)
  }
}

const markAsRead = async () => {
  try {
    await axios.post('/api/chat/read', null, {
      params: { userId: userInfo.value.id, senderId: friendId }
    })
  } catch (error) {
    console.error(error)
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
  } catch (error) {
    showFailToast('发送失败')
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
  } catch (error) {
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
    showSuccessToast('分享成功')
    shareDialogVisible.value = false
  } catch (error) {
    showFailToast('分享失败')
  }
}

const parseShareContent = (content, prefix) => {
  if (!content || !content.startsWith(prefix)) return null
  try {
    return JSON.parse(content.slice(prefix.length))
  } catch (error) {
    return null
  }
}

const getParagraphShare = (msg) => parseShareContent(msg.content, PARAGRAPH_SHARE_PREFIX)
const getAudioShare = (msg) => parseShareContent(msg.content, AUDIO_SHARE_PREFIX)

const formatSharePosition = (share) => {
  if (!share) return '来自聊天分享'
  const parts = []
  if (share.chapterIndex !== null && share.chapterIndex !== undefined) {
    parts.push(`第 ${share.chapterIndex + 1} 章`)
  }
  if (share.paragraphIndex !== null && share.paragraphIndex !== undefined) {
    parts.push(`第 ${share.paragraphIndex + 1} 段`)
  }
  if (parts.length > 0) return parts.join(' · ')
  if (share.sourceType === 'chapter') return '整章听书'
  if (share.sourceType === 'paragraph') return '段落朗读'
  return '朗读音频'
}

const openSharedParagraph = (msg) => {
  const share = getParagraphShare(msg)
  if (!share?.bookId) return
  router.push({
    path: `/read/${share.bookId}`,
    query: {
      chapterIndex: share.chapterIndex,
      paragraphIndex: share.paragraphIndex
    }
  })
}

const openSharedAudio = (msg) => {
  const share = getAudioShare(msg)
  if (!share?.bookId) return
  router.push({
    path: `/read/${share.bookId}`,
    query: {
      chapterIndex: share.chapterIndex ?? undefined,
      paragraphIndex: share.paragraphIndex ?? undefined
    }
  })
}

const formatTime = (timeStr) => {
  if (!timeStr) return ''
  const d = new Date(timeStr)
  const pad = (num) => String(num).padStart(2, '0')
  const today = new Date()
  if (d.toDateString() === today.toDateString()) {
    return `${pad(d.getHours())}:${pad(d.getMinutes())}`
  }
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<template>
  <div class="chat-page">
    <van-nav-bar :title="friendInfo.nickname || '聊天'" left-arrow @click-left="$router.push('/friends')">
      <template #right>
        <van-icon name="user-o" size="20" @click="$router.push(`/user/${friendId}`)" />
      </template>
    </van-nav-bar>

    <div class="messages-area" ref="messagesArea">
      <van-empty v-if="messages.length === 0" description="开始聊天吧" image="search" />
      <div
        v-for="msg in messages"
        :key="msg.id"
        :class="['msg-row', msg.senderId === userInfo.id ? 'mine' : 'theirs']"
      >
        <van-image
          v-if="msg.senderId !== userInfo.id"
          round
          width="32"
          height="32"
          :src="friendInfo.avatar || defaultAvatar"
          class="msg-avatar"
        />
        <div class="bubble-wrap">
          <div v-if="getParagraphShare(msg)" class="paragraph-share-card" @click="openSharedParagraph(msg)">
            <div class="share-card-label">段落分享</div>
            <div class="share-card-title">《{{ getParagraphShare(msg).bookTitle || '当前书籍' }}》</div>
            <div class="share-card-meta">{{ formatSharePosition(getParagraphShare(msg)) }}</div>
            <div class="share-card-quote">“{{ getParagraphShare(msg).quote }}”</div>
            <div v-if="getParagraphShare(msg).message" class="share-card-note">
              留言：{{ getParagraphShare(msg).message }}
            </div>
            <div class="share-card-link">点击前往阅读页</div>
          </div>
          <div v-else-if="getAudioShare(msg)" class="audio-share-card" @click="openSharedAudio(msg)">
            <div class="share-card-label">音频分享</div>
            <div class="share-card-title">{{ getAudioShare(msg).title || '朗读音频' }}</div>
            <div class="share-card-meta">{{ formatSharePosition(getAudioShare(msg)) }}</div>
            <div v-if="getAudioShare(msg).message" class="share-card-note">
              留言：{{ getAudioShare(msg).message }}
            </div>
            <audio
              class="audio-inline-player"
              :src="getAudioShare(msg).audioUrl"
              controls
              preload="metadata"
              @click.stop
            />
            <div v-if="getAudioShare(msg).bookId" class="share-card-link">点击跳转到阅读页</div>
          </div>
          <div v-else class="bubble-text">{{ msg.content }}</div>
          <div class="bubble-time">{{ formatTime(msg.createTime) }}</div>
        </div>
        <van-image
          v-if="msg.senderId === userInfo.id"
          round
          width="32"
          height="32"
          :src="userInfo.avatar || defaultAvatar"
          class="msg-avatar"
        />
      </div>
    </div>

    <div class="chat-input-bar">
      <van-icon name="photo-o" size="24" color="#8b6f52" @click="openShareDialog" />
      <van-field v-model="inputMessage" placeholder="输入消息..." @keypress.enter="sendMessage" class="msg-field" />
      <van-button type="primary" size="small" round :disabled="!inputMessage.trim()" @click="sendMessage">
        发送
      </van-button>
    </div>

    <van-popup v-model:show="shareDialogVisible" position="bottom" round :style="{ maxHeight: '60%' }">
      <div class="share-popup">
        <h3>分享书籍</h3>
        <van-empty v-if="myShelf.length === 0" description="书架为空" />
        <div v-else class="share-grid">
          <div
            v-for="book in myShelf"
            :key="book.bookId"
            :class="['share-card', selectedBookId === book.bookId ? 'sel' : '']"
            @click="selectedBookId = book.bookId"
          >
            <img :src="book.coverUrl || defaultCover" class="s-cover"  alt=""/>
            <div class="s-name">{{ book.bookName }}</div>
          </div>
        </div>
        <van-field v-model="shareMsg" placeholder="附言（可选）" style="margin-top: 10px;" />
        <van-button
          type="primary"
          block
          round
          style="margin-top: 10px;"
          :disabled="!selectedBookId"
          @click="confirmShare"
        >
          分享
        </van-button>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-bg);
}

.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.msg-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  max-width: 82%;
}

.msg-avatar {
  flex-shrink: 0;
}

.bubble-text {
  padding: 10px 14px;
  border-radius: 14px;
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
  background: var(--color-bg-card);
  color: var(--color-text);
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 4px rgba(60, 40, 20, 0.05);
}

.bubble-time {
  font-size: 10px;
  color: var(--color-text-muted);
  margin-top: 4px;
}

.mine .bubble-time {
  text-align: right;
}

.paragraph-share-card,
.audio-share-card {
  min-width: 220px;
  max-width: 300px;
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid rgba(139, 111, 82, 0.22);
  background: rgba(245, 240, 232, 0.95);
}

.mine .paragraph-share-card,
.mine .audio-share-card {
  background: rgba(255, 255, 255, 0.18);
  border-color: rgba(255, 255, 255, 0.24);
}

.share-card-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.share-card-title {
  margin-top: 4px;
  font-size: 15px;
  font-weight: 700;
}

.share-card-meta,
.share-card-link {
  margin-top: 6px;
  font-size: 12px;
  opacity: 0.82;
}

.share-card-quote {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.share-card-note {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.6;
}

.audio-inline-player {
  width: 100%;
  margin-top: 10px;
}

.chat-input-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px calc(10px + var(--safe-bottom));
  border-top: 1px solid var(--color-border-light);
  background: var(--color-bg-card);
  flex-shrink: 0;
}

.msg-field {
  flex: 1;
}

.share-popup {
  padding: 20px;
}

.share-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(80px, 1fr));
  gap: 8px;
  max-height: 200px;
  overflow-y: auto;
}

.share-card {
  border: 2px solid transparent;
  border-radius: 6px;
  padding: 4px;
  text-align: center;
  cursor: pointer;
}

.s-cover {
  width: 100%;
  height: 80px;
  object-fit: cover;
  border-radius: 4px;
}

.s-name {
  font-size: 11px;
  margin-top: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
