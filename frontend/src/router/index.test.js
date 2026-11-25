import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { routes } from './routes.js'

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes,
  })
}

describe('route boundaries', () => {
  it('matches /products/new as the create page before the detail route', () => {
    const resolved = createTestRouter().resolve('/products/new')

    expect(resolved.name).toBe('product-create')
    expect(resolved.meta.title).toBe('上架商品')
  })

  it('keeps regular product ids on the detail route', () => {
    const resolved = createTestRouter().resolve('/products/42')

    expect(resolved.name).toBe('product-detail')
    expect(resolved.params.id).toBe('42')
  })

  it('does not let extra segments fall into the detail route', () => {
    const resolved = createTestRouter().resolve('/products/new/extra')

    expect(resolved.name).toBe('not-found')
  })

  it('falls back unknown paths to the 404 route', () => {
    const resolved = createTestRouter().resolve('/unknown/path')

    expect(resolved.name).toBe('not-found')
    expect(resolved.meta.title).toBe('页面不存在')
  })
})
