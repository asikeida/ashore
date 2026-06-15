<script setup lang="ts">
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Collection, Pointer, Promotion } from '@element-plus/icons-vue'
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
  post.value.praiseCount += post.value.praised ? 1 : -1
  await refreshAuth()
}

async function collect() {
  if (!post.value) return
  post.value.collected = await toggleCollect(props.id)
  post.value.collectCount += post.value.collected ? 1 : -1
  ElMessage.success(post.value.collected ? '已加入收藏' : '已取消收藏')
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
    ElMessage.success('评论已发布')
  } finally {
    submitting.value = false
  }
}

function startInterview() {
  aiStarting.value = true
  router.push(`/posts/${props.id}/interview`)
}

function formatTime(time: string): string {
  return time.replace('T', ' ').slice(0, 16)
}

onMounted(load)
</script>

<template>
  <div class="container">
    <el-empty v-if="notFound" description="面经不存在或不可见">
      <RouterLink to="/"><el-button type="primary" plain>返回列表</el-button></RouterLink>
    </el-empty>

    <template v-if="post">
      <article class="panel">
        <h1 class="post-title">{{ post.title }}</h1>
        <div class="muted meta-row">
          <el-avatar :size="24" class="author-avatar">{{ (post.authorId ?? '匿名')[0] }}</el-avatar>
          <span>{{ post.authorId }}</span>
          <el-divider direction="vertical" />
          <span>{{ formatTime(post.createTime) }}</span>
          <el-divider direction="vertical" />
          <span>阅 {{ post.readCount }}</span>
        </div>
        <div v-if="post.tags.length" class="tag-row">
          <el-tag v-for="tag in post.tags" :key="tag" effect="plain" round>{{ tag }}</el-tag>
        </div>
        <el-divider style="margin: 16px 0" />
        <div class="post-content" v-html="render(post.content)"></div>

        <div class="actions">
          <el-button
            :type="post.praised ? 'primary' : 'default'"
            :icon="Pointer"
            round
            @click="praise"
          >
            {{ post.praised ? '已赞' : '点赞' }} · {{ post.praiseCount }}
          </el-button>
          <el-button
            :type="post.collected ? 'warning' : 'default'"
            :icon="Collection"
            round
            @click="collect"
          >
            {{ post.collected ? '已藏' : '收藏' }} · {{ post.collectCount }}
          </el-button>
          <el-button
            type="primary"
            :icon="ChatDotRound"
            :loading="aiStarting"
            @click="startInterview"
          >
            AI 面试官
          </el-button>
        </div>
      </article>

      <section class="panel">
        <h3 class="section-title">评论（{{ post.commentCount }}）</h3>
        <div v-if="auth.loggedIn" class="comment-editor">
          <el-input
            v-model="commentText"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            placeholder="写下你的评论…"
          />
          <div style="margin-top: 10px; text-align: right">
            <el-button type="primary" :icon="Promotion" :loading="submitting" @click="submitComment">
              发布评论
            </el-button>
          </div>
        </div>
        <el-alert
          v-else
          title="登录后即可参与评论"
          type="info"
          :closable="false"
          style="margin-bottom: 12px"
        />

        <div v-for="comment in comments" :key="comment.id" class="comment">
          <div class="comment-head">
            <el-avatar :size="26" class="author-avatar">{{ comment.nickname[0] }}</el-avatar>
            <strong>{{ comment.nickname }}</strong>
            <span class="muted">{{ formatTime(comment.createTime) }}</span>
          </div>
          <div class="comment-body">{{ comment.content }}</div>
          <div v-for="reply in comment.replies" :key="reply.id" class="reply">
            <div class="comment-head">
              <el-avatar :size="22" class="author-avatar">{{ reply.nickname[0] }}</el-avatar>
              <strong>{{ reply.nickname }}</strong>
              <span v-if="reply.replyNickname" class="muted">回复 @{{ reply.replyNickname }}</span>
            </div>
            <div class="comment-body">{{ reply.content }}</div>
          </div>
        </div>
        <el-empty
          v-if="comments.length === 0"
          :image-size="72"
          description="还没有评论，来抢沙发"
        />
      </section>
    </template>
  </div>
</template>

<style scoped>
.post-title {
  margin: 0 0 10px;
  font-size: 24px;
  line-height: 1.45;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.author-avatar {
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary-dark-2);
  font-weight: 700;
  font-size: 13px;
}

.tag-row {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.actions {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-top: 20px;
}

.section-title {
  margin: 0 0 14px;
}

.comment-editor {
  margin-bottom: 18px;
}

.comment {
  border-top: 1px solid var(--border);
  padding: 16px 0;
}

.comment:first-of-type {
  border-top: none;
}

.comment-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.comment-body {
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
  margin-left: 34px;
}

.reply {
  margin-left: 34px;
  padding: 10px 14px;
  background: var(--bg);
  border-radius: 10px;
  margin-top: 10px;
}

.reply .comment-body {
  margin-left: 30px;
}
</style>
