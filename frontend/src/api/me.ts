/**
 * 个人中心 API：我的收藏 / 我的点赞 / 我的 AI 面试会话。
 */
import { apiGet } from './client'

export interface MeFootItem {
  articleId: string
  title: string
  tags: string[]
  authorNickname: string
  footTime: string
}

export interface MeInterviewItem {
  sessionId: string
  articleId: string
  articleTitle: string
  questionCount: number
  status: string
  createTime: string
  updateTime: string
}

export async function fetchMyCollects(): Promise<MeFootItem[]> {
  return apiGet<MeFootItem[]>('/api/v1/me/collects')
}

export async function fetchMyPraises(): Promise<MeFootItem[]> {
  return apiGet<MeFootItem[]>('/api/v1/me/praises')
}

export async function fetchMyInterviews(): Promise<MeInterviewItem[]> {
  return apiGet<MeInterviewItem[]>('/api/v1/me/interviews')
}
