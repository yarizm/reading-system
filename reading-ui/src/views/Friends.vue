<template>
  <div class="friends-container">
    <!-- 顶部导航 -->
    <div class="page-header">
      <div class="header-left">
        <el-button plain round class="back-btn glass-btn" @click="$router.push('/')">
          <el-icon><ArrowLeft /></el-icon> 返回首页
        </el-button>
        <el-divider direction="vertical" />
        <div class="header-title-box">
          <h2>👥 好友中心</h2>
        </div>
      </div>
      <div class="header-right"></div>
    </div>

    <el-row :gutter="24">
      <!-- 左侧：好友列表 -->
      <el-col :span="14">
        <!-- 搜索用户 -->
        <el-card class="section-card" shadow="never">
          <template #header><div class="card-title">🔍 搜索用户</div></template>
          <div class="search-row">
            <el-input v-model="searchKeyword" placeholder="输入用户名或昵称..." @keyup.enter="searchUsers" clearable>
              <template #append>
                <el-button :icon="Search" @click="searchUsers" />
              </template>
            </el-input>
          </div>
          <div v-if="searchResults.length > 0" class="search-results">
            <div v-for="user in searchResults" :key="user.id" class="user-item">
              <div class="user-left" @click="$router.push(`/user/${user.id}`)">
                <el-avatar :size="36" :src="user.avatar || defaultAvatar" />
                <div class="user-text">
                  <div class="user-name">{{ user.nickname || user.username }}</div>
                  <div class="user-sub">@{{ user.username }}</div>
                </div>
              </div>
              <el-button type="primary" size="small" round @click="sendRequest(user.id)">
                <el-icon><Plus /></el-icon> 添加
              </el-button>
            </div>
          </div>
          <el-empty v-if="searched && searchResults.length === 0" description="未找到用户" :image-size="60" />
        </el-card>

        <!-- 好友列表 -->
        <el-card class="section-card" shadow="never">
          <template #header><div class="card-title">📋 我的好友 <el-tag size="small" round>{{ friends.length }}</el-tag></div></template>
          <el-empty v-if="friends.length === 0" description="还没有好友，快去搜索添加吧！" :image-size="80" />
          <div v-for="friend in friends" :key="friend.friendUserId" class="friend-item">
            <div class="user-left" @click="$router.push(`/user/${friend.friendUserId}`)">
              <div class="avatar-wrapper">
                <el-avatar :size="42" :src="friend.avatar || defaultAvatar" />
                <span v-if="getFriendUnread(friend.friendUserId) > 0" class="friend-unread-dot">
                  {{ getFriendUnread(friend.friendUserId) }}
                </span>
              </div>
              <div class="user-text">
                <div class="user-name">{{ friend.nickname || friend.username }}</div>
                <div class="user-sub">好友自 {{ formatDate(friend.friendSince) }}</div>
              </div>
            </div>
            <div class="friend-actions">
              <el-button type="primary" size="small" plain @click="goChat(friend.friendUserId)">
                <el-icon><ChatDotRound /></el-icon> 聊天
              </el-button>
              <el-button size="small" plain @click="openShareDialog(friend.friendUserId)">
                <el-icon><Share /></el-icon> 分享书籍
              </el-button>
              <el-popconfirm title="确定要删除该好友吗？" @confirm="deleteFriend(friend.friendshipId)">
                <template #reference>
                  <el-button type="danger" size="small" plain>
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </template>
              </el-popconfirm>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：待处理请求 + 收到的分享 -->
      <el-col :span="10">
        <!-- 好友请求 -->
        <el-card class="section-card" shadow="never">
          <template #header>
            <div class="card-title">
              📩 好友请求
              <el-badge v-if="pendingRequests.length > 0" :value="pendingRequests.length" type="danger" />
            </div>
          </template>
          <el-empty v-if="pendingRequests.length === 0" description="暂无好友请求" :image-size="60" />
          <div v-for="req in pendingRequests" :key="req.requestId" class="request-item">
            <div class="user-left" @click="$router.push(`/user/${req.fromUserId}`)">
              <el-avatar :size="36" :src="req.avatar || defaultAvatar" />
              <div class="user-text">
                <div class="user-name">{{ req.nickname || req.username }}</div>
                <div class="user-sub">{{ formatDate(req.requestTime) }} 申请</div>
              </div>
            </div>
            <div class="request-actions">
              <el-button type="success" size="small" round @click="acceptRequest(req.requestId)">接受</el-button>
              <el-button type="info" size="small" round @click="rejectRequest(req.requestId)">拒绝</el-button>
            </div>
          </div>
        </el-card>

        <!-- 收到的书籍分享 -->
        <el-card class="section-card" shadow="never">
          <template #header><div class="card-title">📚 收到的分享</div></template>
          <el-empty v-if="receivedShares.length === 0" description="暂无收到的分享" :image-size="60" />
          <div v-for="share in receivedShares" :key="share.shareId" class="share-item" @click="goToBook(share)">
            <img :src="share.coverUrl || defaultCover" class="share-cover"  alt=""/>
            <div class="share-info">
              <div class="share-book-title">{{ share.bookTitle }}</div>
              <div class="share-from">
                <el-avatar :size="18" :src="share.senderAvatar || defaultAvatar" />
                <span>{{ share.senderNickname }} 分享</span>
              </div>
              <div v-if="share.shareMessage" class="share-msg">「{{ share.shareMessage }}」</div>
              <div class="share-time">{{ formatDate(share.shareTime) }}</div>
            </div>
            <el-tag v-if="share.isRead === 0" type="danger" size="small" effect="dark" class="new-tag">新</el-tag>
            <el-popconfirm title="确定要删除这条分享记录吗？" @confirm.stop="deleteShare(share.shareId)">
              <template #reference>
                <el-button type="danger" :icon="Delete" size="small" circle class="share-delete-btn" @click.stop />
              </template>
            </el-popconfirm>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 分享书籍弹窗 -->
    <el-dialog v-model="shareDialogVisible" title="分享书籍给好友" width="500px">
      <el-empty v-if="myShelf.length === 0" description="你的书架是空的，先去收藏一些书吧" :image-size="80" />
      <div v-else class="share-book-list">
        <div v-for="book in myShelf" :key="book.bookId" class="share-book-item"
             :class="{ selected: selectedBookId === book.bookId }"
             @click="selectedBookId = book.bookId">
          <img :src="book.coverUrl || defaultCover" class="share-book-cover"  alt=""/>
          <div class="share-book-name">{{ book.bookName }}</div>
          <el-icon v-if="selectedBookId === book.bookId" class="check-icon"><Select /></el-icon>
        </div>
      </div>
      <el-input v-model="shareMessage" placeholder="附上一句话 (可选)" maxlength="200" show-word-limit style="margin-top: 16px;" />
      <template #footer>
        <el-button @click="shareDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmShare" :disabled="!selectedBookId">确认分享</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, ArrowLeft, Plus, ChatDotRound, Share, Delete, Select } from '@element-plus/icons-vue'
import axios from 'axios'

const router = useRouter()
const defaultAvatar = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
const defaultCover = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII='

// 当前用户
const userInfo = ref({})

// 搜索
const searchKeyword = ref('')
const searchResults = ref([])
const searched = ref(false)

// 好友列表 & 请求
const friends = ref([])
const pendingRequests = ref([])

// 收到的分享
const receivedShares = ref([])

// 好友未读消息数
const friendUnreadMap = ref({})

// 分享弹窗
const shareDialogVisible = ref(false)
const shareTargetUserId = ref(null)
const myShelf = ref([])
const selectedBookId = ref(null)
const shareMessage = ref('')

let ws = null

onMounted(() => {
  const userStr = localStorage.getItem('user')
  if (!userStr) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  userInfo.value = JSON.parse(userStr)
  loadFriends()
  loadPendingRequests()
  loadReceivedShares()
  loadFriendUnreadCounts()
  connectWebSocket()
})

onUnmounted(() => {
  if (ws) ws.close()
})

const connectWebSocket = () => {
  if (!userInfo.value.id) return
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${location.host}/ws/notification?userId=${userInfo.value.id}`
  ws = new WebSocket(wsUrl)

  ws.onmessage = (event) => {
    try {
      const msg = JSON.parse(event.data)
      if (msg.type === 'chat') {
        // 收到新消息，刷新好友未读计数
        loadFriendUnreadCounts()
      } else if (msg.type === 'friend_request') {
        // 收到好友请求，刷新待处理列表
        loadPendingRequests()
        ElMessage.info(`${msg.data?.nickname || '某人'} 想加你为好友`)
      } else if (msg.type === 'book_share') {
        // 收到书籍分享，刷新分享列表
        loadReceivedShares()
        ElMessage.info(`收到一本书籍分享：${msg.data?.bookTitle || ''}`)
      }
    } catch (e) {
      console.error('WS parse error', e)
    }
  }

  ws.onclose = () => {
    if (userInfo.value.id) {
      setTimeout(connectWebSocket, 5000)
    }
  }
}

const searchUsers = async () => {
  if (!searchKeyword.value.trim()) return
  searched.value = true
  try {
    const res = await axios.get('/api/friend/search', {
      params: { keyword: searchKeyword.value, excludeUserId: userInfo.value.id }
    })
    searchResults.value = res.data.data || []
  } catch (e) {
    console.error(e)
  }
}

const sendRequest = async (friendId) => {
  try {
    const res = await axios.post('/api/friend/request', {
      userId: userInfo.value.id,
      friendId: friendId
    })
    if (res.data.code === '200') {
      ElMessage.success('好友请求已发送！')
    } else {
      ElMessage.warning(res.data.msg)
    }
  } catch (e) {
    ElMessage.error('发送失败')
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
    const conversations = res.data.data || []
    const map = {}
    conversations.forEach(c => {
      if (c.unreadCount > 0) {
        map[c.contactId] = c.unreadCount
      }
    })
    friendUnreadMap.value = map
  } catch (e) {
    // 忽略
  }
}

const getFriendUnread = (friendUserId) => {
  return friendUnreadMap.value[friendUserId] || 0
}

const deleteShare = async (shareId) => {
  await axios.delete(`/api/bookShare/${shareId}`)
  ElMessage.success('已删除')
  loadReceivedShares()
}

const acceptRequest = async (requestId) => {
  await axios.post(`/api/friend/accept/${requestId}`)
  ElMessage.success('已接受好友请求')
  loadPendingRequests()
  loadFriends()
}

const rejectRequest = async (requestId) => {
  await axios.post(`/api/friend/reject/${requestId}`)
  ElMessage.info('已拒绝')
  loadPendingRequests()
}

const deleteFriend = async (friendshipId) => {
  await axios.delete(`/api/friend/${friendshipId}`)
  ElMessage.success('已删除好友')
  loadFriends()
}

const goChat = (friendUserId) => {
  router.push(`/chat/${friendUserId}`)
}

const goToBook = async (share) => {
  if (share.isRead === 0) {
    await axios.post(`/api/bookShare/read/${share.shareId}`)
  }
  router.push(`/book/${share.bookId}`)
}

const openShareDialog = async (friendUserId) => {
  shareTargetUserId.value = friendUserId
  selectedBookId.value = null
  shareMessage.value = ''
  // 加载我的书架
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
    const res = await axios.post('/api/bookShare/send', {
      senderId: userInfo.value.id,
      receiverId: shareTargetUserId.value,
      bookId: selectedBookId.value,
      message: shareMessage.value
    })
    if (res.data.code === '200') {
      ElMessage.success('书籍分享成功！')
      shareDialogVisible.value = false
    }
  } catch (e) {
    ElMessage.error('分享失败')
  }
}

const formatDate = (timeStr) => {
  if (!timeStr) return '未知'
  return String(timeStr).split('T')[0]
}
</script>

<style scoped>
.friends-container {
  max-width: 1100px;
  margin: 0 auto;
  padding: 18px 24px;
}



.section-card {
  margin-bottom: 20px;
  border: 1px solid #e8e0d6;
  border-radius: 8px;
}

.card-title {
  font-weight: 600;
  font-size: 15px;
  color: #3d3632;
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 搜索 */
.search-row {
  margin-bottom: 12px;
}
.search-results {
  max-height: 300px;
  overflow-y: auto;
}

/* 用户/好友通用行 */
.user-item, .friend-item, .request-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 4px;
  border-bottom: 1px solid #f5f0e8;
  transition: background 0.15s;
}
.user-item:last-child, .friend-item:last-child, .request-item:last-child {
  border-bottom: none;
}
.user-item:hover, .friend-item:hover, .request-item:hover {
  background: #faf7f2;
}
.user-left {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
}
.user-text {
  display: flex;
  flex-direction: column;
}
.user-name {
  font-size: 14px;
  font-weight: 600;
  color: #3d3632;
}
.user-sub {
  font-size: 12px;
  color: #9b8e82;
  margin-top: 2px;
}

.friend-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}
.request-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

/* 收到的分享 */
.share-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 4px;
  border-bottom: 1px solid #f5f0e8;
  cursor: pointer;
  transition: background 0.15s;
  position: relative;
}
.share-item:hover {
  background: #faf7f2;
}
.share-item:last-child {
  border-bottom: none;
}
.share-cover {
  width: 44px;
  height: 60px;
  object-fit: cover;
  border-radius: 4px;
  background: #f0ece4;
  flex-shrink: 0;
}
.share-info {
  flex: 1;
  min-width: 0;
}
.share-book-title {
  font-weight: 600;
  font-size: 14px;
  color: #3d3632;
  margin-bottom: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.share-from {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #9b8e82;
  margin-bottom: 2px;
}
.share-msg {
  font-size: 12px;
  color: #7a6e63;
  font-style: italic;
}
.share-time {
  font-size: 11px;
  color: #c4b9ab;
  margin-top: 2px;
}
.new-tag {
  position: absolute;
  top: 6px;
  right: 36px;
}
.share-delete-btn {
  position: absolute;
  top: 8px;
  right: 4px;
  opacity: 0.5;
  transition: opacity 0.2s;
}
.share-item:hover .share-delete-btn {
  opacity: 1;
}

/* 好友未读圆点 */
.avatar-wrapper {
  position: relative;
  flex-shrink: 0;
}
.friend-unread-dot {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 18px;
  height: 18px;
  line-height: 18px;
  text-align: center;
  background: #F56C6C;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  border-radius: 9px;
  padding: 0 4px;
  border: 2px solid #fffdf9;
}

/* 分享弹窗 */
.share-book-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(90px, 1fr));
  gap: 12px;
  max-height: 320px;
  overflow-y: auto;
}
.share-book-item {
  cursor: pointer;
  border: 2px solid transparent;
  border-radius: 6px;
  padding: 6px;
  text-align: center;
  transition: border-color 0.2s, background 0.2s;
  position: relative;
}
.share-book-item:hover {
  background: #faf7f2;
}
.share-book-item.selected {
  border-color: #8b6f52;
  background: #faf5ed;
}
.share-book-cover {
  width: 100%;
  height: 100px;
  object-fit: cover;
  border-radius: 4px;
  background: #f0ece4;
  display: block;
}
.share-book-name {
  font-size: 12px;
  margin-top: 6px;
  color: #3d3632;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.check-icon {
  position: absolute;
  top: 4px;
  right: 4px;
  color: #8b6f52;
  font-size: 18px;
}
/* === 玻璃拟态卡片覆写 === */
:deep(.el-card) {
  background: rgba(255, 255, 255, 0.45) !important;
  backdrop-filter: blur(24px) !important;
  -webkit-backdrop-filter: blur(24px) !important;
  border-radius: 16px !important;
  border: 1px solid rgba(255, 255, 255, 0.6) !important;
  box-shadow: 0 8px 32px rgba(60, 40, 20, 0.05) !important;
  transition: all 0.3s ease;
}
:deep(.el-card__header) {
  border-bottom: 1px solid rgba(255, 255, 255, 0.5) !important;
}
.section-card {
  margin-bottom: 20px;
}
/* === 标准统一头部 === */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  border-bottom: 1px solid rgba(60, 40, 20, 0.08);
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
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

</style>
