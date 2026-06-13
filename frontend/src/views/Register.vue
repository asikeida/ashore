<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, EditPen } from '@element-plus/icons-vue'
import { ApiError } from '../api/client'
import { auth, refreshAuth } from '../stores/auth'

const account = ref('')
const password = ref('')
const nickname = ref('')
const error = ref('')
const loading = ref(false)
const router = useRouter()

async function submit() {
  if (!account.value.trim() || !password.value) {
    error.value = '请输入账号和密码'
    return
  }
  if (password.value.length < 6) {
    error.value = '密码至少 6 位'
    return
  }
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
    ElMessage.success('注册成功，已自动登录')
    router.push('/')
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '注册失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="container">
    <div class="panel auth-panel">
      <div class="auth-head">
        <span class="auth-anchor">🚢</span>
        <h2>开启上岸之旅</h2>
        <p class="muted">注册即同意社区守则：分享真实面经，拒绝编造</p>
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
        placeholder="账号（字母、数字、_ @ .）"
        :prefix-icon="User"
        @keyup.enter="submit"
      />
      <el-input
        v-model="password"
        size="large"
        type="password"
        placeholder="密码（至少 6 位）"
        :prefix-icon="Lock"
        show-password
        @keyup.enter="submit"
      />
      <el-input
        v-model="nickname"
        size="large"
        placeholder="昵称（可空，默认自动生成）"
        :prefix-icon="EditPen"
        @keyup.enter="submit"
      />
      <el-button
        type="primary"
        size="large"
        style="width: 100%; margin-top: 4px"
        :loading="loading"
        @click="submit"
      >
        {{ loading ? '注册中…' : '注册并登录' }}
      </el-button>
      <p class="muted" style="text-align: center; margin-top: 14px">
        已有账号？<RouterLink to="/login" class="text-link">去登录</RouterLink>
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

.text-link {
  color: var(--accent);
  font-weight: 600;
  text-decoration: none;
}
</style>
