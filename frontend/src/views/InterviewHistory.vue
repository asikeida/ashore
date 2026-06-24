<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiError } from '../api/client'
import { interviewHistory, type InterviewMessage } from '../api/post'

const props = defineProps<{ sessionId: string }>()

const messages = ref<InterviewMessage[]>([])
const error = ref('')
const loading = ref(true)

async function load() {
  loading.value = true
  try {
    messages.value = await interviewHistory(props.sessionId)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="container interview-wrap">
    <div class="panel interview-panel">
      <div class="history-head">
        <h2 style="margin: 0">🤖 面试回看</h2>
        <span class="muted">只读回放这段 AI 模拟面试——适合复盘自己的回答</span>
        <RouterLink to="/profile" class="back-link">← 返回个人中心</RouterLink>
      </div>

      <el-alert
        v-if="error"
        :title="error"
        type="warning"
        show-icon
        :closable="false"
        style="margin: 16px 0"
      />
      <el-skeleton v-else-if="loading" :rows="6" animated style="padding: 12px 0" />

      <template v-else>
        <div class="chat-thread">
          <div
            v-for="(m, i) in messages"
            :key="i"
            class="chat-row"
            :class="m.role === 'USER' ? 'row-user' : 'row-interviewer'"
          >
            <div class="chat-who muted">{{ m.role === 'USER' ? '我' : '面试官' }}</div>
            <div class="chat-bubble" :class="m.role === 'USER' ? 'chat-user' : 'chat-interviewer'">
              {{ m.content }}
            </div>
          </div>
          <el-empty
            v-if="messages.length === 0"
            :image-size="72"
            description="这段会话还没有消息"
          />
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

.history-head {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border);
  margin-bottom: 16px;
}

.back-link {
  color: var(--accent);
  text-decoration: none;
  font-size: 13px;
}

.chat-thread {
  flex: 1;
  overflow-y: auto;
  max-height: 60vh;
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
</style>
