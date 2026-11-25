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

/**
 * POST /api/products -> 201 + 新建的商品
 * 注意：price 必须是「整数分」。调用方负责把用户输入的元换算好（money.js 的 yuanToCents），
 * 这里不做二次换算，免得两处各转一遍、谁都说不清哪个才是准的。
 */
export function createProduct({ title, price, stock, category, description = '' }) {
  return request('/api/products', {
    method: 'POST',
    body: { title, price, stock, category, description },
  });
}

/**
 * PATCH /api/products/{id}/status -> { id, status }（L0-4 上下架）
 * status 只能是 'on' / 'off'，传别的值后端会 400。
 * 调用方负责决定目标状态（product-status.js 的 toggleTarget），这里只负责发出去。
 */
export function updateProductStatus(id, status) {
  return request(`/api/products/${encodeURIComponent(id)}/status`, {
    method: 'PATCH',
    body: { status },
  });
}

/**
 * 拉「全部」商品。L0-1 的验收要求是"列表必须能访问到全部商品，不能只显示默认的第一页"，
 * 而 mock 的 pageSize 上限就是 50 —— 只发一次请求等于把上限当天花板。
 * 所以这里按 total 循环翻页，直到凑满；maxPages 是防呆，接口行为异常时不会无限翻。
 *
 * 下面这些参数现在都传默认值，等 L0-5 的关键词 / 分类筛选接上来，
 * 只要把 state 里的值传进来就行，不用再改这个函数。
 */
export async function fetchAllProducts({
  keyword = '',
  status = '',
  category = '',
  pageSize = 50,
  maxPages = 20,
  signal,
} = {}) {
  const items = [];
  let total = Infinity;

  for (let page = 1; page <= maxPages && items.length < total; page += 1) {
    const data = await listProducts({ keyword, status, category, page, pageSize, signal });
    const batch = Array.isArray(data?.items) ? data.items : [];
    if (Number.isFinite(data?.total)) total = data.total;
    if (batch.length === 0) break; // 没有更多了（或接口异常），别在这儿转圈
    items.push(...batch);
  }

  return { total: Number.isFinite(total) ? total : items.length, items };
}

/** POST /api/orders -> 201 + 订单（含服务端算好的 totalAmount，单位：分） */
export function createOrder({ productId, quantity }) {
  return request('/api/orders', {
    method: 'POST',
    // quantity 必须是**数字**（README L1-6 的验收项）：调用方传进来之前已经 Number() 过
    body: { productId: Number(productId), quantity: Number(quantity) },
  });
}

/** GET /api/orders -> { total, page, pageSize, items }（mock 按 id 倒序，新订单在前） */
export function listOrders({ page = 1, pageSize = 50, signal } = {}) {
  const params = new URLSearchParams();
  params.set('page', String(page));
  params.set('pageSize', String(pageSize));
  return request(`/api/orders?${params}`, { signal });
}

/**
 * 拉「全部」订单，和 fetchAllProducts 同样的翻页策略：
 * 订单只会越下越多，只发一次请求等于把首页 10 条当天花板。
 */
export async function fetchAllOrders({ pageSize = 50, maxPages = 20, signal } = {}) {
  const items = [];
  let total = Infinity;

  for (let page = 1; page <= maxPages && items.length < total; page += 1) {
    const data = await listOrders({ page, pageSize, signal });
    const batch = Array.isArray(data?.items) ? data.items : [];
    if (Number.isFinite(data?.total)) total = data.total;
    if (batch.length === 0) break;
    items.push(...batch);
  }

  return { total: Number.isFinite(total) ? total : items.length, items };
}

/** POST /api/payments -> { paymentId, orderId, amount, status: 'processing' }（L2-10） */
export function createPayment({ orderId, method }) {
  return request('/api/payments', {
    method: 'POST',
    body: { orderId: Number(orderId), method },
  });
}

/**
 * GET /api/payments/{paymentId} -> { paymentId, status: 'processing' | 'paid', amount, ... }
 * 轮询时传 signal：离开组件 / 页面要能立刻掐掉在飞的请求（L2-10 的「离开页面停止轮询」）。
 */
export function getPayment(paymentId, { signal } = {}) {
  return request(`/api/payments/${encodeURIComponent(paymentId)}`, { signal });
}

