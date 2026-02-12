<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'

const router = useRouter()

// 控制是登录还是注册模式 (true: 登录, false: 注册)
const isLoginMode = ref(true)

// 表单数据
const form = reactive({
  username: '',
  password: '',
  nickname: '',
  age: 18 // 默认年龄
})

// 提交处理
const handleSubmit = async () => {
  // 简单的非空校验
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }

  try {
    if (isLoginMode.value) {
      // === 登录逻辑 ===
      const res = await axios.post('/api/sysUser/login', {
        username: form.username,
        password: form.password
      })

      if (res.data.code === '200') {
        ElMessage.success('登录成功！')
        // 把用户信息存到 localStorage，方便后续使用
        localStorage.setItem('user', JSON.stringify(res.data.data))
        // 跳转到首页
        router.push('/')
      } else {
        ElMessage.error(res.data.msg || '登录失败')
      }

    } else {
      // === 注册逻辑 ===
      const res = await axios.post('/api/sysUser/register', form)

      if (res.data.code === '200') {
        ElMessage.success('注册成功，请登录！')
        // 注册成功后，切换回登录模式
        isLoginMode.value = true
      } else {
        ElMessage.error(res.data.msg || '注册失败')
      }
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('服务器连接失败')
  }
}
</script>

<template>
  <div class="login-container">
    <div class="login-card">
      <div class="brand-strip"></div>

      <div class="title">
        <h2>{{ isLoginMode ? '欢迎回来' : '加入我们' }}</h2>
        <p>智慧阅读 · 让每一页都有温度</p>
      </div>

      <el-form label-position="top" class="login-form">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" prefix-icon="User" />
        </el-form-item>

        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" prefix-icon="Lock" show-password />
        </el-form-item>

        <div v-if="!isLoginMode" class="register-fields">
          <el-form-item label="昵称">
            <el-input v-model="form.nickname" placeholder="给自己取个名字" prefix-icon="Postcard" />
          </el-form-item>
          <el-form-item label="年龄 (用于推荐)">
            <el-input-number v-model="form.age" :min="1" :max="120" />
          </el-form-item>
        </div>

        <el-button type="primary" class="submit-btn" @click="handleSubmit">
          {{ isLoginMode ? '登 录' : '注 册' }}
        </el-button>

        <div class="toggle-link">
          <span @click="isLoginMode = !isLoginMode">
            {{ isLoginMode ? '没有账号？立即注册 →' : '← 已有账号？返回登录' }}
          </span>
        </div>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.login-container {
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: #f0ece4;
  background-image:
    radial-gradient(ellipse at 20% 50%, rgba(180,160,130,0.12) 0%, transparent 60%),
    radial-gradient(ellipse at 80% 20%, rgba(160,140,120,0.08) 0%, transparent 50%);
}

.login-card {
  width: 420px;
  background: #fffdf9;
  padding: 0 38px 36px;
  border-radius: 6px;
  border: 1px solid #e4ddd3;
  box-shadow: 0 1px 3px rgba(80, 60, 40, 0.06),
              0 8px 30px rgba(80, 60, 40, 0.04);
  position: relative;
  overflow: hidden;
}

.brand-strip {
  width: 60px;
  height: 4px;
  background: #8b6f52;
  margin: 36px auto 0;
  border-radius: 2px;
}

.title {
  text-align: center;
  margin: 24px 0 28px;
}

.title h2 {
  margin: 0;
  color: #2e2520;
  font-family: 'Noto Serif SC', serif;
  font-size: 26px;
  font-weight: 600;
  letter-spacing: 1px;
}

.title p {
  color: #9b8e82;
  margin-top: 8px;
  font-size: 13px;
  letter-spacing: 2px;
}

.login-form :deep(.el-form-item__label) {
  color: #6b5e53;
  font-weight: 500;
  font-size: 13px;
}

.login-form :deep(.el-input__wrapper) {
  border-radius: 4px;
  box-shadow: 0 0 0 1px #ddd5ca inset;
}

.login-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #b8a898 inset;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #8b6f52 inset;
}

.register-fields {
  animation: slideDown 0.3s ease;
}

@keyframes slideDown {
  from { opacity: 0; transform: translateY(-8px); }
  to   { opacity: 1; transform: translateY(0); }
}

.submit-btn {
  width: 100%;
  margin-top: 24px;
  height: 42px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 4px;
  background-color: #6b5040;
  border-color: #6b5040;
  border-radius: 4px;
  transition: background-color 0.2s;
}

.submit-btn:hover {
  background-color: #8b6f52;
  border-color: #8b6f52;
}

.toggle-link {
  text-align: center;
  margin-top: 18px;
  font-size: 13px;
  color: #9b8e82;
}

.toggle-link span {
  cursor: pointer;
  transition: color 0.2s;
}

.toggle-link span:hover {
  color: #6b5040;
  text-decoration: underline;
  text-underline-offset: 3px;
}
</style>