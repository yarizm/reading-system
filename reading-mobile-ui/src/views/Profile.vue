<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast, showSuccessToast, showToast } from 'vant'
import axios from 'axios'
import { useAuthStore } from '../stores/auth'
import { getCachedImage } from '../utils/imageCache'

const router = useRouter()
const authStore = useAuthStore()
const defaultAvatar = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
const userInfo = ref({})
const activeTab = ref('info')

const form = reactive({ id: null, nickname: '', avatar: '', age: null, infoVisible: 1 })
const pwdForm = reactive({ oldPassword: '', password: '', confirmPassword: '' })

const displayAvatar = ref(defaultAvatar)
const formDisplayAvatar = ref(defaultAvatar)

watch(() => userInfo.value.avatar, async (newVal) => {
  displayAvatar.value = newVal ? await getCachedImage(newVal) : defaultAvatar
}, { immediate: true })

watch(() => form.avatar, async (newVal) => {
  formDisplayAvatar.value = newVal ? await getCachedImage(newVal) : defaultAvatar
}, { immediate: true })

onMounted(() => {
  const user = authStore.user
  if (!user) {
    router.push('/login')
    return
  }
  userInfo.value = user
  form.id = user.id
  form.nickname = user.nickname
  form.avatar = user.avatar
  form.age = user.age
  form.infoVisible = user.infoVisible !== undefined ? user.infoVisible : 1
})

const handleAvatarUpload = async (file) => {
  const formData = new FormData()
  formData.append('file', file.file)
  try {
    const res = await axios.post('/api/sysBook/upload', formData)
    form.avatar = res.data.data
    showSuccessToast('头像上传成功')
  } catch (error) {
    showFailToast('头像上传失败')
  }
}

const updateProfile = async () => {
  try {
    const res = await axios.post('/api/sysUser/update', form)
    if (res.data.code === '200') {
      const nextUser = res.data.data
      authStore.login(nextUser)
      userInfo.value = nextUser
      showSuccessToast('资料已更新')
    } else {
      showFailToast(res.data.msg)
    }
  } catch (error) {
    showFailToast('资料更新失败')
  }
}

const changePassword = async () => {
  if (!pwdForm.oldPassword) {
    showToast('请输入旧密码')
    return
  }
  if (!pwdForm.password || pwdForm.password.length < 3) {
    showToast('密码至少需要 3 位')
    return
  }
  if (pwdForm.password !== pwdForm.confirmPassword) {
    showToast('两次密码输入不一致')
    return
  }
  try {
    await axios.post('/api/sysUser/password', {
      id: userInfo.value.id,
      oldPassword: pwdForm.oldPassword,
      password: pwdForm.password
    })
    showSuccessToast('密码修改成功，请重新登录')
    authStore.logout()
    router.push('/login')
  } catch (error) {
    showFailToast('密码修改失败')
  }
}

const logout = () => {
  authStore.logout()
  showSuccessToast('已退出登录')
  router.push('/')
}
</script>

<template>
  <div class="profile-page">
    <section class="profile-card">
      <div class="card-eyebrow">Account Center</div>
      <img :src="displayAvatar" class="user-avatar" style="border-radius: 50%; width: 78px; height: 78px; object-fit: cover;" />
      <h1 class="user-name">{{ userInfo.nickname || userInfo.username }}</h1>
      <div class="user-meta">{{ userInfo.role === 1 ? '管理员账号' : '普通用户账号' }}</div>
    </section>

    <van-tabs v-model:active="activeTab" animated class="tabs-card">
      <van-tab title="基本资料" name="info">
        <div class="form-card">
          <van-field v-model="form.nickname" label="昵称" placeholder="请输入昵称" maxlength="20" />
          <van-field v-model="form.age" label="年龄" type="digit" placeholder="可选" />
          <van-cell title="资料公开" center>
            <template #right-icon>
              <van-switch v-model="form.infoVisible" :active-value="1" :inactive-value="0" size="20" />
            </template>
          </van-cell>
          <van-cell title="头像">
            <template #right-icon>
              <van-uploader :after-read="handleAvatarUpload" :max-count="1" :preview-image="false">
                <img :src="formDisplayAvatar" style="border-radius: 50%; width: 52px; height: 52px; object-fit: cover;" />
              </van-uploader>
            </template>
          </van-cell>
          <div class="action-block">
            <van-button type="primary" block round @click="updateProfile">保存修改</van-button>
          </div>
        </div>
      </van-tab>

      <van-tab title="修改密码" name="password">
        <div class="form-card">
          <van-field v-model="pwdForm.oldPassword" label="旧密码" type="password" placeholder="请输入旧密码" />
          <van-field v-model="pwdForm.password" label="新密码" type="password" placeholder="请输入新密码" />
          <van-field v-model="pwdForm.confirmPassword" label="确认密码" type="password" placeholder="请再次输入" />
          <div class="action-block">
            <van-button type="danger" block round @click="changePassword">确认修改</van-button>
          </div>
        </div>
      </van-tab>
    </van-tabs>

    <section class="menu-card">
      <van-cell title="我的书籍" is-link to="/my-books" icon="notes-o" />
      <van-cell title="阅读洞察" is-link to="/insights" icon="bar-chart-o" />
    </section>

    <div class="logout-block">
      <van-button block round plain type="danger" @click="logout">退出登录</van-button>
    </div>
  </div>
</template>

<style scoped>
.profile-page {
  min-height: 100vh;
  padding: 18px 16px calc(72px + var(--safe-bottom));
  background:
    radial-gradient(circle at top left, rgba(214, 191, 165, 0.22), transparent 28%),
    linear-gradient(180deg, #f8f2ea 0%, #f5eee4 42%, #faf6f0 100%);
}

.profile-card,
.tabs-card,
.form-card,
.menu-card {
  border-radius: 22px;
  background: rgba(255, 252, 247, 0.96);
  box-shadow: 0 18px 38px rgba(93, 67, 43, 0.08);
}

.profile-card {
  padding: 22px 18px;
  text-align: center;
  background: linear-gradient(145deg, rgba(255, 250, 244, 0.98), rgba(246, 234, 220, 0.92));
}

.card-eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.16em;
  color: #a17752;
  text-transform: uppercase;
}

.user-avatar {
  margin-top: 12px;
}

.user-name {
  margin: 12px 0 4px;
  font-family: var(--font-serif), serif;
  font-size: 24px;
  color: #3d2c1f;
}

.user-meta {
  font-size: 13px;
  color: #806957;
}

.tabs-card {
  margin-top: 16px;
  padding: 8px 0 0;
}

.form-card {
  margin: 10px 12px 12px;
  padding: 8px 0;
  box-shadow: none;
  background: rgba(250, 245, 238, 0.86);
}

.action-block,
.logout-block {
  padding: 18px 16px 0;
}

.logout-block {
  padding-left: 32px;
  padding-right: 32px;
}

.menu-card {
  margin: 16px 16px 0;
  padding: 4px 0;
  overflow: hidden;
}

.menu-card :deep(.van-cell) {
  background: transparent;
  color: #3d2c1f;
}
</style>
