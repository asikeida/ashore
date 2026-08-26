<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ApiError } from '../api/client'
import {
  fetchNotices,
  markAllRead,
  type NoticeItem,
} from '../api/community'

const items = ref<NoticeItem[]>([])
const loading = ref(false)
const activeType = ref<string>('') // '' = 全部

const TYPE_META: Record<string, { label: string; type: 'primary' | 'success' | 'warning' | 'danger' | 'info' }> = {
  COMMENT: { label: '评论', type: 'primary' },
  REPLY: { label: '回复', type: 'primary' },
  PRAISE: { label: '点赞', type: 'success' },
  COLLECT: { label: '收藏', type: 'warning' },
  SYSTEM: { label: '系统', type: 'info' },
}

async function load() {
  loading.value = true
  try {
    items.value = await fetchNotices(activeType.value || undefined)
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function readAll() {
  try {
    await markAllRead()
    ElMessage.success('已全部标记为已读')
    await load()
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '操作失败')
  }
}

function formatTime(time: string): string {
  return time.replace('T', ' ').slice(0, 16)
}

function onTypeChange() {
  load()
}

onMounted(load)
</script>

<template>
  <div class="container">
    <div class="panel">
      <div class="notice-head">
        <h2 style="margin: 0">消息中心</h2>
        <el-button size="small" round @click="readAll">全部已读</el-button>
      </div>

      <el-radio-group v-model="activeType" @change="onTypeChange" style="margin: 12px 0">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="SYSTEM">系统</el-radio-button>
        <el-radio-button value="COMMENT">评论</el-radio-button>
        <el-radio-button value="PRAISE">点赞</el-radio-button>
        <el-radio-button value="COLLECT">收藏</el-radio-button>
      </el-radio-group>

      <el-skeleton :loading="loading && items.length === 0" :rows="5" animated>
        <el-empty
          v-if="items.length === 0 && !loading"
          :image-size="72"
          description="没有消息——互动和审核通知都会出现在这里"
        />
        <article
          v-for="n in items"
          :key="n.id"
          class="notice-item"
          :class="{ unread: !n.read }"
        >
          <div class="notice-line">
            <el-tag :type="TYPE_META[n.type]?.type ?? 'info'" size="small" effect="light" round>
              {{ TYPE_META[n.type]?.label ?? n.type }}
            </el-tag>
            <span v-if="!n.read" class="dot" title="未读"></span>
            <span class="muted notice-time">{{ formatTime(n.createTime) }}</span>
          </div>
          <div class="notice-content">
            <template v-if="n.operatorNickname">
              <strong>{{ n.operatorNickname }}</strong>　{{ n.content }}
            </template>
            <template v-else>{{ n.content }}</template>
          </div>
          <RouterLink v-if="n.articleId" :to="`/posts/${n.articleId}`" class="notice-link">
            查看相关面经 →
          </RouterLink>
        </article>
      </el-skeleton>
    </div>
  </div>
</template>

<style scoped>
.notice-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.notice-item {
  padding: 12px 4px;
  border-bottom: 1px solid var(--border);
}

.notice-item.unread {
  background: var(--el-color-primary-light-9);
  border-radius: 8px;
  padding-left: 10px;
}

.notice-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-color-danger);
  display: inline-block;
}

.notice-time {
  font-size: 12px;
}

.notice-content {
  margin: 6px 0 4px;
  color: var(--text);
}

.notice-link {
  font-size: 13px;
  color: var(--accent);
  text-decoration: none;
}

.notice-link:hover {
  text-decoration: underline;
}
</style>
