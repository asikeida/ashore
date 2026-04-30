/**
 * 统一请求封装：约定后端响应 { status: { code, msg }, result }。
 * code === 0 成功返回 result；非 0 抛业务错误（含 msg），页面层捕获提示。
 */

export class ApiError extends Error {
  constructor(
    public code: number,
    message: string,
  ) {
    super(message)
  }
}

async function handle<T>(response: Response): Promise<T> {
  const body = await response.json()
  if (!body.status || body.status.code !== 0) {
    throw new ApiError(body.status?.code ?? -1, body.status?.msg ?? '未知错误')
  }
  return body.result as T
}

export async function apiGet<T>(path: string): Promise<T> {
  const response = await fetch(path)
  return handle<T>(response)
}

export async function apiPost<T>(path: string, body?: unknown): Promise<T> {
  const response = await fetch(path, {
    method: 'POST',
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  return handle<T>(response)
}

export async function apiPut<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(path, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  return handle<T>(response)
}

export async function apiDelete<T>(path: string): Promise<T> {
  const response = await fetch(path, { method: 'DELETE' })
  return handle<T>(response)
}
