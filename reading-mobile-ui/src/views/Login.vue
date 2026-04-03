<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showSuccessToast, showFailToast } from 'vant'
import axios from 'axios'

const router = useRouter()
const mode = ref('login') // login, register, reset
const loginMethod = ref('password') // password, code
const loading = ref(false)
const cooldownLogin = ref(0)
const cooldownRegister = ref(0)
const cooldownReset = ref(0)

const pageTitle = computed(() => {
  if (mode.value === 'login') return '欢迎回来'
  if (mode.value === 'register') return '加入我们'
  return '找回密码'
})

const form = reactive({ username: '', password: '', target: '', code: '' })
const regForm = reactive({ target: '', code: '', password: '', nickname: '', age: 18 })
const resetForm = reactive({ target: '', code: '', newPassword: '' })

const sendCode = async (type) => {
  let target = type === 1 ? regForm.target : type === 2 ? form.target : resetForm.target
  if (!target) return showToast('请输入手机号或邮箱')
  try {
    await axios.post('/api/auth/sendCode', { target, type })
    showSuccessToast('验证码已发送')
    let cd = type === 1 ? cooldownRegister : type === 2 ? cooldownLogin : cooldownReset
    cd.value = 60
    const t = setInterval(() => { cd.value--; if (cd.value <= 0) clearInterval(t) }, 1000)
  } catch (e) { showFailToast(e.response?.data?.msg || '发送失败') }
}

const handleLogin = async () => {
  loading.value = true
  try {
    let res
    if (loginMethod.value === 'password') {
      if (!form.username || !form.password) { loading.value = false; return showToast('请输入账号密码') }
      res = await axios.post('/api/sysUser/login', { username: form.username, password: form.password })
    } else {
      if (!form.target || !form.code) { loading.value = false; return showToast('请输入手机/邮箱和验证码') }
      res = await axios.post('/api/auth/loginByCode', { target: form.target, code: form.code })
    }
    if (res.data.code === '200') {
      showSuccessToast('登录成功')
      localStorage.setItem('user', JSON.stringify(res.data.data))
      router.push('/')
    } else { showFailToast(res.data.msg || '登录失败') }
  } catch (e) { showFailToast(e.response?.data?.msg || '服务器异常') }
  finally { loading.value = false }
}

const handleRegister = async () => {
  if (!regForm.target || !regForm.code || !regForm.password) return showToast('请填写必要信息')
  loading.value = true
  try {
    const res = await axios.post('/api/auth/register', regForm)
    if (res.data.code === '200') { showSuccessToast('注册成功'); mode.value = 'login' }
    else showFailToast(res.data.msg || '注册失败')
  } catch (e) { showFailToast(e.response?.data?.msg || '注册失败') }
  finally { loading.value = false }
}

const handleReset = async () => {
  if (!resetForm.target || !resetForm.code || !resetForm.newPassword) return showToast('请填写完整')
  loading.value = true
  try {
    const res = await axios.post('/api/auth/resetPassword', resetForm)
    if (res.data.code === '200') { showSuccessToast('密码已重置'); mode.value = 'login' }
    else showFailToast(res.data.msg || '重置失败')
  } catch (e) { showFailToast(e.response?.data?.msg || '重置失败') }
  finally { loading.value = false }
}
</script>

<template>
  <div class="login-page">
    <van-nav-bar left-arrow @click-left="$router.push('/')" />

    <div class="login-brand">
      <div class="brand-accent"></div>
      <h1 class="brand-title">{{ pageTitle }}</h1>
      <p class="brand-sub">智慧阅读 · 让每一页都有温度</p>
    </div>

    <!-- Mode Tabs (Login vs Register) -->
    <div v-if="mode !== 'reset'" class="mode-tabs">
      <span :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</span>
      <span :class="{ active: mode === 'register' }" @click="mode = 'register'">注册</span>
    </div>

    <div class="form-area">
      <!-- ===== LOGIN ===== -->
      <template v-if="mode === 'login'">
        <van-tabs v-model:active="loginMethod" shrink animated class="method-tabs">
          <van-tab title="密码登录" name="password" />
          <van-tab title="验证码登录" name="code" />
        </van-tabs>

        <template v-if="loginMethod === 'password'">
          <van-field v-model="form.username" label="账号" placeholder="用户名/手机号/邮箱" left-icon="user-o" />
          <van-field v-model="form.password" label="密码" type="password" placeholder="请输入密码" left-icon="lock" @keypress.enter="handleLogin" />
          <div class="forget-link" @click="mode = 'reset'">忘记密码？</div>
        </template>

        <template v-else>
          <van-field v-model="form.target" label="手机/邮箱" placeholder="请输入" left-icon="phone-o" />
          <van-field v-model="form.code" label="验证码" placeholder="6位验证码" left-icon="shield-o">
            <template #button>
              <van-button size="small" :disabled="cooldownLogin > 0" @click="sendCode(2)">
                {{ cooldownLogin > 0 ? `${cooldownLogin}s` : '获取' }}
              </van-button>
            </template>
          </van-field>
        </template>

        <van-button type="primary" block round size="large" :loading="loading" @click="handleLogin" class="submit-btn">
          登 录
        </van-button>
      </template>

      <!-- ===== REGISTER ===== -->
      <template v-if="mode === 'register'">
        <van-field v-model="regForm.target" label="手机/邮箱" placeholder="请输入" left-icon="phone-o" />
        <van-field v-model="regForm.code" label="验证码" placeholder="6位验证码" left-icon="shield-o">
          <template #button>
            <van-button size="small" :disabled="cooldownRegister > 0" @click="sendCode(1)">
              {{ cooldownRegister > 0 ? `${cooldownRegister}s` : '获取' }}
            </van-button>
          </template>
        </van-field>
        <van-field v-model="regForm.password" label="密码" type="password" placeholder="6-20位密码" left-icon="lock" />
        <van-field v-model="regForm.nickname" label="昵称" placeholder="您的称呼" left-icon="contact" />
        <van-field v-model="regForm.age" label="年龄" type="digit" placeholder="年龄" left-icon="friends-o" />

        <van-button type="primary" block round size="large" :loading="loading" @click="handleRegister" class="submit-btn">
          立即注册
        </van-button>
      </template>

      <!-- ===== RESET ===== -->
      <template v-if="mode === 'reset'">
        <van-field v-model="resetForm.target" label="手机/邮箱" placeholder="注册时的手机或邮箱" left-icon="phone-o" />
        <van-field v-model="resetForm.code" label="验证码" placeholder="6位验证码" left-icon="shield-o">
          <template #button>
            <van-button size="small" :disabled="cooldownReset > 0" @click="sendCode(3)">
              {{ cooldownReset > 0 ? `${cooldownReset}s` : '获取' }}
            </van-button>
          </template>
        </van-field>
        <van-field v-model="resetForm.newPassword" label="新密码" type="password" placeholder="新密码" left-icon="lock" />

        <van-button type="danger" block round size="large" :loading="loading" @click="handleReset" class="submit-btn">
          重置密码
        </van-button>
        <div class="back-link" @click="mode = 'login'">← 返回登录</div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  background: var(--color-bg);
}
.login-brand {
  text-align: center;
  padding: 30px 0 20px;
}
.brand-accent {
  width: 40px;
  height: 3px;
  background: var(--color-primary);
  border-radius: 2px;
  margin: 0 auto 20px;
}
.brand-title {
  font-family: var(--font-serif);
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text);
  margin: 0;
}
.brand-sub {
  color: var(--color-text-muted);
  font-size: 13px;
  margin-top: 8px;
  letter-spacing: 2px;
}

.mode-tabs {
  display: flex;
  justify-content: center;
  gap: 30px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--color-border);
  padding-bottom: 10px;
}
.mode-tabs span {
  font-size: 15px;
  color: var(--color-text-muted);
  cursor: pointer;
  padding-bottom: 8px;
  position: relative;
  transition: 0.2s;
}
.mode-tabs span.active {
  color: var(--color-primary-dark);
  font-weight: 600;
}
.mode-tabs span.active::after {
  content: '';
  position: absolute;
  bottom: -11px;
  left: 0;
  width: 100%;
  height: 3px;
  background: var(--color-primary);
  border-radius: 2px;
}

.form-area {
  padding: 0 24px;
}
.method-tabs {
  margin-bottom: 16px;
}
.van-field {
  margin-bottom: 8px;
  border-radius: 8px;
}

.forget-link {
  text-align: right;
  font-size: 13px;
  color: var(--color-text-muted);
  margin: -4px 0 16px;
}
.back-link {
  text-align: center;
  margin-top: 16px;
  font-size: 14px;
  color: var(--color-text-muted);
}

.submit-btn {
  margin-top: 20px;
  letter-spacing: 2px;
  font-size: 16px;
}
</style>
