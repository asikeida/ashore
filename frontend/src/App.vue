<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth, refreshAuth, logout } from './stores/auth'
import { fetchUnreadCount } from './api/community'
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { Bell, EditPen, Search, UserFilled } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const unread = ref(0)
const keyword = ref('')
let pollTimer: ReturnType<typeof setInterval> | undefined

watch(() => route.query.q, q => { keyword.value = typeof q === 'string' ? q : '' }, { immediate: true })

async function pollUnread() {
  if (!auth.loggedIn) return
  try { unread.value = await fetchUnreadCount() } catch { /* 徽标失败不影响主功能 */ }
}

onMounted(async () => {
  await refreshAuth()
  await pollUnread()
  pollTimer = setInterval(pollUnread, 60_000)
})
onUnmounted(() => { if (pollTimer) clearInterval(pollTimer) })

function search() {
  const q = keyword.value.trim()
  router.push(q ? { path: '/', query: { q } } : { path: '/' })
}

async function handleLogout() {
  await logout()
  unread.value = 0
  router.push('/')
}
</script>

<template>
  <ElConfigProvider :locale="zhCn">
    <div class="app-shell">
      <header class="site-header">
        <div class="header-inner">
          <RouterLink to="/about" class="site-logo"><span class="logo-mark">◇</span>Ashore</RouterLink>
          <nav class="primary-nav" aria-label="主导航">
            <RouterLink to="/about" :class="{ active: route.path === '/about' }">项目介绍</RouterLink>
            <RouterLink to="/" :class="{ active: route.path === '/' }">面经</RouterLink>
          </nav>
          <form class="header-search" role="search" @submit.prevent="search">
            <el-icon><Search /></el-icon>
            <input v-model="keyword" aria-label="搜索面经" placeholder="搜索面经标题或正文" />
            <button type="submit" aria-label="搜索">↵</button>
          </form>
          <div class="header-actions">
            <RouterLink to="/write" class="write-button"><el-icon><EditPen /></el-icon><span>写面经</span></RouterLink>
            <template v-if="auth.loggedIn">
              <RouterLink to="/notices" class="notice-link" aria-label="消息中心">
                <el-badge :value="unread" :hidden="unread === 0" :max="99"><el-icon><Bell /></el-icon></el-badge>
              </RouterLink>
              <el-dropdown trigger="click">
                <button class="account-button" type="button"><el-icon><UserFilled /></el-icon><span>{{ auth.account }}</span></button>
                <template #dropdown><el-dropdown-menu>
                  <el-dropdown-item @click="router.push('/profile')">个人中心</el-dropdown-item>
                  <el-dropdown-item v-if="auth.role === 'ADMIN'" @click="router.push('/admin')">管理台</el-dropdown-item>
                  <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
                </el-dropdown-menu></template>
              </el-dropdown>
            </template>
            <template v-else>
              <RouterLink to="/login" class="plain-link">登录</RouterLink>
              <RouterLink to="/register" class="outline-link">注册</RouterLink>
            </template>
          </div>
        </div>
      </header>
      <main class="page-main"><RouterView /></main>
      <footer class="site-footer">Ashore 上岸 · 每一次复盘，都让下一次回答更好</footer>
    </div>
  </ElConfigProvider>
</template>

<style scoped>
.app-shell{min-height:100vh;display:flex;flex-direction:column}.page-main{flex:1;min-width:0}
.site-header{position:sticky;top:0;z-index:100;background:rgba(255,254,250,.96);border-bottom:1px solid var(--border);backdrop-filter:blur(12px)}
.header-inner{max-width:1190px;min-height:68px;margin:auto;padding:0 22px;display:flex;align-items:center;gap:30px}
.site-logo{font-size:23px;font-weight:800;letter-spacing:-.7px;color:var(--text);text-decoration:none;white-space:nowrap}.logo-mark{color:var(--accent);margin-right:8px}
.primary-nav{display:flex;gap:26px;align-self:stretch;align-items:stretch}.primary-nav a{display:flex;align-items:center;color:var(--muted);text-decoration:none;white-space:nowrap;border-bottom:2px solid transparent;padding-top:2px}.primary-nav a.active{color:var(--accent);border-color:var(--accent);font-weight:700}
.header-search{height:38px;max-width:290px;min-width:160px;flex:1;margin-left:auto;padding:0 11px;display:flex;align-items:center;gap:8px;border:1px solid var(--border);background:#f5f1eb;border-radius:8px;color:var(--muted)}.header-search input{border:0;outline:0;background:transparent;color:var(--text);width:100%;min-width:0;font:inherit}.header-search button{border:0;background:none;color:var(--muted);cursor:pointer}
.header-actions{display:flex;align-items:center;gap:14px;white-space:nowrap}.header-actions a{text-decoration:none}.write-button{display:inline-flex;align-items:center;gap:6px;padding:9px 14px;background:var(--accent);color:white;border-radius:8px;font-size:13px;font-weight:700}.write-button:hover{background:var(--el-color-primary-dark-2)}.plain-link{color:var(--text)}.outline-link{border:1px solid var(--border);color:var(--text);border-radius:8px;padding:7px 13px}.notice-link{font-size:19px;color:var(--muted)}.account-button{display:flex;align-items:center;gap:6px;border:0;background:none;color:var(--text);cursor:pointer}.site-footer{text-align:center;color:var(--muted);padding:24px;font-size:12px}
@media(max-width:850px){.header-inner{flex-wrap:wrap;gap:0 18px;padding:11px 16px}.primary-nav{order:3;width:100%;height:38px;gap:24px}.header-search{order:4;max-width:none;flex-basis:100%;margin:5px 0 0}.header-actions{margin-left:auto}.account-button span{display:none}}
@media(max-width:520px){.header-actions{gap:9px}.write-button{padding:8px}.write-button span{display:none}.outline-link{padding:6px 9px}.site-logo{font-size:20px}}
</style>
