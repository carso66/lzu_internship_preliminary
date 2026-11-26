import { describe, expect, it } from 'vitest'
import {
  canManageProduct,
  createDemoSession,
  DEMO_SESSION,
  USER_ROLES,
} from './permissions.js'

const product = {
  id: 7,
  title: '同名商品',
  sellerId: 42,
  sellerName: '李同学',
}

describe('canManageProduct', () => {
  it('没有会话时拒绝管理', () => {
    expect(canManageProduct(product, null)).toBe(false)
    expect(canManageProduct(product, undefined)).toBe(false)
  })

  it('游客和买家即使有用户 ID 也不能管理商品', () => {
    expect(
      canManageProduct(product, {
        id: 42,
        role: USER_ROLES.visitor,
      }),
    ).toBe(false)
    expect(
      canManageProduct(product, {
        id: 42,
        role: USER_ROLES.buyer,
      }),
    ).toBe(false)
  })

  it('卖家只能管理 sellerId 与自身 ID 一致的商品', () => {
    expect(
      canManageProduct(product, {
        id: 42,
        role: USER_ROLES.seller,
      }),
    ).toBe(true)
    expect(
      canManageProduct(product, {
        id: 43,
        role: USER_ROLES.seller,
      }),
    ).toBe(false)
  })

  it('数字和字符串形式的同一 ID 仍视为同一卖家', () => {
    expect(
      canManageProduct(
        { ...product, sellerId: '42' },
        { id: 42, role: USER_ROLES.seller },
      ),
    ).toBe(true)
  })

  it('同名不能替代 sellerId，卖家名称相同也没有管理权', () => {
    expect(
      canManageProduct(
        { ...product, sellerId: undefined, sellerName: '李同学' },
        { id: 42, name: '李同学', role: USER_ROLES.seller },
      ),
    ).toBe(false)
  })

  it('sellerId 为空、空白或不是有效值时拒绝管理', () => {
    for (const sellerId of [undefined, null, '', '   ']) {
      expect(
        canManageProduct(
          { ...product, sellerId },
          { id: 42, role: USER_ROLES.seller },
        ),
      ).toBe(false)
    }
  })

  it('卖家 ID 相同但展示名称不同仍按 ID 判定归属', () => {
    expect(
      canManageProduct(product, {
        id: '42',
        name: '另一个展示名',
        role: USER_ROLES.seller,
      }),
    ).toBe(true)
  })

  it('管理员可管理没有 sellerId 的商品', () => {
    expect(
      canManageProduct(
        { ...product, sellerId: undefined },
        { id: 1, role: USER_ROLES.admin },
      ),
    ).toBe(true)
  })

  it('演示管理员会话是明确标记的前端演示身份', () => {
    expect(DEMO_SESSION).toMatchObject({
      id: 'demo-admin',
      role: USER_ROLES.admin,
      isDemo: true,
    })
    expect(canManageProduct(product, DEMO_SESSION)).toBe(true)
  })
})

describe('createDemoSession', () => {
  it('默认创建明确标记的管理员演示会话', () => {
    expect(createDemoSession()).toEqual({
      id: 'demo-admin',
      name: '演示管理员',
      role: USER_ROLES.admin,
      isDemo: true,
    })
  })

  it('游客、买家和管理员使用稳定的演示会话 ID', () => {
    for (const role of [
      USER_ROLES.visitor,
      USER_ROLES.buyer,
      USER_ROLES.admin,
    ]) {
      expect(createDemoSession(role, 'ignored')).toMatchObject({
        id: `demo-${role}`,
        role,
        isDemo: true,
      })
    }
  })

  it('卖家编号会去除首尾空白并用于精确归属判断', () => {
    const session = createDemoSession(USER_ROLES.seller, ' 42 ')

    expect(session).toMatchObject({
      id: '42',
      name: '演示卖家',
      role: USER_ROLES.seller,
      isDemo: true,
    })
    expect(canManageProduct(product, session)).toBe(true)
  })

  it('卖家未填写编号时保持空 ID，并按失败关闭处理', () => {
    const session = createDemoSession(USER_ROLES.seller, '   ')

    expect(session.id).toBe('')
    expect(canManageProduct(product, session)).toBe(false)
  })

  it('未知角色降级为游客，避免错误获得管理能力', () => {
    const session = createDemoSession('super-admin')

    expect(session).toMatchObject({
      id: 'demo-visitor',
      role: USER_ROLES.visitor,
      isDemo: true,
    })
    expect(canManageProduct(product, session)).toBe(false)
  })
})
