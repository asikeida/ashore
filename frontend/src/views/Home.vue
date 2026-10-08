<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchPosts, searchPosts, type PostItem, type SearchItem } from '../api/post'
import { fetchDailyRank, fetchHotPosts, type HotPostItem, type RankItem } from '../api/community'

const route = useRoute()
const router = useRouter()
const items = ref<PostItem[]>([])
const pinned = ref<PostItem[]>([])
const cursor = ref<string | null>(null)
const loading = ref(false)
const finished = ref(false)
const searchResults = ref<SearchItem[] | null>(null)
const searchLoading = ref(false)
const searchError = ref(false)
const hotPosts = ref<HotPostItem[]>([])
const dailyRank = ref<RankItem[]>([])
const keyword = ref('')

async function loadSidebar() {
  const [hot, rank] = await Promise.allSettled([fetchHotPosts(8), fetchDailyRank(8)])
  if (hot.status === 'fulfilled') hotPosts.value = hot.value
  if (rank.status === 'fulfilled') dailyRank.value = rank.value
}

async function loadMore() {
  if (loading.value || finished.value) return
  loading.value = true
  try {
    const page = await fetchPosts(cursor.value)
    if (cursor.value === null) {
      pinned.value = page.items.filter(item => item.topped)
      items.value = page.items.filter(item => !item.topped)
    } else {
      items.value.push(...page.items)
    }
    cursor.value = page.nextCursor
    finished.value = page.nextCursor === null
  } catch {
    ElMessage.error('面经加载失败，请重试')
  } finally {
    loading.value = false
  }
}

async function runSearch(q: string) {
  keyword.value = q
  if (!q) { searchResults.value = null; searchError.value = false; return }
  searchError.value = false
  searchLoading.value = true
  try {
    searchResults.value = await searchPosts(q)
  } catch {
    searchResults.value = null
    searchError.value = true
  } finally {
    searchLoading.value = false
  }
}

function clearSearch() { router.push('/') }
function formatTime(time: string) { return time.replace('T', ' ').slice(0, 16) }
function displayExcerpt(excerpt: string | null) {
  if (!excerpt) return ''
  return excerpt
    .replace(/!\[[^\]]*\]\([^)]*(?:\)|$)/g, '')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/(^|\s)#{1,6}\s/g, ' ')
    .replace(/[*_`>]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
}

watch(() => route.query.q, q => { void runSearch(typeof q === 'string' ? q.trim() : '') }, { immediate: true })
onMounted(() => { void loadMore(); void loadSidebar() })
</script>

<template>
  <div class="container-wide home-page">
    <div class="home-head">
      <div><span class="eyebrow">INTERVIEW NOTES</span><h1>{{ keyword ? '搜索结果' : '面经' }}</h1>
        <p>{{ keyword ? `关键词：${keyword}` : '阅读真实经历，记录自己的复盘' }}</p></div>
      <RouterLink v-if="!keyword" to="/write" class="head-write">写面经</RouterLink>
      <button v-else class="clear-search" @click="clearSearch">清除搜索</button>
    </div>
    <div class="home-layout">
      <main class="main-col">
        <template v-if="keyword">
          <div v-if="searchLoading" class="list-status">正在搜索…</div>
          <div v-else-if="searchError" class="list-status">搜索暂时不可用，请稍后重试</div>
          <template v-else-if="searchResults">
            <div v-if="searchResults.length === 0" class="list-status">没有匹配的面经，换个关键词试试</div>
            <article v-for="item in searchResults" :key="item.id" class="post-row">
              <div class="post-row-meta">{{ item.authorNickname }} · {{ formatTime(item.createTime) }}</div>
              <RouterLink :to="`/posts/${item.id}`" class="post-title">
                <span v-if="item.titleHighlight" v-html="item.titleHighlight"></span><span v-else>{{ item.title }}</span>
              </RouterLink>
              <p v-if="item.contentHighlight" class="post-excerpt" v-html="item.contentHighlight"></p>
              <div class="post-tags"><span v-for="tag in item.tags.slice(0, 3)" :key="tag">{{ tag }}</span></div>
            </article>
          </template>
        </template>
        <template v-else>
          <section v-if="pinned.length" class="pinned-section">
            <h2>置顶推荐</h2>
            <div class="pinned-grid"><RouterLink v-for="item in pinned" :key="item.id" :to="`/posts/${item.id}`" class="pinned-card">
              <span>置顶</span><strong>{{ item.title }}</strong><small>{{ item.tags.slice(0, 2).join(' · ') }} · {{ item.read }} 阅读 →</small>
            </RouterLink></div>
          </section>
          <div v-if="loading && !items.length && !pinned.length" class="list-status">面经加载中…</div>
          <div v-else-if="!items.length && !pinned.length" class="list-status">还没有面经，来写下第一篇吧</div>
          <article v-for="item in items" :key="item.id" class="post-row">
            <div class="post-row-meta">{{ item.authorNickname }} · {{ formatTime(item.createTime) }}</div>
            <RouterLink :to="`/posts/${item.id}`" class="post-title">{{ item.title }}</RouterLink>
            <p v-if="displayExcerpt(item.excerpt)" class="post-excerpt">{{ displayExcerpt(item.excerpt) }}</p>
            <div class="post-bottom"><div class="post-tags"><span v-for="tag in item.tags.slice(0, 3)" :key="tag">{{ tag }}</span></div>
              <div class="post-stats">{{ item.read }} 阅读 · {{ item.praise }} 赞 · {{ item.comment }} 评论</div></div>
          </article>
          <button v-if="!finished && (items.length || pinned.length)" class="load-more" :disabled="loading" @click="loadMore">{{ loading ? '加载中…' : '加载更多' }}</button>
        </template>
      </main>
      <aside class="home-sidebar">
        <section class="side-card"><h2>热门文章 <small>按互动热度</small></h2>
          <p v-if="!hotPosts.length" class="side-empty">暂无热度数据</p>
          <RouterLink v-for="(item, index) in hotPosts" :key="item.id" :to="`/posts/${item.id}`" class="rank-row">
            <b>{{ String(index + 1).padStart(2, '0') }}</b><span>{{ item.title }}</span><small>{{ item.hotScore.toFixed(0) }}</small>
          </RouterLink></section>
        <section class="side-card"><h2>今日活跃 <small>发文与互动积分</small></h2>
          <p v-if="!dailyRank.length" class="side-empty">今日还没有活跃用户</p>
          <RouterLink v-for="item in dailyRank" :key="item.userId" :to="`/user/${item.userId}`" class="rank-row">
            <b>{{ String(item.rank).padStart(2, '0') }}</b><span>{{ item.nickname }}</span><small>{{ item.score }} 分</small>
          </RouterLink></section>
        <section class="side-card about-card"><h2>关于 Ashore</h2><p>面经分享与 AI 辅助复盘社区。</p><RouterLink to="/about">了解项目 →</RouterLink></section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.home-page{max-width:1190px;padding-top:45px}.home-head{display:flex;align-items:end;justify-content:space-between;margin-bottom:26px}.eyebrow{font-size:11px;font-weight:800;letter-spacing:2px;color:var(--accent)}.home-head h1{font-size:31px;line-height:1.25;margin:9px 0 4px}.home-head p{color:var(--muted);margin:0}.head-write,.clear-search{padding:8px 15px;border-radius:8px;background:var(--accent);color:#fff;border:0;text-decoration:none;cursor:pointer}.clear-search{background:var(--card);color:var(--text);border:1px solid var(--border)}.home-layout{display:grid;grid-template-columns:minmax(0,1fr) 290px;gap:22px;align-items:start}.main-col{min-width:0}.post-row,.side-card{background:var(--card);border:1px solid var(--border);border-radius:10px;box-shadow:var(--shadow-card)}.post-row{padding:21px 24px;margin-bottom:11px}.post-row:hover{border-color:#d4b4a6}.post-row-meta{font-size:12px;color:var(--muted)}.post-title{display:block;margin:7px 0;font-size:18px;font-weight:700;line-height:1.45;color:var(--text);text-decoration:none}.post-title:hover{color:var(--accent)}.post-excerpt{color:var(--muted);font-size:13px;line-height:1.7;margin:0 0 12px}.post-bottom{display:flex;align-items:center;justify-content:space-between;gap:10px}.post-tags{display:flex;gap:6px;flex-wrap:wrap}.post-tags span{font-size:11px;padding:2px 8px;background:#f7ece6;color:#a65c43;border-radius:4px}.post-stats{font-size:11px;color:var(--muted);white-space:nowrap}.list-status{padding:35px;text-align:center;color:var(--muted);background:var(--card);border:1px solid var(--border);border-radius:10px}.load-more{background:var(--card);border:1px solid var(--border);border-radius:8px;padding:9px 16px;color:var(--text);cursor:pointer}.load-more:disabled{opacity:.5}.pinned-section h2{font-size:16px;margin:0 0 10px}.pinned-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin-bottom:19px}.pinned-card{display:flex;flex-direction:column;gap:7px;padding:17px;background:#fff7f1;border:1px solid #ead9ca;border-radius:9px;text-decoration:none;color:var(--text)}.pinned-card:hover{border-color:var(--accent)}.pinned-card span{color:var(--accent);font-size:11px;font-weight:700}.pinned-card strong{line-height:1.45}.pinned-card small{color:var(--muted)}.home-sidebar{display:flex;flex-direction:column;gap:13px;position:sticky;top:90px}.side-card{padding:19px}.side-card h2{font-size:15px;margin:0 0 11px}.side-card h2 small{font-size:11px;font-weight:400;color:var(--muted)}.rank-row{display:flex;align-items:start;gap:9px;padding:10px 0;border-top:1px solid var(--border);text-decoration:none;color:var(--text);font-size:12px}.rank-row:hover span{color:var(--accent)}.rank-row b{color:var(--accent);width:22px;flex:none}.rank-row span{flex:1;line-height:1.5}.rank-row small{color:var(--muted);white-space:nowrap}.side-empty,.about-card p{color:var(--muted);font-size:12px}.about-card a{font-size:12px;color:var(--accent);text-decoration:none}
@media(max-width:900px){.home-layout{grid-template-columns:1fr}.home-sidebar{position:static;display:grid;grid-template-columns:repeat(2,minmax(0,1fr))}.about-card{grid-column:1/-1}}
@media(max-width:600px){.home-page{padding-top:27px}.home-sidebar,.pinned-grid{grid-template-columns:1fr}.post-row{padding:17px}.post-bottom{align-items:start;flex-direction:column}.home-head h1{font-size:26px}}
</style>
