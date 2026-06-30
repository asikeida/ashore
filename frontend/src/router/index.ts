import { createRouter, createWebHistory } from "vue-router"
import { auth, refreshAuth } from '../stores/auth'

const Home = () => import('../views/Home.vue')
const Login = () => import('../views/Login.vue')
const Register = () => import('../views/Register.vue')
const PostDetail = () => import('../views/PostDetail.vue')
const PostEdit = () => import('../views/PostEdit.vue')
const Interview = () => import('../views/Interview.vue')
const Admin = () => import('../views/Admin.vue')
const Profile = () => import('../views/Profile.vue')
const InterviewHistory = () => import('../views/InterviewHistory.vue')

export const router = createRouter({
  // history 模式：非 API 路径由 Nginx try_files 兜底回 index.html（生产）；
  // 本地 vite dev server 自带 SPA fallback，直接可用
  history: createWebHistory(),
  routes: [
    { path: '/', component: Home },
    { path: '/login', component: Login },
    { path: '/register', component: Register },
    { path: '/posts/:id', component: PostDetail, props: true },
    { path: '/write', component: PostEdit, meta: { requiresAuth: true } },
    { path: '/posts/:id/interview', component: Interview, meta: { requiresAuth: true }, props: true },
    { path: '/profile', component: Profile, meta: { requiresAuth: true } },
    { path: '/profile/interview/:sessionId', component: InterviewHistory, meta: { requiresAuth: true }, props: true },
    { path: '/admin', component: Admin, meta: { requiresAuth: true, requiresAdmin: true } },
  ],
})

router.beforeEach(async to => {
  // 整页刷新时异步 auth 探测未完成，守卫先等探测结束再判定（否则有效会话被误踢）
  if (!auth.loaded) {
    await refreshAuth()
  }
  if (to.meta.requiresAuth && !auth.loggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && auth.role !== 'ADMIN') {
    return { path: '/' }
  }
  return true
})
