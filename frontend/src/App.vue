<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { auth, refreshAuth, logout } from './stores/auth'

const router = useRouter()

onMounted(refreshAuth)

async function handleLogout() {
  await logout()
  router.push('/')
}
</script>

<template>
  <header class="navbar">
    <RouterLink to="/" class="brand">Ashore · 上岸</RouterLink>
    <nav>
      <RouterLink v-if="auth.loggedIn" to="/write">写面经</RouterLink>
      <RouterLink v-if="auth.role === 'ADMIN'" to="/admin">管理台</RouterLink>
      <template v-if="auth.loggedIn">
        <span class="user">{{ auth.account }}</span>
        <button class="link" @click="handleLogout">登出</button>
      </template>
      <template v-else>
        <RouterLink to="/login">登录</RouterLink>
        <RouterLink to="/register">注册</RouterLink>
      </template>
    </nav>
  </header>
  <main class="container">
    <RouterView />
  </main>
  <footer class="footer">Every offer begins ashore.</footer>
</template>
