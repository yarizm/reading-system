<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showSuccessToast, showFailToast, showConfirmDialog } from 'vant'
import axios from 'axios'

const showCommentPopup = ref(false)

const route = useRoute()
const router = useRouter()
const bookId = route.params.id
const defaultCover = 'https://via.placeholder.com/150'
const defaultAvatar = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'

const bookInfo = ref({})
const commentList = ref([])
const userInfo = ref({})
const inShelf = ref(false)
const myComment = ref('')
const myRating = ref(5)
const submitting = ref(false)
const replyTarget = ref(null)
const replyParent = ref(null)

const totalComments = computed(() => {
  let c = commentList.value.length
  commentList.value.forEach(x => { if (x.children) c += x.children.length })
  return c
})

onMounted(() => {
  const u = localStorage.getItem('user')
  if (u) userInfo.value = JSON.parse(u)
  loadBookDetail()
  loadComments()
  checkShelf()
})

const loadBookDetail = async () => {
  const res = await axios.get(`/api/sysBook/${bookId}`)
  if (res.data.code === '200') bookInfo.value = res.data.data
}
const loadComments = async () => {
  const res = await axios.get(`/api/comment/list/${bookId}`, { params: { userId: userInfo.value.id } })
  if (res.data.code === '200') commentList.value = res.data.data
}
const checkShelf = async () => {
  if (!userInfo.value.id) return
  const res = await axios.get(`/api/bookshelf/list/${userInfo.value.id}`)
  if (res.data.code === '200') inShelf.value = res.data.data.some(b => b.bookId === bookId)
}

const toggleShelf = async () => {
  if (!userInfo.value.id) return showToast('请先登录')
  if (inShelf.value) {
    await showConfirmDialog({ title: '提示', message: '确定移出书架吗？' })
    await axios.delete('/api/bookshelf/removeByBook', { params: { userId: userInfo.value.id, bookId } })
    showSuccessToast('已移出'); inShelf.value = false
  } else {
    await axios.post('/api/bookshelf/add', { userId: userInfo.value.id, bookId })
    showSuccessToast('已加入书架'); inShelf.value = true
  }
}

const startReading = () => router.push(`/read/${bookId}`)

const replyTo = (parent, target) => {
  if (!userInfo.value.id) return showToast('请先登录')
  replyParent.value = parent; replyTarget.value = target
  showCommentPopup.value = true
}
const cancelReply = () => { replyTarget.value = null; replyParent.value = null; showCommentPopup.value = false }

const submitComment = async () => {
  if (!userInfo.value.id) return showToast('请先登录')
  if (!myComment.value.trim()) return showToast('请输入内容')
  submitting.value = true
  try {
    const isReply = !!replyTarget.value
    await axios.post('/api/comment/add', {
      bookId, userId: userInfo.value.id, content: myComment.value,
      rating: isReply ? 0 : myRating.value,
      parentId: isReply ? replyParent.value.id : 0,
      replyUserId: isReply ? replyTarget.value.userId : null
    })
    showSuccessToast(isReply ? '回复成功' : '发表成功')
    myComment.value = ''; cancelReply(); loadComments()
    if (!isReply) loadBookDetail()
    showCommentPopup.value = false
  } catch (e) { showFailToast('发表失败') }
  finally { submitting.value = false }
}

const toggleLike = async (item) => {
  if (!userInfo.value.id) return showToast('请先登录')
  const res = await axios.post('/api/comment/like', { commentId: item.id, userId: userInfo.value.id })
  if (res.data.code === '200') { item.likeCount = res.data.data.likeCount; item.isLiked = res.data.data.isLiked }
}

const deleteComment = async (id) => {
  await showConfirmDialog({ title: '提示', message: '确定删除吗？' })
  await axios.delete(`/api/comment/${id}`)
  showSuccessToast('已删除'); loadComments()
}

const formatTime = (s) => s ? s.replace('T', ' ').substring(0, 16) : ''

const shareBook = () => {
  const shareText = `书荒救星！我发现了一本好书《${bookInfo.value.title}》，强烈推荐给你：\n${window.location.href}`
  if (navigator.clipboard) {
    navigator.clipboard.writeText(shareText).then(() => showSuccessToast('分享链接已复制')).catch(() => showFailToast('复制失败'))
  } else {
    showSuccessToast('请手动复制链接分享')
  }
}
</script>

<template>
  <div class="detail-page">
    <van-nav-bar title="书籍详情" left-arrow @click-left="$router.back()" />

    <!-- Book Info Card -->
    <div class="book-info-card">
      <img :src="bookInfo.coverUrl || defaultCover" class="book-cover"  alt=""/>
      <div class="info-right">
        <h2 class="book-title">{{ bookInfo.title || bookInfo.name }}</h2>
        <p class="book-author">✍ {{ bookInfo.author }}</p>
        <p class="book-cat">📂 {{ bookInfo.category }}</p>
        <van-rate v-model="bookInfo.avgRating" readonly allow-half size="16" color="#f5a623" void-color="#e0d8c8" />
        <span class="rating-text" v-if="bookInfo.avgRating">{{ bookInfo.avgRating?.toFixed(1) }} 分</span>
      </div>
    </div>

    <!-- Description -->
    <div class="desc-section m-card" style="margin: 0 16px 12px; position: relative;">
      <div class="desc-label" style="font-size: 16px; margin-bottom: 8px;">简介</div>
      <p class="desc-text" style="color: #666; font-size: 14px; line-height: 1.8;">{{ bookInfo.description || '暂无简介...' }}</p>
    </div>

    <!-- Comments Section -->
    <div class="comment-section">
      <div class="m-section-title">💬 书友评论 ({{ totalComments }})</div>

      <van-empty v-if="commentList.length === 0" description="暂无评论，快来抢沙发！" image="search" />

      <div v-for="item in commentList" :key="item.id" class="comment-item">
        <van-image round width="36" height="36" :src="item.avatar || defaultAvatar" class="c-avatar" @click="$router.push(`/user/${item.userId}`)" />
        <div class="c-body">
          <div class="c-header">
            <span class="c-name" @click="$router.push(`/user/${item.userId}`)">{{ item.nickname || '匿名' }}</span>
            <van-rate v-if="item.rating" v-model="item.rating" readonly size="12" color="#f5a623" />
          </div>
          <p class="c-text">{{ item.content }}</p>
          <div class="c-footer">
            <span class="c-time">{{ formatTime(item.createTime) }}</span>
            <div class="c-actions">
              <span @click="toggleLike(item)" :class="{ liked: item.isLiked }">
                {{ item.isLiked ? '♥' : '♡' }} {{ item.likeCount || 0 }}
              </span>
              <span @click="replyTo(item, item)">回复</span>
              <span v-if="userInfo.id === item.userId || userInfo.role === 1" class="delete-action" @click="deleteComment(item.id)">删除</span>
            </div>
          </div>

          <!-- Sub comments -->
          <div v-if="item.children && item.children.length > 0" class="sub-comments">
            <div v-for="sub in item.children" :key="sub.id" class="sub-item">
              <van-image round width="24" height="24" :src="sub.avatar || defaultAvatar" />
              <div class="sub-body">
                <span class="sub-name">{{ sub.nickname }}</span>
                <span v-if="sub.replyUserId && sub.replyUserId !== item.userId" class="sub-reply-tag"> 回复 @{{ sub.replyNickname }}</span>
                <p class="sub-text">{{ sub.content }}</p>
                <div class="c-actions" style="margin-top: 4px;">
                  <span @click="toggleLike(sub)" :class="{ liked: sub.isLiked }">{{ sub.isLiked ? '♥' : '♡' }} {{ sub.likeCount || 0 }}</span>
                  <span @click="replyTo(item, sub)">回复</span>
                  <span v-if="userInfo.id === sub.userId || userInfo.role === 1" class="delete-action" @click="deleteComment(sub.id)">删除</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Bottom Action Bar -->
    <van-action-bar>
      <van-action-bar-icon
        icon="chat-o"
        text="留评"
        @click="replyTarget=null; showCommentPopup=true;" 
      />
      <van-action-bar-icon
        icon="share-o"
        text="分享"
        @click="shareBook"
      />
      <van-action-bar-icon
        :icon="inShelf ? 'star' : 'star-o'"
        :text="inShelf ? '已收藏' : '收藏'"
        :color="inShelf ? '#f5a623' : ''"
        @click="toggleShelf"
      />
      <van-action-bar-button
        type="primary"
        text="立即阅读"
        @click="startReading"
      />
    </van-action-bar>

    <!-- Comment Popup -->
    <van-popup v-model:show="showCommentPopup" position="bottom" round :style="{ padding: '16px' }">
      <div class="comment-input-area">
        <div class="popup-header">
          <span class="popup-title">{{ replyTarget ? `回复 @${replyTarget.nickname}` : '发表感悟' }}</span>
          <van-icon name="cross" @click="cancelReply" />
        </div>
        <div v-if="!replyTarget" class="rate-box" style="margin-bottom: 12px;">
          <span style="font-size: 14px; margin-right: 8px;">综合评分</span>
          <van-rate v-model="myRating" size="20" color="#f5a623" />
        </div>
        <van-field
          v-model="myComment"
          rows="4"
          autosize
          type="textarea"
          placeholder="写下你的真实感想..."
          style="background: #f7f8fa; border-radius: 8px; margin-bottom: 12px; padding: 12px;"
        />
        <van-button type="primary" round block :loading="submitting" @click="submitComment">
          {{ replyTarget ? '回复' : '发布' }}
        </van-button>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.detail-page { padding-bottom: 130px; background: var(--color-bg); }

.book-info-card {
  display: flex;
  gap: 16px;
  padding: 16px;
  margin: 0 16px 12px;
  background: var(--color-bg-card);
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(60,40,20,0.05);
}
.book-cover { width: 100px; border-radius: 8px; box-shadow: 0 4px 12px rgba(60,40,20,0.12); object-fit: cover; aspect-ratio: 3/4; }
.info-right { flex: 1; display: flex; flex-direction: column; gap: 4px; }
.book-title { font-size: 20px; font-weight: 700; margin: 0; font-family: var(--font-serif),serif; }
.book-author, .book-cat { font-size: 13px; color: var(--color-text-secondary); margin: 0; }
.rating-text { font-size: 13px; color: #f5a623; font-weight: 600; margin-left: 4px; }

.desc-section .desc-label { font-size: 14px; font-weight: 600; margin-bottom: 6px; color: var(--color-text-secondary); }
.comment-section { padding: 0 16px; padding-bottom: 24px; }

.comment-item { display: flex; gap: 10px; padding: 14px 0; border-bottom: 1px solid var(--color-border-light); }
.c-avatar { flex-shrink: 0; }
.c-body { flex: 1; min-width: 0; }
.c-header { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.c-name { font-weight: 600; font-size: 14px; }
.c-text { font-size: 14px; line-height: 1.6; margin: 0 0 6px; }
.c-footer { display: flex; align-items: center; justify-content: space-between; }
.c-time { font-size: 11px; color: var(--color-text-muted); }
.c-actions { display: flex; gap: 14px; font-size: 12px; color: var(--color-text-muted); }
.c-actions span { cursor: pointer; }
.c-actions .liked { color: var(--color-danger); }
.c-actions .delete-action { color: var(--color-danger); }

.sub-comments { background: var(--color-bg-warm); border-radius: 8px; padding: 10px; margin-top: 8px; }
.sub-item { display: flex; gap: 8px; margin-bottom: 10px; }
.sub-item:last-child { margin-bottom: 0; }
.sub-body { flex: 1; }
.sub-name { font-weight: 600; font-size: 13px; color: var(--color-text-secondary); }
.sub-reply-tag { font-size: 12px; color: var(--color-text-muted); }
.sub-text { font-size: 13px; margin: 2px 0 0; }

.popup-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.popup-title { font-weight: 600; font-size: 16px; }

/* Custom Action Bar colors */
</style>
