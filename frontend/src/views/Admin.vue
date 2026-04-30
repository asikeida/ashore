<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiError } from '../api/client'
import {
  fetchAdminPosts,
  setPostOffline,
  setPostOnline,
  type AdminPostItem,
} from '../api/admin'

const items = ref<AdminPostItem[]>([])
const filter = ref<'REVIEW' | 'ONLINE' | 'OFFLINE' | ''>('REVIEW')
const error = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  error.value = ''
  try {
    items.value = await fetchAdminPosts(filter.value || undefined)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function online(id: string) {
  await setPostOnline(id)
  await load()
}

async function offline(id: string) {
  await setPostOffline(id)
  await load()
}

onMounted(load)
</script>

<template>
  <div class="card">
    <h2>管理台 · 面经审核</h2>
    <div style="margin: 12px 0; display: flex; gap: 8px">
      <button
        v-for="f in ['', 'REVIEW', 'ONLINE', 'OFFLINE']"
        :key="f"
        class="ghost"
        :style="filter === f ? 'border-color: var(--accent); color: var(--accent)' : ''"
        @click="filter = f as typeof filter; load()"
      >
        {{ f === '' ? '全部' : f }}
      </button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <div v-for="item in items" :key="item.id" class="comment">
      <RouterLink :to="`/posts/${item.id}`" class="item-title">{{ item.title }}</RouterLink>
      <div class="muted" style="margin: 4px 0">
        {{ item.authorNickname }} · {{ item.createTime }} ·
        <strong>{{ item.status }}</strong>
      </div>
      <div style="margin-top: 8px; display: flex; gap: 8px">
        <button
          v-if="item.status === 'REVIEW'"
          class="primary"
          @click="online(item.id)"
        >
          审核通过上线
        </button>
        <button
          v-if="item.status === 'ONLINE' || item.status === 'REVIEW'"
          class="ghost"
          @click="offline(item.id)"
        >
          下线/驳回
        </button>
      </div>
    </div>
    <p v-if="items.length === 0 && !loading" class="muted">没有符合条件的面经。</p>
  </div>
</template>
