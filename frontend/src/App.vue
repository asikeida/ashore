<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth, refreshAuth, logout } from './stores/auth'
import { fetchUnreadCount } from './api/community'
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { Bell, ChatDotRound, Document, EditPen, House, Setting, SwitchButton, User, UserFilled } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const unread = ref(0)
// 左栏折叠态：点击汉堡切换（不随鼠标焦点），持久化到 localStorage
const navCollapsed = ref(localStorage.getItem('ashore-nav-collapsed') === '1')

function toggleNav() {
  navCollapsed.value = !navCollapsed.value
  localStorage.setItem('ashore-nav-collapsed', navCollapsed.value ? '1' : '0')
}

async function pollUnread() {
  if (!auth.loggedIn) return
  try {
    unread.value = await fetchUnreadCount()
  } catch {
    // 静默：徽标失败不影响主功能
  }
}

onMounted(async () => {
  await refreshAuth()
  await pollUnread()
  // 60s 轮询未读数（轻量端点）；页面隐藏时浏览器会自动节流
  setInterval(pollUnread, 60_000)
})

async function handleLogout() {
  await logout()
  router.push('/')
}

/** 左栏条目：按登录态/角色过滤；active 随路由 */
const navItems = [
  { path: '/', label: '首页', icon: House, show: () => true, exact: true },
  { path: '/write', label: '写面经', icon: EditPen, show: () => auth.loggedIn },
  { path: '/profile', label: '个人主页', icon: User, show: () => auth.loggedIn },
  { path: '/notices', label: '消息中心', icon: Bell, show: () => auth.loggedIn },
  { path: '/admin', label: '管理台', icon: Setting, show: () => auth.loggedIn && auth.role === 'ADMIN' },
]

function isActive(path: string, exact: boolean) {
  return exact ? route.path === path : route.path.startsWith(path)
}
</script>

<template>
  <ElConfigProvider :locale="zhCn">
    <!-- 顶栏：横贯全宽，汉堡在最左 -->
    <header class="navbar">
      <button class="burger" :title="navCollapsed ? '展开导航' : '收起导航'" @click="toggleNav">
        <span class="burger-line"></span>
        <span class="burger-line"></span>
        <span class="burger-line"></span>
      </button>
      <RouterLink to="/" class="brand">
        <span class="brand-anchor">⚓</span>
        Ashore
        <span class="brand-sub">上岸 · 面经社区</span>
      </RouterLink>
      <span class="nav-spacer"></span>
      <nav class="nav-actions">
        <template v-if="auth.loggedIn">
          <el-badge :value="unread" :hidden="unread === 0" :max="99">
            <RouterLink to="/notices" class="bell-link">
              <el-button :icon="Bell" circle text />
            </RouterLink>
          </el-badge>
          <RouterLink to="/write">
            <el-button type="primary" :icon="EditPen" plain size="default">写面经</el-button>
          </RouterLink>
          <RouterLink v-if="auth.role === 'ADMIN'" to="/admin">
            <el-button :icon="Document" text>管理台</el-button>
          </RouterLink>
          <el-dropdown trigger="click">
            <span class="user-chip">
              <el-icon><UserFilled /></el-icon>
              {{ auth.account }}
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item :icon="User" @click="router.push('/profile')">个人主页</el-dropdown-item>
                <el-dropdown-item divided :icon="SwitchButton" @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <RouterLink to="/login">
            <el-button :icon="UserFilled" text>登录</el-button>
          </RouterLink>
          <RouterLink to="/register">
            <el-button type="primary" :icon="ChatDotRound">注册</el-button>
          </RouterLink>
        </template>
      </nav>
    </header>

    <!-- 下方：左栏 + 内容 -->
    <div class="below">
      <nav class="leftnav" :class="{ collapsed: navCollapsed }">
        <RouterLink
          v-for="item in navItems.filter(i => i.show())"
          :key="item.path"
          :to="item.path"
          class="ln-item"
          :class="{ active: isActive(item.path, item.exact) }"
        >
          <el-icon class="ico"><component :is="item.icon" /></el-icon>
          <span class="lbl">{{ item.label }}</span>
        </RouterLink>
      </nav>

      <main class="page-main">
        <RouterView />
      </main>
    </div>

    <footer class="footer">⚓ Every offer begins ashore. · Ashore 上岸</footer>
  </ElConfigProvider>
</template>

<style scoped>
/* ── 汉堡按钮 ── */
.burger {
  width: 34px;
  height: 34px;
  border: none;
  background: transparent;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin-right: 2px;
}

.burger:hover {
  background: #f0f3f7;
}

.burger-line {
  width: 16px;
  height: 2px;
  background: var(--text);
  border-radius: 2px;
}

/* ── 顶栏 ── */
.navbar {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 12px;
  height: 56px;
  padding: 0 20px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--border);
}

.brand {
  font-weight: 800;
  font-size: 17px;
  color: var(--text);
  text-decoration: none;
}

.brand-anchor {
  color: var(--accent);
  margin-right: 4px;
}

.brand-sub {
  color: var(--muted);
  font-size: 12px;
  font-weight: 400;
  margin-left: 4px;
}

.nav-spacer {
  flex: 1;
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.nav-actions a {
  text-decoration: none;
}

.bell-link {
  text-decoration: none;
  display: inline-flex;
}

.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 14px;
  color: var(--text);
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--card);
  outline: none;
}

.user-chip:hover {
  border-color: var(--el-color-primary-light-5);
  color: var(--accent);
}

/* ── 下方区域：左栏 + 内容 ── */
.below {
  display: flex;
  align-items: stretch;
}

.leftnav {
  width: 168px;
  background: #fff;
  border-right: 1px solid var(--border);
  padding: 10px 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  position: sticky;
  top: 56px;
  height: calc(100vh - 56px);
  overflow: hidden;
  flex-shrink: 0;
  transition: width 0.2s ease;
}

.leftnav.collapsed {
  width: 64px;
}

.ln-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 14px;
  color: var(--text);
  text-decoration: none;
  white-space: nowrap;
}

.ln-item .ico {
  font-size: 17px;
  flex-shrink: 0;
}

.ln-item.active {
  background: var(--el-color-primary-light-9);
  color: var(--accent);
  font-weight: 600;
}

.ln-item:hover:not(.active) {
  background: #f5f7fa;
}

/* 折叠态：图标上、小字下（竖排） */
.leftnav.collapsed .ln-item {
  flex-direction: column;
  gap: 3px;
  padding: 9px 4px;
  font-size: 11px;
}

.leftnav.collapsed .ln-item .ico {
  font-size: 18px;
}

.page-main {
  flex: 1;
  min-width: 0;
}

.footer {
  text-align: center;
  color: var(--muted);
  font-size: 13px;
  padding: 24px 0 30px;
}

@media (max-width: 900px) {
  .leftnav {
    display: none;
  }
  .burger {
    display: none;
  }
}
</style>
