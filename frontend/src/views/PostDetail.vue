<script setup lang="ts">
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  createComment,
  fetchComments,
  fetchPost,
  toggleCollect,
  togglePraise,
  type CommentItem,
  type PostDetail,
} from '../api/post'
import { auth, refreshAuth } from '../stores/auth'

const props = defineProps<{ id: string }>()

const post = ref<PostDetail | null>(null)
const comments = ref<CommentItem[]>([])
const notFound = ref(false)
const commentText = ref('')
const submitting = ref(false)
const aiStarting = ref(false)
const router = useRouter()

function render(markdown: string | null): string {
  if (!markdown) return ''
  // 用户输入 markdown → HTML 后必须消毒（防 XSS）
  return DOMPurify.sanitize(marked.parse(markdown) as string)
}

async function load() {
  try {
    post.value = await fetchPost(props.id)
    const page = await fetchComments(props.id, null)
    comments.value = page.items
  } catch {
    notFound.value = true
  }
}

async function praise() {
  if (!post.value) return
  post.value.praised = await togglePraise(props.id)
  await refreshAuth()
}

async function collect() {
  if (!post.value) return
  post.value.collected = await toggleCollect(props.id)
}

async function submitComment() {
  if (!commentText.value.trim() || submitting.value) return
  submitting.value = true
  try {
    await createComment(props.id, commentText.value.trim(), null)
    commentText.value = ''
    post.value!.commentCount += 1
    const page = await fetchComments(props.id, null)
    comments.value = page.items
  } finally {
    submitting.value = false
  }
}

function startInterview() {
  aiStarting.value = true
  router.push(`/posts/${props.id}/interview`)
}

onMounted(load)
</script>

<template>
  <div v-if="notFound" class="card">
    <p>面经不存在或不可见。</p>
    <RouterLink to="/">返回列表</RouterLink>
  </div>

  <div v-else-if="post" class="card">
    <h2>{{ post.title }}</h2>
    <div class="muted">
      {{ post.authorId }} · {{ post.createTime }} · 赞 {{ post.praiseCount }} / 藏
      {{ post.collectCount }} / 评 {{ post.commentCount }} / 阅 {{ post.readCount }}
    </div>
    <div style="margin: 10px 0">
      <span v-for="tag in post.tags" :key="tag" class="tag">{{ tag }}</span>
    </div>
    <div class="post-content" v-html="render(post.content)"></div>

    <div class="actions">
      <button class="ghost" :class="{ active: post.praised }" @click="praise">
        👍 {{ post.praised ? '已赞' : '点赞' }} · {{ post.praiseCount }}
      </button>
      <button class="ghost" :class="{ active: post.collected }" @click="collect">
        ⭐ {{ post.collected ? '已藏' : '收藏' }} · {{ post.collectCount }}
      </button>
      <button class="primary" :disabled="aiStarting" @click="startInterview">
        🤖 AI 面试官
      </button>
    </div>
  </div>

  <div v-if="post" class="card">
    <h3>评论（{{ post.commentCount }}）</h3>
    <div v-if="auth.loggedIn" style="margin-bottom: 12px">
      <textarea v-model="commentText" style="min-height: 80px" placeholder="写下你的评论…"></textarea>
      <button class="primary" :disabled="submitting" @click="submitComment">
        {{ submitting ? '发布中…' : '发布评论' }}
      </button>
    </div>
    <div v-for="comment in comments" :key="comment.id" class="comment">
      <strong>{{ comment.nickname }}</strong>
      <span class="muted" style="margin-left: 8px">{{ comment.createTime }}</span>
      <div class="post-content">{{ comment.content }}</div>
      <div v-for="reply in comment.replies" :key="reply.id" class="reply">
        <strong>{{ reply.nickname }}</strong>
        <span v-if="reply.replyNickname" class="muted"> 回复 @{{ reply.replyNickname }}</span>
        <div class="post-content">{{ reply.content }}</div>
      </div>
    </div>
    <p v-if="comments.length === 0" class="muted">还没有评论。</p>
  </div>
</template>

<style scoped>
button.ghost.active {
  border-color: var(--accent);
  color: var(--accent);
}
</style>
