import { reactive } from 'vue'
import { apiGet } from '../api/client'

/**
 * 登录态：会话凭据为 HttpOnly Cookie（后端签发），前端仅持有身份快照。
 * 应用启动时经 /auth/me 探测刷新。
 */
export const auth = reactive({
  loggedIn: false,
  userId: '' as string | null,
  account: '' as string | null,
  role: '' as string | null,
  loaded: false,
})

export async function refreshAuth(): Promise<void> {
  try {
    const me = await apiGet<{ id: string; account: string; role: string }>('/api/v1/auth/me')
    auth.loggedIn = true
    auth.userId = me.id
    auth.account = me.account
    auth.role = me.role
  } catch {
    auth.loggedIn = false
    auth.userId = null
    auth.account = null
    auth.role = null
  } finally {
    auth.loaded = true
  }
}

export async function logout(): Promise<void> {
  await fetch('/api/v1/auth/logout', { method: 'DELETE' })
  auth.loggedIn = false
  auth.userId = null
  auth.account = null
  auth.role = null
}
