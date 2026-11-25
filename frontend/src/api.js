/**
 * 所有接口调用都收在这里：换地址只改一处。
 * 统一响应包：{ code: 0, message: 'ok', data: ... }，出错时 code != 0 且 data = null。
 */

// mock 默认 3001；如果 mock 换了端口，改这里或启动时设 VITE_API_BASE
export const API_BASE = import.meta.env?.VITE_API_BASE || 'http://127.0.0.1:3001';

/** 业务错误：既要能显示 message，也要能识别 HTTP 状态码 / 业务 code */
export class ApiError extends Error {
  constructor(message, { status = 0, code = 0 } = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

async function request(path, { method = 'GET', body, signal } = {}) {
  let response;
  try {
    response = await fetch(API_BASE + path, {
      method,
      signal,
      headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (error) {
    if (error.name === 'AbortError') throw error; // 主动取消不当作业务错误
    // 网络层失败（mock 没起 / 地址写错）最容易发生，给一句能定位的话
    throw new ApiError(`连不上接口（${API_BASE}），请确认 mock 服务已启动`, { status: 0, code: -1 });
  }

  let payload = null;
  try {
    payload = await response.json();
  } catch {
    payload = null;
  }

  if (!response.ok || !payload || payload.code !== 0) {
    throw new ApiError(payload?.message || `请求失败（HTTP ${response.status}）`, {
      status: response.status,
      code: payload?.code ?? 0,
    });
  }
  return payload.data;
}

/** GET /api/products -> { total, page, pageSize, items } */
export function listProducts({ keyword = '', status = '', category = '', page = 1, pageSize = 50, signal } = {}) {
  const params = new URLSearchParams();
  if (keyword) params.set('keyword', keyword);
  if (status) params.set('status', status);
  if (category) params.set('category', category);
  params.set('page', String(page));
  params.set('pageSize', String(pageSize));
  return request(`/api/products?${params}`, { signal });
}

/** GET /api/categories -> ['数码', '图书', ...] */
export function listCategories() {
  return request('/api/categories');
}
