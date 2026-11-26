import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  API_BASE_URL,
  createOrder,
  createPayment,
  createProduct,
  getOrders,
  getPayment,
  getProduct,
  getProducts,
  updateProductStatus,
} from './index.js'

const fetchMock = vi.fn()

function mockResponse(body, { ok = true, status = 200 } = {}) {
  return {
    ok,
    status,
    json: vi.fn().mockResolvedValue(body),
  }
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('API_BASE_URL', () => {
  it('测试环境默认使用同源 /api，并去掉末尾斜杠', () => {
    expect(API_BASE_URL).toBe('/api')
  })
})

describe('URL 与请求选项', () => {
  it('getProducts 默认不拼接无意义参数', async () => {
    fetchMock.mockResolvedValue(mockResponse({ code: 0, data: { items: [] } }))
    await getProducts()

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/products',
      expect.not.objectContaining({ body: expect.anything() }),
    )
  })

  it('getProducts 只序列化传入的 page 和 pageSize', async () => {
    fetchMock.mockResolvedValue(mockResponse({ code: 0, data: { items: [] } }))
    await getProducts({ page: 2, pageSize: 50 })

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/products?page=2&pageSize=50',
      expect.not.objectContaining({ body: expect.anything() }),
    )
  })

  it('getOrders 固定请求 pageSize=50', async () => {
    fetchMock.mockResolvedValue(mockResponse({ code: 0, data: { items: [] } }))
    await getOrders()

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/orders?pageSize=50',
      expect.not.objectContaining({ body: expect.anything() }),
    )
  })

  it('路径参数经过编码', async () => {
    fetchMock.mockResolvedValue(mockResponse({ code: 0, data: null }))
    await getProduct('a/b')
    await getPayment('p/1')

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      '/api/products/a%2Fb',
      expect.any(Object),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/payments/p%2F1',
      expect.any(Object),
    )
  })

  it.each([
    [
      'createProduct',
      createProduct,
      '/api/products',
      'POST',
      [{ title: '书' }],
      { title: '书' },
    ],
    [
      'updateProductStatus',
      updateProductStatus,
      '/api/products/1/status',
      'PATCH',
      [1, 'off'],
      { status: 'off' },
    ],
    [
      'createOrder',
      createOrder,
      '/api/orders',
      'POST',
      [{ productId: 1, quantity: 2 }],
      { productId: 1, quantity: 2 },
    ],
    [
      'createPayment',
      createPayment,
      '/api/payments',
      'POST',
      [{ orderId: 3, method: 'alipay' }],
      { orderId: 3, method: 'alipay' },
    ],
  ])(
    '%s 使用正确的方法、路径和 JSON 请求体',
    async (_, fn, url, method, args, body) => {
      fetchMock.mockResolvedValue(mockResponse({ code: 0, data: { id: 1 } }))
      await fn(...args)

      expect(fetchMock).toHaveBeenCalledWith(
        url,
        expect.objectContaining({
          method,
          body: JSON.stringify(body),
          headers: expect.objectContaining({
            'Content-Type': 'application/json',
            Accept: 'application/json',
          }),
        }),
      )
    },
  )
})

describe('响应与错误处理', () => {
  it('code 为 0 时返回 data', async () => {
    const data = { items: [{ id: 1 }] }
    fetchMock.mockResolvedValue(mockResponse({ code: 0, data }))

    await expect(getProducts()).resolves.toEqual(data)
  })

  it('业务 code 非 0 时抛出后端 message', async () => {
    fetchMock.mockResolvedValue(
      mockResponse({ code: 400, message: '库存不足', data: null }),
    )

    await expect(getProducts()).rejects.toThrow('库存不足')
  })

  it('业务失败但没有 message 时使用通用文案', async () => {
    fetchMock.mockResolvedValue(mockResponse({ code: 1, data: null }))

    await expect(getProducts()).rejects.toThrow('请求失败')
  })

  it('HTTP 非 2xx 时优先使用响应体 message', async () => {
    fetchMock.mockResolvedValue(
      mockResponse(
        { code: 400, message: '订单已支付，请勿重复支付' },
        { ok: false, status: 409 },
      ),
    )

    await expect(
      createPayment({ orderId: 3, method: 'wechat' }),
    ).rejects.toThrow('订单已支付，请勿重复支付')
  })

  it('HTTP 非 2xx 且响应体不是 JSON 时使用状态码兜底', async () => {
    const response = {
      ok: false,
      status: 500,
      json: vi.fn().mockRejectedValue(new Error('invalid json')),
    }
    fetchMock.mockResolvedValue(response)

    await expect(getOrders()).rejects.toThrow('请求失败（HTTP 500）')
  })

  it('HTTP 非 2xx 且响应体为空时使用状态码兜底', async () => {
    fetchMock.mockResolvedValue(mockResponse(null, { ok: false, status: 503 }))

    await expect(getProducts()).rejects.toThrow('请求失败（HTTP 503）')
  })

  it('网络错误原样向上抛出', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))

    await expect(getProducts()).rejects.toThrow('Failed to fetch')
  })
})
