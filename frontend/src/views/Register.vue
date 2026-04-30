<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError } from '../api/client'
import { auth, refreshAuth } from '../stores/auth'

const account = ref('')
const password = ref('')
const nickname = ref('')
const error = ref('')
const loading = ref(false)
const router = useRouter()

async function submit() {
  error.value = ''
  loading.value = true
  try {
    const response = await fetch('/api/v1/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        account: account.value,
        password: password.value,
        nickname: nickname.value || undefined,
      }),
    })
    const body = await response.json()
    if (body.status?.code !== 0) {
      throw new ApiError(body.status?.code, body.status?.msg)
    }
    await refreshAuth()
    router.push('/')
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '注册失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="card form-card">
    <h2>注册</h2>
    <input v-model="account" placeholder="账号（字母数字 _ @ .）" @keyup.enter="submit" />
    <input v-model="password" type="password" placeholder="密码（至少 6 位）" @keyup.enter="submit" />
    <input v-model="nickname" placeholder="昵称（可空，默认自动生成）" @keyup.enter="submit" />
    <p v-if="error" class="error">{{ error }}</p>
    <button class="primary" style="width: 100%" :disabled="loading" @click="submit">
      {{ loading ? '注册中…' : '注册并登录' }}
    </button>
    <p class="muted" style="text-align: center">
      已有账号？<RouterLink to="/login">去登录</RouterLink>
    </p>
  </div>
</template>
