<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showSuccessToast, showFailToast } from 'vant'
import axios from 'axios'

const route = useRoute()
const router = useRouter()
const shareCode = route.params.shareCode
const defaultCover = 'https://via.placeholder.com/80x110'

const booklistInfo = ref(null)
const userInfo = ref({})
const importing = ref(false)

onMounted(async () => {
  const u = localStorage.getItem('user')
  if (u) userInfo.value = JSON.parse(u)
  try {
    const res = await axios.get(`/api/booklist/share/${shareCode}`)
    if (res.data.code === '200') booklistInfo.value = res.data.data
    else showFailToast(res.data.msg || '书单不存在')
  } catch (e) { showFailToast('加载失败') }
})

const importBooklist = async () => {
  if (!userInfo.value.id) return showToast('请先登录')
  importing.value = true
  try {
    const res = await axios.post(`/api/booklist/import/${shareCode}`, { userId: userInfo.value.id })
    if (res.data.code === '200') { showSuccessToast('导入成功'); router.push('/shelf') }
    else showFailToast(res.data.msg)
  } catch (e) { showFailToast('导入失败') }
  finally { importing.value = false }
}
</script>

<template>
  <div class="import-page">
    <van-nav-bar title="导入书单" left-arrow @click-left="$router.push('/')" />
    <div v-if="booklistInfo" class="m-card" style="margin: 16px;">
      <h3>📋 {{ booklistInfo.name }}</h3>
      <p v-if="booklistInfo.description" style="color: var(--color-text-muted); margin: 6px 0 12px;">{{ booklistInfo.description }}</p>
      <div v-for="b in booklistInfo.books" :key="b.id" class="book-row" @click="$router.push(`/book/${b.id}`)">
        <img :src="b.coverUrl || defaultCover" class="book-thumb"  alt=""/>
        <div class="book-info"><div class="book-t">{{ b.title }}</div><div class="book-a">{{ b.author }}</div></div>
      </div>
      <van-button type="primary" block round :loading="importing" @click="importBooklist" style="margin-top: 16px;">
        一键导入到我的书单
      </van-button>
    </div>
    <van-empty v-else description="书单加载中..." />
  </div>
</template>

<style scoped>
.import-page { min-height: 100vh; background: var(--color-bg); }
.book-row { display: flex; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--color-border-light); }
.book-thumb { width: 40px; height: 55px; object-fit: cover; border-radius: 4px; flex-shrink: 0; }
.book-info { flex: 1; }
.book-t { font-weight: 600; font-size: 14px; }
.book-a { font-size: 12px; color: var(--color-text-muted); }
</style>
