<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'
import axios from 'axios'

const router = useRouter()
const defaultAvatar = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
const defaultCover = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII='

const userInfo = ref({})
const searchKeyword = ref('')
const searchResults = ref([])
const searched = ref(false)
const friends = ref([])
const pendingRequests = ref([])
const receivedShares = ref([])
const friendUnreadMap = ref({})

const shareDialogVisible = ref(false)
const shareTargetUserId = ref(null)
const myShelf = ref([])
const selectedBookId = ref(null)
const shareMessage = ref('')

let ws = null

onMounted(() => {
  const user = localStorage.getItem('user')
  if (!user) {
    showToast('请先登录')
    router.push('/login')
    return
  }
  userInfo.value = JSON.parse(user)
  loadFriends()
  loadPendingRequests()
  loadReceivedShares()
  loadFriendUnreadCounts()
  connectWs()
})

onUnmounted(() => {
  if (ws) ws.close()
})

const connectWs = () => {
  if (!userInfo.value.id) return
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  ws = new WebSocket(`${protocol}//${location.host}/ws/notification?userId=${userInfo.value.id}`)
  ws.onmessage = (event) => {
    try {
      const msg = JSON.parse(event.data)
      if (msg.type === 'chat') {
        loadFriendUnreadCounts()
      } else if (msg.type === 'friend_request') {
        loadPendingRequests()
        showToast(`${msg.data?.nickname || '一位用户'} 想添加你为好友`)
      } else if (msg.type === 'book_share') {
        loadReceivedShares()
        showToast('收到新的图书分享')
      }
    } catch (error) {
      console.error(error)
    }
  }
  ws.onclose = () => {
    if (userInfo.value.id) {
      setTimeout(connectWs, 5000)
    }
  }
}

const searchUsers = async () => {
  if (!searchKeyword.value.trim()) return
  searched.value = true
  const res = await axios.get('/api/friend/search', {
    params: {
      keyword: searchKeyword.value,
      excludeUserId: userInfo.value.id
    }
  })
  searchResults.value = res.data.data || []
}

const sendRequest = async (friendId) => {
  const res = await axios.post('/api/friend/request', {
    userId: userInfo.value.id,
    friendId
  })
  if (res.data.code === '200') {
    showSuccessToast('好友请求已发送')
  } else {
    showToast(res.data.msg)
  }
}

const loadFriends = async () => {
  const res = await axios.get(`/api/friend/list/${userInfo.value.id}`)
  friends.value = res.data.data || []
}

const loadPendingRequests = async () => {
  const res = await axios.get(`/api/friend/pending/${userInfo.value.id}`)
  pendingRequests.value = res.data.data || []
}

const loadReceivedShares = async () => {
  const res = await axios.get(`/api/bookShare/received/${userInfo.value.id}`)
  receivedShares.value = res.data.data || []
}

const loadFriendUnreadCounts = async () => {
  try {
    const res = await axios.get(`/api/chat/conversations/${userInfo.value.id}`)
    const nextMap = {}
    ;(res.data.data || []).forEach((item) => {
      if (item.unreadCount > 0) {
        nextMap[item.contactId] = item.unreadCount
      }
    })
    friendUnreadMap.value = nextMap
  } catch (error) {
    console.error(error)
  }
}

const getUnread = (id) => friendUnreadMap.value[id] || 0

const acceptRequest = async (id) => {
  await axios.post(`/api/friend/accept/${id}`)
  showSuccessToast('已通过好友请求')
  loadPendingRequests()
  loadFriends()
}

const rejectRequest = async (id) => {
  await axios.post(`/api/friend/reject/${id}`)
  showToast('已拒绝请求')
  loadPendingRequests()
}

const deleteFriend = async (id) => {
  await showConfirmDialog({ message: '确定删除这位好友吗？' })
  await axios.delete(`/api/friend/${id}`)
  showSuccessToast('好友已删除')
  loadFriends()
}

const deleteShare = async (id) => {
  await axios.delete(`/api/bookShare/${id}`)
  showSuccessToast('分享记录已删除')
  loadReceivedShares()
}

const goChat = (id) => {
  router.push(`/chat/${id}`)
}

const goToBook = async (share) => {
  if (share.isRead === 0) {
    await axios.post(`/api/bookShare/read/${share.shareId}`)
  }
  router.push(`/book/${share.bookId}`)
}

const formatDate = (dateTime) => {
  if (!dateTime) return '刚刚'
  return String(dateTime).split('T')[0]
}

const openShareDialog = async (friendId) => {
  shareTargetUserId.value = friendId
  selectedBookId.value = null
  shareMessage.value = ''
  try {
    const res = await axios.get(`/api/bookshelf/list/${userInfo.value.id}`)
    myShelf.value = res.data.data || []
  } catch (error) {
    myShelf.value = []
  }
  shareDialogVisible.value = true
}

const confirmShare = async () => {
  const res = await axios.post('/api/bookShare/send', {
    senderId: userInfo.value.id,
    receiverId: shareTargetUserId.value,
    bookId: selectedBookId.value,
    message: shareMessage.value
  })
  if (res.data.code === '200') {
    showSuccessToast('图书分享成功')
    shareDialogVisible.value = false
  }
}
</script>

<template>
  <div class="friends-page">
    <section class="hero-card">
      <div class="hero-copy">
        <div class="hero-eyebrow">Friend Hub</div>
        <h1 class="hero-title">好友中心</h1>
        <p class="hero-desc">把聊天、好友申请和图书分享放进同一个入口里，日常交流会轻松很多。</p>
      </div>
      <div class="hero-badge">{{ friends.length }} 位好友</div>
    </section>

    <section class="search-card">
      <div class="section-title">搜索用户</div>
      <div class="section-tip">按用户名或昵称查找新的书友。</div>
      <van-search
        v-model="searchKeyword"
        placeholder="搜索用户..."
        shape="round"
        background="transparent"
        @search="searchUsers"
      />
      <div v-if="searchResults.length > 0" class="search-list">
        <article v-for="user in searchResults" :key="user.id" class="user-card">
          <div class="user-main" @click="$router.push(`/user/${user.id}`)">
            <van-image round width="40" height="40" :src="user.avatar || defaultAvatar" />
            <div class="user-copy">
              <div class="user-name">{{ user.nickname || user.username }}</div>
              <div class="user-sub">@{{ user.username }}</div>
            </div>
          </div>
          <van-button size="mini" type="primary" round @click.stop="sendRequest(user.id)">添加</van-button>
        </article>
      </div>
      <van-empty v-if="searched && searchResults.length === 0" description="没有找到匹配的用户" image="search" />
    </section>

    <section v-if="pendingRequests.length > 0" class="content-card">
      <div class="section-title">好友申请</div>
      <div class="section-tip">这些用户正在等待你的处理。</div>
      <div class="request-list">
        <article v-for="request in pendingRequests" :key="request.requestId" class="request-card">
          <div class="user-main" @click="$router.push(`/user/${request.fromUserId}`)">
            <van-image round width="40" height="40" :src="request.avatar || defaultAvatar" />
            <div class="user-copy">
              <div class="user-name">{{ request.nickname || request.username }}</div>
              <div class="user-sub">{{ formatDate(request.requestTime) }} 发起申请</div>
            </div>
          </div>
          <div class="request-actions">
            <van-button size="mini" type="success" round @click="acceptRequest(request.requestId)">接受</van-button>
            <van-button size="mini" round @click="rejectRequest(request.requestId)">拒绝</van-button>
          </div>
        </article>
      </div>
    </section>

    <section class="content-card">
      <div class="section-title">我的好友</div>
      <div class="section-tip">点击头像可查看主页，点击按钮可直接聊天或分享图书。</div>
      <van-empty v-if="friends.length === 0" description="还没有好友，先从搜索开始吧" image="network" />
      <div v-else class="friend-list">
        <article v-for="friend in friends" :key="friend.friendUserId" class="friend-card">
          <div class="friend-main" @click="$router.push(`/user/${friend.friendUserId}`)">
            <van-badge :content="getUnread(friend.friendUserId) || ''" :max="99">
              <van-image round width="44" height="44" :src="friend.avatar || defaultAvatar" />
            </van-badge>
            <div class="user-copy">
              <div class="user-name">{{ friend.nickname || friend.username }}</div>
              <div class="user-sub">成为好友于 {{ formatDate(friend.friendSince) }}</div>
            </div>
          </div>
          <div class="friend-actions">
            <van-button size="mini" type="primary" plain round @click="goChat(friend.friendUserId)">聊天</van-button>
            <van-button size="mini" plain round @click="openShareDialog(friend.friendUserId)">分享</van-button>
            <van-button size="mini" plain round type="danger" @click="deleteFriend(friend.friendshipId)">删除</van-button>
          </div>
        </article>
      </div>
    </section>

    <section class="content-card">
      <div class="section-title">收到的图书分享</div>
      <div class="section-tip">别人推荐给你的书，点开就能继续看。</div>
      <van-empty v-if="receivedShares.length === 0" description="暂时还没有收到图书分享" image="search" />
      <div v-else class="share-list">
        <article v-for="share in receivedShares" :key="share.shareId" class="share-card" @click="goToBook(share)">
          <img :src="share.coverUrl || defaultCover" class="share-cover" alt="" />
          <div class="share-body">
            <div class="share-title">{{ share.bookTitle }}</div>
            <div class="share-meta">{{ share.senderNickname }} 分享于 {{ formatDate(share.shareTime) }}</div>
            <div v-if="share.shareMessage" class="share-message">“{{ share.shareMessage }}”</div>
          </div>
          <div class="share-side">
            <van-tag v-if="share.isRead === 0" type="danger" size="mini">新</van-tag>
            <van-icon name="delete-o" size="18" color="#d35645" @click.stop="deleteShare(share.shareId)" />
          </div>
        </article>
      </div>
    </section>

    <van-popup v-model:show="shareDialogVisible" position="bottom" round :style="{ maxHeight: '72%' }">
      <div class="popup-panel">
        <div class="popup-title">分享图书</div>
        <div class="popup-tip">从你的书架里挑一本发给好友，对方会在好友中心和聊天页同时看到。</div>
        <van-empty v-if="myShelf.length === 0" description="你的书架还是空的" />
        <div v-else class="popup-grid">
          <div
            v-for="book in myShelf"
            :key="book.bookId"
            :class="['popup-book-card', { selected: selectedBookId === book.bookId }]"
            @click="selectedBookId = book.bookId"
          >
            <img :src="book.coverUrl || defaultCover" class="popup-book-cover" alt="" />
            <div class="popup-book-name">{{ book.bookName }}</div>
          </div>
        </div>
        <van-field v-model="shareMessage" placeholder="附上一句话，可不填" maxlength="200" class="popup-field" />
        <van-button type="primary" block round :disabled="!selectedBookId" @click="confirmShare">确认分享</van-button>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.friends-page {
  min-height: 100vh;
  padding: 18px 16px calc(84px + var(--safe-bottom));
  background:
    radial-gradient(circle at top right, rgba(211, 193, 170, 0.24), transparent 32%),
    linear-gradient(180deg, #f8f2ea 0%, #f5eee4 42%, #faf6f0 100%);
}

.hero-card,
.search-card,
.content-card {
  padding: 18px;
  border-radius: 22px;
  background: rgba(255, 252, 247, 0.96);
  box-shadow: 0 18px 38px rgba(93, 67, 43, 0.08);
}

.hero-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
  background: linear-gradient(145deg, rgba(255, 250, 244, 0.98), rgba(246, 234, 220, 0.92));
}

.hero-eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.16em;
  color: #a17752;
  text-transform: uppercase;
}

.hero-title {
  margin: 8px 0 6px;
  font-family: var(--font-serif), serif;
  font-size: 26px;
  color: #3d2c1f;
}

.hero-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.75;
  color: #77604c;
}

.hero-badge {
  padding: 10px 12px;
  border-radius: 999px;
  background: rgba(143, 117, 87, 0.12);
  font-size: 12px;
  color: #6d5644;
  white-space: nowrap;
}

.search-card,
.content-card {
  margin-bottom: 16px;
}

.section-title {
  font-family: var(--font-serif), serif;
  font-size: 20px;
  color: #3d2c1f;
}

.section-tip {
  margin-top: 5px;
  margin-bottom: 12px;
  font-size: 12px;
  line-height: 1.65;
  color: #8a725d;
}

.search-list,
.request-list,
.friend-list,
.share-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.user-card,
.request-card,
.friend-card,
.share-card {
  padding: 14px;
  border-radius: 18px;
  background: rgba(250, 245, 238, 0.92);
  box-shadow: 0 10px 26px rgba(93, 67, 43, 0.05);
}

.user-card,
.request-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.user-main,
.friend-main {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.user-copy {
  min-width: 0;
}

.user-name {
  font-size: 15px;
  font-weight: 700;
  color: #3d2c1f;
}

.user-sub,
.share-meta {
  margin-top: 4px;
  font-size: 12px;
  color: #8a725d;
}

.request-actions,
.friend-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  flex-wrap: wrap;
}

.share-card {
  display: flex;
  gap: 12px;
  align-items: center;
}

.share-cover {
  width: 52px;
  height: 72px;
  object-fit: cover;
  border-radius: 10px;
  flex-shrink: 0;
}

.share-body {
  flex: 1;
  min-width: 0;
}

.share-title {
  font-size: 15px;
  font-weight: 700;
  color: #3d2c1f;
}

.share-message {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.7;
  color: #644d3c;
  font-style: italic;
}

.share-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}

.popup-panel {
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

.popup-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(88px, 1fr));
  gap: 12px;
  margin: 16px 0 14px;
  max-height: 280px;
  overflow-y: auto;
}

.popup-book-card {
  padding: 6px;
  border: 2px solid transparent;
  border-radius: 16px;
  background: rgba(250, 245, 238, 0.86);
  transition: border-color 0.2s ease, transform 0.2s ease;
}

.popup-book-card.selected {
  border-color: #9c7a58;
  transform: translateY(-2px);
}

.popup-book-cover {
  width: 100%;
  height: 112px;
  object-fit: cover;
  border-radius: 12px;
}

.popup-book-name {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.4;
  color: #4f3b2d;
  text-align: center;
}

.popup-field {
  margin-bottom: 14px;
  border-radius: 18px;
  background: rgba(247, 242, 235, 0.9);
}
</style>
