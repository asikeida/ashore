/**
 * 他人主页 API（公开）。
 */
import { apiGet } from './client'

export interface UserPostCard {
  id: string
  title: string
  tags: string[]
  createTime: string
  topped: boolean
}

export interface UserProfile {
  id: string
  nickname: string
  avatar: string | null
  role: string
  createTime: string | null
  articleCount: number
  totalRead: number
  totalPraise: number
  articles: UserPostCard[]
}

export async function fetchUserProfile(id: string): Promise<UserProfile> {
  return apiGet<UserProfile>(`/api/v1/users/${id}`)
}
