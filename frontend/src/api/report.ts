/**
 * 举报 API：用户提交（文章/评论），管理台处理队列。
 */
import { apiGet, apiPost } from './client'

export interface ReportItem {
  id: string
  targetType: string // ARTICLE / COMMENT
  targetId: string
  targetPreview: string
  reason: string
  reporterNickname: string
  status: string // PENDING / RESOLVED / DISMISSED
  createTime: string
}

export async function submitReport(targetType: 'ARTICLE' | 'COMMENT', targetId: string, reason: string): Promise<void> {
  await apiPost<void>('/api/v1/reports', { targetType, targetId, reason })
}

export async function fetchReports(status?: string): Promise<ReportItem[]> {
  const params = status ? `?status=${status}` : ''
  return apiGet<ReportItem[]>(`/api/v1/admin/reports${params}`)
}

export async function handleReport(id: string, action: 'DISMISS' | 'OFFLINE'): Promise<void> {
  await apiPost<void>(`/api/v1/admin/reports/${id}/handle`, { action })
}
