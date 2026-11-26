import { request } from './client.js'

/**
 * 分类是接口契约里的固定枚举（README §4.1），不是业务数据，所以写在前端常量里。
 * 后端的 POST /api/products 只认这 5 个值，下拉恰好 5 项。
 */
export const CATEGORIES = ['数码', '图书', '服饰', '食品', '文具']

/**
 * 商品列表。按 `id` 升序，`pageSize` 直接给上限 50 一次取全，
 * 保证列表能访问到全部商品（README §4.4 允许这种做法）。
 * @param {{ keyword?: string, status?: string, category?: string, page?: number, pageSize?: number }} [query]
 * @param {AbortSignal} [signal]
 */
export function listProducts(query = {}, signal) {
  const params = new URLSearchParams()
  if (query.keyword) params.set('keyword', query.keyword)
  if (query.status) params.set('status', query.status)
  if (query.category) params.set('category', query.category)
  params.set('page', String(query.page ?? 1))
  params.set('pageSize', String(query.pageSize ?? 50))
  return request(`/products?${params.toString()}`, { signal })
}

/** 单个商品详情 */
export function getProduct(id, signal) {
  return request(`/products/${id}`, { signal })
}

/**
 * 当前登录卖家。作业没有账号体系，表单也只有 5 个字段（README §6.2），
 * 但后端把 sellerName 定为必填，所以由接口层统一补齐，不给界面加输入框。
 */
export const DEFAULT_SELLER_NAME = '我'

/**
 * 发布商品。`price` 必须是整数分，调用方负责把用户输入的元换算好。
 * @param {{ title: string, price: number, stock: number, category: string, description?: string, sellerName?: string }} payload
 */
export function createProduct(payload) {
  const body = {
    title: payload.title,
    price: payload.price,
    stock: payload.stock,
    category: payload.category,
    description: payload.description ?? '',
    sellerName: payload.sellerName?.trim() || DEFAULT_SELLER_NAME,
  }
  return request('/products', { method: 'POST', body })
}

/**
 * 上架 / 下架。
 * @param {number} id
 * @param {'on'|'off'} status
 */
export function updateProductStatus(id, status) {
  return request(`/products/${id}/status`, { method: 'PATCH', body: { status } })
}
