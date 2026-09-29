/**
 * 面经 AI 助手 API：常驻详情页右侧，SSE 流式对话（协议与面试官一致）；
 * 对话落库后提供历史回填与个人中心记录。
 */
import { ApiError, apiGet } from './client'

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

/**
 * 流式对话：POST SSE 端点，逐帧解析增量。
 * 事件协议（后端约定）：{"d":"增量"} / {"done":true} / {"err":"原因"}。
 * onDelta 只收非空增量；正常结束 resolve 完整文本。
 */
export async function assistantStream(
  articleId: string,
  message: string,
  history: ChatMessage[],
  onDelta: (delta: string) => void,
): Promise<string> {
  const response = await fetch(`/api/v1/assistant/${articleId}/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, history }),
  })
  if (!response.ok || !response.body) {
    const body = await response.json().catch(() => null)
    throw new ApiError(body?.status?.code ?? -1, body?.status?.msg ?? '流式请求失败')
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let full = ''
  let streamError: string | null = null
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const frames = buffer.split('\n\n')
    buffer = frames.pop() ?? ''
    for (const frame of frames) {
      const dataLines = frame
        .split('\n')
        .filter(l => l.startsWith('data:'))
        .map(l => l.slice(5).trim())
      if (dataLines.length === 0) continue
      const payload = JSON.parse(dataLines.join('\n'))
      if (payload.d) {
        full += payload.d
        onDelta(payload.d)
      } else if (payload.err) {
        streamError = payload.err
      }
    }
  }
  if (streamError) throw new ApiError(-1, streamError)
  return full
}

/** 历史对话（当前用户 + 指定面经，正序）——打开面板时回填会话 */
export async function assistantHistory(articleId: string): Promise<ChatMessage[]> {
  return apiGet<ChatMessage[]>(`/api/v1/assistant/${articleId}/history`)
}

/** 上传聊天图片（复用文章配图通道，≤5MB），返回 /images/ URL */
export async function uploadChatImage(file: File): Promise<string> {
  const form = new FormData()
  form.append('file', file)
  const response = await fetch('/api/v1/posts/images', { method: 'POST', body: form })
  const body = await response.json()
  if (!body.status || body.status.code !== 0) {
    throw new Error(body.status?.msg ?? '上传失败')
  }
  return body.result as string
}

/** 个人中心：AI 助手记录（按面经分组，最近活跃在前） */
export interface AssistantRecordItem {
  articleId: string
  articleTitle: string
  turns: number
  lastTime: string
  lastContent: string
}

export async function fetchAssistantRecords(): Promise<AssistantRecordItem[]> {
  return apiGet<AssistantRecordItem[]>('/api/v1/me/assistant-records')
}
