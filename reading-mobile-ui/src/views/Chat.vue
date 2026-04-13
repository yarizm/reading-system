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

const userInfo = ref({})
const friendInfo = ref({})
const messages = ref([])
const inputMessage = ref('')
const messagesArea = ref(null)
let pollTimer = null
const PARAGRAPH_SHARE_PREFIX = '__PARAGRAPH_SHARE__'

const shareDialogVisible = ref(false)
const myShelf = ref([])
const selectedBookId = ref(null)
const shareMsg = ref('')

onMounted(async () => {
  const u = localStorage.getItem('user')
  if (!u) { showToast('请先登录'); router.push('/login'); return }
  userInfo.value = JSON.parse(u)
  try {
    const r = await axios.get(`/api/sysUser/profile/${friendId}`)
    if (r.data.code === '200') friendInfo.value = r.data.data
  } catch (e) {}
  await loadMessages(); markAsRead()
  pollTimer = setInterval(async () => { await loadMessages() }, 3000)
})
onUnmounted(() => { if (pollTimer) clearInterval(pollTimer) })

const loadMessages = async () => {
  const r = await axios.get('/api/chat/history', { params: { userId: userInfo.value.id, friendId } })
  const msgs = r.data.data || []
  if (msgs.length !== messages.value.length) { messages.value = msgs; await nextTick(); scrollToBottom(); markAsRead() }
}
const markAsRead = async () => { try { await axios.post('/api/chat/read', null, { params: { userId: userInfo.value.id, senderId: friendId } }) } catch (e) {} }

const sendMessage = async () => {
  const text = inputMessage.value.trim()
  if (!text) return
  await axios.post('/api/chat/send', { senderId: userInfo.value.id, receiverId: friendId, content: text })
  inputMessage.value = ''; await loadMessages()
}

const scrollToBottom = () => { if (messagesArea.value) messagesArea.value.scrollTop = messagesArea.value.scrollHeight }

const openShareDialog = async () => {
  selectedBookId.value = null; shareMsg.value = ''
  try { const r = await axios.get(`/api/bookshelf/list/${userInfo.value.id}`); myShelf.value = r.data.data || [] } catch (e) { myShelf.value = [] }
  shareDialogVisible.value = true
}
const confirmShare = async () => {
  await axios.post('/api/bookShare/send', { senderId: userInfo.value.id, receiverId: friendId, bookId: selectedBookId.value, message: shareMsg.value })
  showSuccessToast('分享成功'); shareDialogVisible.value = false
}

const parseParagraphShare = (content) => {
  if (!content || !content.startsWith(PARAGRAPH_SHARE_PREFIX)) return null
  try {
    return JSON.parse(content.slice(PARAGRAPH_SHARE_PREFIX.length))
  } catch (e) {
    return null
  }
}

const getParagraphShare = (msg) => parseParagraphShare(msg.content)

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

const formatTime = (s) => {
  if (!s) return ''
  const d = new Date(s), pad = n => String(n).padStart(2, '0'), today = new Date()
  if (d.toDateString() === today.toDateString()) return `${pad(d.getHours())}:${pad(d.getMinutes())}`
  return `${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
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
      <van-empty v-if="messages.length === 0" description="开始聊天吧！" image="search" />
      <div v-for="msg in messages" :key="msg.id" :class="['msg-row', msg.senderId === userInfo.id ? 'mine' : 'theirs']">
        <van-image v-if="msg.senderId !== userInfo.id" round width="32" height="32" :src="friendInfo.avatar || defaultAvatar" class="msg-avatar" />
        <div class="bubble-wrap">
          <div v-if="getParagraphShare(msg)" class="paragraph-share-card" @click="openSharedParagraph(msg)">
            <div class="paragraph-share-label">段落分享</div>
            <div class="paragraph-share-title">《{{ getParagraphShare(msg).bookTitle || '当前书籍' }}》</div>
            <div class="paragraph-share-meta">第 {{ getParagraphShare(msg).chapterIndex + 1 }} 章 · 第 {{ getParagraphShare(msg).paragraphIndex + 1 }} 段</div>
            <div class="paragraph-share-quote">“{{ getParagraphShare(msg).quote }}”</div>
            <div v-if="getParagraphShare(msg).message" class="paragraph-share-note">留言：{{ getParagraphShare(msg).message }}</div>
            <div class="paragraph-share-link">点击前往阅读页</div>
          </div>
          <div v-else class="bubble-text">{{ msg.content }}</div>
          <div class="bubble-time">{{ formatTime(msg.createTime) }}</div>
        </div>
        <van-image v-if="msg.senderId === userInfo.id" round width="32" height="32" :src="userInfo.avatar || defaultAvatar" class="msg-avatar" />
      </div>
    </div>

    <div class="chat-input-bar">
      <van-icon name="photo-o" size="24" color="#8b6f52" @click="openShareDialog" />
      <van-field v-model="inputMessage" placeholder="输入消息..." @keypress.enter="sendMessage" class="msg-field" />
      <van-button type="primary" size="small" round :disabled="!inputMessage.trim()" @click="sendMessage">发送</van-button>
    </div>

    <!-- Share popup -->
    <van-popup v-model:show="shareDialogVisible" position="bottom" round :style="{ maxHeight: '60%' }">
      <div style="padding: 20px;">
        <h3>分享书籍</h3>
        <van-empty v-if="myShelf.length === 0" description="书架为空" />
        <div v-else class="share-grid">
          <div v-for="b in myShelf" :key="b.bookId" :class="['share-card', selectedBookId === b.bookId ? 'sel' : '']" @click="selectedBookId = b.bookId">
            <img :src="b.coverUrl || defaultCover" class="s-cover" /><div class="s-name">{{ b.bookName }}</div>
          </div>
        </div>
        <van-field v-model="shareMsg" placeholder="附言 (可选)" style="margin-top: 10px;" />
        <van-button type="primary" block round style="margin-top: 10px;" :disabled="!selectedBookId" @click="confirmShare">分享</van-button>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.chat-page { display: flex; flex-direction: column; height: 100vh; background: var(--color-bg); }

.messages-area { flex: 1; overflow-y: auto; padding: 16px; display: flex; flex-direction: column; gap: 12px; }

.msg-row { display: flex; align-items: flex-start; gap: 8px; max-width: 80%; }
.msg-row.mine { align-self: flex-end; flex-direction: row-reverse; }
.msg-row.theirs { align-self: flex-start; }
.msg-avatar { flex-shrink: 0; }

.bubble-wrap {}
.bubble-text {
  padding: 10px 14px; border-radius: 14px; font-size: 14px; line-height: 1.5; word-break: break-word;
}
.mine .bubble-text { background: linear-gradient(135deg, #8b6f52, #a68968); color: #fff; border-bottom-right-radius: 4px; }
.theirs .bubble-text { background: var(--color-bg-card); color: var(--color-text); border-bottom-left-radius: 4px; box-shadow: 0 1px 4px rgba(60,40,20,0.05); }
.bubble-time { font-size: 10px; color: var(--color-text-muted); margin-top: 4px; }
.mine .bubble-time { text-align: right; }
.paragraph-share-card {
  min-width: 220px;
  max-width: 300px;
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid rgba(139, 111, 82, 0.22);
  background: rgba(245, 240, 232, 0.95);
}
.mine .paragraph-share-card {
  background: rgba(255, 255, 255, 0.18);
  border-color: rgba(255, 255, 255, 0.24);
}
.paragraph-share-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.paragraph-share-title {
  margin-top: 4px;
  font-size: 15px;
  font-weight: 700;
}
.paragraph-share-meta,
.paragraph-share-link {
  margin-top: 6px;
  font-size: 12px;
  opacity: 0.82;
}
.paragraph-share-quote {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
.paragraph-share-note {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.6;
}

.chat-input-bar {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 12px calc(10px + var(--safe-bottom));
  border-top: 1px solid var(--color-border-light);
  background: var(--color-bg-card);
  flex-shrink: 0;
}
.msg-field { flex: 1; }

.share-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(80px, 1fr)); gap: 8px; max-height: 200px; overflow-y: auto; }
.share-card { border: 2px solid transparent; border-radius: 6px; padding: 4px; text-align: center; cursor: pointer; }
.share-card.sel { border-color: var(--color-primary); background: var(--color-bg-warm); }
.s-cover { width: 100%; height: 80px; object-fit: cover; border-radius: 4px; }
.s-name { font-size: 11px; margin-top: 3px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
