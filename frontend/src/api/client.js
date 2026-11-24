// 所有请求只走这一个文件：换端口/换地址只改 BASE_URL 一行。
export const BASE_URL = 'http://127.0.0.1:3001/api'

// mock 的响应统一是 { code, message, data }。这里把壳剥掉，只把 data 交给页面；
// 只要 code != 0（400/404/409/503 都算），就抛出后端的 message，让页面能直接显示给用户。
async function request(path, options = {}) {
  let res
  try {
    res = await fetch(BASE_URL + path, {
      ...options,
      headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    })
  } catch {
    throw new Error('连不上数据服务，请先运行 node frontend/mock/server.mjs')
  }

  const body = await res.json().catch(() => null)
  if (!body || body.code !== 0) {
    throw new Error(body?.message || `请求失败（HTTP ${res.status}）`)
  }
  return body.data
}

const query = (params) =>
  new URLSearchParams(Object.entries(params).filter(([, v]) => v !== '' && v != null)).toString()

export const api = {
  listProducts: (params) => request(`/products?${query(params)}`),
  createProduct: (data) => request('/products', { method: 'POST', body: JSON.stringify(data) }),
  setProductStatus: (id, status) =>
    request(`/products/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) }),
  createOrder: (data) => request('/orders', { method: 'POST', body: JSON.stringify(data) }),
  listOrders: (params) => request(`/orders?${query(params)}`),
}
