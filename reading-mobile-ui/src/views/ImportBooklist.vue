<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showFailToast, showSuccessToast, showToast } from 'vant'
import axios from 'axios'

const route = useRoute()
const router = useRouter()
const shareCode = route.params.shareCode
const defaultCover = 'https://via.placeholder.com/80x110'

const booklistInfo = ref(null)
const userInfo = ref({})
const importing = ref(false)

onMounted(async () => {
  const user = localStorage.getItem('user')
  if (user) userInfo.value = JSON.parse(user)

  try {
    const res = await axios.get(`/api/booklist/share/${shareCode}`)
    if (res.data.code === '200') {
      booklistInfo.value = res.data.data
    } else {
      showFailToast(res.data.msg || '书单不存在')
    }
  } catch (error) {
    showFailToast('书单加载失败')
  }
})

const importBooklist = async () => {
  if (!userInfo.value.id) {
    showToast('请先登录')
    return
  }
  importing.value = true
  try {
    const res = await axios.post(`/api/booklist/import/${shareCode}`, {
      userId: userInfo.value.id
    })
    if (res.data.code === '200') {
      showSuccessToast('书单导入成功')
      router.push('/shelf')
    } else {
      showFailToast(res.data.msg)
    }
  } catch (error) {
    showFailToast('书单导入失败')
  } finally {
    importing.value = false
  }
}
</script>

<template>
  <div class="import-page">
    <van-nav-bar title="导入书单" left-arrow @click-left="$router.push('/')" />

    <section v-if="booklistInfo" class="import-card">
      <div class="card-eyebrow">Shared Booklist</div>
      <h1 class="card-title">{{ booklistInfo.name }}</h1>
      <p v-if="booklistInfo.description" class="card-desc">{{ booklistInfo.description }}</p>

      <div class="book-list">
        <article v-for="book in booklistInfo.books" :key="book.id" class="book-row" @click="$router.push(`/book/${book.id}`)">
          <img :src="book.coverUrl || defaultCover" class="book-thumb" alt="" />
          <div class="book-info">
            <div class="book-title">{{ book.title }}</div>
            <div class="book-author">{{ book.author || '作者未知' }}</div>
          </div>
        </article>
      </div>

      <van-button type="primary" block round class="import-btn" :loading="importing" @click="importBooklist">
        一键导入到我的书单
      </van-button>
    </section>

    <van-empty v-else description="正在加载书单..." />
  </div>
</template>

<style scoped>
.import-page {
  min-height: 100vh;
  padding-bottom: calc(48px + var(--safe-bottom));
  background:
    radial-gradient(circle at top left, rgba(214, 191, 165, 0.18), transparent 28%),
    linear-gradient(180deg, #f8f2ea 0%, #f5eee4 44%, #faf6f0 100%);
}

.import-card {
  margin: 16px;
  padding: 18px;
  border-radius: 22px;
  background: rgba(255, 252, 247, 0.96);
  box-shadow: 0 18px 38px rgba(93, 67, 43, 0.08);
}

.card-eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #a17752;
}

.card-title {
  margin: 8px 0 6px;
  font-family: var(--font-serif), serif;
  font-size: 26px;
  color: #3d2c1f;
}

.card-desc {
  margin: 0 0 14px;
  font-size: 13px;
  line-height: 1.75;
  color: #78614d;
}

.book-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.book-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 16px;
  background: rgba(250, 245, 238, 0.92);
}

.book-thumb {
  width: 44px;
  height: 60px;
  object-fit: cover;
  border-radius: 8px;
  flex-shrink: 0;
}

.book-info {
  flex: 1;
  min-width: 0;
}

.book-title {
  font-size: 15px;
  font-weight: 700;
  color: #3d2c1f;
}

.book-author {
  margin-top: 4px;
  font-size: 12px;
  color: #8a725d;
}

.import-btn {
  margin-top: 16px;
}
</style>
