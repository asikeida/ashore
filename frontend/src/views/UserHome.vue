<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { View } from '@element-plus/icons-vue'
import { fetchUserProfile, type UserProfile } from '../api/user'

const profile = ref<UserProfile | null>(null)
const notFound = ref(false)
const loading = ref(true)
const route = useRoute()

function formatTime(time: string | null): string {
  return time ? time.replace('T', ' ').slice(0, 10) : ''
}

onMounted(async () => {
  try {
    profile.value = await fetchUserProfile(route.params.id as string)
  } catch {
    notFound.value = true
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="container-wide">
    <el-empty v-if="notFound" description="用户不存在">
      <RouterLink to="/"><el-button type="primary" plain>返回列表</el-button></RouterLink>
    </el-empty>

    <div v-else class="panel" v-loading="loading">
      <div class="profile-head">
        <el-avatar :size="56" :src="profile?.avatar || undefined" class="profile-avatar">
          {{ profile?.nickname?.[0] ?? '?' }}
        </el-avatar>
        <div>
          <h2 style="margin: 0">
            {{ profile?.nickname }}
            <el-tag v-if="profile?.role === 'ADMIN'" type="danger" size="small" round style="margin-left: 6px">管理员</el-tag>
          </h2>
          <p class="muted" style="margin: 4px 0 0">加入于 {{ formatTime(profile?.createTime ?? null) }} ·
            {{ profile?.articleCount }} 篇面经 ·
            累计阅读 {{ profile?.totalRead }} · 累计获赞 {{ profile?.totalPraise }}</p>
        </div>
      </div>

      <el-empty
        v-if="profile && profile.articles.length === 0"
        :image-size="72"
        description="TA 还没有已上线的面经"
      />
      <article v-for="a in profile?.articles" :key="a.id" class="panel panel-hover post-card">
        <RouterLink :to="`/posts/${a.id}`" class="item-title">
          <el-tag v-if="a.topped" type="danger" size="small" effect="dark" round style="margin-right: 6px">置顶</el-tag>{{ a.title }}
        </RouterLink>
        <div class="muted meta-row">{{ formatTime(a.createTime) }}</div>
        <div v-if="a.tags.length" class="tag-row">
          <el-tag v-for="tag in a.tags" :key="tag" size="small" effect="plain" round>{{ tag }}</el-tag>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.profile-head {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.profile-avatar {
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary-dark-2);
  font-weight: 700;
  font-size: 24px;
}

.post-card {
  margin-bottom: 12px;
}

.item-title {
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
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-right: 16px;
}

.tag-row {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
</style>
