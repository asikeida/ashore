/**
 * 面经 AI 助手 API：常驻详情页右侧，SSE 流式对话（协议与面试官一致）。
 */
import { ApiError } from './client'

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
