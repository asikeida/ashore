import { apiGet, apiPost } from './client'
import type { PostItem } from './post'

/** 管理台文章列表（含 status 字段） */
export interface AdminPostItem extends PostItem {
  status: string
}

export async function fetchAdminPosts(status?: string): Promise<AdminPostItem[]> {
  const params = status ? `?status=${status}` : ''
  return apiGet<AdminPostItem[]>(`/api/v1/admin/posts${params}`)
}

export async function setPostOnline(id: string): Promise<void> {
  return apiPost<void>(`/api/v1/posts/${id}/online`)
}

export async function setPostOffline(id: string): Promise<void> {
  return apiPost<void>(`/api/v1/posts/${id}/offline`)
}

/** 管理台用户条目（UserVo，id 为字符串化雪花 ID） */
export interface AdminUserItem {
  id: string
  account: string
  nickname: string
  role: string // USER / ADMIN
}

export async function fetchAdminUsers(accountKeyword?: string): Promise<AdminUserItem[]> {
  const params = accountKeyword ? `?accountKeyword=${encodeURIComponent(accountKeyword)}` : ''
  return apiGet<AdminUserItem[]>(`/api/v1/admin/users${params}`)
}

export async function banUser(id: string): Promise<AdminUserItem> {
  return apiPost<AdminUserItem>(`/api/v1/admin/users/${id}/ban`)
}

export async function unbanUser(id: string): Promise<AdminUserItem> {
  return apiPost<AdminUserItem>(`/api/v1/admin/users/${id}/unban`)
}
