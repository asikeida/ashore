<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { Promotion } from '@element-plus/icons-vue'
import { ApiError } from '../api/client'
import {
  answerInterview,
  interviewHistory,
  startInterview,
  type InterviewMessage,
} from '../api/post'

const props = defineProps<{ id: string }>()

const messages = ref<InterviewMessage[]>([])
const sessionId = ref<string | null>(null)
const answer = ref('')
const error = ref('')
const disabled = ref(false)
const loading = ref(false)
const threadEl = ref<HTMLElement | null>(null)

async function load() {
  loading.value = true
  try {
    const reply = await startInterview(props.id)
    sessionId.value = reply.sessionId
    messages.value = await interviewHistory(reply.sessionId)
    await scrollBottom()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'AI 面试官暂时不可用'
    // 凭证未配置（503001）时隐藏输入区
    disabled.value = e instanceof ApiError && e.code === 503001
  } finally {
    loading.value = false
  }
}

async function scrollBottom() {
  await nextTick()
  threadEl.value?.scrollTo({ top: threadEl.value.scrollHeight, behavior: 'smooth' })
}

async function submitAnswer() {
  if (!answer.value.trim() || !sessionId.value || loading.value) return
  loading.value = true
  try {
    const reply = await answerInterview(sessionId.value, answer.value.trim())
    // 先落用户答案，再追加 AI 回复
    messages.value.push({
      role: 'USER',
      content: answer.value.trim(),
      createTime: '',
    })
    messages.value.push({
      role: 'INTERVIEWER',
      content: reply.content,
      createTime: '',
    })
    answer.value = ''
    await scrollBottom()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'AI 调用失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="container interview-wrap">
    <div class="panel interview-panel">
      <div class="interview-head">
        <h2 style="margin: 0">🤖 AI 面试官</h2>
        <span class="muted">基于这篇面经的模拟面试：出题 → 作答 → 点评与追问</span>
      </div>

      <el-alert
        v-if="error"
        :title="error"
        type="warning"
        show-icon
        :closable="false"
        style="margin: 16px 0"
      >
        <template v-if="disabled" #default>
          <p style="margin: 4px 0">
            管理员尚未配置 AI 供应商凭证（DeepSeek / 智谱任一即可启用）。
          </p>
          <el-button size="small" @click="load">重试</el-button>
        </template>
      </el-alert>

      <template v-else>
        <div ref="threadEl" class="chat-thread">
          <div
            v-for="(m, i) in messages"
            :key="i"
            class="chat-row"
            :class="m.role === 'USER' ? 'row-user' : 'row-interviewer'"
          >
            <div class="chat-who muted">
              {{ m.role === 'USER' ? '我' : '面试官' }}
            </div>
            <div class="chat-bubble" :class="m.role === 'USER' ? 'chat-user' : 'chat-interviewer'">
              {{ m.content }}
            </div>
          </div>

          <div v-if="loading" class="chat-row row-interviewer">
            <div class="chat-who muted">面试官</div>
            <div class="chat-bubble chat-interviewer">
              <span class="typing-dots"><span></span><span></span><span></span></span>
              <span class="muted" style="margin-left: 8px; font-size: 12px">
                {{ messages.length === 0 ? '正在阅读面经并出题…' : '正在思考你的回答…' }}
              </span>
            </div>
          </div>
        </div>

        <div v-if="sessionId" class="answer-zone">
          <el-input
            v-model="answer"
            type="textarea"
            :autosize="{ minRows: 4, maxRows: 10 }"
            placeholder="输入你的回答…（Ctrl/⌘ + Enter 发送）"
            :disabled="loading"
            @keydown.ctrl.enter.prevent="submitAnswer"
            @keydown.meta.enter.prevent="submitAnswer"
          />
          <div class="answer-actions">
            <span class="muted">回答尽量结合原理与实战，点评会更有针对性</span>
            <el-button
              type="primary"
              :icon="Promotion"
              :loading="loading"
              @click="submitAnswer"
            >
              提交回答
            </el-button>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.interview-wrap {
  max-width: 860px;
}

.interview-panel {
  display: flex;
  flex-direction: column;
  min-height: 60vh;
}

.interview-head {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border);
  margin-bottom: 16px;
}

.chat-thread {
  flex: 1;
  overflow-y: auto;
  max-height: 52vh;
  padding-right: 4px;
}

.chat-row {
  margin-bottom: 16px;
}

.row-user {
  text-align: right;
}

.row-interviewer {
  text-align: left;
}

.chat-who {
  font-size: 12px;
  margin-bottom: 4px;
}

.row-user .chat-bubble {
  display: inline-block;
  text-align: left;
}

.row-interviewer .chat-bubble {
  display: inline-block;
}

.answer-zone {
  margin-top: 16px;
}

.answer-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
}
</style>
