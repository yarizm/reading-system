<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showFailToast, showSuccessToast, showToast } from 'vant'
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
  let count = commentList.value.length
  commentList.value.forEach((item) => {
    if (item.children) count += item.children.length
  })
  return count
})

onMounted(() => {
  const user = localStorage.getItem('user')
  if (user) userInfo.value = JSON.parse(user)
  loadBookDetail()
  loadComments()
  checkShelf()
})

const loadBookDetail = async () => {
  const res = await axios.get(`/api/sysBook/${bookId}`)
  if (res.data.code === '200') {
    bookInfo.value = res.data.data
  }
}

const loadComments = async () => {
  const res = await axios.get(`/api/comment/list/${bookId}`, {
    params: { userId: userInfo.value.id }
  })
  if (res.data.code === '200') {
    commentList.value = res.data.data || []
  }
}

const checkShelf = async () => {
  if (!userInfo.value.id) return
  const res = await axios.get(`/api/bookshelf/list/${userInfo.value.id}`)
  if (res.data.code === '200') {
    inShelf.value = (res.data.data || []).some((book) => String(book.bookId) === String(bookId))
  }
}

const toggleShelf = async () => {
  if (!userInfo.value.id) {
    showToast('请先登录')
    return
  }
  if (inShelf.value) {
    await showConfirmDialog({ title: '提示', message: '确定将这本书移出书架吗？' })
    await axios.delete('/api/bookshelf/removeByBook', {
      params: { userId: userInfo.value.id, bookId }
    })
    inShelf.value = false
    showSuccessToast('已移出书架')
  } else {
    await axios.post('/api/bookshelf/add', {
      userId: userInfo.value.id,
      bookId
    })
    inShelf.value = true
    showSuccessToast('已加入书架')
  }
}

const startReading = () => {
  router.push(`/read/${bookId}`)
}

const openCommentPopup = () => {
  replyTarget.value = null
  replyParent.value = null
  showCommentPopup.value = true
}

const replyTo = (parent, target) => {
  if (!userInfo.value.id) {
    showToast('请先登录')
    return
  }
  replyParent.value = parent
  replyTarget.value = target
  showCommentPopup.value = true
}

const cancelReply = () => {
  replyTarget.value = null
  replyParent.value = null
  showCommentPopup.value = false
}

const submitComment = async () => {
  if (!userInfo.value.id) {
    showToast('请先登录')
    return
  }
  if (!myComment.value.trim()) {
    showToast('请输入评论内容')
    return
  }
  submitting.value = true
  try {
    const isReply = !!replyTarget.value
    await axios.post('/api/comment/add', {
      bookId,
      userId: userInfo.value.id,
      content: myComment.value,
      rating: isReply ? 0 : myRating.value,
      parentId: isReply ? replyParent.value.id : 0,
      replyUserId: isReply ? replyTarget.value.userId : null
    })
    showSuccessToast(isReply ? '回复成功' : '评论成功')
    myComment.value = ''
    cancelReply()
    await loadComments()
    if (!isReply) await loadBookDetail()
  } catch (error) {
    showFailToast('提交失败，请稍后再试')
  } finally {
    submitting.value = false
  }
}

const toggleLike = async (item) => {
  if (!userInfo.value.id) {
    showToast('请先登录')
    return
  }
  const res = await axios.post('/api/comment/like', {
    commentId: item.id,
    userId: userInfo.value.id
  })
  if (res.data.code === '200') {
    item.likeCount = res.data.data.likeCount
    item.isLiked = res.data.data.isLiked
  }
}

const deleteComment = async (id) => {
  await showConfirmDialog({ title: '提示', message: '确定删除这条评论吗？' })
  await axios.delete(`/api/comment/${id}`)
  showSuccessToast('评论已删除')
  loadComments()
}

const formatTime = (time) => {
  if (!time) return ''
  return String(time).replace('T', ' ').substring(0, 16)
}

const shareBook = async () => {
  const shareText = `我在智能阅读里发现了一本不错的书：《${bookInfo.value.title || bookInfo.value.name}》\n${window.location.href}`
  try {
    await navigator.clipboard.writeText(shareText)
    showSuccessToast('分享文案已复制')
  } catch (error) {
    showFailToast('复制失败，请手动分享链接')
  }
}
</script>

<template>
  <div class="detail-page">
    <van-nav-bar title="图书详情" left-arrow @click-left="$router.back()" />

    <section class="hero-card">
      <img :src="bookInfo.coverUrl || defaultCover" class="book-cover" alt="" />
      <div class="hero-main">
        <div class="book-label">书籍档案</div>
        <h1 class="book-title">{{ bookInfo.title || bookInfo.name }}</h1>
        <div class="meta-line">作者：{{ bookInfo.author || '未知作者' }}</div>
        <div class="meta-line">分类：{{ bookInfo.category || '未分类' }}</div>
        <div class="rating-row">
          <van-rate
            v-model="bookInfo.avgRating"
            readonly
            allow-half
            size="16"
            color="#f2a74b"
            void-color="#e7dcc8"
          />
          <span class="rating-text">{{ bookInfo.avgRating ? `${bookInfo.avgRating.toFixed(1)} 分` : '暂无评分' }}</span>
        </div>
      </div>
    </section>

    <section class="content-card">
      <div class="section-head">
        <div>
          <div class="section-title">内容简介</div>
          <div class="section-tip">先快速了解这本书适不适合现在的你。</div>
        </div>
      </div>
      <p class="book-desc">{{ bookInfo.description || '暂时还没有简介，先开始读读看吧。' }}</p>
    </section>

    <section class="content-card">
      <div class="section-head">
        <div>
          <div class="section-title">书友评论</div>
          <div class="section-tip">共 {{ totalComments }} 条评论，看看大家都聊了些什么。</div>
        </div>
        <van-button size="small" round plain type="primary" @click="openCommentPopup">写评论</van-button>
      </div>

      <van-empty v-if="commentList.length === 0" description="还没有评论，来抢个沙发吧" image="search" />

      <div v-else class="comment-list">
        <article v-for="item in commentList" :key="item.id" class="comment-item">
          <van-image
            round
            width="38"
            height="38"
            :src="item.avatar || defaultAvatar"
            class="comment-avatar"
            @click="$router.push(`/user/${item.userId}`)"
          />
          <div class="comment-body">
            <div class="comment-top">
              <span class="comment-name" @click="$router.push(`/user/${item.userId}`)">
                {{ item.nickname || '匿名书友' }}
              </span>
              <van-rate v-if="item.rating" v-model="item.rating" readonly size="12" color="#f2a74b" />
            </div>
            <div class="comment-text">{{ item.content }}</div>
            <div class="comment-foot">
              <span class="comment-time">{{ formatTime(item.createTime) }}</span>
              <div class="comment-actions">
                <span :class="{ liked: item.isLiked }" @click="toggleLike(item)">
                  {{ item.isLiked ? '已赞' : '点赞' }} {{ item.likeCount || 0 }}
                </span>
                <span @click="replyTo(item, item)">回复</span>
                <span
                  v-if="userInfo.id === item.userId || userInfo.role === 1"
                  class="danger-text"
                  @click="deleteComment(item.id)"
                >
                  删除
                </span>
              </div>
            </div>

            <div v-if="item.children && item.children.length > 0" class="reply-list">
              <div v-for="sub in item.children" :key="sub.id" class="reply-item">
                <van-image round width="24" height="24" :src="sub.avatar || defaultAvatar" />
                <div class="reply-body">
                  <div class="reply-head">
                    <span class="reply-name">{{ sub.nickname }}</span>
                    <span v-if="sub.replyUserId && sub.replyUserId !== item.userId" class="reply-tag">
                      回复 @{{ sub.replyNickname }}
                    </span>
                  </div>
                  <div class="reply-text">{{ sub.content }}</div>
                  <div class="reply-actions">
                    <span :class="{ liked: sub.isLiked }" @click="toggleLike(sub)">
                      {{ sub.isLiked ? '已赞' : '点赞' }} {{ sub.likeCount || 0 }}
                    </span>
                    <span @click="replyTo(item, sub)">回复</span>
                    <span
                      v-if="userInfo.id === sub.userId || userInfo.role === 1"
                      class="danger-text"
                      @click="deleteComment(sub.id)"
                    >
                      删除
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </article>
      </div>
    </section>

    <van-action-bar>
      <van-action-bar-icon icon="chat-o" text="评论" @click="openCommentPopup" />
      <van-action-bar-icon icon="share-o" text="分享" @click="shareBook" />
      <van-action-bar-icon
        :icon="inShelf ? 'star' : 'star-o'"
        :text="inShelf ? '已收藏' : '加入书架'"
        :color="inShelf ? '#f2a74b' : ''"
        @click="toggleShelf"
      />
      <van-action-bar-button type="primary" text="立即阅读" @click="startReading" />
    </van-action-bar>

    <van-popup v-model:show="showCommentPopup" position="bottom" round>
      <div class="popup-panel">
        <div class="popup-head">
          <div>
            <div class="popup-title">{{ replyTarget ? `回复 @${replyTarget.nickname}` : '写下你的感受' }}</div>
            <div class="popup-tip">{{ replyTarget ? '这条回复会显示在原评论下方。' : '一段真诚的推荐，往往比高分更打动人。' }}</div>
          </div>
          <van-icon name="cross" size="20" @click="cancelReply" />
        </div>
        <div v-if="!replyTarget" class="rate-box">
          <span class="rate-label">综合评分</span>
          <van-rate v-model="myRating" size="22" color="#f2a74b" />
        </div>
        <van-field
          v-model="myComment"
          rows="4"
          autosize
          type="textarea"
          placeholder="写下你的真实阅读感受..."
          class="comment-field"
        />
        <van-button type="primary" round block :loading="submitting" @click="submitComment">
          {{ replyTarget ? '发送回复' : '发布评论' }}
        </van-button>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.detail-page {
  min-height: 100vh;
  padding-bottom: calc(120px + var(--safe-bottom));
  background:
    radial-gradient(circle at top left, rgba(214, 191, 165, 0.22), transparent 28%),
    linear-gradient(180deg, #f8f2ea 0%, #f6efe5 34%, #faf6f0 100%);
}

.hero-card,
.content-card {
  margin: 16px;
  padding: 18px;
  border-radius: 22px;
  background: rgba(255, 252, 247, 0.96);
  box-shadow: 0 18px 38px rgba(93, 67, 43, 0.08);
}

.hero-card {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.book-cover {
  width: 108px;
  flex-shrink: 0;
  border-radius: 16px;
  object-fit: cover;
  aspect-ratio: 3 / 4;
  box-shadow: 0 14px 24px rgba(61, 44, 31, 0.16);
}

.hero-main {
  flex: 1;
  min-width: 0;
}

.book-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.16em;
  color: #a17752;
  text-transform: uppercase;
}

.book-title {
  margin: 8px 0 10px;
  font-family: var(--font-serif), serif;
  font-size: 24px;
  line-height: 1.25;
  color: #3d2c1f;
}

.meta-line {
  margin-top: 5px;
  font-size: 13px;
  color: #7e6855;
}

.rating-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}

.rating-text {
  font-size: 13px;
  color: #8a725d;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.section-title {
  font-family: var(--font-serif), serif;
  font-size: 20px;
  color: #3d2c1f;
}

.section-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: #8a725d;
}

.book-desc {
  margin: 0;
  font-size: 14px;
  line-height: 1.9;
  color: #5a4637;
}

.comment-list {
  display: flex;
  flex-direction: column;
}

.comment-item {
  display: flex;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid rgba(143, 117, 87, 0.12);
}

.comment-item:last-child {
  border-bottom: none;
}

.comment-body {
  flex: 1;
  min-width: 0;
}

.comment-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.comment-name,
.reply-name {
  font-size: 14px;
  font-weight: 700;
  color: #3d2c1f;
}

.comment-text,
.reply-text {
  margin-top: 8px;
  font-size: 14px;
  line-height: 1.8;
  color: #554132;
  white-space: pre-wrap;
  word-break: break-word;
}

.comment-foot,
.comment-actions,
.reply-actions {
  display: flex;
  align-items: center;
}

.comment-foot {
  justify-content: space-between;
  gap: 8px;
  margin-top: 10px;
}

.comment-time {
  font-size: 11px;
  color: #9a826c;
}

.comment-actions,
.reply-actions {
  gap: 14px;
  flex-wrap: wrap;
  font-size: 12px;
  color: #8a725d;
}

.liked {
  color: #c26647;
}

.danger-text {
  color: #c64e3e;
}

.reply-list {
  margin-top: 12px;
  padding: 12px;
  border-radius: 16px;
  background: rgba(247, 238, 228, 0.88);
}

.reply-item {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.reply-item:last-child {
  margin-bottom: 0;
}

.reply-body {
  flex: 1;
  min-width: 0;
}

.reply-head {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.reply-tag {
  font-size: 12px;
  color: #957e69;
}

.reply-actions {
  margin-top: 8px;
}

.popup-panel {
  padding: 22px 18px 28px;
}

.popup-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.popup-title {
  font-family: var(--font-serif), serif;
  font-size: 22px;
  color: #3d2c1f;
}

.popup-tip {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: #8a725d;
}

.rate-box {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.rate-label {
  font-size: 14px;
  color: #5a4637;
}

.comment-field {
  margin-bottom: 14px;
  border-radius: 18px;
  background: rgba(247, 242, 235, 0.9);
}
</style>
