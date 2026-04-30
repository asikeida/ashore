<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  fetchPosts,
  searchPosts,
  type PostItem,
  type SearchItem,
} from '../api/post'

const items = ref<PostItem[]>([])
const cursor = ref<string | null>(null)
const loading = ref(false)
const finished = ref(false)

const keyword = ref('')
const searchResults = ref<SearchItem[] | null>(null)
const searchLoading = ref(false)

async function loadMore() {
  if (loading.value || finished.value) return
  loading.value = true
  try {
    const page = await fetchPosts(cursor.value)
    items.value.push(...page.items)
    cursor.value = page.nextCursor
    finished.value = page.nextCursor === null
  } finally {
    loading.value = false
  }
}

async function doSearch() {
  if (!keyword.value.trim()) return
  searchLoading.value = true
  try {
    searchResults.value = await searchPosts(keyword.value.trim())
  } finally {
    searchLoading.value = false
  }
}

function clearSearch() {
  keyword.value = ''
  searchResults.value = null
}

onMounted(loadMore)
</script>

<template>
  <div class="card">
    <input
      v-model="keyword"
      placeholder="搜索面经关键词（标题 / 正文）"
      @keyup.enter="doSearch"
    />
    <button class="primary" :disabled="searchLoading" @click="doSearch">搜索</button>
    <button v-if="searchResults" class="ghost" style="margin-left: 8px" @click="clearSearch">
      清除
    </button>
  </div>

  <template v-if="searchResults">
    <div v-for="item in searchResults" :key="item.id" class="card">
      <RouterLink :to="`/posts/${item.id}`" class="item-title">
        <span v-if="item.titleHighlight" v-html="item.titleHighlight"></span>
        <span v-else>{{ item.title }}</span>
      </RouterLink>
      <div class="muted" style="margin: 6px 0">{{ item.authorNickname }} · {{ item.createTime }}</div>
      <div v-if="item.contentHighlight" class="muted highlight" v-html="item.contentHighlight"></div>
    </div>
    <p v-if="searchResults.length === 0" class="muted">没有匹配的面经。</p>
  </template>

  <template v-else>
    <div v-for="item in items" :key="item.id" class="card">
      <RouterLink :to="`/posts/${item.id}`" class="item-title">{{ item.title }}</RouterLink>
      <div class="muted" style="margin: 6px 0">{{ item.authorNickname }} · {{ item.createTime }}</div>
      <div>
        <span v-for="tag in item.tags" :key="tag" class="tag">{{ tag }}</span>
      </div>
    </div>
    <button v-if="!finished" class="ghost load-more" :disabled="loading" @click="loadMore">
      {{ loading ? '加载中…' : '加载更多' }}
    </button>
    <p v-if="items.length === 0 && !loading" class="muted">还没有面经，写一篇吧。</p>
  </template>
</template>
