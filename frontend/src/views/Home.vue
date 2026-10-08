<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import {
  fetchPosts,
  searchPosts,
  type PostItem,
  type SearchItem,
} from '../api/post'
import {
  fetchDailyRank,
  fetchHotPosts,
  type HotPostItem,
  type RankItem,
} from '../api/community'

const items = ref<PostItem[]>([])
const cursor = ref<string | null>(null)
const loading = ref(false)
const finished = ref(false)
const firstPageLoaded = ref(false)

const keyword = ref('')
const searchResults = ref<SearchItem[] | null>(null)
const searchLoading = ref(false)

// ── 置顶横滑卡组：首页第一页中的 topped 文章 ────────────────────
const pinned = computed(() =>
  firstPageLoaded.value ? items.value.filter(i => i.topped) : [],
)

// ── 侧栏：热榜 + 活跃榜（上下平铺，无 tab）────────────────────
const hotPosts = ref<HotPostItem[]>([])
const dailyRank = ref<RankItem[]>([])

async function loadSidebar() {
  try {
    hotPosts.value = await fetchHotPosts(8)
  } catch {
    // 侧栏数据失败静默，不打断主列表
  }
  try {
    dailyRank.value = await fetchDailyRank(8)
  } catch {
    // 同上
  }
}

async function loadMore() {
  if (loading.value || finished.value) return
  loading.value = true
  try {
    const page = await fetchPosts(cursor.value)
    // 首页第一页：置顶项剥离进横滑卡组，列表只留普通文章（置顶区/列表职责分离）
    const rest = firstPageLoaded.value
      ? page.items
      : page.items.filter(i => !i.topped)
    items.value.push(...rest)
    cursor.value = page.nextCursor
    finished.value = page.nextCursor === null
    firstPageLoaded.value = true
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

function coverOf(item: PostItem): string {
  return item.tags.length > 0 ? item.tags[0][0] : '⚓'
}

onMounted(() => {
  loadMore()
  loadSidebar()
})
</script>

<template>
  <div class="container-wide home-layout">
    <!-- 主列 -->
    <div class="main-col">
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

      <!-- 置顶横滑卡组（首屏置顶文，CSDN 卡样式）-->
      <template v-if="!searchResults && pinned.length > 0">
        <div class="pin-head">
          <span class="pin-title">📌 置顶推荐</span>
          <span class="pin-hint">← 滑动查看 →</span>
        </div>
        <div class="pin-strip">
          <RouterLink
            v-for="p in pinned"
            :key="p.id"
            :to="`/posts/${p.id}`"
            class="pin-card"
          >
            <span class="pin-cover">{{ coverOf(p) }}</span>
            <span class="pin-body">
              <span class="pin-card-title">{{ p.title }}</span>
              <span class="pin-meta">
                <span v-if="p.tags.length" class="pin-tag"><span class="dot"></span>{{ p.tags[0] }}</span>
                <span>👁 {{ p.read }}</span>
              </span>
            </span>
            <span class="pin-btn">阅读全文 →</span>
          </RouterLink>
        </div>
      </template>

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
            <p v-if="item.excerpt" class="card-excerpt">{{ item.excerpt }}</p>
            <div class="muted meta-row">
              <span class="nick">{{ item.authorNickname }}</span>
              <span>{{ formatTime(item.createTime) }}</span>
              <span v-for="tag in item.tags.slice(0, 3)" :key="tag" class="tagchip">{{ tag }}</span>
              <span class="stats">
                <span>{{ item.read }} 阅读</span>
                <span>{{ item.praise }} 赞</span>
                <span>{{ item.comment }} 评</span>
              </span>
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

    <!-- 侧栏：三卡上下平铺（无 tab，对齐技术派/牛客）-->
    <aside class="sidebar">
      <div class="panel side-card">
        <div class="side-head">
          <span class="side-title">🔥 热门文章</span>
          <span class="side-more">按互动热度</span>
        </div>
        <el-empty
          v-if="hotPosts.length === 0"
          :image-size="56"
          description="暂无热度数据"
        />
        <ol v-else class="rank-list">
          <li v-for="(h, i) in hotPosts" :key="h.id" class="rank-row">
            <span class="rank-no" :class="{ top3: i < 3 }">{{ i + 1 }}</span>
            <RouterLink :to="`/posts/${h.id}`" class="rank-title">{{ h.title }}</RouterLink>
            <span class="rank-meta">{{ h.hotScore.toFixed(0) }}</span>
          </li>
        </ol>
      </div>

      <div class="panel side-card">
        <div class="side-head">
          <span class="side-title">🏅 本月活跃</span>
          <span class="side-more">发文/互动积分</span>
        </div>
        <el-empty
          v-if="dailyRank.length === 0"
          :image-size="56"
          description="今日还没有活跃用户"
        />
        <ol v-else class="rank-list">
          <li v-for="r in dailyRank" :key="r.userId" class="rank-row">
            <span class="rank-no" :class="{ top3: r.rank <= 3 }">{{ r.rank }}</span>
            <span class="avatar-sm">{{ r.nickname[0] }}</span>
            <span class="rank-title">{{ r.nickname }}</span>
            <span class="rank-meta">{{ r.score }} 分</span>
          </li>
        </ol>
      </div>

      <div class="panel side-card">
        <div class="side-head">
          <span class="side-title">⚓ 关于 Ashore</span>
        </div>
        <div class="about-text">面经分享 + AI 模拟面试社区。<br>每一次复盘，都是离 offer 更近的一步。</div>
      </div>
    </aside>
  </div>
</template>

<style scoped>
.home-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 20px;
  align-items: start;
}

.main-col {
  min-width: 0;
}

@media (max-width: 1000px) {
  .home-layout {
    grid-template-columns: 1fr;
  }
  .sidebar {
    display: none;
  }
}

/* ── 置顶横滑卡组 ── */
.pin-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 10px;
}

.pin-title {
  font-size: 15px;
  font-weight: 700;
}

.pin-hint {
  font-size: 12px;
  color: var(--muted);
}

.pin-strip {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: calc(50% - 6px);
  gap: 12px;
  overflow-x: auto;
  scroll-snap-type: x mandatory;
  padding-bottom: 8px;
  margin-bottom: 20px;
}

.pin-strip::-webkit-scrollbar {
  height: 6px;
}

.pin-strip::-webkit-scrollbar-thumb {
  background: #d5dae1;
  border-radius: 3px;
}

.pin-card {
  scroll-snap-align: start;
  background: linear-gradient(135deg, #f7faff 0%, #eef3ff 100%);
  border: 1px solid #dfe8ff;
  border-radius: 12px;
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  text-decoration: none;
}

.pin-card:hover {
  border-color: var(--el-color-primary);
}

.pin-cover {
  width: 52px;
  height: 52px;
  border-radius: 10px;
  flex-shrink: 0;
  background: #fff;
  border: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 700;
  color: var(--el-color-primary);
}

.pin-body {
  min-width: 0;
  flex: 1;
}

.pin-card-title {
  display: block;
  font-size: 14.5px;
  font-weight: 700;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pin-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--muted);
}

.pin-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.pin-tag .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #34c724;
}

.pin-btn {
  border: 1px solid var(--border);
  background: #fff;
  border-radius: 8px;
  padding: 6px 12px;
  font-size: 12.5px;
  color: var(--text);
  flex-shrink: 0;
  white-space: nowrap;
}

/* ── 文章卡 ── */
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

.card-excerpt {
  font-size: 13px;
  color: var(--muted);
  margin: 6px 0 0;
  line-height: 1.65;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 10px 0 0;
  flex-wrap: wrap;
}

.nick {
  font-weight: 600;
  color: var(--text);
}

.tagchip {
  font-size: 11px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  padding: 1px 8px;
  border-radius: 999px;
}

.stats {
  margin-left: auto;
  display: flex;
  gap: 12px;
}

.load-more {
  text-align: center;
  padding: 8px 0 16px;
}

/* ── 侧栏 ── */
.sidebar {
  display: flex;
  flex-direction: column;
  gap: 14px;
  position: sticky;
  top: 76px;
}

.side-card {
  padding: 14px 16px;
}

.side-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 10px;
}

.side-title {
  font-size: 14px;
  font-weight: 700;
}

.side-more {
  font-size: 12px;
  color: var(--muted);
}

.about-text {
  font-size: 13px;
  color: var(--muted);
  line-height: 1.7;
}

.rank-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.rank-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px dashed var(--border);
  font-size: 13px;
}

.rank-row:last-child {
  border-bottom: none;
}

.rank-no {
  width: 18px;
  text-align: center;
  font-style: italic;
  font-weight: 800;
  color: #c3ccd6;
  flex-shrink: 0;
}

.rank-no.top3 {
  color: var(--el-color-danger);
}

.rank-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text);
  text-decoration: none;
}

a.rank-title:hover {
  color: var(--accent);
}

.rank-meta {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.avatar-sm {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary-dark-2);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}

/* ── 搜索 ── */
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

.search-hit .item-title {
  font-size: 17px;
}

.excerpt {
  margin-top: 4px;
  line-height: 1.7;
}
</style>
