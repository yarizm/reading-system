<template>
  <div class="import-container">
    <div class="import-card" v-loading="loading">
      <!-- 加载失败 -->
      <div v-if="error" class="error-state">
        <el-icon size="48" color="#c4b9ab"><WarningFilled /></el-icon>
        <h3>书单不存在或链接已失效</h3>
        <el-button @click="$router.push('/')">返回首页</el-button>
      </div>

      <!-- 书单内容 -->
      <template v-if="booklist && !error">
        <div class="list-header">
          <h2>📖 {{ booklist.name }}</h2>
          <p class="list-desc" v-if="booklist.description">{{ booklist.description }}</p>
          <p class="list-meta">共 {{ booklist.books?.length || 0 }} 本书</p>
        </div>

        <div class="book-list">
          <div class="book-item" v-for="book in booklist.books" :key="book.id">
            <img :src="book.coverUrl || 'https://via.placeholder.com/60x80'" class="mini-cover" />
            <div class="book-info">
              <div class="book-title">{{ book.title }}</div>
              <div class="book-author">{{ book.author }}</div>
              <div class="book-category">{{ book.category }}</div>
            </div>
          </div>
        </div>

        <div class="import-actions">
          <el-button type="primary" size="large" :loading="importing" @click="doImport" :disabled="!userInfo.id">
            <el-icon style="margin-right:6px"><Download /></el-icon>
            一键导入到我的书架
          </el-button>
          <p class="login-hint" v-if="!userInfo.id">
            请先 <span @click="$router.push('/login')">登录</span> 后导入
          </p>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { WarningFilled, Download } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const booklist = ref(null)
const loading = ref(true)
const error = ref(false)
const importing = ref(false)
const userInfo = ref({})

onMounted(async () => {
  const userStr = localStorage.getItem('user')
  if (userStr) userInfo.value = JSON.parse(userStr)

  try {
    const res = await axios.get(`/api/booklist/share/${route.params.shareCode}`)
    if (res.data.code === '200') {
      booklist.value = res.data.data
    } else {
      error.value = true
    }
  } catch (e) {
    error.value = true
  } finally {
    loading.value = false
  }
})

const doImport = async () => {
  importing.value = true
  try {
    const res = await axios.post(`/api/booklist/import/${route.params.shareCode}?userId=${userInfo.value.id}`)
    if (res.data.code === '200') {
      ElMessage.success(res.data.data || '导入成功')
      router.push('/shelf')
    } else {
      ElMessage.error(res.data.msg || '导入失败')
    }
  } catch (e) {
    ElMessage.error('导入失败')
  } finally {
    importing.value = false
  }
}
</script>

<style scoped>
.import-container {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 60px 24px;
  background-color: #faf8f5;
}
.import-card {
  width: 560px;
  background: #fffdf9;
  border: 1px solid #e8e0d6;
  border-radius: 6px;
  padding: 36px 32px;
}

/* === 头部 === */
.list-header {
  text-align: center;
  margin-bottom: 28px;
  padding-bottom: 18px;
  border-bottom: 1px solid #ede7de;
}
.list-header h2 {
  font-family: 'Noto Serif SC', serif;
  color: #2e2520;
  font-size: 22px;
  margin: 0 0 8px;
}
.list-desc {
  color: #7a6e63;
  font-size: 14px;
  margin: 0 0 6px;
}
.list-meta {
  color: #b5a99c;
  font-size: 13px;
  margin: 0;
}

/* === 书籍列表 === */
.book-list {
  margin-bottom: 28px;
}
.book-item {
  display: flex;
  gap: 14px;
  padding: 12px 0;
  border-bottom: 1px solid #f0ece4;
  align-items: center;
}
.book-item:last-child { border-bottom: none; }
.mini-cover {
  width: 48px;
  height: 64px;
  object-fit: cover;
  border-radius: 3px;
  flex-shrink: 0;
  background: #f0ece4;
}
.book-info { flex: 1; }
.book-title {
  font-weight: 600;
  font-size: 15px;
  color: #3d3632;
  margin-bottom: 3px;
}
.book-author { font-size: 13px; color: #9b8e82; }
.book-category { font-size: 12px; color: #c4b9ab; margin-top: 2px; }

/* === 底部操作 === */
.import-actions {
  text-align: center;
}
.import-actions :deep(.el-button--primary) {
  background-color: #5a4435;
  border-color: #5a4435;
  width: 100%;
  height: 44px;
  font-size: 15px;
  letter-spacing: 2px;
}
.import-actions :deep(.el-button--primary:hover) {
  background-color: #6b5040;
  border-color: #6b5040;
}
.login-hint {
  margin-top: 10px;
  color: #9b8e82;
  font-size: 13px;
}
.login-hint span {
  color: #8b6f52;
  cursor: pointer;
  font-weight: 600;
}

/* === 错误状态 === */
.error-state {
  text-align: center;
  padding: 40px 0;
}
.error-state h3 {
  color: #7a6e63;
  margin: 16px 0;
}
</style>
