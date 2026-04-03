<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showSuccessToast, showFailToast, showConfirmDialog } from 'vant'
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

// Share dialog
const shareDialogVisible = ref(false)
const shareTargetUserId = ref(null)
const myShelf = ref([])
const selectedBookId = ref(null)
const shareMessage = ref('')

let ws = null

onMounted(() => {
  const u = localStorage.getItem('user')
  if (!u) { showToast('请先登录'); router.push('/login'); return }
  userInfo.value = JSON.parse(u)
  loadFriends(); loadPendingRequests(); loadReceivedShares(); loadFriendUnreadCounts()
  connectWs()
})
onUnmounted(() => { if (ws) ws.close() })

const connectWs = () => {
  if (!userInfo.value.id) return
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  ws = new WebSocket(`${protocol}//${location.host}/ws/notification?userId=${userInfo.value.id}`)
  ws.onmessage = (e) => {
    try {
      const msg = JSON.parse(e.data)
      if (msg.type === 'chat') loadFriendUnreadCounts()
      else if (msg.type === 'friend_request') { loadPendingRequests(); showToast(`${msg.data?.nickname || '某人'} 想加你为好友`) }
      else if (msg.type === 'book_share') { loadReceivedShares(); showToast(`收到书籍分享`) }
    } catch (err) {}
  }
  ws.onclose = () => { if (userInfo.value.id) setTimeout(connectWs, 5000) }
}

const searchUsers = async () => {
  if (!searchKeyword.value.trim()) return
  searched.value = true
  const res = await axios.get('/api/friend/search', { params: { keyword: searchKeyword.value, excludeUserId: userInfo.value.id } })
  searchResults.value = res.data.data || []
}

const sendRequest = async (friendId) => {
  const res = await axios.post('/api/friend/request', { userId: userInfo.value.id, friendId })
  if (res.data.code === '200') showSuccessToast('已发送')
  else showToast(res.data.msg)
}

const loadFriends = async () => { const r = await axios.get(`/api/friend/list/${userInfo.value.id}`); friends.value = r.data.data || [] }
const loadPendingRequests = async () => { const r = await axios.get(`/api/friend/pending/${userInfo.value.id}`); pendingRequests.value = r.data.data || [] }
const loadReceivedShares = async () => { const r = await axios.get(`/api/bookShare/received/${userInfo.value.id}`); receivedShares.value = r.data.data || [] }
const loadFriendUnreadCounts = async () => {
  try {
    const r = await axios.get(`/api/chat/conversations/${userInfo.value.id}`)
    const m = {}; (r.data.data || []).forEach(c => { if (c.unreadCount > 0) m[c.contactId] = c.unreadCount })
    friendUnreadMap.value = m
  } catch (e) {}
}
const getUnread = (id) => friendUnreadMap.value[id] || 0

const acceptRequest = async (id) => { await axios.post(`/api/friend/accept/${id}`); showSuccessToast('已接受'); loadPendingRequests(); loadFriends() }
const rejectRequest = async (id) => { await axios.post(`/api/friend/reject/${id}`); showToast('已拒绝'); loadPendingRequests() }
const deleteFriend = async (id) => { await showConfirmDialog({ message: '确定删除好友？' }); await axios.delete(`/api/friend/${id}`); showSuccessToast('已删除'); loadFriends() }
const deleteShare = async (id) => { await axios.delete(`/api/bookShare/${id}`); showSuccessToast('已删除'); loadReceivedShares() }

const goChat = (id) => router.push(`/chat/${id}`)
const goToBook = async (share) => { if (share.isRead === 0) await axios.post(`/api/bookShare/read/${share.shareId}`); router.push(`/book/${share.bookId}`) }
const formatDate = (s) => s ? String(s).split('T')[0] : ''

const openShareDialog = async (friendId) => {
  shareTargetUserId.value = friendId; selectedBookId.value = null; shareMessage.value = ''
  try { const r = await axios.get(`/api/bookshelf/list/${userInfo.value.id}`); myShelf.value = r.data.data || [] } catch (e) { myShelf.value = [] }
  shareDialogVisible.value = true
}
const confirmShare = async () => {
  const res = await axios.post('/api/bookShare/send', { senderId: userInfo.value.id, receiverId: shareTargetUserId.value, bookId: selectedBookId.value, message: shareMessage.value })
  if (res.data.code === '200') { showSuccessToast('分享成功'); shareDialogVisible.value = false }
}
</script>

<template>
  <div class="friends-page">
    <h2 class="page-title">👥 好友中心</h2>

    <!-- Search -->
    <van-search v-model="searchKeyword" placeholder="搜索用户..." shape="round" @search="searchUsers" background="transparent" />
    <div v-if="searchResults.length > 0" class="search-results m-card" style="margin: 0 16px 12px;">
      <van-cell v-for="u in searchResults" :key="u.id" :title="u.nickname || u.username" :label="`@${u.username}`" clickable @click="$router.push(`/user/${u.id}`)">
        <template #icon><van-image round width="36" height="36" :src="u.avatar || defaultAvatar" style="margin-right: 10px;" /></template>
        <template #right-icon><van-button size="mini" type="primary" round @click.stop="sendRequest(u.id)">添加</van-button></template>
      </van-cell>
    </div>
    <van-empty v-if="searched && searchResults.length === 0" description="未找到" image="search" />

    <!-- Pending Requests -->
    <div v-if="pendingRequests.length > 0" class="m-section-title">📩 好友请求 <van-badge :content="pendingRequests.length" /></div>
    <div v-if="pendingRequests.length > 0" class="m-card" style="margin: 0 16px 12px;">
      <van-cell v-for="r in pendingRequests" :key="r.requestId" :title="r.nickname || r.username" :label="formatDate(r.requestTime) + ' 申请'">
        <template #icon><van-image round width="36" height="36" :src="r.avatar || defaultAvatar" style="margin-right: 10px;" /></template>
        <template #right-icon>
          <div style="display: flex; gap: 6px;">
            <van-button size="mini" type="success" round @click="acceptRequest(r.requestId)">接受</van-button>
            <van-button size="mini" round @click="rejectRequest(r.requestId)">拒绝</van-button>
          </div>
        </template>
      </van-cell>
    </div>

    <!-- Friends List -->
    <div class="m-section-title">📋 我的好友 <van-tag round>{{ friends.length }}</van-tag></div>
    <van-empty v-if="friends.length === 0" description="还没有好友" image="network" />
    <div v-for="f in friends" :key="f.friendUserId" class="friend-card m-card" style="margin: 0 16px 8px;">
      <div class="friend-row" @click="$router.push(`/user/${f.friendUserId}`)">
        <div class="friend-left">
          <van-badge :content="getUnread(f.friendUserId) || ''" :max="99">
            <van-image round width="42" height="42" :src="f.avatar || defaultAvatar" />
          </van-badge>
          <div class="friend-text">
            <div class="friend-name">{{ f.nickname || f.username }}</div>
            <div class="friend-since">好友自 {{ formatDate(f.friendSince) }}</div>
          </div>
        </div>
      </div>
      <div class="friend-actions">
        <van-button size="mini" type="primary" plain round @click="goChat(f.friendUserId)">聊天</van-button>
        <van-button size="mini" plain round @click="openShareDialog(f.friendUserId)">分享</van-button>
        <van-button size="mini" plain round type="danger" @click="deleteFriend(f.friendshipId)">删除</van-button>
      </div>
    </div>

    <!-- Received Shares -->
    <div v-if="receivedShares.length > 0" class="m-section-title">📚 收到的分享</div>
    <div v-for="s in receivedShares" :key="s.shareId" class="share-row m-card" style="margin: 0 16px 8px;" @click="goToBook(s)">
      <img :src="s.coverUrl || defaultCover" class="share-cover" />
      <div class="share-info">
        <div class="share-title">{{ s.bookTitle }}</div>
        <div class="share-from">{{ s.senderNickname }} 分享</div>
        <div v-if="s.shareMessage" class="share-msg">「{{ s.shareMessage }}」</div>
      </div>
      <van-tag v-if="s.isRead === 0" type="danger" size="mini">新</van-tag>
      <van-icon name="delete-o" size="16" color="#ee4d38" @click.stop="deleteShare(s.shareId)" />
    </div>

    <!-- Share Book Popup -->
    <van-popup v-model:show="shareDialogVisible" position="bottom" round :style="{ maxHeight: '70%' }">
      <div style="padding: 20px;">
        <h3>分享书籍</h3>
        <van-empty v-if="myShelf.length === 0" description="书架为空" />
        <div v-else class="share-grid">
          <div v-for="b in myShelf" :key="b.bookId" :class="['share-book-card', selectedBookId === b.bookId ? 'sel' : '']" @click="selectedBookId = b.bookId">
            <img :src="b.coverUrl || defaultCover" class="share-book-cover" />
            <div class="share-book-name">{{ b.bookName }}</div>
          </div>
        </div>
        <van-field v-model="shareMessage" placeholder="附上一句话 (可选)" maxlength="200" style="margin-top: 12px;" />
        <van-button type="primary" block round style="margin-top: 12px;" :disabled="!selectedBookId" @click="confirmShare">确认分享</van-button>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.friends-page { padding: 16px 0 70px; }
.page-title { font-family: var(--font-serif); font-size: 20px; padding: 0 16px; margin: 0 0 8px; }

.friend-row { display: flex; align-items: center; }
.friend-left { display: flex; align-items: center; gap: 12px; flex: 1; }
.friend-text {}
.friend-name { font-size: 15px; font-weight: 600; }
.friend-since { font-size: 12px; color: var(--color-text-muted); }
.friend-actions { display: flex; gap: 6px; margin-top: 10px; }

.share-row { display: flex; align-items: center; gap: 12px; }
.share-cover { width: 44px; height: 60px; object-fit: cover; border-radius: 4px; flex-shrink: 0; }
.share-info { flex: 1; min-width: 0; }
.share-title { font-weight: 600; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.share-from { font-size: 12px; color: var(--color-text-muted); }
.share-msg { font-size: 12px; color: var(--color-text-secondary); font-style: italic; }

.share-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(80px, 1fr)); gap: 10px; max-height: 250px; overflow-y: auto; }
.share-book-card { cursor: pointer; border: 2px solid transparent; border-radius: 6px; padding: 4px; text-align: center; transition: 0.2s; }
.share-book-card.sel { border-color: var(--color-primary); background: var(--color-bg-warm); }
.share-book-cover { width: 100%; height: 90px; object-fit: cover; border-radius: 4px; }
.share-book-name { font-size: 11px; margin-top: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
