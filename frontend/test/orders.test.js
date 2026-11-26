import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  DEFAULT_BUYER,
  ORDER_PAGE_SIZE,
  createOrder,
  listOrders,
} from '../src/api/orders.js'
import {
  formatDateTime,
  orderStatusClass,
  orderStatusText,
} from '../src/utils/order.js'

/** 拦截 fetch，返回统一响应包，并把请求体和 URL 交出来给断言。 */
function stubFetch(data = null) {
  const calls = []
  globalThis.fetch = vi.fn(async (url, init) => {
    calls.push({ url: String(url), init, body: init?.body ? JSON.parse(init.body) : undefined })
    return {
      status: 200,
      json: async () => ({ code: 0, message: 'ok', data }),
    }
  })
  return calls
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('createOrder 请求体', () => {
  it('带上后端要求的 5 个字段，一个不多一个不少', async () => {
    const calls = stubFetch({ orderNo: 'SO1' })
    await createOrder({ productId: 7, quantity: 2 })

    expect(calls).toHaveLength(1)
    expect(calls[0].init.method).toBe('POST')
    expect(calls[0].url).toContain('/api/orders')
    expect(Object.keys(calls[0].body).sort()).toEqual(
      ['address', 'buyerName', 'buyerPhone', 'productId', 'quantity'],
    )
  })

  it('quantity 保持 number 类型，不会被转成字符串（L1-6 明确要求）', async () => {
    const calls = stubFetch({ orderNo: 'SO2' })
    await createOrder({ productId: 7, quantity: 3 })

    expect(typeof calls[0].body.quantity).toBe('number')
    expect(calls[0].body.quantity).toBe(3)
  })

  it('productId 强制转成数字，避免路由拿到 "7" 拼出错误的路径', async () => {
    const calls = stubFetch({ orderNo: 'SO3' })
    await createOrder({ productId: '7', quantity: 1 })

    expect(calls[0].body.productId).toBe(7)
  })

  it('不传 buyer 时用 DEFAULT_BUYER 兜底，后端不会因为缺手机号 400', async () => {
    const calls = stubFetch({ orderNo: 'SO4' })
    await createOrder({ productId: 1, quantity: 1 })

    expect(calls[0].body.buyerName).toBe(DEFAULT_BUYER.name)
    expect(calls[0].body.buyerPhone).toBe(DEFAULT_BUYER.phone)
    expect(calls[0].body.address).toBe(DEFAULT_BUYER.address)
  })

  it('显式传的 buyer 覆盖默认值', async () => {
    const calls = stubFetch({ orderNo: 'SO5' })
    await createOrder({
      productId: 1,
      quantity: 1,
      buyer: { name: '张三', phone: '13900139000', address: '兰州市榆中县夏官营镇' },
    })

    expect(calls[0].body).toMatchObject({
      buyerName: '张三',
      buyerPhone: '13900139000',
      address: '兰州市榆中县夏官营镇',
    })
  })

  it('不夹带金额字段：单价和总价只能由服务端算', async () => {
    const calls = stubFetch({ orderNo: 'SO6' })
    await createOrder({ productId: 1, quantity: 2, unitPrice: 100, totalAmount: 200 })

    expect(calls[0].body).not.toHaveProperty('unitPrice')
    expect(calls[0].body).not.toHaveProperty('totalAmount')
  })

  it('后端返回业务错误码时抛出可读消息', async () => {
    globalThis.fetch = vi.fn(async () => ({
      status: 200,
      json: async () => ({ code: 40901, message: '库存不足', data: null }),
    }))

    await expect(createOrder({ productId: 1, quantity: 999 })).rejects.toThrow('库存不足')
  })
})

describe('listOrders 查询参数', () => {
  it('buyerPhone 必带，page/pageSize 有默认值', async () => {
    const calls = stubFetch({ total: 0, items: [] })
    await listOrders({ buyerPhone: '13800138000' })

    expect(calls[0].url).toContain('buyerPhone=13800138000')
    expect(calls[0].url).toContain('page=1')
    expect(calls[0].url).toContain(`pageSize=${ORDER_PAGE_SIZE}`)
  })

  it('订单按一屏取全：默认 pageSize 就是后端上限 50', () => {
    expect(ORDER_PAGE_SIZE).toBe(50)
  })

  it('显式分页参数生效', async () => {
    const calls = stubFetch({ total: 0, items: [] })
    await listOrders({ buyerPhone: '13800138000', page: 3, pageSize: 5 })

    expect(calls[0].url).toContain('page=3')
    expect(calls[0].url).toContain('pageSize=5')
  })
})

describe('订单状态文案', () => {
  it('created 显示「待支付」，paid 显示「已支付」', () => {
    expect(orderStatusText('created')).toBe('待支付')
    expect(orderStatusText('paid')).toBe('已支付')
  })

  it('未知状态不露英文原文，兜底成可读中文', () => {
    expect(orderStatusText('refunded')).toBe('未知状态（refunded）')
  })

  it('空值兜底为「未知状态」，页面不会出现 undefined', () => {
    expect(orderStatusText(undefined)).toBe('未知状态')
    expect(orderStatusText(null)).toBe('未知状态')
    expect(orderStatusText('   ')).toBe('未知状态')
  })

  it('paid 用成功色、created 用待支付色、其它用失效色', () => {
    expect(orderStatusClass('paid')).toBe('badge-on')
    expect(orderStatusClass('created')).toBe('badge-pending')
    expect(orderStatusClass('refunded')).toBe('badge-off')
    expect(orderStatusClass(undefined)).toBe('badge-off')
  })
})

describe('formatDateTime', () => {
  it('后端不带时区的 ISO 字符串转成「YYYY-MM-DD HH:mm」', () => {
    expect(formatDateTime('2026-11-25T10:12:33')).toBe('2026-11-25 10:12')
  })

  it('个位月/日/时/分补零', () => {
    expect(formatDateTime('2026-01-02T03:04:05')).toBe('2026-01-02 03:04')
  })

  it('null/空串显示破折号，不是 undefined', () => {
    expect(formatDateTime(null)).toBe('—')
    expect(formatDateTime('')).toBe('—')
    expect(formatDateTime(undefined)).toBe('—')
  })

  it('非法时间原样回显，方便排查而不是静默变成 NaN', () => {
    expect(formatDateTime('not-a-date')).toBe('not-a-date')
  })
})
