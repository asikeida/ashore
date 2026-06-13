<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheck, CircleClose } from '@element-plus/icons-vue'
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
const actingId = ref<string | null>(null)

const STATUS_META: Record<string, { label: string; type: 'warning' | 'success' | 'info' }> = {
  REVIEW: { label: '待审核', type: 'warning' },
  ONLINE: { label: '已上线', type: 'success' },
  OFFLINE: { label: '已下线', type: 'info' },
}

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
  actingId.value = id
  try {
    await setPostOnline(id)
    ElMessage.success('已上线')
    await load()
  } finally {
    actingId.value = null
  }
}

async function offline(id: string) {
  actingId.value = id
  try {
    await setPostOffline(id)
    ElMessage.success('已下线/驳回')
    await load()
  } finally {
    actingId.value = null
  }
}

function formatTime(time: string): string {
  return time.replace('T', ' ').slice(0, 16)
}

onMounted(load)
</script>

<template>
  <div class="container-wide">
    <div class="panel">
      <div class="admin-head">
        <h2 style="margin: 0">管理台 · 面经审核</h2>
        <el-radio-group v-model="filter" @change="load">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="REVIEW">待审核</el-radio-button>
          <el-radio-button value="ONLINE">已上线</el-radio-button>
          <el-radio-button value="OFFLINE">已下线</el-radio-button>
        </el-radio-group>
      </div>

      <el-alert
        v-if="error"
        :title="error"
        type="error"
        show-icon
        :closable="false"
        style="margin: 12px 0"
      />

      <el-table
        v-loading="loading"
        :data="items"
        style="width: 100%"
        empty-text="没有符合条件的面经"
      >
        <el-table-column label="标题" min-width="260">
          <template #default="{ row }">
            <RouterLink :to="`/posts/${row.id}`" class="table-title">{{ row.title }}</RouterLink>
          </template>
        </el-table-column>
        <el-table-column prop="authorNickname" label="作者" width="120" />
        <el-table-column label="时间" width="140">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="STATUS_META[row.status]?.type ?? 'info'" effect="light" round>
              {{ STATUS_META[row.status]?.label ?? row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" align="center">
          <template #default="{ row }">
            <el-popconfirm
              v-if="row.status === 'REVIEW'"
              title="确认审核通过并上线？"
              confirm-button-text="上线"
              cancel-button-text="取消"
              @confirm="online(row.id)"
            >
              <template #reference>
                <el-button type="primary" size="small" :icon="CircleCheck" :loading="actingId === row.id">
                  审核上线
                </el-button>
              </template>
            </el-popconfirm>
            <el-popconfirm
              v-if="row.status === 'ONLINE' || row.status === 'REVIEW'"
              title="确认下线 / 驳回？"
              confirm-button-text="下线"
              cancel-button-text="取消"
              @confirm="offline(row.id)"
            >
              <template #reference>
                <el-button size="small" :icon="CircleClose" :loading="actingId === row.id">
                  下线 / 驳回
                </el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.admin-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.table-title {
  color: var(--text);
  font-weight: 600;
  text-decoration: none;
}

.table-title:hover {
  color: var(--accent);
}
</style>
