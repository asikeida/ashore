<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { ApiError } from '../api/client'
import { auth, refreshAuth } from '../stores/auth'

const account = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)
const guestLoading = ref(false)
const route = useRoute()
const router = useRouter()

async function submit() {
  if (!account.value.trim() || !password.value) {
    error.value = '请输入账号和密码'
    return
  }
  error.value = ''
  loading.value = true
  try {
    const response = await fetch('/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ account: account.value, password: password.value }),
    })
    const body = await response.json()
    if (body.status?.code !== 0) {
      throw new ApiError(body.status?.code, body.status?.msg)
    }
    await refreshAuth()
    ElMessage.success('欢迎回来！')
    router.push((route.query.redirect as string) ?? '/')
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function loginAsGuest() {
  error.value = ''
  guestLoading.value = true
  try {
    const response = await fetch('/api/v1/auth/guest-login', { method: 'POST' })
    const body = await response.json()
    if (body.status?.code !== 0) throw new ApiError(body.status?.code, body.status?.msg)
    await refreshAuth()
    ElMessage.success('已以游客身份登录')
    router.push((route.query.redirect as string) ?? '/')
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '游客登录失败，请稍后重试'
  } finally {
    guestLoading.value = false
  }
}
</script>

<template>
  <div class="container">
    <div class="panel auth-panel">
      <div class="auth-head">
        <span class="auth-anchor">⚓</span>
        <h2>欢迎回来</h2>
        <p class="muted">登录后可以写面经、评论、与 AI 面试官过招</p>
      </div>

      <el-alert
        v-if="error"
        :title="error"
        type="error"
        show-icon
        :closable="false"
        style="margin-bottom: 16px"
      />

      <el-input
        v-model="account"
        size="large"
        placeholder="账号"
        :prefix-icon="User"
        @keyup.enter="submit"
      />
      <el-input
        v-model="password"
        size="large"
        type="password"
        placeholder="密码"
        :prefix-icon="Lock"
        show-password
        @keyup.enter="submit"
      />
      <el-button
        type="primary"
        size="large"
        style="width: 100%; margin-top: 4px"
        :loading="loading"
        @click="submit"
      >
        {{ loading ? '登录中…' : '登 录' }}
      </el-button>
      <el-button class="guest-button" size="large" :loading="guestLoading" :disabled="loading" @click="loginAsGuest">
        {{ guestLoading ? '登录中…' : '以游客身份登录' }}
      </el-button>
      <p class="guest-note">游客共用一个账号，消息、收藏和互动记录会对其他游客可见。</p>
      <p class="muted" style="text-align: center; margin-top: 14px">
        没有账号？<RouterLink to="/register" class="text-link">去注册</RouterLink>
      </p>
    </div>
  </div>
</template>

<style scoped>
.auth-panel {
  max-width: 420px;
  margin: 48px auto;
  padding: 32px;
}

.auth-head {
  text-align: center;
  margin-bottom: 24px;
}

.auth-anchor {
  font-size: 30px;
}

.auth-head h2 {
  margin: 6px 0 4px;
}

.auth-head p {
  margin: 0;
}

.auth-panel .el-input {
  margin-bottom: 14px;
}
.guest-button { width: 100%; margin: 12px 0 0; }
.guest-note { color: var(--muted); font-size: 12px; line-height: 1.6; margin: 10px 0 0; }

.text-link {
  color: var(--accent);
  font-weight: 600;
  text-decoration: none;
}
</style>
