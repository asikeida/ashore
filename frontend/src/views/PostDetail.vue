<script setup lang="ts">
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Collection, Pointer, Promotion, WarningFilled } from '@element-plus/icons-vue'
import {
  createComment,
  toggleCommentLike,
  fetchComments,
  fetchPost,
  toggleCollect,
  togglePraise,
  type CommentItem,
  type PostDetail,
} from '../api/post'
import { assistantStream, assistantHistory, uploadChatImage, type ChatMessage } from '../api/assistant'
import { auth, refreshAuth } from '../stores/auth'
import { submitReport } from '../api/report'

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

// ── 左侧目录（TOC）：渲染后遍历标题元素建目录，点击平滑跳转 ──
const contentEl = ref<HTMLElement | null>(null)
const toc = ref<{ id: string; text: string; level: number }[]>([])
const activeHeading = ref('')

async function buildToc() {
  await nextTick()
  const root = contentEl.value
  if (!root) return
  const headings = Array.from(root.querySelectorAll('h1, h2, h3, h4'))
  toc.value = headings.map((el, i) => {
    const id = `md-h-${i}`
    el.id = id
    return { id, text: el.textContent?.trim() ?? '', level: Number(el.tagName[1]) }
  })
  if (headings.length > 0) activeHeading.value = toc.value[0].id
}

function scrollToHeading(id: string) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  activeHeading.value = id
}

// ── 右侧 AI 助手：常驻面板，SSE 流式对话（历史前端携带，不落库）──
const assistantOpen = ref(true)
const chat = ref<ChatMessage[]>([])
const chatInput = ref('')
const chatting = ref(false)
const chatListEl = ref<HTMLElement | null>(null)

async function sendToAssistant() {
  const text = chatInput.value.trim()
  if (!text || chatting.value) return
  chatInput.value = ''
  chatting.value = true
  chat.value.push({ role: 'user', content: text })
  const placeholder: ChatMessage = { role: 'assistant', content: '' }
  chat.value.push(placeholder)
  scrollChat()
  try {
    // 历史不含刚 push 的占位 assistant（避免空内容进入上下文）
    const history = chat.value.slice(0, -2)
    await assistantStream(props.id, text, history, delta => {
      placeholder.content += delta
      scrollChat()
    })
  } catch (err) {
    placeholder.content = placeholder.content || (err instanceof Error ? err.message : 'AI 服务暂不可用')
  } finally {
    chatting.value = false
    scrollChat()
  }
}

function scrollChat() {
  requestAnimationFrame(() => {
    chatListEl.value?.scrollTo({ top: chatListEl.value.scrollHeight })
  })
}

/** 聊天里发图：上传 MinIO → 以 Markdown 图片语法进消息（气泡渲染为图片） */
async function onChatImage(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || chatting.value) {
    input.value = ''
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('图片不能超过 5MB')
    input.value = ''
    return
  }
  chatting.value = true
  try {
    const url = await uploadChatImage(file)
    chatInput.value = (chatInput.value ? chatInput.value + '\n' : '') + `![](${url})`
    ElMessage.success('图片已就绪，输入你想问的问题后回车发送')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '图片上传失败')
  } finally {
    chatting.value = false
    input.value = ''
  }
}

/** 用户气泡渲染：图片语法转 <img>，其余按纯文本（消毒后输出） */
function renderChatUser(text: string): string {
  const withImg = text.replace(/!\[\]\(([^)]+)\)/g, '<img src="$1" style="max-width:100%;border-radius:8px;margin-top:6px">')
  const safe = DOMPurify.sanitize(withImg)
  return safe.replace(/\n/g, '<br>')
}

async function load() {
  try {
    post.value = await fetchPost(props.id)
    const page = await fetchComments(props.id, null)
    comments.value = page.items
    await buildToc()
    // 助手会话：有落库历史则回填，否则给开场白（历史加载失败不阻塞页面）
    try {
      const history = await assistantHistory(props.id)
      if (history.length > 0) {
        chat.value = history
        return
      }
    } catch {
      /* ignore */
    }
    if (post.value.content) {
      chat.value.push({
        role: 'assistant',
        content: '我已读完这篇面经。可以问我文中概念的细节、帮你评估复盘质量、模拟面试官追问，或者提改进建议。',
      })
    }
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
    await createComment(props.id, commentText.value.trim(), replyTo.value?.commentId ?? null)
    commentText.value = ''
    post.value!.commentCount += 1
    const page = await fetchComments(props.id, null)
    comments.value = page.items
    ElMessage.success(replyTo.value ? '回复已发布' : '评论已发布')
    cancelReply()
  } finally {
    submitting.value = false
  }
}

// ── 评论回复（后端 parentCommentId 已支持，此处补交互入口）──
const replyTo = ref<{ commentId: string; nickname: string } | null>(null)
const commentInputRef = ref<InstanceType<typeof import('element-plus')['ElInput']>>()

function startReply(commentId: string, nickname: string) {
  replyTo.value = { commentId, nickname }
  // 滚动到评论输入框并聚焦（评论列表可能很长）
  document.querySelector('.comment-editor')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  requestAnimationFrame(() => commentInputRef.value?.focus())
}

function cancelReply() {
  replyTo.value = null
}

/** 评论点赞 toggle：本地状态即时更新（乐观），失败回滚 */
async function likeComment(commentId: string) {
  const target = findComment(commentId)
  if (!target) return
  const wasLiked = target.liked
  // 乐观更新
  target.liked = !wasLiked
  target.likeCount += wasLiked ? -1 : 1
  try {
    const result = await toggleCommentLike(commentId)
    target.liked = result.liked
    target.likeCount = result.likeCount
  } catch (err) {
    // 回滚
    target.liked = wasLiked
    target.likeCount += wasLiked ? 1 : -1
    ElMessage.error(err instanceof Error ? err.message : '操作失败')
  }
}

/** 在评论树（顶级+子回复）中按 id 定位 */
function findComment(commentId: string): { liked: boolean; likeCount: number } | undefined {
  for (const c of comments.value) {
    if (c.id === commentId) return c
    const r = c.replies.find(x => x.id === commentId)
    if (r) return r
  }
  return undefined
}

function startInterview() {
  aiStarting.value = true
  router.push(`/posts/${props.id}/interview`)
}

// ── 举报 ─────────────────────────────────────────────────────
async function reportArticle() {
  try {
    const { value } = await ElMessageBox.prompt('请填写举报理由（500 字内）', '举报这篇面经', {
      confirmButtonText: '提交举报',
      cancelButtonText: '取消',
      inputPattern: /^.{1,500}$/,
      inputErrorMessage: '理由不能为空且不超过 500 字',
    })
    await submitReport('ARTICLE', props.id, value.trim())
    ElMessage.success('举报已提交，管理员将尽快处理')
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') {
      ElMessage.error(err instanceof Error ? err.message : '提交失败')
    }
  }
}

async function reportComment(commentId: string) {
  try {
    const { value } = await ElMessageBox.prompt('请填写举报理由（500 字内）', '举报该评论', {
      confirmButtonText: '提交举报',
      cancelButtonText: '取消',
      inputPattern: /^.{1,500}$/,
      inputErrorMessage: '理由不能为空且不超过 500 字',
    })
    await submitReport('COMMENT', commentId, value.trim())
    ElMessage.success('举报已提交，管理员将尽快处理')
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') {
      ElMessage.error(err instanceof Error ? err.message : '提交失败')
    }
  }
}

function formatTime(time: string): string {
  return time.replace('T', ' ').slice(0, 16)
}

onMounted(load)
</script>

<template>
  <div class="container-wide">
    <el-empty v-if="notFound" description="面经不存在或不可见">
      <RouterLink to="/"><el-button type="primary" plain>返回列表</el-button></RouterLink>
    </el-empty>

    <div v-if="post" class="detail-layout" :class="{ 'has-toc': toc.length > 0 }">
      <!-- 左：目录 -->
      <aside v-if="toc.length > 0" class="toc panel">
        <div class="toc-title">目录</div>
        <a
          v-for="item in toc"
          :key="item.id"
          class="toc-item"
          :class="[`lv${item.level}`, { active: activeHeading === item.id }]"
          @click="scrollToHeading(item.id)"
        >{{ item.text }}</a>
      </aside>

      <!-- 中：正文 + 评论 -->
      <div class="detail-main">
        <article class="panel">
          <h1 class="post-title">{{ post.title }}</h1>
          <div class="muted meta-row">
            <el-avatar :size="24" class="author-avatar">{{ (post.authorNickname ?? post.authorId ?? '匿名')[0] }}</el-avatar>
            <RouterLink :to="`/user/${post.authorId}`" class="author-link">{{ post.authorNickname ?? post.authorId }}</RouterLink>
            <el-divider direction="vertical" />
            <span>{{ formatTime(post.createTime) }}</span>
            <el-divider direction="vertical" />
            <span>阅 {{ post.readCount }}</span>
          </div>
          <div v-if="post.tags.length" class="tag-row">
            <el-tag v-for="tag in post.tags" :key="tag" effect="plain" round>{{ tag }}</el-tag>
          </div>
          <el-divider style="margin: 16px 0" />
          <div ref="contentEl" class="post-content" v-html="render(post.content)"></div>

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
            <el-button
              v-if="auth.loggedIn"
              :icon="WarningFilled"
              round
              @click="reportArticle"
            >
              举报
            </el-button>
          </div>
        </article>

        <section class="panel">
          <h3 class="section-title">评论（{{ post.commentCount }}）</h3>
          <div v-if="auth.loggedIn" class="comment-editor">
            <el-alert
              v-if="replyTo"
              :title="`回复 @${replyTo.nickname}`"
              type="info"
              :closable="true"
              style="margin-bottom: 8px"
              @close="cancelReply"
            />
            <el-input
              ref="commentInputRef"
              v-model="commentText"
              type="textarea"
              :rows="3"
              maxlength="1000"
              show-word-limit
              :placeholder="replyTo ? `回复 @${replyTo.nickname}…` : '写下你的评论…'"
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
              <RouterLink :to="`/user/${comment.userId}`" class="author-link"><strong>{{ comment.nickname }}</strong></RouterLink>
              <span class="muted">{{ formatTime(comment.createTime) }}</span>
              <button
                v-if="auth.loggedIn"
                class="reply-btn"
                @click="startReply(comment.id, comment.nickname)"
              >回复</button>
              <button
                v-if="auth.loggedIn"
                class="like-btn"
                :class="{ on: comment.liked }"
                @click="likeComment(comment.id)"
              >👍 {{ comment.likeCount > 0 ? comment.likeCount : '' }}</button>
              <button
                v-if="auth.loggedIn"
                class="reply-btn"
                style="color: var(--el-text-color-secondary)"
                @click="reportComment(comment.id)"
              >举报</button>
            </div>
            <div class="comment-body">{{ comment.content }}</div>
            <div v-for="reply in comment.replies" :key="reply.id" class="reply">
              <div class="comment-head">
                <el-avatar :size="22" class="author-avatar">{{ reply.nickname[0] }}</el-avatar>
                <RouterLink :to="`/user/${reply.userId}`" class="author-link"><strong>{{ reply.nickname }}</strong></RouterLink>
                <span v-if="reply.replyNickname" class="muted">回复 @{{ reply.replyNickname }}</span>
                <button
                  v-if="auth.loggedIn"
                  class="reply-btn"
                  @click="startReply(reply.id, reply.nickname)"
                >回复</button>
                <button
                  v-if="auth.loggedIn"
                  class="like-btn"
                  :class="{ on: reply.liked }"
                  @click="likeComment(reply.id)"
                >👍 {{ reply.likeCount > 0 ? reply.likeCount : '' }}</button>
                <button
                  v-if="auth.loggedIn"
                  class="reply-btn"
                  style="color: var(--el-text-color-secondary)"
                  @click="reportComment(reply.id)"
                >举报</button>
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
      </div>

      <!-- 右：AI 助手 -->
      <aside class="ai-panel panel">
        <div class="ai-head">
          <span class="ai-title">🤖 AI 助手</span>
          <span class="muted ai-sub">已读本文 · 随问随答</span>
        </div>
        <div ref="chatListEl" class="chat-list">
          <div
            v-for="(m, i) in chat"
            :key="i"
            class="chat-row"
            :class="m.role === 'user' ? 'row-user' : 'row-assistant'"
          >
            <div class="bubble" v-html="m.role === 'assistant' ? render(m.content) : renderChatUser(m.content)"></div>
          </div>
        </div>
        <div class="chat-input">
          <label class="chat-img-btn" title="发图片给助手（它会看到图片链接并尝试结合上下文回答）">
            <input type="file" accept="image/png,image/jpeg,image/webp,image/gif" hidden :disabled="chatting" @change="onChatImage" />
            🖼
          </label>
          <el-input
            v-model="chatInput"
            placeholder="问问这篇文章…（Enter 发送）"
            :disabled="chatting"
            @keyup.enter="sendToAssistant"
          />
          <el-button type="primary" :icon="Promotion" :loading="chatting" @click="sendToAssistant" />
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 18px;
  align-items: start;
}

.detail-main,
.ai-panel {
  min-width: 0;
}

.detail-layout.has-toc {
  grid-template-columns: 190px minmax(0, 1fr) 300px;
}

@media (max-width: 1280px) {
  .toc {
    display: none;
  }
  .detail-layout.has-toc {
    grid-template-columns: minmax(0, 1fr) 300px;
  }
}

@media (max-width: 1000px) {
  .detail-layout,
  .detail-layout.has-toc {
    grid-template-columns: minmax(0, 1fr);
  }
  .ai-panel {
    position: static;
    max-height: none;
  }
}

/* 目录 */
.toc {
  position: sticky;
  top: 76px;
  max-height: calc(100vh - 100px);
  overflow-y: auto;
  padding: 12px;
}

.toc-title {
  font-weight: 700;
  font-size: 13px;
  color: var(--muted);
  margin-bottom: 8px;
  padding-left: 8px;
}

.toc-item {
  display: block;
  font-size: 13px;
  color: var(--text);
  text-decoration: none;
  padding: 5px 8px;
  border-radius: 6px;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toc-item:hover {
  background: var(--bg);
  color: var(--accent);
}

.toc-item.active {
  color: var(--accent);
  font-weight: 600;
  background: var(--el-color-primary-light-9);
}

.toc-item.lv2 {
  padding-left: 20px;
}

.toc-item.lv3,
.toc-item.lv4 {
  padding-left: 32px;
  color: var(--muted);
}

/* AI 面板 */
.ai-panel {
  position: sticky;
  top: 76px;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 100px);
  max-height: 720px;
  padding: 14px;
}

.ai-head {
  display: flex;
  flex-direction: column;
  gap: 2px;
  border-bottom: 1px solid var(--border);
  padding-bottom: 10px;
  margin-bottom: 10px;
}

.ai-title {
  font-weight: 700;
  font-size: 14px;
}

.ai-sub {
  font-size: 12px;
}

.chat-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 4px 2px;
}

.chat-row {
  display: flex;
}

.row-user {
  justify-content: flex-end;
}

.row-assistant {
  justify-content: flex-start;
}

.bubble {
  max-width: 88%;
  padding: 8px 12px;
  border-radius: 12px;
  font-size: 13.5px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.row-user .bubble {
  background: var(--el-color-primary);
  color: #fff;
  border-bottom-right-radius: 4px;
}

.row-assistant .bubble {
  background: var(--bg);
  color: var(--text);
  border-bottom-left-radius: 4px;
}

.cursor {
  animation: blink 0.9s infinite;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.chat-input {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.chat-input :deep(.el-input) {
  min-width: 0;
}

/* 正文排版 */
.post-content {
  line-height: 1.85;
  word-break: break-word;
}

.post-content :deep(h1),
.post-content :deep(h2),
.post-content :deep(h3),
.post-content :deep(h4) {
  margin: 22px 0 12px;
  scroll-margin-top: 80px;
}

.post-content :deep(h2) {
  font-size: 20px;
  border-bottom: 1px solid var(--border);
  padding-bottom: 8px;
}

.post-content :deep(p) {
  margin: 10px 0;
}

.post-content :deep(img) {
  max-width: 100%;
  border-radius: 8px;
  border: 1px solid var(--border);
  margin: 8px 0;
}

.post-content :deep(code) {
  background: var(--bg);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
}

.post-content :deep(pre) {
  background: #1e1e2e;
  color: #e6e6e6;
  padding: 14px;
  border-radius: 8px;
  overflow-x: auto;
}

.post-content :deep(pre code) {
  background: transparent;
  color: inherit;
  padding: 0;
}

.post-content :deep(blockquote) {
  border-left: 3px solid var(--el-color-primary);
  margin: 10px 0;
  padding: 4px 14px;
  color: var(--muted);
  background: var(--bg);
  border-radius: 0 8px 8px 0;
}

.post-content :deep(ul),
.post-content :deep(ol) {
  padding-left: 24px;
  margin: 10px 0;
}

.post-content :deep(table) {
  border-collapse: collapse;
  margin: 12px 0;
  width: 100%;
}

.post-content :deep(th),
.post-content :deep(td) {
  border: 1px solid var(--border);
  padding: 6px 12px;
  font-size: 13.5px;
}

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

.author-link {
  color: var(--text);
  font-weight: 600;
  text-decoration: none;
}

.author-link:hover {
  color: var(--accent);
  text-decoration: underline;
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
  flex-wrap: wrap;
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

.reply-btn {
  margin-left: auto;
  border: none;
  background: transparent;
  color: var(--el-color-primary);
  font-size: 12px;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
}

.reply-btn:hover {
  background: var(--el-color-primary-light-9);
}

.like-btn {
  border: none;
  background: transparent;
  color: var(--muted, #86909c);
  font-size: 12px;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
}

.like-btn:hover {
  background: #f5f7fa;
}

.like-btn.on {
  color: var(--el-color-primary);
  font-weight: 600;
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
