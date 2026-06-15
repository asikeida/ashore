<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { auth, refreshAuth, logout } from './stores/auth'
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { ChatDotRound, Document, EditPen, SwitchButton, UserFilled } from '@element-plus/icons-vue'

const router = useRouter()

onMounted(refreshAuth)

async function handleLogout() {
  await logout()
  router.push('/')
}
</script>

<template>
  <ElConfigProvider :locale="zhCn">
    <div class="page-shell">
      <header class="navbar">
        <div class="navbar-inner">
          <RouterLink to="/" class="brand">
            <span class="brand-anchor">⚓</span>
            Ashore
            <span class="brand-sub">上岸 · 面经社区</span>
          </RouterLink>
          <nav class="nav-actions">
            <template v-if="auth.loggedIn">
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
                    <el-dropdown-item :icon="SwitchButton" @click="handleLogout">退出登录</el-dropdown-item>
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
        </div>
      </header>
      <main class="page-main">
        <RouterView />
      </main>
      <footer class="footer">⚓ Every offer begins ashore. · Ashore 上岸</footer>
    </div>
  </ElConfigProvider>
</template>

<style scoped>
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

.nav-actions a {
  text-decoration: none;
}
</style>
