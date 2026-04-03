<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showSuccessToast, showFailToast } from 'vant'
import axios from 'axios'

const router = useRouter()
const defaultAvatar = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
const userInfo = ref({})
const activeTab = ref('info')

const form = reactive({ id: null, nickname: '', avatar: '', age: null, infoVisible: 1 })
const pwdForm = reactive({ password: '', confirmPassword: '' })

onMounted(() => {
  const u = localStorage.getItem('user')
  if (u) {
    const user = JSON.parse(u); userInfo.value = user
    form.id = user.id; form.nickname = user.nickname; form.avatar = user.avatar; form.age = user.age
    form.infoVisible = user.infoVisible !== undefined ? user.infoVisible : 1
  } else { router.push('/login') }
})

const handleAvatarUpload = async (file) => {
  const formData = new FormData()
  formData.append('file', file.file)
  try {
    const res = await axios.post('/api/sysBook/upload', formData)
    form.avatar = res.data.data
    showSuccessToast('上传成功')
  } catch (e) { showFailToast('上传失败') }
}

const updateProfile = async () => {
  try {
    const res = await axios.post('/api/sysUser/update', form)
    if (res.data.code === '200') {
      showSuccessToast('更新成功')
      const newUser = res.data.data; localStorage.setItem('user', JSON.stringify(newUser)); userInfo.value = newUser
    } else showFailToast(res.data.msg)
  } catch (e) { showFailToast('更新失败') }
}

const changePassword = async () => {
  if (!pwdForm.password || pwdForm.password.length < 3) return showToast('密码至少3位')
  if (pwdForm.password !== pwdForm.confirmPassword) return showToast('两次密码不一致')
  try {
    await axios.post('/api/sysUser/password', { id: userInfo.value.id, password: pwdForm.password })
    showSuccessToast('密码修改成功'); localStorage.removeItem('user'); router.push('/login')
  } catch (e) { showFailToast('修改失败') }
}

const logout = () => {
  localStorage.removeItem('user'); showSuccessToast('已退出'); router.push('/')
}
</script>

<template>
  <div class="profile-page">
    <!-- User Card -->
    <div class="user-card">
      <div class="user-card-accent"></div>
      <van-image round width="72" height="72" :src="userInfo.avatar || defaultAvatar" class="user-avatar" />
      <h2 class="user-name">{{ userInfo.nickname || userInfo.username }}</h2>
      <van-tag :type="userInfo.role === 1 ? 'danger' : 'success'" round>
        {{ userInfo.role === 1 ? '管理员' : '普通用户' }}
      </van-tag>
    </div>

    <!-- Settings Tabs -->
    <van-tabs v-model:active="activeTab" animated class="setting-tabs">
      <van-tab title="基本资料" name="info">
        <div class="form-section">
          <van-field v-model="form.nickname" label="昵称" placeholder="请输入昵称" maxlength="20" />
          <van-field v-model="form.age" label="年龄" type="digit" placeholder="年龄" />
          <van-cell title="资料公开" center>
            <template #right-icon>
              <van-switch v-model="form.infoVisible" :active-value="1" :inactive-value="0" size="20" />
            </template>
          </van-cell>
          <van-cell title="头像">
            <template #right-icon>
              <van-uploader :after-read="handleAvatarUpload" :max-count="1" :preview-image="false">
                <van-image round width="50" height="50" :src="form.avatar || defaultAvatar" />
              </van-uploader>
            </template>
          </van-cell>
          <div style="padding: 20px 16px;">
            <van-button type="primary" block round @click="updateProfile">保存修改</van-button>
          </div>
        </div>
      </van-tab>
      <van-tab title="修改密码" name="password">
        <div class="form-section">
          <van-field v-model="pwdForm.password" label="新密码" type="password" placeholder="请输入新密码" />
          <van-field v-model="pwdForm.confirmPassword" label="确认密码" type="password" placeholder="再次输入" />
          <div style="padding: 20px 16px;">
            <van-button type="danger" block round @click="changePassword">确认重置</van-button>
          </div>
        </div>
      </van-tab>
    </van-tabs>

    <div style="padding: 20px 32px 80px;">
      <van-button block round plain type="danger" @click="logout">退出登录</van-button>
    </div>
  </div>
</template>

<style scoped>
.profile-page { background: var(--color-bg); min-height: 100vh; padding-bottom: 60px; }

.user-card {
  text-align: center; padding: 30px 0 20px;
  background: var(--color-bg-card);
  border-radius: 0 0 20px 20px;
  margin-bottom: 16px;
  position: relative;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(60,40,20,0.05);
}
.user-card-accent { position: absolute; top: 0; left: 0; right: 0; height: 4px; background: linear-gradient(90deg, #8b6f52, #c09a5c); }
.user-avatar { margin-bottom: 10px; }
.user-name { font-family: var(--font-serif); font-size: 20px; margin: 0 0 6px; }

.setting-tabs { margin: 0 16px; }
.form-section { padding-top: 10px; }
</style>
