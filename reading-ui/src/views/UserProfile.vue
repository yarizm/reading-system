<template>
  <div class="user-profile-container" v-loading="loading">
    <!-- 用户不存在 -->
    <div v-if="error" class="error-state">
      <el-icon size="48" color="#c4b9ab"><WarningFilled /></el-icon>
      <h3>用户不存在</h3>
      <el-button @click="$router.go(-1)">返回</el-button>
    </div>

    <template v-if="profile && !error">
      <!-- 用户信息卡片 -->
      <div class="profile-card">
        <el-avatar :size="80" :src="profile.avatar || defaultAvatar" class="user-avatar" />
        <div class="user-info">
          <h2 class="user-nickname">{{ profile.nickname || '匿名用户' }}</h2>
          <div class="user-meta">
            <span v-if="profile.age" class="meta-tag">
              <el-icon><Calendar /></el-icon> {{ profile.age }} 岁
            </span>
            <span v-if="profile.preferences" class="meta-tag">
              <el-icon><CollectionTag /></el-icon> {{ profile.preferences }}
            </span>
            <span class="meta-tag">
              <el-icon><Calendar /></el-icon> {{ formatDate(profile.createTime) }} 加入
            </span>
            <el-tag v-if="profile.viewedByAdmin && profile.infoVisible === 0" type="danger" size="small" effect="dark" style="margin-left: 8px">
              管理员特权查看 (用户已设为私密)
            </el-tag>
          </div>
        </div>
      </div>

      <!-- 书架区域 -->
      <div class="shelf-section">
        <div class="section-header">
          <h3>📚 TA 的书架</h3>
          <span class="visibility-badge" :class="profile.shelfVisible === 1 ? 'public' : 'private'">
            {{ profile.shelfVisible === 1 ? '公开' : '私密' }}
          </span>
          <el-tag v-if="profile.viewedByAdmin && profile.shelfVisible === 0" type="danger" size="small" effect="dark">
            管理员特权查看
          </el-tag>
        </div>

        <!-- 书架私密（且非管理员查看时） -->
        <div v-if="profile.shelfVisible === 0 && !profile.viewedByAdmin" class="private-tip">
          <el-icon size="36" color="#c4b9ab"><Lock /></el-icon>
          <p>该用户的书架不对外公开</p>
        </div>

        <!-- 书架公开 -->
        <template v-else>
          <el-empty v-if="!profile.shelfList || profile.shelfList.length === 0" description="书架空空如也" />
          <div class="book-grid" v-else>
            <div
                class="book-card"
                v-for="item in profile.shelfList"
                :key="item.bookId"
                @click="$router.push(`/book/${item.bookId}`)"
            >
              <img :src="item.coverUrl || 'https://via.placeholder.com/120x160'" class="book-cover"  alt=""/>
              <div class="book-info">
                <div class="book-title">{{ item.bookName }}</div>
                <div class="book-author">{{ item.author }}</div>
              </div>
            </div>
          </div>
        </template>
      </div>

      <div class="back-row">
        <el-button plain @click="$router.go(-1)">← 返回</el-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'
import { WarningFilled, CollectionTag, Calendar, Lock } from '@element-plus/icons-vue'

const route = useRoute()
const profile = ref(null)
const loading = ref(true)
const error = ref(false)
const defaultAvatar = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'

onMounted(async () => {
  const userStr = localStorage.getItem('user')
  let viewerId = null
  if (userStr) {
    const u = JSON.parse(userStr)
    viewerId = u.id
  }

  try {
    const res = await axios.get(`/api/sysUser/profile/${route.params.id}`, {
      params: { viewerId }
    })
    if (res.data.code === '200') {
      profile.value = res.data.data
    } else {
      error.value = true
    }
  } catch (e) {
    error.value = true
  } finally {
    loading.value = false
  }
})

const formatDate = (timeStr) => {
  if (!timeStr) return '未知'
  return String(timeStr).split('T')[0]
}
</script>

<style scoped>
.user-profile-container {
  max-width: 900px;
  margin: 28px auto;
  padding: 0 24px;
}

/* === 用户卡片 === */
.profile-card {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 28px 32px;
  background: #fffdf9;
  border: 1px solid #e8e0d6;
  border-radius: 6px;
  margin-bottom: 28px;
}
.user-avatar {
  flex-shrink: 0;
  border: 2px solid #e8e0d6;
}
.user-nickname {
  font-family: 'Noto Serif SC', serif;
  color: #2e2520;
  font-size: 22px;
  margin: 0 0 8px;
}
.user-meta {
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
}
.meta-tag {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #9b8e82;
}

/* === 书架区域 === */
.shelf-section {
  margin-bottom: 28px;
}
.section-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e8e0d6;
}
.section-header h3 {
  font-family: 'Noto Serif SC', serif;
  color: #2e2520;
  margin: 0;
  font-size: 18px;
}
.visibility-badge {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 3px;
  font-weight: 600;
}
.visibility-badge.public {
  background: #edf7ed;
  color: #6a8c5a;
}
.visibility-badge.private {
  background: #f5eeee;
  color: #a05050;
}

/* 私密提示 */
.private-tip {
  text-align: center;
  padding: 48px 0;
  color: #9b8e82;
}
.private-tip p {
  margin-top: 12px;
  font-size: 15px;
}

/* === 书籍网格 === */
.book-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 18px;
}
.book-card {
  cursor: pointer;
  background: #fffdf9;
  border: 1px solid #e8e0d6;
  border-radius: 6px;
  overflow: hidden;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.book-card:hover {
  border-color: #c4b09a;
  box-shadow: 0 4px 12px rgba(60, 40, 20, 0.08);
}
.book-cover {
  width:100%;
  height: 180px;
  object-fit: cover;
  display: block;
  background: #f0ece4;
}
.book-info {
  padding: 10px 12px;
}
.book-title {
  font-weight: 600;
  font-size: 14px;
  color: #3d3632;
  margin-bottom: 3px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.book-author {
  font-size: 12px;
  color: #9b8e82;
}

/* === 返回 === */
.back-row {
  text-align: center;
  padding: 12px 0 40px;
}

/* === 错误状态 === */
.error-state {
  text-align: center;
  padding: 80px 0;
}
.error-state h3 {
  color: #7a6e63;
  margin: 16px 0;
}
</style>
