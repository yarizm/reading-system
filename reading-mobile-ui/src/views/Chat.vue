<script setup>
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showFailToast, showSuccessToast, showToast } from 'vant'
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
  const user = localStorage.getItem('user')
  if (!user) {
    showToast('请先登录')
    router.push('/login')
    return
  }
  userInfo.value = JSON.parse(user)

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
  if (pollTimer) clearInterval(pollTimer)
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
    showSuccessToast('图书分享成功')
    shareDialogVisible.value = false
  } catch (error) {
    showFailToast('图书分享失败')
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

const getParagraphShare = (message) => parseShareContent(message.content, PARAGRAPH_SHARE_PREFIX)
const getAudioShare = (message) => parseShareContent(message.content, AUDIO_SHARE_PREFIX)

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

const openSharedParagraph = (message) => {
  const share = getParagraphShare(message)
  if (!share?.bookId) return
  router.push({
    path: `/read/${share.bookId}`,
    query: {
      chapterIndex: share.chapterIndex,
      paragraphIndex: share.paragraphIndex
    }
  })
}

const openSharedAudio = (message) => {
  const share = getAudioShare(message)
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
  const date = new Date(timeStr)
  const pad = (value) => String(value).padStart(2, '0')
  const today = new Date()
  if (date.toDateString() === today.toDateString()) {
    return `${pad(date.getHours())}:${pad(date.getMinutes())}`
  }
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}
</script>

<template>
  <div class="chat-page">
    <van-nav-bar :title="friendInfo.nickname || '聊天'" left-arrow @click-left="$router.push('/friends')">
      <template #right>
        <van-icon name="user-o" size="20" @click="$router.push(`/user/${friendId}`)" />
      </template>
    </van-nav-bar>

    <section class="chat-head">
      <div class="chat-title">{{ friendInfo.nickname || '这位书友' }}</div>
      <div class="chat-subtitle">分享阅读想法、段落摘录和听书音频都会显示在这里。</div>
    </section>

    <div class="messages-area" ref="messagesArea">
      <van-empty v-if="messages.length === 0" description="开始聊聊最近在看的书吧" image="search" />

      <div
        v-for="message in messages"
        :key="message.id"
        :class="['msg-row', message.senderId === userInfo.id ? 'mine' : 'theirs']"
      >
        <van-image
          v-if="message.senderId !== userInfo.id"
          round
          width="34"
          height="34"
          :src="friendInfo.avatar || defaultAvatar"
          class="msg-avatar"
        />

        <div class="bubble-wrap">
          <div v-if="getParagraphShare(message)" class="paragraph-share-card" @click="openSharedParagraph(message)">
            <div class="share-card-label">段落分享</div>
            <div class="share-card-title">《{{ getParagraphShare(message).bookTitle || '当前书籍' }}》</div>
            <div class="share-card-meta">{{ formatSharePosition(getParagraphShare(message)) }}</div>
            <div class="share-card-quote">“{{ getParagraphShare(message).quote }}”</div>
            <div v-if="getParagraphShare(message).message" class="share-card-note">
              留言：{{ getParagraphShare(message).message }}
            </div>
            <div class="share-card-link">点击跳转到阅读页</div>
          </div>

          <div v-else-if="getAudioShare(message)" class="audio-share-card" @click="openSharedAudio(message)">
            <div class="share-card-label">音频分享</div>
            <div class="share-card-title">{{ getAudioShare(message).title || '朗读音频' }}</div>
            <div class="share-card-meta">{{ formatSharePosition(getAudioShare(message)) }}</div>
            <div v-if="getAudioShare(message).message" class="share-card-note">
              留言：{{ getAudioShare(message).message }}
            </div>
            <audio
              class="audio-inline-player"
              :src="getAudioShare(message).audioUrl"
              controls
              preload="metadata"
              @click.stop
            />
            <div v-if="getAudioShare(message).bookId" class="share-card-link">点击返回对应阅读位置</div>
          </div>

          <div v-else class="bubble-text">{{ message.content }}</div>
          <div class="bubble-time">{{ formatTime(message.createTime) }}</div>
        </div>

        <van-image
          v-if="message.senderId === userInfo.id"
          round
          width="34"
          height="34"
          :src="userInfo.avatar || defaultAvatar"
          class="msg-avatar"
        />
      </div>
    </div>

    <div class="chat-input-bar">
      <van-icon name="photo-o" size="24" color="#8b6f52" @click="openShareDialog" />
      <van-field
        v-model="inputMessage"
        placeholder="输入消息..."
        class="msg-field"
        @keypress.enter="sendMessage"
      />
      <van-button type="primary" size="small" round :disabled="!inputMessage.trim()" @click="sendMessage">
        发送
      </van-button>
    </div>

    <van-popup v-model:show="shareDialogVisible" position="bottom" round :style="{ maxHeight: '66%' }">
      <div class="share-popup">
        <div class="popup-title">分享图书</div>
        <div class="popup-tip">从你的书架中挑一本书发给这位好友。</div>
        <van-empty v-if="myShelf.length === 0" description="你的书架还是空的" />
        <div v-else class="share-grid">
          <div
            v-for="book in myShelf"
            :key="book.bookId"
            :class="['share-card', { selected: selectedBookId === book.bookId }]"
            @click="selectedBookId = book.bookId"
          >
            <img :src="book.coverUrl || defaultCover" class="s-cover" alt="" />
            <div class="s-name">{{ book.bookName }}</div>
          </div>
        </div>
        <van-field v-model="shareMsg" placeholder="附上一句话，可不填" class="share-field" />
        <van-button type="primary" block round :disabled="!selectedBookId" @click="confirmShare">确认分享</van-button>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background:
    radial-gradient(circle at top right, rgba(214, 191, 165, 0.2), transparent 26%),
    linear-gradient(180deg, #f8f2ea 0%, #f5eee4 42%, #faf6f0 100%);
}

.chat-head {
  margin: 14px 16px 0;
  padding: 16px 18px;
  border-radius: 20px;
  background: rgba(255, 252, 247, 0.94);
  box-shadow: 0 14px 30px rgba(93, 67, 43, 0.07);
}

.chat-title {
  font-family: var(--font-serif), serif;
  font-size: 22px;
  color: #3d2c1f;
}

.chat-subtitle {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.65;
  color: #8a725d;
}

.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.msg-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  max-width: 88%;
}

.mine {
  align-self: flex-end;
}

.theirs {
  align-self: flex-start;
}

.msg-avatar {
  flex-shrink: 0;
}

.bubble-wrap {
  max-width: 100%;
}

.bubble-text {
  padding: 12px 14px;
  border-radius: 18px;
  font-size: 14px;
  line-height: 1.65;
  word-break: break-word;
}

.mine .bubble-text {
  background: linear-gradient(135deg, #8b6f52, #a98b69);
  color: #fff;
  border-bottom-right-radius: 6px;
}

.theirs .bubble-text {
  background: rgba(255, 252, 247, 0.95);
  color: #4d392b;
  border-bottom-left-radius: 6px;
  box-shadow: 0 8px 22px rgba(93, 67, 43, 0.06);
}

.bubble-time {
  margin-top: 6px;
  font-size: 10px;
  color: #9a826c;
}

.mine .bubble-time {
  text-align: right;
}

.paragraph-share-card,
.audio-share-card {
  min-width: 220px;
  max-width: 300px;
  padding: 13px 14px;
  border-radius: 18px;
  border: 1px solid rgba(139, 111, 82, 0.18);
  background: rgba(255, 250, 243, 0.95);
  box-shadow: 0 10px 26px rgba(93, 67, 43, 0.06);
}

.mine .paragraph-share-card,
.mine .audio-share-card {
  background: rgba(255, 255, 255, 0.18);
  border-color: rgba(255, 255, 255, 0.26);
  color: #fff;
}

.share-card-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.share-card-title {
  margin-top: 4px;
  font-size: 15px;
  font-weight: 700;
}

.share-card-meta,
.share-card-link {
  margin-top: 7px;
  font-size: 12px;
  opacity: 0.82;
}

.share-card-quote,
.share-card-note {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.7;
  word-break: break-word;
  white-space: pre-wrap;
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
  border-top: 1px solid rgba(143, 117, 87, 0.14);
  background: rgba(255, 252, 247, 0.96);
  backdrop-filter: blur(12px);
}

.msg-field {
  flex: 1;
  border-radius: 999px;
  background: rgba(247, 242, 235, 0.92);
}

.share-popup {
  padding: 22px 18px 28px;
}

.popup-title {
  font-family: var(--font-serif), serif;
  font-size: 22px;
  color: #3d2c1f;
}

.popup-tip {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.65;
  color: #8a725d;
}

.share-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(86px, 1fr));
  gap: 12px;
  margin: 16px 0 14px;
  max-height: 260px;
  overflow-y: auto;
}

.share-card {
  padding: 6px;
  border: 2px solid transparent;
  border-radius: 16px;
  background: rgba(250, 245, 238, 0.88);
  transition: border-color 0.2s ease, transform 0.2s ease;
}

.share-card.selected {
  border-color: #9c7a58;
  transform: translateY(-2px);
}

.s-cover {
  width: 100%;
  height: 100px;
  object-fit: cover;
  border-radius: 12px;
}

.s-name {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.45;
  color: #4f3b2d;
  text-align: center;
}

.share-field {
  margin-bottom: 14px;
  border-radius: 18px;
  background: rgba(247, 242, 235, 0.9);
}
</style>
