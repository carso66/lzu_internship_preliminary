import { ref } from 'vue'
import { DEFAULT_BUYER, listOrders } from '../api/orders.js'

/**
 * 当前要查询的订单归属手机号。
 *
 * 模块级（而不是 useOrders 内部）：下单页把收货手机号改了之后，
 * 「我的订单」页必须按同一个号码去查，否则刚下的那一单查不到（L1-7）。
 * 各页面调 useOrders() 拿到的是同一份状态。
 */
const sharedBuyerPhone = ref(DEFAULT_BUYER.phone)

/**
 * 记下实际下单用的手机号。空值 / 非法值直接忽略，
 * 不要把已经正确过的值覆盖成空。
 * @param {string} [phone]
 */
export function rememberBuyerPhone(phone) {
  const text = String(phone ?? '').trim()
  if (/^\d{11}$/.test(text)) sharedBuyerPhone.value = text
}

/**
 * 订单列表状态。
 *
 * 和 useProducts 的差别：后端 `GET /api/orders` 的 `buyerPhone` 是必填的，
 * 所以这里没有筛选栏，只有一个固定的手机号 —— 也就是下单时用的那个手机号。
 * 手机号变了（用户在收货信息里改）就重新拉一次。
 *
 * 同样用自增 `requestId` 丢掉过期响应：下单成功后商品和订单会同时刷新，
 * 没有这个保护就可能先到的空列表把新订单盖掉。
 */
export function useOrders() {
  const items = ref([])
  const total = ref(0)
  const loading = ref(false)
  const error = ref('')
  // 订单按手机号归属；默认值和 DEFAULT_BUYER 保持一致，且跨页面共享
  const buyerPhone = sharedBuyerPhone

  let requestId = 0

  async function reload(phone = buyerPhone.value) {
    buyerPhone.value = phone
    const current = ++requestId
    loading.value = true
    error.value = ''
    try {
      const data = await listOrders({ buyerPhone: phone })
      if (current !== requestId) return
      items.value = Array.isArray(data?.items) ? data.items : []
      total.value = Number.isFinite(Number(data?.total)) ? Number(data.total) : items.value.length
    } catch (err) {
      if (current !== requestId) return
      items.value = []
      total.value = 0
      error.value = err?.message || '加载订单失败'
    } finally {
      if (current === requestId) loading.value = false
    }
  }

  return { items, total, loading, error, buyerPhone, reload }
}
