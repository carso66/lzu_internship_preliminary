import { describe, expect, it } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { createAppRouter, routes } from '../src/router/index.js'

/**
 * 路由是纯逻辑，用 memory history 就能完整验证，
 * 所以这里不 mount 组件、不碰 `window`（test.environment 是 node）。
 */
function makeRouter() {
  return createAppRouter(createMemoryHistory())
}

describe('路由表', () => {
  it('覆盖五个页面 + 根路径重定向', () => {
    const named = routes.map((r) => r.name).filter(Boolean)
    expect(named).toEqual([
      'products',
      'product-detail',
      'publish',
      'orders',
      'not-found',
    ])
  })

  it('根路径重定向到商品列表，不留白屏', () => {
    const root = routes.find((r) => r.path === '/')
    expect(root?.redirect).toEqual({ name: 'products' })
  })

  it('详情页路径带 :id 参数并把 props 打开', () => {
    const detail = routes.find((r) => r.name === 'product-detail')
    expect(detail?.path).toBe('/products/:id')
    // props: true 让详情页通过 props 拿 id，不必再用 useRoute()
    expect(detail?.props).toBe(true)
  })

  it('每个页面都是懒加载，首屏不把全部页面打进来', () => {
    const pageRoutes = routes.filter((r) => r.component)
    expect(pageRoutes.length).toBeGreaterThanOrEqual(4)
    for (const r of pageRoutes) {
      expect(typeof r.component).toBe('function')
    }
  })
})

describe('resolve', () => {
  it('/products/3 解析出 id=3 并落到详情页', () => {
    const matched = makeRouter().resolve('/products/3')
    expect(matched.name).toBe('product-detail')
    expect(matched.params.id).toBe('3')
  })

  it("按 name + params 生成 /products/3", () => {
    const href = makeRouter().resolve({
      name: 'product-detail',
      params: { id: 3 },
    }).href
    expect(href).toBe('/products/3')
  })

  it('三个导航目标分别是 /products /publish /orders', () => {
    const router = makeRouter()
    expect(router.resolve({ name: 'products' }).path).toBe('/products')
    expect(router.resolve({ name: 'publish' }).path).toBe('/publish')
    expect(router.resolve({ name: 'orders' }).path).toBe('/orders')
  })

  it('地址写错落到 not-found，而不是白屏', () => {
    const matched = makeRouter().resolve('/this/does/not/exist')
    expect(matched.name).toBe('not-found')
  })

  it('query 原样保留（发布成功后的 ?highlight= 靠它）', () => {
    const matched = makeRouter().resolve('/products?highlight=12')
    expect(matched.name).toBe('products')
    expect(matched.query.highlight).toBe('12')
  })
})

describe('navigate', () => {
  it('根路径会重定向到 /products', async () => {
    const router = makeRouter()
    await router.push('/')
    expect(router.currentRoute.value.name).toBe('products')
    expect(router.currentRoute.value.path).toBe('/products')
  })

  it('list → detail → back 能回到列表', async () => {
    const router = makeRouter()
    await router.push('/products')
    await router.push('/products/1')
    expect(router.currentRoute.value.name).toBe('product-detail')
    router.back()
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(router.currentRoute.value.name).toBe('products')
  })
})
