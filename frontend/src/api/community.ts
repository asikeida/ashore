/**
 * 社区 API：通知、热榜、活跃榜。
 */
import { apiGet, apiPut } from './client'

// ── 通知 ─────────────────────────────────────────────────────
export interface NoticeItem {
  id: string
  type: string // COMMENT / REPLY / PRAISE / COLLECT / SYSTEM
  articleId: string | null
  commentId: string | null
  operatorNickname: string | null
  content: string
  read: boolean
  createTime: string
}

export async function fetchNotices(type?: string, page = 1, pageSize = 20): Promise<NoticeItem[]> {
  const params = new URLSearchParams()
  if (type) params.set('type', type)
  params.set('page', String(page))
  params.set('pageSize', String(pageSize))
  return apiGet<NoticeItem[]>(`/api/v1/notices?${params.toString()}`)
}

export async function fetchUnreadCount(): Promise<number> {
  return apiGet<number>('/api/v1/notices/unread-count')
}

export async function markAllRead(): Promise<void> {
  await apiPut<void>('/api/v1/notices/read')
}

// ── 热榜 ─────────────────────────────────────────────────────
export interface HotPostItem {
  id: string
  title: string
  authorNickname: string
  read: number
  praise: number
  comment: number
  hotScore: number
}

export async function fetchHotPosts(limit = 10): Promise<HotPostItem[]> {
  return apiGet<HotPostItem[]>(`/api/v1/posts/hot?limit=${limit}`)
}

// ── 活跃用户榜 ───────────────────────────────────────────────
export interface RankItem {
  rank: number
  userId: string
  nickname: string
  score: number
}

export async function fetchDailyRank(limit = 10): Promise<RankItem[]> {
  return apiGet<RankItem[]>(`/api/v1/rank/daily?limit=${limit}`)
}
