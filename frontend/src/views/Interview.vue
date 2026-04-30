<script setup lang="ts">
import { onMounted, ref } from 'vue'
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

async function load() {
  loading.value = true
  try {
    const reply = await startInterview(props.id)
    sessionId.value = reply.sessionId
    messages.value = await interviewHistory(reply.sessionId)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'AI 面试官暂时不可用'
    // 凭证未配置（503001）时隐藏输入区
    disabled.value = e instanceof ApiError && e.code === 503001
  } finally {
    loading.value = false
  }
}

async function submitAnswer() {
  if (!answer.value.trim() || !sessionId.value) return
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
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'AI 调用失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="card">
    <h2>🤖 AI 面试官</h2>
    <p class="muted">基于这篇面经的 AI 模拟面试：出题 → 作答 → 点评与追问。</p>

    <div v-if="error" class="card" style="background: #fef2f2">
      <p class="error">{{ error }}</p>
      <p v-if="disabled" class="muted">
        管理员尚未配置 AI 供应商凭证（DeepSeek / 智谱任一即可启用）。
      </p>
      <button class="ghost" @click="load">重试</button>
    </div>

    <template v-else>
      <div v-for="(m, i) in messages" :key="i" class="chat-bubble"
           :class="m.role === 'USER' ? 'chat-user' : 'chat-interviewer'">
        <div class="muted" style="margin-bottom: 4px">
          {{ m.role === 'USER' ? '我' : '面试官' }}
        </div>
        {{ m.content }}
      </div>

      <div v-if="sessionId && !loading" style="margin-top: 16px">
        <textarea v-model="answer" style="min-height: 120px" placeholder="输入你的回答…"></textarea>
        <button class="primary" @click="submitAnswer">提交回答</button>
      </div>
      <p v-if="loading" class="muted">AI 思考中…</p>
    </template>
  </div>
</template>
