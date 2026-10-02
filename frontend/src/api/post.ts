import { ApiError, apiGet, apiPost } from './client'

/** 面经列表项 */
export interface PostItem {
  id: string
  title: string
  tags: string[]
  authorNickname: string
  createTime: string
  topped?: boolean
}

/** 面经详情 */
export interface PostDetail {
  id: string
  title: string
  status: string
  tags: string[]
  content: string | null
  version: number | null
  authorId: string
  authorNickname: string
  createTime: string
  praiseCount: number
  collectCount: number
  commentCount: number
  readCount: number
  praised: boolean
  collected: boolean
}

/** 游标分页结果 */
export interface PostPage {
  nextCursor: string | null
  items: PostItem[]
}

/** 评论（顶级） */
export interface CommentItem {
  id: string
  userId: string
  nickname: string
  content: string
  createTime: string
  likeCount: number
  liked: boolean
  replies: {
    id: string
    nickname: string
    content: string
    replyNickname: string | null
    createTime: string
    likeCount: number
    liked: boolean
  }[]
}

/** 评论分页 */
export interface CommentPage {
  nextCursor: string | null
  total: number
  items: CommentItem[]
}

/** 搜索结果条目 */
export interface SearchItem {
  id: string
  title: string
  tags: string[]
  authorNickname: string
  createTime: string
  titleHighlight: string | null
  contentHighlight: string | null
}

export async function fetchPosts(cursor: string | null, limit = 20, tag?: string): Promise<PostPage> {
  const params = new URLSearchParams({ limit: String(limit) })
  if (cursor) params.set('cursor', cursor)
  if (tag) params.set('tag', tag)
  return apiGet<PostPage>(`/api/v1/posts?${params}`)
}

export async function searchPosts(keyword: string, limit = 20): Promise<SearchItem[]> {
  const params = new URLSearchParams({ q: keyword, limit: String(limit) })
  return apiGet<SearchItem[]>(`/api/v1/posts/search?${params}`)
}

export async function fetchPost(id: string): Promise<PostDetail> {
  return apiGet<PostDetail>(`/api/v1/posts/${id}`)
}

export async function fetchComments(id: string, cursor: string | null): Promise<CommentPage> {
  const params = new URLSearchParams()
  if (cursor) params.set('cursor', cursor)
  return apiGet<CommentPage>(`/api/v1/posts/${id}/comments?${params}`)
}

export async function publishPost(title: string, content: string, tags: string[]): Promise<{ id: string }> {
  return apiPost<{ id: string }>('/api/v1/posts', { title, content, tags })
}

export async function togglePraise(id: string): Promise<boolean> {
  return apiPost<boolean>(`/api/v1/posts/${id}/praise`)
}

export async function toggleCollect(id: string): Promise<boolean> {
  return apiPost<boolean>(`/api/v1/posts/${id}/collect`)
}

export async function createComment(
  id: string,
  content: string,
  parentCommentId: string | null,
): Promise<unknown> {
  return apiPost(`/api/v1/posts/${id}/comments`, { content, parentCommentId })
}

/** 评论点赞/取消（幂等 toggle），返回操作后状态与最新计数 */
export async function toggleCommentLike(commentId: string): Promise<{ liked: boolean; likeCount: number }> {
  return apiPost<{ liked: boolean; likeCount: number }>(`/api/v1/comments/${commentId}/like`)
}

/** AI 面试官 */
export interface InterviewReply {
  sessionId: string
  questionCount: number
  content: string
}

export interface InterviewMessage {
  role: 'INTERVIEWER' | 'USER'
  content: string
  createTime: string
}

export async function startInterview(postId: string): Promise<InterviewReply> {
  return apiPost<InterviewReply>(`/api/v1/posts/${postId}/interview`)
}

export async function answerInterview(sessionId: string, content: string): Promise<InterviewReply> {
  return apiPost<InterviewReply>(`/api/v1/interviews/${sessionId}/answer`, { content })
}

export async function interviewHistory(sessionId: string): Promise<InterviewMessage[]> {
  return apiGet<InterviewMessage[]>(`/api/v1/interviews/${sessionId}`)
}

/**
 * 流式作答：POST SSE 端点，逐帧解析增量。
 * 事件协议（后端约定）：{"d":"增量"} / {"done":true} / {"err":"原因"}。
 * onDelta 只收非空增量；正常结束 resolve 完整文本，失败 reject（本次后端不落库可重试）。
 */
export async function answerInterviewStream(
  sessionId: string,
  content: string,
  onDelta: (delta: string) => void,
): Promise<string> {
  const response = await fetch(`/api/v1/interviews/${sessionId}/answer/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content }),
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
    // SSE 帧以空行分隔；单帧可含多行 data:（后端 JSON 单行编码，罕见多行仍按规范拼接）
    const frames = buffer.split("\n\n")
    buffer = frames.pop() ?? ''
    for (const frame of frames) {
      const dataLines = frame
        .split("\n")
        .filter(l => l.startsWith('data:'))
        .map(l => l.slice(5).trim())
      if (dataLines.length === 0) continue
      const payload = JSON.parse(dataLines.join("\n"))
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
