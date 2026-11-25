/**
 * 统一请求层：拼 /api 前缀、解包 {code,message,data}、把业务错误抛给页面。
 * 约定：所有已实现接口成功时 code === 0；失败时 message 必须展示给用户。
 */

const BASE = '/api';

async function request(path, { method = 'GET', body } = {}) {
  const init = { method, headers: {} };
  if (body !== undefined) {
    init.headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }

  let res;
  try {
    res = await fetch(BASE + path, init);
  } catch (e) {
    throw new Error(`网络请求失败：${e.message}`);
  }

  let payload;
  try {
    payload = await res.json();
  } catch {
    throw new Error(`服务返回了非 JSON 内容（HTTP ${res.status}）`);
  }

  if (!res.ok || !payload || payload.code !== 0) {
    const err = new Error((payload && payload.message) || `请求失败（HTTP ${res.status}）`);
    err.code = payload && payload.code;
    err.status = res.status;
    throw err;
  }
  return payload.data;
}

function qs(params) {
  const u = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== '' && v !== null && v !== undefined) u.set(k, String(v));
  });
  const s = u.toString();
  return s ? `?${s}` : '';
}

export const api = {
  categories: () => request('/categories'),
  products: (params = {}) => request(`/products${qs(params)}`),
  product: (id) => request(`/products/${id}`),
  createProduct: (body) => request('/products', { method: 'POST', body }),
  setStatus: (id, status) => request(`/products/${id}/status`, { method: 'PATCH', body: { status } }),
  createOrder: (body) => request('/orders', { method: 'POST', body }),
  orders: (params = {}) => request(`/orders${qs(params)}`),
};
