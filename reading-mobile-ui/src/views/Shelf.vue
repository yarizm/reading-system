<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showSuccessToast, showFailToast, showConfirmDialog } from 'vant'
import axios from 'axios'

const router = useRouter()
const userInfo = ref({})
const shelfList = ref([])
const shelfVisible = ref(1)
const booklists = ref([])
const showCreateSheet = ref(false)
const showBooklistPopup = ref(false)
const newBooklist = ref({ name: '', description: '' })
const detailBooklist = ref(null)
const showDetailPopup = ref(false)

onMounted(() => {
  const u = localStorage.getItem('user')
  if (u) { userInfo.value = JSON.parse(u); shelfVisible.value = userInfo.value.shelfVisible ?? 1; loadShelf(); loadBooklists() }
  else { showToast('请先登录'); router.push('/login') }
})

const loadShelf = async () => {
  const res = await axios.get(`/api/bookshelf/list/${userInfo.value.id}`)
  if (res.data.code === '200') shelfList.value = res.data.data
}

const loadBooklists = async () => {
  const res = await axios.get(`/api/booklist/list/${userInfo.value.id}`)
  if (res.data.code === '200') booklists.value = res.data.data
}

const continueRead = (bookId) => router.push(`/read/${bookId}`)

const removeFromShelf = async (id) => {
  await showConfirmDialog({ message: '确定移出书架？' })
  await axios.delete(`/api/bookshelf/remove/${id}`)
  showSuccessToast('已移出'); loadShelf()
}

const calcPercent = (item) => {
  const t = item.totalChapters || 0, c = item.currentChapterIndex || 0
  if (!t) return 0
  let p = Math.round(((c + 1) / t) * 100)
  return p > 100 ? 100 : p
}

const formatTime = (s) => s ? s.split('T')[0] : '刚刚'

const toggleVisibility = async (val) => {
  await axios.post('/api/sysUser/update', { id: userInfo.value.id, shelfVisible: val })
  userInfo.value.shelfVisible = val
  localStorage.setItem('user', JSON.stringify(userInfo.value))
  showSuccessToast(val === 1 ? '书架已公开' : '书架已私密')
}

const createBooklist = async () => {
  if (!newBooklist.value.name.trim()) return
  const res = await axios.post('/api/booklist/create', { userId: userInfo.value.id, name: newBooklist.value.name.trim(), description: newBooklist.value.description.trim() })
  if (res.data.code === '200') { showSuccessToast('创建成功'); showCreateSheet.value = false; newBooklist.value = { name: '', description: '' }; loadBooklists() }
}

const deleteBooklist = async (id) => {
  await showConfirmDialog({ message: '确定删除此书单？' })
  await axios.delete(`/api/booklist/delete/${id}`); showSuccessToast('已删除'); loadBooklists()
}

const viewDetail = async (id) => {
  const res = await axios.get(`/api/booklist/detail/${id}`)
  if (res.data.code === '200') { detailBooklist.value = res.data.data; showDetailPopup.value = true }
}

const removeBookFromList = async (blId, bookId) => {
  await axios.delete(`/api/booklist/removeBook?booklistId=${blId}&bookId=${bookId}`)
  showSuccessToast('已移除'); viewDetail(blId)
}

const copyShareLink = (bl) => {
  const link = `${window.location.origin}/shelf/import/${bl.shareCode}`
  navigator.clipboard?.writeText(link).then(() => showSuccessToast('链接已复制'))
    .catch(() => showToast(link))
}

const addBookToList = async (booklistId, bookId) => {
  const res = await axios.post('/api/booklist/addBook', { booklistId, bookId })
  if (res.data.code === '200') showSuccessToast('已添加')
  else showToast(res.data.msg)
}
</script>

<template>
  <div class="shelf-page">
    <div class="shelf-header">
      <h2>📚 我的书架</h2>
      <div class="shelf-actions">
        <van-switch v-model="shelfVisible" :active-value="1" :inactive-value="0" size="20" @change="toggleVisibility" />
        <span class="vis-text">{{ shelfVisible === 1 ? '公开' : '私密' }}</span>
      </div>
    </div>

    <div class="shelf-tools">
      <van-button size="small" plain round icon="bars" @click="showBooklistPopup = true">书单</van-button>
      <van-button size="small" plain round icon="plus" @click="showCreateSheet = true">创建</van-button>
    </div>

    <van-empty v-if="shelfList.length === 0" description="书架空空，快去首页看看" image="search">
      <van-button type="primary" round size="small" @click="$router.push('/')">去逛逛</van-button>
    </van-empty>

    <div class="shelf-grid">
      <div v-for="item in shelfList" :key="item.id" class="shelf-card" @click="continueRead(item.bookId)">
        <div class="card-cover-box">
          <img :src="item.coverUrl || 'https://via.placeholder.com/120x160'" class="card-cover cover-aspect" />
          <van-tag v-if="calcPercent(item) >= 100" type="success" class="done-tag">已读完</van-tag>
        </div>
        <div class="card-info">
          <div class="card-title">{{ item.bookName }}</div>
          <div class="card-author">{{ item.author }}</div>
          <van-progress :percentage="calcPercent(item)" :stroke-width="4" color="#8b6f52" track-color="#e8e0d6" :show-pivot="false" />
          <div class="card-meta">
            <span class="card-time">{{ formatTime(item.lastReadTime) }}</span>
            <van-icon name="delete-o" size="16" color="#ee4d38" @click.stop="removeFromShelf(item.id)" />
          </div>
        </div>
      </div>
    </div>

    <!-- Create Booklist -->
    <van-popup v-model:show="showCreateSheet" position="bottom" round>
      <div style="padding: 24px 20px 40px;">
        <h3>创建书单</h3>
        <van-field v-model="newBooklist.name" label="名称" placeholder="给书单起个名字" maxlength="30" />
        <van-field v-model="newBooklist.description" label="简介" placeholder="可选" type="textarea" rows="2" maxlength="200" />
        <van-button type="primary" block round style="margin-top: 16px;" @click="createBooklist" :disabled="!newBooklist.name.trim()">创建</van-button>
      </div>
    </van-popup>

    <!-- Booklists -->
    <van-popup v-model:show="showBooklistPopup" position="right" :style="{ width: '80%', height: '100%' }">
      <div style="padding: 20px 16px;">
        <h3>📋 我的书单</h3>
        <van-empty v-if="booklists.length === 0" description="暂无书单" />
        <div v-for="bl in booklists" :key="bl.id" class="bl-item">
          <div @click="viewDetail(bl.id)">
            <div class="bl-name">{{ bl.name }}</div>
            <div v-if="bl.description" class="bl-desc">{{ bl.description }}</div>
          </div>
          <div class="bl-actions">
            <van-button size="mini" plain @click="viewDetail(bl.id)">查看</van-button>
            <van-button size="mini" plain @click="copyShareLink(bl)">分享</van-button>
            <van-button size="mini" plain type="danger" @click="deleteBooklist(bl.id)">删除</van-button>
          </div>
        </div>
      </div>
    </van-popup>

    <!-- Booklist Detail -->
    <van-popup v-model:show="showDetailPopup" position="bottom" round :style="{ maxHeight: '70%' }">
      <div v-if="detailBooklist" style="padding: 20px;">
        <h3>{{ detailBooklist.name }}</h3>
        <p v-if="detailBooklist.description" style="color: var(--color-text-muted); margin-bottom: 12px;">{{ detailBooklist.description }}</p>
        <van-empty v-if="!detailBooklist.books?.length" description="书单为空" />
        <div v-for="b in detailBooklist.books" :key="b.id" class="detail-book-row">
          <img :src="b.coverUrl || 'https://via.placeholder.com/40x55'" class="detail-cover" />
          <div class="detail-info"><div class="detail-title">{{ b.title }}</div><div class="detail-author">{{ b.author }}</div></div>
          <van-icon name="delete-o" color="#ee4d38" @click="removeBookFromList(detailBooklist.id, b.id)" />
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.shelf-page { padding: 16px 16px 70px; }
.shelf-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.shelf-header h2 { font-family: var(--font-serif); font-size: 20px; margin: 0; }
.shelf-actions { display: flex; align-items: center; gap: 6px; }
.vis-text { font-size: 12px; color: var(--color-text-muted); }
.shelf-tools { display: flex; gap: 8px; margin-bottom: 16px; }

.shelf-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.shelf-card { background: var(--color-bg-card); border-radius: 10px; overflow: hidden; box-shadow: 0 2px 8px rgba(60,40,20,0.05); }
.card-cover-box { position: relative; }
.card-cover { width: 100%; height: 180px; object-fit: cover; }
.done-tag { position: absolute; top: 6px; right: 6px; }
.card-info { padding: 10px 12px; }
.card-title { font-size: 14px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-bottom: 2px; }
.card-author { font-size: 12px; color: var(--color-text-muted); margin-bottom: 8px; }
.card-meta { display: flex; justify-content: space-between; align-items: center; margin-top: 8px; }
.card-time { font-size: 11px; color: var(--color-text-muted); }

.bl-item { padding: 12px 0; border-bottom: 1px solid var(--color-border-light); }
.bl-name { font-weight: 600; font-size: 15px; margin-bottom: 4px; }
.bl-desc { font-size: 13px; color: var(--color-text-muted); }
.bl-actions { display: flex; gap: 6px; margin-top: 8px; }

.detail-book-row { display: flex; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--color-border-light); }
.detail-cover { width: 40px; height: 55px; object-fit: cover; border-radius: 4px; flex-shrink: 0; }
.detail-info { flex: 1; }
.detail-title { font-weight: 600; font-size: 14px; }
.detail-author { font-size: 12px; color: var(--color-text-muted); }
</style>
