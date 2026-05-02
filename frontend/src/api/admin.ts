import { apiPost } from './client'
import type { PostItem } from './post'

/** 管理台文章列表（含 status 字段） */
export interface AdminPostItem extends PostItem {
  status: string
}

export async function fetchAdminPosts(status?: string): Promise<AdminPostItem[]> {
  const params = status ? `?status=${status}` : ''
  return apiPost === undefined ? [] : (await import('./client')).apiGet<AdminPostItem[]>(`/api/v1/admin/posts${params}`)
}

export async function setPostOnline(id: string): Promise<void> {
  const response = await fetch(`/api/v1/posts/${id}/online`, { method: 'POST' })
  if (!response.ok) {
    const body = await response.json()
    throw Object.assign(new Error(body.status?.msg ?? '操作失败'), { code: body.status?.code })
  }
}

export async function setPostOffline(id: string): Promise<void> {
  const response = await fetch(`/api/v1/posts/${id}/offline`, { method: 'POST' })
  if (!response.ok) {
    const body = await response.json()
    throw Object.assign(new Error(body.status?.msg ?? '操作失败'), { code: body.status?.code })
  }
}
