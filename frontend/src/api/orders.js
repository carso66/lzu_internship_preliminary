import { request } from './client.js'

/**
 * 当前下单人（收货信息）。
 *
 * 题目没有账号体系（README §8 明确「登录 / 注册不在本轮范围」），订单表也没有收货人字段；
 * 但后端 `POST /api/orders` 把 `buyerName` / `buyerPhone` / `address` 定为必填，
 * 而 `GET /api/orders` 又是按 `buyerPhone` 查的（后端不给手机号直接 400）。
 *
 * 所以前端统一维护一份收货信息：下单时带上，查询订单时用同一个手机号。
 * mock 只读 `productId` / `quantity`，多传的字段会被忽略，所以两种后端都能跑。
 */
export const DEFAULT_BUYER = {
  name: '我',
  phone: '13800138000',
  address: '甘肃省兰州市城关区天水南路 222 号 · 菜鸟驿站',
}

/** 后端 pageSize 上限 50；订单一屏取全，和商品列表的做法保持一致。 */
export const ORDER_PAGE_SIZE = 50

/**
 * 下单。`quantity` 必须是 number 类型的正整数（README L1-6 明确要求「不是 "2" 字符串」），
 * 由调用方用 `Number()` 转换好再传进来，这里不做字符串兼容 —— 静默转换会把类型错误藏起来。
 *
 * 不传 `unitPrice` / `totalAmount`：金额一律由服务端按数据库里的商品价格算，
 * 客户端就算传了也会被忽略，索性不传，避免让人误以为前端能定价。
 *
 * @param {{ productId: number, quantity: number, buyer?: { name: string, phone: string, address: string } }} payload
 */
export function createOrder({ productId, quantity, buyer = DEFAULT_BUYER }) {
  return request('/orders', {
    method: 'POST',
    body: {
      productId: Number(productId),
      quantity,
      buyerName: buyer.name,
      buyerPhone: buyer.phone,
      address: buyer.address,
    },
  })
}

/**
 * 我的订单。`buyerPhone` 必填 —— 这就是后端这个接口的语义（查某个手机号的订单）。
 *
 * @param {{ buyerPhone: string, page?: number, pageSize?: number }} query
 * @param {AbortSignal} [signal]
 */
export function listOrders({ buyerPhone, page = 1, pageSize = ORDER_PAGE_SIZE }, signal) {
  const params = new URLSearchParams()
  params.set('buyerPhone', buyerPhone)
  params.set('page', String(page))
  params.set('pageSize', String(pageSize))
  return request(`/orders?${params.toString()}`, { signal })
}
