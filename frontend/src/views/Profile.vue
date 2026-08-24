<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ChatDotRound, Collection, Pointer, View } from '@element-plus/icons-vue'
import {
  fetchMyArticles,
  fetchMyCollects,
  fetchMyInterviews,
  fetchMyPraises,
  type MeArticleItem,
  type MeFootItem,
  type MeInterviewItem,
} from '../api/me'
import { auth } from '../stores/auth'

const activeTab = ref<'articles' | 'collects' | 'praises' | 'interviews'>('articles')
const myArticles = ref<MeArticleItem[]>([])
const collects = ref<MeFootItem[]>([])
const praises = ref<MeFootItem[]>([])
const interviews = ref<MeInterviewItem[]>([])
const loading = ref(false)
const router = useRouter()

const STATUS_META: Record<string, { label: string; type: 'warning' | 'success' | 'info' }> = {
  REVIEW: { label: '审核中', type: 'warning' },
  ONLINE: { label: '已上线', type: 'success' },
  OFFLINE: { label: '已下线', type: 'info' },
}

function formatTime(time: string | null): string {
  return time ? time.replace('T', ' ').slice(0, 16) : ''
}

async function loadAll() {
  loading.value = true
  try {
    myArticles.value = await fetchMyArticles()
    collects.value = await fetchMyCollects()
    praises.value = await fetchMyPraises()
    interviews.value = await fetchMyInterviews()
  } finally {
    loading.value = false
  }
}

function openInterview(row: unknown) {
  const sessionId = (row as { sessionId: string }).sessionId
  router.push(`/profile/interview/${sessionId}`)
}

onMounted(loadAll)
</script>

<template>
  <div class="container">
    <div class="panel">
      <div class="profile-head">
        <el-avatar :size="44" class="profile-avatar">{{ auth.account?.[0] ?? '?' }}</el-avatar>
        <div>
          <h2 style="margin: 0">{{ auth.account }}</h2>
          <p class="muted" style="margin: 2px 0 0">我的面经、收藏点赞复习角、AI 面试记录都在这里</p>
        </div>
      </div>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="我的面经" name="articles">
          <el-empty
            v-if="myArticles.length === 0 && !loading"
            :image-size="72"
            description="还没有发布过面经——去写下第一篇吧"
          />
          <el-table :data="myArticles" style="width: 100%" empty-text="暂无面经">
            <el-table-column label="标题" min-width="240">
              <template #default="{ row }">
                <RouterLink :to="`/posts/${row.articleId}`" class="table-title">{{ row.title }}</RouterLink>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="STATUS_META[row.status]?.type ?? 'info'" size="small" round>
                  {{ STATUS_META[row.status]?.label ?? row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="数据" width="240" align="center">
              <template #default="{ row }">
                <span class="stat-item" :title="'阅读 ' + row.read"><el-icon><View /></el-icon>{{ row.read }}</span>
                <span class="stat-item" :title="'点赞 ' + row.praise">{{ row.praise }} 赞</span>
                <span class="stat-item" :title="'收藏 ' + row.collect">{{ row.collect }} 藏</span>
                <span class="stat-item" :title="'评论 ' + row.comment">{{ row.comment }} 评</span>
              </template>
            </el-table-column>
            <el-table-column label="发布时间" width="150">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="我的收藏" name="collects">
          <el-empty
            v-if="collects.length === 0 && !loading"
            :image-size="72"
            description="还没有收藏——去面经详情页点收藏，回来复习"
          />
          <article
            v-for="item in collects"
            :key="item.articleId"
            class="panel panel-hover foot-card"
          >
            <RouterLink :to="`/posts/${item.articleId}`" class="item-title">{{ item.title }}</RouterLink>
            <div class="muted meta-row">
              {{ item.authorNickname }} · 收藏于 {{ formatTime(item.footTime) }}
            </div>
            <div v-if="item.tags.length" class="tag-row">
              <el-tag v-for="tag in item.tags" :key="tag" size="small" effect="plain" round>{{ tag }}</el-tag>
            </div>
          </article>
        </el-tab-pane>

        <el-tab-pane label="我的点赞" name="praises">
          <el-empty
            v-if="praises.length === 0 && !loading"
            :image-size="72"
            description="还没有点赞记录"
          />
          <article
            v-for="item in praises"
            :key="item.articleId"
            class="panel panel-hover foot-card"
          >
            <RouterLink :to="`/posts/${item.articleId}`" class="item-title">{{ item.title }}</RouterLink>
            <div class="muted meta-row">
              {{ item.authorNickname }} · 点赞于 {{ formatTime(item.footTime) }}
            </div>
            <div v-if="item.tags.length" class="tag-row">
              <el-tag v-for="tag in item.tags" :key="tag" size="small" effect="plain" round>{{ tag }}</el-tag>
            </div>
          </article>
        </el-tab-pane>

        <el-tab-pane label="AI 面试记录" name="interviews">
          <el-empty
            v-if="interviews.length === 0 && !loading"
            :image-size="72"
            description="还没和 AI 面试官过招——去面经详情页试试"
          />
          <el-table :data="interviews" style="width: 100%" empty-text="暂无记录">
            <el-table-column label="面经" min-width="220">
              <template #default="{ row }">
                <RouterLink :to="`/posts/${row.articleId}`" class="table-title">{{ row.articleTitle }}</RouterLink>
              </template>
            </el-table-column>
            <el-table-column label="轮数" width="90" align="center">
              <template #default="{ row }">{{ row.questionCount }} 题</template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small" round>
                  {{ row.status === 'ACTIVE' ? '进行中' : '已结束' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="最近活跃" width="150">
              <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120" align="center">
              <template #default="{ row }">
                <el-button
                  type="primary"
                  size="small"
                  :icon="ChatDotRound"
                  plain
                  @click="openInterview(row)"
                >
                  回看
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<style scoped>
.profile-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 8px;
}

.profile-avatar {
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary-dark-2);
  font-weight: 700;
  font-size: 20px;
}

.foot-card .item-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text);
  text-decoration: none;
}

.item-title:hover {
  color: var(--accent);
}

.meta-row {
  margin: 6px 0 8px;
}

.tag-row {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.table-title {
  color: var(--text);
  font-weight: 600;
  text-decoration: none;
}

.table-title:hover {
  color: var(--accent);
}

.stat-item {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin: 0 8px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
