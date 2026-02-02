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
      <div class="title">
        <h2>{{ isLoginMode ? '欢迎登录' : '新用户注册' }}</h2>
        <p>智能书籍阅读系统</p>
      </div>

      <el-form label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" prefix-icon="User" />
        </el-form-item>

        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" prefix-icon="Lock" show-password />
        </el-form-item>

        <div v-if="!isLoginMode">
          <el-form-item label="昵称">
            <el-input v-model="form.nickname" placeholder="请输入昵称" prefix-icon="Postcard" />
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
            {{ isLoginMode ? '没有账号？去注册' : '已有账号？去登录' }}
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
  background: linear-gradient(135deg, #74ebd5 0%, #ACB6E5 100%);
}

.login-card {
  width: 400px;
  background: white;
  padding: 40px;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}

.title {
  text-align: center;
  margin-bottom: 30px;
}

.title h2 {
  margin: 0;
  color: #333;
}

.title p {
  color: #666;
  margin-top: 5px;
}

.submit-btn {
  width: 100%;
  margin-top: 20px;
  height: 40px;
  font-size: 16px;
}

.toggle-link {
  text-align: center;
  margin-top: 15px;
  font-size: 14px;
  color: #409EFF;
  cursor: pointer;
}

.toggle-link span:hover {
  text-decoration: underline;
}
</style>