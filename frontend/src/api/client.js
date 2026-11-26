/**
 * 统一响应包 `{ code, message, data }` 的 fetch 封装。
 *
 * - `code === 0`：直接返回 `data`；
 * - `code !== 0`：抛 `ApiError`，`message` 就是后端 / mock 的中文提示，
 *   页面必须把它显示给用户（README §4.3：业务错误不能只打 Console）。
 *
 * 默认走同源 `/api`，由 Vite 代理到后端（`vite.config.js` 的 `server.proxy`，
 * 目标默认 http://127.0.0.1:8080，可用 API_TARGET / MOCK_PORT / BACKEND_PORT 覆盖）。
 * 前端始终同源请求，所以后端**不需要**配 CORS。
 *
 * 只有在前端独立部署（不走 Vite dev server）时才需要 `VITE_API_BASE`
 * 显式指向后端，例如 `VITE_API_BASE=http://127.0.0.1:8080/api`——那时浏览器
 * 会真的跨域，后端必须同时开 CORS。
 */
const BASE = (import.meta.env.VITE_API_BASE || '/api').replace(/\/+$/, '')

export class ApiError extends Error {
  /**
   * @param {string} message 面向用户的中文提示
   * @param {{ code?: number, status?: number }} [meta]
   */
  constructor(message, { code = -1, status = 0 } = {}) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
}

/**
 * @param {string} path 以 `/` 开头、相对 `/api` 的路径，如 `/products`
 * @param {{ method?: string, body?: unknown, signal?: AbortSignal }} [options]
 */
export async function request(path, { method = 'GET', body, signal } = {}) {
  const hasBody = body !== undefined
  let response
  try {
    response = await fetch(`${BASE}${path}`, {
      method,
      headers: hasBody ? { 'Content-Type': 'application/json' } : undefined,
      body: hasBody ? JSON.stringify(body) : undefined,
      signal,
    })
  } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw new ApiError('连接不上接口服务，请确认后端已启动（cd backend && mvnw spring-boot:run）')
  }

  let payload = null
  try {
    payload = await response.json()
  } catch {
    payload = null
  }

  if (!payload || typeof payload.code !== 'number') {
    throw new ApiError(`接口返回格式异常（HTTP ${response.status}）`, { status: response.status })
  }
  if (payload.code !== 0) {
    throw new ApiError(payload.message || '请求失败', { code: payload.code, status: response.status })
  }
  return payload.data
}
