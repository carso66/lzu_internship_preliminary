// 修改 mock 服务端口时，只需要改这一处。
export const API_BASE_URL = 'http://127.0.0.1:3001/api';

/**
 * 统一发送请求并解析 { code, message, data }。
 * HTTP 非 2xx 时优先读取响应体里的 message，便于页面直接调用。
 */
async function request(path, options = {}) {
  const { headers = {}, body: requestBody, ...restOptions } = options;
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...restOptions,
    ...(requestBody !== undefined ? { body: requestBody } : {}),
    headers: {
      Accept: 'application/json',
      ...(requestBody !== undefined
        ? { 'Content-Type': 'application/json' }
        : {}),
      ...headers,
    },
  });

  let payload = null;
  try {
    payload = await response.json();
  } catch {
    // 空响应或非 JSON 响应交给下面的状态码/业务错误分支统一报错。
  }

  if (!response.ok) {
    throw new Error(payload?.message || `请求失败（HTTP ${response.status}）`);
  }

  if (payload?.code !== 0) {
    throw new Error(payload?.message || '请求失败');
  }

  return payload.data;
}

export function getProducts({ page, pageSize } = {}) {
  const params = new URLSearchParams();

  if (page !== undefined && page !== null && page !== '') {
    params.set('page', String(page));
  }
  if (pageSize !== undefined && pageSize !== null && pageSize !== '') {
    params.set('pageSize', String(pageSize));
  }

  const query = params.toString();
  return request(`/products${query ? `?${query}` : ''}`);
}

export function getProduct(id) {
  return request(`/products/${encodeURIComponent(id)}`);
}

export function createProduct(payload) {
  return request('/products', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function updateProductStatus(id, status) {
  return request(`/products/${encodeURIComponent(id)}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  });
}

export function createOrder(payload) {
  return request('/orders', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function getOrders() {
  return request('/orders');
}

export function createPayment({ orderId, method }) {
  return request('/payments', {
    method: 'POST',
    body: JSON.stringify({ orderId, method }),
  });
}

export function getPayment(paymentId) {
  return request(`/payments/${encodeURIComponent(paymentId)}`);
}
