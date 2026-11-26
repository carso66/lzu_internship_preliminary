import { afterEach, describe, expect, it, vi } from 'vitest'
import { DEFAULT_SELLER_NAME, createProduct, listProducts } from '../src/api/products.js'

/** 拦截 fetch，返回统一响应包，并把请求体解析出来交给断言。 */
function stubFetch(data = null) {
  const calls = []
  globalThis.fetch = vi.fn(async (url, init) => {
    calls.push({ url, init, body: init?.body ? JSON.parse(init.body) : undefined })
    return {
      status: 200,
      json: async () => ({ code: 0, message: 'ok', data }),
    }
  })
  return calls
}

const base = { title: '手冲挂耳咖啡 10 包', price: 2990, stock: 33, category: '食品' }

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('createProduct 补齐 sellerName', () => {
  it('表单只给 5 个字段时自动补默认卖家名', async () => {
    const calls = stubFetch({ id: 1 })
    await createProduct({ ...base, description: '自习室续命装。' })

    expect(calls).toHaveLength(1)
    expect(calls[0].init.method).toBe('POST')
    expect(calls[0].url).toContain('/api/products')
    expect(calls[0].body).toEqual({
      title: base.title,
      price: 2990,
      stock: 33,
      category: '食品',
      description: '自习室续命装。',
      sellerName: DEFAULT_SELLER_NAME,
    })
  })

  it('description 缺省时发空串，不让后端拿到 undefined', async () => {
    const calls = stubFetch({ id: 2 })
    await createProduct(base)

    expect(calls[0].body.description).toBe('')
    expect(Object.keys(calls[0].body).sort()).toEqual(
      ['category', 'description', 'price', 'sellerName', 'stock', 'title'],
    )
  })

  it('显式传的 sellerName 优先，且首尾空格被去掉', async () => {
    const calls = stubFetch({ id: 3 })
    await createProduct({ ...base, sellerName: '  旧书回收站  ' })

    expect(calls[0].body.sellerName).toBe('旧书回收站')
  })

  it('sellerName 传空白时回退到默认值，避免后端 400 卖家名不能为空', async () => {
    const calls = stubFetch({ id: 4 })
    await createProduct({ ...base, sellerName: '   ' })

    expect(calls[0].body.sellerName).toBe(DEFAULT_SELLER_NAME)
  })

  it('不在 payload 里夹带接口不认识的字段', async () => {
    const calls = stubFetch({ id: 5 })
    await createProduct({ ...base, id: 99, status: 'off' })

    expect(calls[0].body).not.toHaveProperty('id')
    expect(calls[0].body).not.toHaveProperty('status')
  })
})

describe('listProducts 分页参数', () => {
  it('默认 page=1、pageSize=50', async () => {
    const calls = stubFetch({ total: 0, page: 1, pageSize: 50, items: [] })
    await listProducts()

    expect(calls[0].url).toContain('page=1')
    expect(calls[0].url).toContain('pageSize=50')
  })

  it('空字符串的筛选项不下发，避免后端把空串当成有效条件', async () => {
    const calls = stubFetch({ total: 0, page: 1, pageSize: 50, items: [] })
    await listProducts({ keyword: '', status: '', category: '' })

    expect(calls[0].url).not.toContain('keyword')
    expect(calls[0].url).not.toContain('status')
    expect(calls[0].url).not.toContain('category')
  })
})
