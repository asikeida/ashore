import { createRouter, createWebHashHistory } from "vue-router"
import { auth, refreshAuth } from '../stores/auth'

const Home = () => import('../views/Home.vue')
const Login = () => import('../views/Login.vue')
const Register = () => import('../views/Register.vue')
const PostDetail = () => import('../views/PostDetail.vue')
const PostEdit = () => import('../views/PostEdit.vue')
const Interview = () => import('../views/Interview.vue')
const Admin = () => import('../views/Admin.vue')

export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', component: Home },
    { path: '/login', component: Login },
    { path: '/register', component: Register },
    { path: '/posts/:id', component: PostDetail, props: true },
    { path: '/write', component: PostEdit, meta: { requiresAuth: true } },
    { path: '/posts/:id/interview', component: Interview, meta: { requiresAuth: true }, props: true },
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
