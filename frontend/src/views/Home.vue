<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
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

function formatTime(time: string): string {
  return time.replace('T', ' ').slice(0, 16)
}

onMounted(loadMore)
</script>

<template>
  <div class="container">
    <el-card shadow="never" class="search-panel">
      <el-input
        v-model="keyword"
        size="large"
        placeholder="搜索面经关键词（标题 / 正文），支持 ES 高亮"
        :prefix-icon="Search"
        clearable
        @keyup.enter="doSearch"
      >
        <template #append>
          <el-button :loading="searchLoading" @click="doSearch">搜索</el-button>
        </template>
      </el-input>
      <el-button
        v-if="searchResults"
        link
        type="primary"
        style="margin-top: 8px"
        @click="clearSearch"
      >
        ← 清除搜索，回到全部面经
      </el-button>
    </el-card>

    <template v-if="searchResults">
      <el-empty
        v-if="searchResults.length === 0"
        description="没有匹配的面经，换个关键词试试"
      />
      <article
        v-for="item in searchResults"
        :key="item.id"
        class="panel panel-hover search-hit"
      >
        <RouterLink :to="`/posts/${item.id}`" class="item-title">
          <span v-if="item.titleHighlight" v-html="item.titleHighlight"></span>
          <span v-else>{{ item.title }}</span>
        </RouterLink>
        <div class="muted meta-row">
          {{ item.authorNickname }} · {{ formatTime(item.createTime) }}
        </div>
        <div v-if="item.contentHighlight" class="muted highlight excerpt" v-html="item.contentHighlight"></div>
      </article>
    </template>

    <template v-else>
      <el-skeleton :loading="loading && items.length === 0" :rows="4" animated>
        <article v-for="item in items" :key="item.id" class="panel panel-hover post-card">
          <RouterLink :to="`/posts/${item.id}`" class="item-title">{{ item.title }}</RouterLink>
          <div class="muted meta-row">
            {{ item.authorNickname }} · {{ formatTime(item.createTime) }}
          </div>
          <div v-if="item.tags.length" class="tag-row">
            <el-tag v-for="tag in item.tags" :key="tag" size="small" effect="plain" round>
              {{ tag }}
            </el-tag>
          </div>
        </article>
        <el-empty
          v-if="items.length === 0 && !loading"
          description="还没有面经，来写下第一篇吧"
        />
      </el-skeleton>

      <div v-if="!finished && items.length > 0" class="load-more">
        <el-button :loading="loading" round @click="loadMore">
          {{ loading ? '加载中…' : '加载更多' }}
        </el-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.search-panel {
  border-radius: 14px;
  border: 1px solid var(--border);
  margin-bottom: 16px;
}

.search-panel :deep(.el-input__wrapper) {
  border-radius: 10px;
}

.search-panel :deep(.el-input-group__append) {
  background: var(--el-color-primary);
  border: none;
  padding: 0;
}

.search-panel :deep(.el-input-group__append .el-button) {
  color: #fff;
  margin: 0;
  padding: 0 20px;
  height: 100%;
}

.post-card .item-title,
.search-hit .item-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--text);
  text-decoration: none;
  line-height: 1.5;
}

.item-title:hover {
  color: var(--accent);
}

.meta-row {
  margin: 8px 0 10px;
}

.tag-row {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.excerpt {
  margin-top: 4px;
  line-height: 1.7;
}

.load-more {
  text-align: center;
  padding: 8px 0 16px;
}
</style>
