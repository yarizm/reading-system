<script setup>
import request from '../utils/request'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { useAuthStore } from '../stores/auth'
import { getCachedImage } from '../utils/imageCache'

const route = useRoute()
const authStore = useAuthStore()
const userId = route.params.id
const defaultAvatar = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'

const profile = ref({})
const shelfBooks = ref([])
const currentUser = computed(() => authStore.user || {})

const displayAvatar = ref(defaultAvatar)

watch(() => profile.value.avatar, async (newVal) => {
  displayAvatar.value = newVal ? await getCachedImage(newVal) : defaultAvatar
}, { immediate: true })

onMounted(async () => {

  try {
    const res = await request.get(`/api/sysUser/profile/${userId}`)
    if (res.data.code === '200') profile.value = res.data.data
  } catch (error) {
    console.error(error)
  }

  try {
    const res = await request.get(`/api/bookshelf/list/${userId}`)
    if (res.data.code === '200') shelfBooks.value = res.data.data || []
  } catch (error) {
    console.error(error)
  }
})

const addFriend = async () => {
  if (!currentUser.value.id) {
    showToast('请先登录')
    return
  }
  const res = await request.post('/api/friend/request', {
    userId: currentUser.value.id,
    friendId: userId
  })
  if (res.data.code === '200') {
    showSuccessToast('好友请求已发送')
  } else {
    showToast(res.data.msg)
  }
}
</script>

<template>
  <div class="user-profile-page">
    <van-nav-bar title="用户资料" left-arrow @click-left="$router.back()" :border="false" class="glass-header" />

    <section class="profile-card glass-panel fade-in-up" style="animation-delay: 0.1s">
      <img :src="displayAvatar" style="border-radius: 50%; width: 80px; height: 80px; object-fit: cover;" />
      <h1 class="profile-name">{{ profile.nickname || profile.username }}</h1>
      <p v-if="profile.infoVisible === 1 && profile.age" class="profile-meta">{{ profile.age }} 岁</p>
      <van-button
        v-if="currentUser.id && String(currentUser.id) !== String(userId)"
        type="primary"
        size="small"
        round
        class="profile-action"
        @click="addFriend"
      >
        添加好友
      </van-button>
    </section>

    <section class="shelf-card glass-panel fade-in-up" style="animation-delay: 0.2s">
      <div class="section-title">TA 的书架</div>
      <div class="section-tip">如果对方公开了收藏，这里会展示正在读或已经收藏的书。</div>
      <div v-if="shelfBooks.length > 0" class="shelf-scroll">
        <div v-for="book in shelfBooks" :key="book.bookId" class="shelf-item" @click="$router.push(`/book/${book.bookId}`)">
          <img :src="book.coverUrl || 'https://via.placeholder.com/80x110'" class="shelf-cover" alt="" />
          <div class="shelf-name">{{ book.bookName }}</div>
        </div>
      </div>
      <van-empty v-else description="对方未公开书架，或暂时没有可展示的图书" />
    </section>
  </div>
</template>

<style scoped>
.user-profile-page {
  min-height: 100vh;
  padding-bottom: calc(48px + var(--safe-bottom));
  background: var(--bg-color);
}

.profile-card,
.shelf-card {
  margin: 16px;
  padding: 18px;
}

.profile-card {
  text-align: center;
}

.profile-name {
  margin: 12px 0 4px;
  font-family: var(--font-serif), serif;
  font-size: 24px;
  color: var(--text-primary);
}

.profile-meta {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.profile-action {
  margin-top: 12px;
}

.section-title {
  font-family: var(--font-serif), serif;
  font-size: 20px;
  color: var(--text-primary);
}

.section-tip {
  margin-top: 6px;
  margin-bottom: 14px;
  font-size: 12px;
  line-height: 1.65;
  color: var(--text-secondary);
}

.shelf-scroll {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.shelf-item {
  width: 84px;
  flex-shrink: 0;
}

.shelf-cover {
  width: 84px;
  height: 116px;
  border-radius: 12px;
  object-fit: cover;
  box-shadow: 0 8px 20px rgba(93, 67, 43, 0.09);
}

.shelf-name {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.45;
  color: var(--text-primary);
  text-align: center;
}
</style>
