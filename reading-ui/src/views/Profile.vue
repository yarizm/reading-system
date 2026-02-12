<template>
  <div class="profile-container">
    <div class="profile-header">
      <h2>个人中心</h2>
      <el-button type="default" @click="goHome">
        <el-icon style="margin-right: 5px"><HomeFilled /></el-icon>
        返回首页
      </el-button>
    </div>

    <el-row :gutter="20">
      <el-col :span="8">
        <el-card class="box-card user-card">
          <div class="user-header">
            <el-avatar
                :size="100"
                :src="userInfo.avatar || defaultAvatar"
                @error="() => true"
            >
              <img :src="defaultAvatar"/>
            </el-avatar>
            <h2 class="username">{{ userInfo.username }}</h2>
            <el-tag :type="userInfo.role === 1 ? 'danger' : 'success'">
              {{ userInfo.role === 1 ? '管理员' : '普通用户' }}
            </el-tag>
          </div>
          <div class="user-info-list">
            <div class="info-item">
              <el-icon><User /></el-icon>
              <span>昵称：{{ userInfo.nickname || '未设置' }}</span>
            </div>
            <div class="info-item">
              <el-icon><Calendar /></el-icon>
              <span>注册时间：{{ formatTime(userInfo.createTime) }}</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="16">
        <el-card class="box-card">
          <template #header>
            <div class="card-header">
              <span>个人设置</span>
            </div>
          </template>

          <el-tabs v-model="activeTab">
            <el-tab-pane label="基本资料" name="info">
              <el-form label-width="80px" style="margin-top: 20px; max-width: 500px">
                <el-form-item label="昵称">
                  <el-input v-model="form.nickname" placeholder="请输入昵称" />
                </el-form-item>

                <el-form-item label="头像">
                  <el-upload
                      class="avatar-uploader"
                      action="/api/sysBook/upload"
                      :show-file-list="false"
                      :on-success="handleAvatarSuccess"
                      :before-upload="beforeAvatarUpload"
                  >
                    <img v-if="form.avatar" :src="form.avatar" class="avatar" />
                    <el-icon v-else class="avatar-uploader-icon"><Plus /></el-icon>

                    <div class="upload-tip" v-if="!form.avatar">点击上传头像</div>
                  </el-upload>
                  <div style="font-size: 12px; color: #999; margin-top: 5px;">
                    支持 JPG/PNG 格式，文件小于 10MB
                  </div>
                </el-form-item>

                <el-form-item>
                  <el-button type="primary" @click="updateProfile">保存修改</el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>

            <el-tab-pane label="修改密码" name="password">
              <el-form
                  ref="pwdFormRef"
                  :model="pwdForm"
                  :rules="pwdRules"
                  label-width="100px"
                  style="margin-top: 20px; max-width: 500px"
              >
                <el-form-item label="新密码" prop="password">
                  <el-input v-model="pwdForm.password" type="password" show-password />
                </el-form-item>
                <el-form-item label="确认密码" prop="confirmPassword">
                  <el-input v-model="pwdForm.confirmPassword" type="password" show-password />
                </el-form-item>
                <el-form-item>
                  <el-button type="danger" @click="changePassword">确认重置</el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>

    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { User, Calendar, HomeFilled, Plus } from '@element-plus/icons-vue' // 引入新图标
import { ElMessage } from 'element-plus'
import axios from 'axios'
import { useRouter } from 'vue-router'

const router = useRouter()
// 默认头像
const defaultAvatar = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
const activeTab = ref('info')

// 当前展示的用户信息
const userInfo = ref({})

// 修改资料表单
const form = reactive({
  id: null,
  nickname: '',
  avatar: ''
})

// 修改密码表单
const pwdForm = reactive({
  password: '',
  confirmPassword: ''
})

// 密码校验规则
const pwdFormRef = ref(null)
const validatePass2 = (rule, value, callback) => {
  if (value === '') {
    callback(new Error('请再次输入密码'))
  } else if (value !== pwdForm.password) {
    callback(new Error('两次输入密码不一致!'))
  } else {
    callback()
  }
}
const pwdRules = {
  password: [{ required: true, message: '请输入新密码', trigger: 'blur' }, { min: 3, message: '密码长度至少3位', trigger: 'blur' }],
  confirmPassword: [{ validator: validatePass2, trigger: 'blur' }]
}

// 初始化加载
onMounted(() => {
  const userStr = localStorage.getItem('user')
  if (userStr) {
    const user = JSON.parse(userStr)
    userInfo.value = user
    // 初始化表单数据
    form.id = user.id
    form.nickname = user.nickname
    form.avatar = user.avatar
  } else {
    router.push('/login')
  }
})

// === 新增：返回首页 ===
const goHome = () => {
  router.push('/')
}

// === 新增：头像上传处理 ===
// 复用之前的上传接口 /api/sysBook/upload，因为它是通用的文件上传
// 如果你想要更规范，可以在后端加一个 /api/sysUser/upload，但功能是一样的
const handleAvatarSuccess = (response) => {
  // 假设后端直接返回文件URL字符串，或者是 result.data
  // 根据之前的代码，result.data 是 url
  form.avatar = response.data
  ElMessage.success('头像上传成功')
}

const beforeAvatarUpload = (rawFile) => {
  const isImage = rawFile.type === 'image/jpeg' || rawFile.type === 'image/png';
  const isLt2M = rawFile.size / 1024 / 1024 < 10;

  if (!isImage) {
    ElMessage.error('上传头像图片只能是 JPG/PNG 格式!');
    return false;
  }
  if (!isLt2M) {
    ElMessage.error('上传头像图片大小不能超过 2MB!');
    return false;
  }
  return true;
}

// === 方法 1: 更新基本资料 ===
const updateProfile = async () => {
  try {
    const res = await axios.post('/api/sysUser/update', form)
    if (res.data.code === '200') {
      ElMessage.success('个人资料更新成功')

      // 关键步骤：更新本地缓存
      const newUser = res.data.data
      localStorage.setItem('user', JSON.stringify(newUser))

      // 更新当前页面显示
      userInfo.value = newUser

      // 这里的 form.avatar 已经是最新的了，不需要额外赋值
    } else {
      ElMessage.error(res.data.msg)
    }
  } catch (e) {
    console.error(e)
    ElMessage.error('更新失败')
  }
}

// === 方法 2: 修改密码 ===
const changePassword = () => {
  pwdFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        await axios.post('/api/sysUser/password', {
          id: userInfo.value.id,
          password: pwdForm.password
        })
        ElMessage.success('密码修改成功，请重新登录')

        // 登出逻辑
        localStorage.removeItem('user')
        router.push('/login')
      } catch (e) {
        ElMessage.error('修改失败')
      }
    }
  })
}

const formatTime = (timeStr) => {
  if(!timeStr) return '未知'
  return timeStr.split('T')[0]
}
</script>

<style scoped>
.profile-container {
  max-width: 960px;
  margin: 24px auto;
  padding: 0 24px;
}

/* === 顶部头部 === */
.profile-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  border-bottom: 1px solid #e8e0d6;
  padding-bottom: 14px;
}
.profile-header h2 {
  font-family: 'Noto Serif SC', serif;
  color: #2e2520;
  font-weight: 600;
}

/* === 用户卡片 === */
.user-card {
  text-align: center;
  padding-bottom: 18px;
  border: 1px solid #e8e0d6;
  position: relative;
  overflow: hidden;
}
.user-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: linear-gradient(90deg, #8b6f52, #c09a5c);
}

.user-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 18px;
  padding-top: 8px;
}

.username {
  margin: 10px 0;
  font-size: 20px;
  font-family: 'Noto Serif SC', serif;
  color: #2e2520;
}

.user-info-list {
  text-align: left;
  padding: 0 18px;
}

.info-item {
  display: flex;
  align-items: center;
  margin-bottom: 14px;
  font-size: 13px;
  color: #6b5e53;
  border-bottom: 1px dashed #e8e0d6;
  padding-bottom: 10px;
}

.info-item .el-icon {
  margin-right: 10px;
  font-size: 16px;
  color: #8b6f52;
}

/* === 右侧设置卡片 === */
.box-card {
  border: 1px solid #e8e0d6;
}
.card-header span {
  font-family: 'Noto Serif SC', serif;
  font-weight: 600;
  color: #3d3632;
}

/* === 头像上传 === */
.avatar-uploader {
  border: 1px dashed #d4c8ba;
  border-radius: 4px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  width: 100px;
  height: 100px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: border-color 0.2s;
}
.avatar-uploader:hover {
  border-color: #8b6f52;
}
.avatar-uploader-icon {
  font-size: 24px;
  color: #b5a99c;
}
.avatar {
  width: 100px;
  height: 100px;
  display: block;
  object-fit: cover;
}
.upload-tip {
  position: absolute;
  bottom: 4px;
  width: 100%;
  text-align: center;
  font-size: 11px;
  color: #9b8e82;
  background: rgba(255,253,249,0.85);
}
</style>