<script setup>
import request from '../utils/request'
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast, showSuccessToast, showToast } from 'vant'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const mode = ref('login')
const loginMethod = ref('password')
const loading = ref(false)
const cooldownLogin = ref(0)
const cooldownRegister = ref(0)
const cooldownReset = ref(0)

const pageTitle = computed(() => {
  if (mode.value === 'login') return '欢迎回来'
  if (mode.value === 'register') return '加入智能阅读'
  return '找回密码'
})

const pageSubtitle = computed(() => {
  if (mode.value === 'login') return '继续你的阅读旅程，书架和听书记录都在等你。'
  if (mode.value === 'register') return '创建一个账号，把你的阅读偏好和收藏保存下来。'
  return '通过验证码快速重设密码，然后继续安心阅读。'
})

const form = reactive({ username: '', password: '', target: '', code: '' })
const regForm = reactive({ target: '', code: '', password: '', nickname: '', age: 18 })
const resetForm = reactive({ target: '', code: '', newPassword: '' })

const startCooldown = (targetRef) => {
  targetRef.value = 60
  const timer = setInterval(() => {
    targetRef.value -= 1
    if (targetRef.value <= 0) clearInterval(timer)
  }, 1000)
}

const sendCode = async (type) => {
  const target = type === 1 ? regForm.target : type === 2 ? form.target : resetForm.target
  if (!target) {
    showToast('请输入手机号或邮箱')
    return
  }
  try {
    await request.post('/api/auth/sendCode', { target, type })
    showSuccessToast('验证码已发送')
    if (type === 1) startCooldown(cooldownRegister)
    if (type === 2) startCooldown(cooldownLogin)
    if (type === 3) startCooldown(cooldownReset)
  } catch (error) {
    showFailToast(error.response?.data?.msg || '验证码发送失败')
  }
}

const handleLogin = async () => {
  loading.value = true
  try {
    let res
    if (loginMethod.value === 'password') {
      if (!form.username || !form.password) {
        showToast('请输入账号和密码')
        return
      }
      res = await request.post('/api/sysUser/login', {
        username: form.username,
        password: form.password
      })
    } else {
      if (!form.target || !form.code) {
        showToast('请输入手机号或邮箱和验证码')
        return
      }
      res = await request.post('/api/auth/loginByCode', {
        target: form.target,
        code: form.code
      })
    }
    if (res.data.code === '200') {
      authStore.login(res.data.data)
      showSuccessToast('登录成功')
      router.push('/')
    } else {
      showFailToast(res.data.msg || '登录失败')
    }
  } catch (error) {
    showFailToast(error.response?.data?.msg || '登录失败')
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  if (!regForm.target || !regForm.code || !regForm.password) {
    showToast('请填写必要信息')
    return
  }
  loading.value = true
  try {
    const res = await request.post('/api/auth/register', regForm)
    if (res.data.code === '200') {
      showSuccessToast('注册成功，请登录')
      mode.value = 'login'
    } else {
      showFailToast(res.data.msg || '注册失败')
    }
  } catch (error) {
    showFailToast(error.response?.data?.msg || '注册失败')
  } finally {
    loading.value = false
  }
}

const handleReset = async () => {
  if (!resetForm.target || !resetForm.code || !resetForm.newPassword) {
    showToast('请填写完整信息')
    return
  }
  loading.value = true
  try {
    const res = await request.post('/api/auth/resetPassword', resetForm)
    if (res.data.code === '200') {
      showSuccessToast('密码已重置，请重新登录')
      mode.value = 'login'
    } else {
      showFailToast(res.data.msg || '重置失败')
    }
  } catch (error) {
    showFailToast(error.response?.data?.msg || '重置失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <van-nav-bar left-arrow @click-left="$router.push('/')" :border="false" />

    <section class="hero-card glass-panel fade-in-up" style="animation-delay: 0.1s">
      <div class="hero-eyebrow">Smart Reading</div>
      <h1 class="hero-title">{{ pageTitle }}</h1>
      <p class="hero-desc">{{ pageSubtitle }}</p>
    </section>

    <div v-if="mode !== 'reset'" class="mode-tabs fade-in-up" style="animation-delay: 0.2s">
      <span :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</span>
      <span :class="{ active: mode === 'register' }" @click="mode = 'register'">注册</span>
    </div>

    <section class="form-card glass-panel fade-in-up" style="animation-delay: 0.3s">
      <template v-if="mode === 'login'">
        <van-tabs v-model:active="loginMethod" shrink animated class="method-tabs">
          <van-tab title="密码登录" name="password" />
          <van-tab title="验证码登录" name="code" />
        </van-tabs>

        <template v-if="loginMethod === 'password'">
          <van-field v-model="form.username" label="账号" placeholder="用户名 / 手机号 / 邮箱" left-icon="user-o" />
          <van-field
            v-model="form.password"
            label="密码"
            type="password"
            placeholder="请输入密码"
            left-icon="lock"
            @keypress.enter="handleLogin"
          />
          <div class="aux-link" @click="mode = 'reset'">忘记密码？</div>
        </template>

        <template v-else>
          <van-field v-model="form.target" label="手机号/邮箱" placeholder="请输入手机号或邮箱" left-icon="phone-o" />
          <van-field v-model="form.code" label="验证码" placeholder="请输入 6 位验证码" left-icon="shield-o">
            <template #button>
              <van-button size="small" :disabled="cooldownLogin > 0" @click="sendCode(2)">
                {{ cooldownLogin > 0 ? `${cooldownLogin}s` : '获取验证码' }}
              </van-button>
            </template>
          </van-field>
        </template>

        <van-button type="primary" block round class="submit-btn" :loading="loading" @click="handleLogin">
          登录
        </van-button>
      </template>

      <template v-else-if="mode === 'register'">
        <van-field v-model="regForm.target" label="手机号/邮箱" placeholder="用于接收验证码" left-icon="envelop-o" />
        <van-field v-model="regForm.code" label="验证码" placeholder="请输入验证码" left-icon="shield-o">
          <template #button>
            <van-button size="small" :disabled="cooldownRegister > 0" @click="sendCode(1)">
              {{ cooldownRegister > 0 ? `${cooldownRegister}s` : '获取验证码' }}
            </van-button>
          </template>
        </van-field>
        <van-field v-model="regForm.password" label="密码" type="password" placeholder="至少 6 位更安心" left-icon="lock" />
        <van-field v-model="regForm.nickname" label="昵称" placeholder="给自己起个名字" left-icon="contact-o" />
        <van-field v-model="regForm.age" label="年龄" type="digit" placeholder="可选" left-icon="calendar-o" />
        <van-button type="primary" block round class="submit-btn" :loading="loading" @click="handleRegister">
          注册
        </van-button>
      </template>

      <template v-else>
        <van-field v-model="resetForm.target" label="手机号/邮箱" placeholder="请输入手机号或邮箱" left-icon="envelop-o" />
        <van-field v-model="resetForm.code" label="验证码" placeholder="请输入验证码" left-icon="shield-o">
          <template #button>
            <van-button size="small" :disabled="cooldownReset > 0" @click="sendCode(3)">
              {{ cooldownReset > 0 ? `${cooldownReset}s` : '获取验证码' }}
            </van-button>
          </template>
        </van-field>
        <van-field v-model="resetForm.newPassword" label="新密码" type="password" placeholder="请输入新密码" left-icon="lock" />
        <van-button type="primary" block round class="submit-btn" :loading="loading" @click="handleReset">
          重置密码
        </van-button>
        <div class="aux-link center" @click="mode = 'login'">返回登录</div>
      </template>
    </section>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  padding-bottom: calc(48px + var(--safe-bottom));
  background: var(--bg-color);
  position: relative;
  overflow: hidden;
}

.login-page::before {
  content: '';
  position: absolute;
  top: -10%; left: -10%; width: 250px; height: 250px;
  background: radial-gradient(circle, var(--primary-color) 0%, transparent 70%);
  opacity: 0.15; filter: blur(40px);
  animation: float-slow 12s ease-in-out infinite alternate;
}
.login-page::after {
  content: '';
  position: absolute;
  bottom: 0%; right: -10%; width: 300px; height: 300px;
  background: radial-gradient(circle, var(--primary-hover) 0%, transparent 70%);
  opacity: 0.1; filter: blur(50px);
  animation: float-slow 16s ease-in-out infinite alternate-reverse;
}

@keyframes float-slow {
  0% { transform: translate(0, 0); }
  100% { transform: translate(20px, -30px); }
}

.fade-in-up {
  animation: fadeInUp 0.5s cubic-bezier(0.25, 0.8, 0.25, 1) forwards;
  opacity: 0;
  transform: translateY(15px);
  position: relative;
  z-index: 2;
}

@keyframes fadeInUp {
  to { opacity: 1; transform: translateY(0); }
}

.hero-card,
.form-card {
  margin: 16px;
  padding: 24px;
}

.hero-eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--primary-color);
}

.hero-title {
  margin: 8px 0 6px;
  font-size: 26px;
  color: var(--text-primary);
}

.hero-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-secondary);
}

.mode-tabs {
  display: flex;
  gap: 18px;
  padding: 0 18px;
  margin-top: 6px;
  position: relative;
  z-index: 2;
}

.mode-tabs span {
  position: relative;
  font-size: 15px;
  color: var(--text-secondary);
  padding-bottom: 6px;
  transition: color var(--transition-fast);
}

.mode-tabs span.active {
  color: var(--text-primary);
  font-weight: 700;
}

.mode-tabs span.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 3px;
  border-radius: 999px;
  background: var(--primary-color);
}

.method-tabs {
  margin-bottom: 8px;
}

.submit-btn {
  margin-top: 18px;
  transition: transform var(--transition-fast), box-shadow var(--transition-fast);
  box-shadow: var(--shadow-sm);
}

.submit-btn:active {
  transform: scale(0.98);
  box-shadow: none;
}

.aux-link {
  margin-top: 12px;
  font-size: 13px;
  color: var(--primary-color);
}

.aux-link.center {
  text-align: center;
}
</style>
