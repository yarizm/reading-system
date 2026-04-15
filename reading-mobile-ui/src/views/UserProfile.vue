<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showSuccessToast } from 'vant'
import axios from 'axios'

const route = useRoute()
const router = useRouter()
const userId = route.params.id
const defaultAvatar = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'

const profile = ref({})
const shelfBooks = ref([])
const currentUser = ref({})

onMounted(async () => {
  const u = localStorage.getItem('user')
  if (u) currentUser.value = JSON.parse(u)
  try {
    const res = await axios.get(`/api/sysUser/profile/${userId}`)
    if (res.data.code === '200') profile.value = res.data.data
  } catch (e) {}
  try {
    const res = await axios.get(`/api/bookshelf/list/${userId}`)
    if (res.data.code === '200') shelfBooks.value = res.data.data || []
  } catch (e) {}
})

const addFriend = async () => {
  if (!currentUser.value.id) return showToast('请先登录')
  const res = await axios.post('/api/friend/request', { userId: currentUser.value.id, friendId: userId })
  if (res.data.code === '200') showSuccessToast('好友请求已发送')
  else showToast(res.data.msg)
}
</script>

<template>
  <div class="user-profile-page">
    <van-nav-bar title="用户资料" left-arrow @click-left="$router.back()" />
    <div class="profile-card m-card" style="margin: 16px; text-align: center;">
      <van-image round width="72" height="72" :src="profile.avatar || defaultAvatar" />
      <h2 style="margin: 8px 0 4px; font-family: var(--font-serif),serif;">{{ profile.nickname || profile.username }}</h2>
      <p v-if="profile.infoVisible === 1 && profile.age" style="color: var(--color-text-muted); font-size: 13px;">{{ profile.age }} 岁</p>
      <van-button v-if="currentUser.id && currentUser.id !== userId" type="primary" size="small" round @click="addFriend" style="margin-top: 10px;">
        添加好友
      </van-button>
    </div>

    <div v-if="shelfBooks.length > 0" class="m-section-title">📚 TA 的书架</div>
    <div v-if="shelfBooks.length > 0" class="shelf-scroll">
      <div v-for="b in shelfBooks" :key="b.bookId" class="shelf-item" @click="$router.push(`/book/${b.bookId}`)">
        <img :src="b.coverUrl || 'https://via.placeholder.com/80x110'" class="cover-aspect" style="width: 80px; height: 110px;"  alt=""/>
        <div class="shelf-name">{{ b.bookName }}</div>
      </div>
    </div>
    <van-empty v-else description="未公开书架或暂无书籍" />
  </div>
</template>

<style scoped>
.user-profile-page { min-height: 100vh; background: var(--color-bg); }
.shelf-scroll { display: flex; gap: 12px; overflow-x: auto; padding: 0 16px 20px; -webkit-overflow-scrolling: touch; }
.shelf-item { flex-shrink: 0; width: 80px; }
.shelf-name { font-size: 12px; margin-top: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
