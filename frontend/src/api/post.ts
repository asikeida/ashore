import { apiGet, apiPost } from './client'

/** 面经列表项 */
export interface PostItem {
  id: string
  title: string
  tags: string[]
  authorNickname: string
  createTime: string
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
  replies: {
    id: string
    nickname: string
    content: string
    replyNickname: string | null
    createTime: string
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
