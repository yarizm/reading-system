<template>
  <div class="login-container">
    <div class="login-card">
      <div class="brand-strip"></div>

      <div class="title">
        <h2>{{ pageTitle }}</h2>
        <p>智慧阅读 · 让每一页都有温度</p>
      </div>

      <!-- 登录/注册 Tabs (仅在非找回密码模式下显示) -->
      <div v-if="mode !== 'reset'" class="auth-tabs">
        <span :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</span>
        <span :class="{ active: mode === 'register' }" @click="mode = 'register'">注册</span>
      </div>

      <el-form label-position="top" class="login-form">

        <!-- === 登录模式 === -->
        <template v-if="mode === 'login'">
          <el-tabs v-model="loginMethod" class="login-method-tabs">
            <el-tab-pane label="密码登录" name="password"></el-tab-pane>
            <el-tab-pane label="验证码登录" name="code"></el-tab-pane>
          </el-tabs>

          <!-- 账号密码登录 -->
          <template v-if="loginMethod === 'password'">
            <el-form-item label="账号">
              <el-input v-model="form.username" placeholder="用户名/手机号/邮箱" prefix-icon="User" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="form.password" type="password" placeholder="请输入密码" prefix-icon="Lock" show-password @keyup.enter="handleLogin" />
            </el-form-item>
            <div class="forget-pass" @click="mode = 'reset'">忘记密码？</div>
          </template>

          <!-- 验证码登录 -->
          <template v-else>
            <el-form-item label="手机号/邮箱">
              <el-input v-model="form.target" placeholder="请输入手机或邮箱" prefix-icon="Iphone" />
            </el-form-item>
            <el-form-item label="验证码">
              <div class="code-row">
                <el-input v-model="form.code" placeholder="6位验证码" prefix-icon="Key" @keyup.enter="handleLogin" />
                <el-button :disabled="cooldown > 0" @click="sendCode(2)">
                  {{ cooldown > 0 ? `${cooldown}s` : '获取验证码' }}
                </el-button>
              </div>
            </el-form-item>
          </template>

          <el-button type="primary" class="submit-btn" :loading="loading" @click="handleLogin">
            登 录
          </el-button>
        </template>

        <!-- === 注册模式 === -->
        <template v-if="mode === 'register'">
          <el-form-item label="手机号/邮箱">
            <el-input v-model="regForm.target" placeholder="请输入手机或邮箱" prefix-icon="Iphone" />
          </el-form-item>
          
          <el-form-item label="验证码">
            <div class="code-row">
              <el-input v-model="regForm.code" placeholder="6位验证码" prefix-icon="Key" />
              <el-button :disabled="cooldown > 0" @click="sendCode(1)">
                {{ cooldown > 0 ? `${cooldown}s` : '获取验证码' }}
              </el-button>
            </div>
          </el-form-item>

          <el-form-item label="设置密码">
            <el-input v-model="regForm.password" type="password" placeholder="6-20位密码" prefix-icon="Lock" show-password />
          </el-form-item>
          
          <el-form-item label="昵称">
            <el-input v-model="regForm.nickname" placeholder="您的称呼" prefix-icon="Postcard" />
          </el-form-item>

          <el-form-item label="年龄">
             <el-input-number v-model="regForm.age" :min="1" :max="120" style="width: 100%" />
          </el-form-item>

          <el-button type="primary" class="submit-btn" :loading="loading" @click="handleRegister">
            立 即 注 册
          </el-button>
        </template>

        <!-- === 找回密码模式 === -->
        <template v-if="mode === 'reset'">
           <el-form-item label="手机号/邮箱">
            <el-input v-model="resetForm.target" placeholder="请输入注册时的手机或邮箱" prefix-icon="Iphone" />
          </el-form-item>

           <el-form-item label="验证码">
            <div class="code-row">
              <el-input v-model="resetForm.code" placeholder="6位验证码" prefix-icon="Key" />
              <el-button :disabled="cooldown > 0" @click="sendCode(3)">
                {{ cooldown > 0 ? `${cooldown}s` : '获取验证码' }}
              </el-button>
            </div>
          </el-form-item>

          <el-form-item label="新密码">
            <el-input v-model="resetForm.newPassword" type="password" placeholder="请输入新密码" prefix-icon="Lock" show-password />
          </el-form-item>

          <el-button type="danger" class="submit-btn" :loading="loading" @click="handleReset">
            重 置 密 码
          </el-button>
          
          <div class="back-login" @click="mode = 'login'">返回登录</div>
        </template>

      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import { User, Lock, Postcard, Iphone, Key } from '@element-plus/icons-vue'

const router = useRouter()
const mode = ref('login') // login, register, reset
const loginMethod = ref('password') // password, code
const loading = ref(false)
const cooldown = ref(0) // 验证码倒计时

const pageTitle = computed(() => {
  if (mode.value === 'login') return '欢迎回来'
  if (mode.value === 'register') return '加入我们'
  return '找回密码'
})

// 登录表单
const form = reactive({
  username: '',
  password: '',
  target: '',
  code: ''
})

// 注册表单
const regForm = reactive({
  target: '',
  code: '',
  password: '',
  nickname: '',
  age: 18
})

// 重置表单
const resetForm = reactive({
  target: '',
  code: '',
  newPassword: ''
})

// 发送验证码
const sendCode = async (type) => {
  let target = ''
  if (type === 1) target = regForm.target
  if (type === 2) target = form.target
  if (type === 3) target = resetForm.target

  if (!target) return ElMessage.warning('请输入手机号或邮箱')
  
  // 简单格式校验
  // const isPhone = /^1[3-9]\d{9}$/.test(target)
  // const isEmail = /.+@.+\..+/.test(target)
  // if (!isPhone && !isEmail) return ElMessage.warning('格式不正确')

  try {
    await axios.post('/api/auth/sendCode', { target, type })
    ElMessage.success('验证码发送成功 (请查看控制台)')
    
    // 启动倒计时
    cooldown.value = 60
    const timer = setInterval(() => {
      cooldown.value--
      if (cooldown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || '发送失败')
  }
}

// 处理登录
const handleLogin = async () => {
  loading.value = true
  try {
    let res
    if (loginMethod.value === 'password') {
       if (!form.username || !form.password) {
         loading.value = false
         return ElMessage.warning('请输入账号密码')
       }
       res = await axios.post('/api/sysUser/login', {
         username: form.username,
         password: form.password
       })
    } else {
       if (!form.target || !form.code) {
         loading.value = false
         return ElMessage.warning('请输入手机/邮箱和验证码')
       }
       res = await axios.post('/api/auth/loginByCode', {
         target: form.target,
         code: form.code
       })
    }

    if (res.data.code === '200') {
      ElMessage.success('登录成功')
      localStorage.setItem('user', JSON.stringify(res.data.data))
      router.push('/')
    } else {
      ElMessage.error(res.data.msg || '登录失败')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || '服务器异常')
  } finally {
    loading.value = false
  }
}

// 处理注册
const handleRegister = async () => {
  if (!regForm.target || !regForm.code || !regForm.password) {
    return ElMessage.warning('请填写必要信息')
  }
  loading.value = true
  try {
    const res = await axios.post('/api/auth/register', regForm)
    if (res.data.code === '200') {
      ElMessage.success('注册成功，请登录')
      mode.value = 'login'
    } else {
      ElMessage.error(res.data.msg || '注册失败')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || '注册失败')
  } finally {
    loading.value = false
  }
}

// 处理重置
const handleReset = async () => {
  if (!resetForm.target || !resetForm.code || !resetForm.newPassword) {
    return ElMessage.warning('请填写完整')
  }
  loading.value = true
  try {
    const res = await axios.post('/api/auth/resetPassword', resetForm)
     if (res.data.code === '200') {
      ElMessage.success('密码重置成功，请登录')
      mode.value = 'login'
    } else {
      ElMessage.error(res.data.msg || '重置失败')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || '重置失败')
  } finally {
    loading.value = false
  }
}
</script>

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
  margin: 24px 0 24px;
}
.title h2 { margin: 0; color: #2e2520; font-family: 'Noto Serif SC', serif; font-size: 26px; }
.title p { color: #9b8e82; margin-top: 8px; font-size: 13px; letter-spacing: 2px; }

/* Capsular Tabs */
.auth-tabs {
  display: flex;
  justify-content: center;
  gap: 30px;
  margin-bottom: 24px;
  border-bottom: 1px solid #efeae4;
  padding-bottom: 10px;
}
.auth-tabs span {
  font-size: 15px;
  color: #9b8e82;
  cursor: pointer;
  padding-bottom: 8px;
  position: relative;
  transition: all 0.2s;
}
.auth-tabs span.active {
  color: #5a4435;
  font-weight: 600;
}
.auth-tabs span.active::after {
  content: '';
  position: absolute;
  bottom: -11px;
  left: 0;
  width: 100%;
  height: 3px;
  background: #8b6f52;
  border-radius: 2px;
}

.login-method-tabs {
  margin-bottom: 18px;
}
:deep(.el-tabs__item.is-active) { color: #8b6f52; }
:deep(.el-tabs__active-bar) { background-color: #8b6f52; }

.code-row { display: flex; gap: 10px; }
.code-row .el-button { width: 110px; }

.forget-pass {
  text-align: right;
  font-size: 13px;
  color: #9b8e82;
  cursor: pointer;
  margin-top: -10px;
  margin-bottom: 18px;
}
.forget-pass:hover { color: #6b5040; text-decoration: underline; }

.submit-btn {
  width: 100%;
  height: 42px;
  font-size: 15px;
  background-color: #6b5040;
  border-color: #6b5040;
  letter-spacing: 2px;
}
.submit-btn:hover { background-color: #8b6f52; border-color: #8b6f52; }

.back-login {
  text-align: center;
  margin-top: 18px;
  font-size: 13px;
  color: #9b8e82;
  cursor: pointer;
}
.back-login:hover { color: #6b5040; text-decoration: underline; }
</style>