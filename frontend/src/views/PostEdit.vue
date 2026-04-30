<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError } from '../api/client'
import { publishPost } from '../api/post'

const title = ref('')
const content = ref('')
const tags = ref('')
const error = ref('')
const submitting = ref(false)
const router = useRouter()

async function submit() {
  error.value = ''
  submitting.value = true
  try {
    const tagList = tags.value
      .split(/[,，\s]+/)
      .map(t => t.trim())
      .filter(Boolean)
    const result = await publishPost(title.value, content.value, tagList)
    // 发布进 REVIEW 审核态，提示后回列表
    router.push('/')
    return result
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '发布失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="card">
    <h2>写面经</h2>
    <p class="muted">发布后进入审核，审核通过即公开可见。</p>
    <input v-model="title" placeholder="标题（5-128 字）" />
    <textarea v-model="content" placeholder="正文（Markdown，支持后续渲染）"></textarea>
    <input v-model="tags" placeholder="标签，逗号分隔（如：字节跳动, Redis, 后端）" />
    <p v-if="error" class="error">{{ error }}</p>
    <button class="primary" :disabled="submitting" @click="submit">
      {{ submitting ? '发布中…' : '发布（进审核）' }}
    </button>
  </div>
</template>
