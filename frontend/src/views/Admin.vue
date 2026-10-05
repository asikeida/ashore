<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, CircleClose, Lock, Unlock } from '@element-plus/icons-vue'
import { ApiError } from '../api/client'
import {
  fetchAdminPosts,
  setPostOffline,
  setPostOnline,
  setPostTopping,
  fetchAdminUsers,
  banUser,
  unbanUser,
  type AdminPostItem,
  type AdminUserItem,
} from '../api/admin'
import { fetchReports, handleReport, type ReportItem } from '../api/report'

// ── 面经审核 tab ──────────────────────────────────────────────
const posts = ref<AdminPostItem[]>([])
const postFilter = ref<'REVIEW' | 'ONLINE' | 'OFFLINE' | ''>('REVIEW')
const postsLoading = ref(false)

const STATUS_META: Record<string, { label: string; type: 'warning' | 'success' | 'info' }> = {
  REVIEW: { label: '待审核', type: 'warning' },
  ONLINE: { label: '已上线', type: 'success' },
  OFFLINE: { label: '已下线', type: 'info' },
}

async function loadPosts() {
  postsLoading.value = true
  try {
    posts.value = await fetchAdminPosts(postFilter.value || undefined)
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '加载失败')
  } finally {
    postsLoading.value = false
  }
}

async function online(id: string) {
  try {
    await setPostOnline(id)
    ElMessage.success('已上线')
    await loadPosts()
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '操作失败')
  }
}

async function offline(id: string) {
  try {
    await setPostOffline(id)
    ElMessage.success('已下线/驳回')
    await loadPosts()
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '操作失败')
  }
}

async function toggleTopping(row: AdminPostItem) {
  try {
    await setPostTopping(row.id, !row.topped)
    ElMessage.success(row.topped ? '已取消置顶' : '已置顶')
    await loadPosts()
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '操作失败')
  }
}

// ── 用户管理 tab ──────────────────────────────────────────────
// list 端点不返回 status（UserVo 无此字段），本地记录本次会话内封禁过的用户，
// 仅用于按钮展示；刷新后以接口能力为准（封禁 ADMIN/自己由后端防线兜底提示）
const users = ref<AdminUserItem[]>([])
const userKeyword = ref('')
const usersLoading = ref(false)
const actingUserId = ref<string | null>(null)

async function loadUsers() {
  usersLoading.value = true
  try {
    users.value = await fetchAdminUsers(userKeyword.value.trim() || undefined)
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '加载失败')
  } finally {
    usersLoading.value = false
  }
}

async function ban(user: AdminUserItem) {
  try {
    await ElMessageBox.confirm(
      `确认封禁「${user.nickname || user.account}」？其全部登录会话将即刻失效。`,
      '封禁用户',
      { confirmButtonText: '封禁', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return // 用户取消
  }
  actingUserId.value = user.id
  try {
    await banUser(user.id)
    ElMessage.success('已封禁并强制下线')
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '操作失败')
  } finally {
    actingUserId.value = null
    await loadUsers()
  }
}

async function unban(user: AdminUserItem) {
  actingUserId.value = user.id
  try {
    await unbanUser(user.id)
    ElMessage.success('已解封')
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '操作失败')
  } finally {
    actingUserId.value = null
    await loadUsers()
  }
}

function formatTime(time: string): string {
  return time.replace('T', ' ').slice(0, 16)
}

const activeTab = ref<'posts' | 'users' | 'reports'>('posts')

// ── 举报处理 tab ──────────────────────────────────────────────
const reports = ref<ReportItem[]>([])
const reportFilter = ref<'PENDING' | 'RESOLVED' | 'DISMISSED' | ''>('PENDING')
const reportsLoading = ref(false)
const actingReportId = ref<string | null>(null)

const REPORT_STATUS_META: Record<string, { label: string; type: 'warning' | 'success' | 'info' }> = {
  PENDING: { label: '待处理', type: 'warning' },
  RESOLVED: { label: '已处置', type: 'success' },
  DISMISSED: { label: '已驳回', type: 'info' },
}

async function loadReports() {
  reportsLoading.value = true
  try {
    reports.value = await fetchReports(reportFilter.value || undefined)
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '加载失败')
  } finally {
    reportsLoading.value = false
  }
}

async function onReportTabEnter() {
  if (reports.value.length === 0) await loadReports()
}

async function actOnReport(row: ReportItem, action: 'DISMISS' | 'OFFLINE') {
  const verb = action === 'OFFLINE' ? '处置内容（文章下线/评论删除）' : '驳回（标记误报）'
  try {
    await ElMessageBox.confirm(`确认${verb}？`, '处理举报', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  actingReportId.value = row.id
  try {
    await handleReport(row.id, action)
    ElMessage.success('已处理')
    await loadReports()
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '操作失败')
  } finally {
    actingReportId.value = null
  }
}

function onTabChange(tab: { paneName: string }) {
  if (tab.paneName === 'reports') onReportTabEnter()
}

onMounted(() => {
  loadPosts()
})
</script>

<template>
  <div class="container-wide">
    <div class="panel">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="面经审核" name="posts">
          <div class="admin-head">
            <el-radio-group v-model="postFilter" @change="loadPosts">
              <el-radio-button value="">全部</el-radio-button>
              <el-radio-button value="REVIEW">待审核</el-radio-button>
              <el-radio-button value="ONLINE">已上线</el-radio-button>
              <el-radio-button value="OFFLINE">已下线</el-radio-button>
            </el-radio-group>
          </div>

          <el-table
            v-loading="postsLoading"
            :data="posts"
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
                <el-tag v-if="row.topped" type="danger" size="small" effect="dark" round style="margin-left: 4px">顶</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="260" align="center">
              <template #default="{ row }">
                <el-popconfirm
                  v-if="row.status === 'REVIEW'"
                  title="确认审核通过并上线？"
                  confirm-button-text="上线"
                  cancel-button-text="取消"
                  @confirm="online(row.id)"
                >
                  <template #reference>
                    <el-button type="primary" size="small" :icon="CircleCheck" :loading="actingUserId === row.id">
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
                    <el-button size="small" :icon="CircleClose" :loading="actingUserId === row.id">
                      下线 / 驳回
                    </el-button>
                  </template>
                </el-popconfirm>
                <el-button
                  size="small"
                  :type="row.topped ? 'warning' : 'default'"
                  :loading="actingUserId === row.id"
                  @click="toggleTopping(row)"
                >
                  {{ row.topped ? '取消置顶' : '置顶' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="用户管理" name="users" lazy>
          <div class="admin-head">
            <el-input
              v-model="userKeyword"
              placeholder="按账号搜索"
              clearable
              style="max-width: 280px"
              @keyup.enter="loadUsers"
              @clear="loadUsers"
            >
              <template #append>
                <el-button @click="loadUsers">搜索</el-button>
              </template>
            </el-input>
          </div>

          <el-table
            v-loading="usersLoading"
            :data="users"
            style="width: 100%"
            empty-text="没有匹配的用户"
          >
            <el-table-column prop="account" label="账号" min-width="180" />
            <el-table-column prop="nickname" label="昵称" min-width="140" />
            <el-table-column label="角色" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'" effect="light" round>
                  {{ row.role }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" align="center">
              <template #default="{ row }">
                <!-- ADMIN 行不渲染封禁按钮（后端防线兜底，前端隐藏减少误触） -->
                <el-popconfirm
                  v-if="row.role !== 'ADMIN'"
                  title="封禁后其全部登录会话即刻失效，确认？"
                  confirm-button-text="封禁"
                  cancel-button-text="取消"
                  @confirm="ban(row)"
                >
                  <template #reference>
                    <el-button type="danger" size="small" :icon="Lock" :loading="actingUserId === row.id">
                      封禁
                    </el-button>
                  </template>
                </el-popconfirm>
                <el-button
                  size="small"
                  :icon="Unlock"
                  :loading="actingUserId === row.id"
                  @click="unban(row)"
                >
                  解封
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="举报处理" name="reports" lazy>
          <div class="admin-head">
            <el-radio-group v-model="reportFilter" @change="loadReports">
              <el-radio-button value="PENDING">待处理</el-radio-button>
              <el-radio-button value="RESOLVED">已处置</el-radio-button>
              <el-radio-button value="DISMISSED">已驳回</el-radio-button>
            </el-radio-group>
          </div>

          <el-table v-loading="reportsLoading" :data="reports" style="width: 100%" empty-text="没有符合条件的举报">
            <el-table-column label="目标" min-width="200">
              <template #default="{ row }">
                <el-tag :type="row.targetType === 'ARTICLE' ? 'primary' : 'info'" size="small" effect="light" round style="margin-right: 6px">
                  {{ row.targetType === 'ARTICLE' ? '面经' : '评论' }}
                </el-tag>{{ row.targetPreview }}
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="举报理由" min-width="180" />
            <el-table-column prop="reporterNickname" label="举报人" width="120" />
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="REPORT_STATUS_META[row.status]?.type ?? 'info'" size="small" round>
                  {{ REPORT_STATUS_META[row.status]?.label ?? row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" align="center">
              <template #default="{ row }">
                <template v-if="row.status === 'PENDING'">
                  <el-button type="danger" size="small" :loading="actingReportId === row.id" @click="actOnReport(row, 'OFFLINE')">
                    处置内容
                  </el-button>
                  <el-button size="small" :loading="actingReportId === row.id" @click="actOnReport(row, 'DISMISS')">
                    驳回
                  </el-button>
                </template>
                <span v-else class="muted">已处理</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
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
