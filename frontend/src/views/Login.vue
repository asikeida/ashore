<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '../api/client'
import { auth, refreshAuth } from '../stores/auth'

const account = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)
const route = useRoute()
const router = useRouter()

async function submit() {
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
    router.push((route.query.redirect as string) ?? '/')
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="card form-card">
    <h2>登录</h2>
    <input v-model="account" placeholder="账号" @keyup.enter="submit" />
    <input v-model="password" type="password" placeholder="密码" @keyup.enter="submit" />
    <p v-if="error" class="error">{{ error }}</p>
    <button class="primary" style="width: 100%" :disabled="loading" @click="submit">
      {{ loading ? '登录中…' : '登录' }}
    </button>
    <p class="muted" style="text-align: center">
      没有账号？<RouterLink to="/register">去注册</RouterLink>
    </p>
  </div>
</template>
