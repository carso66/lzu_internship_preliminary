import { describe, expect, it } from 'vitest'
import {
  buildProductGroups,
  summarizeGroupPermissions,
} from './productGroups.js'
import { USER_ROLES } from './permissions.js'

const listings = [
  {
    id: 1,
    title: '同名英语耳机',
    price: 1990,
    stock: 2,
    status: 'on',
    sellerId: 42,
    sellerName: '李同学',
  },
  {
    id: 2,
    title: '同名英语耳机',
    price: 2990,
    stock: 0,
    status: 'on',
    sellerId: 43,
    sellerName: '张同学',
  },
  {
    id: 3,
    title: '同名英语耳机',
    price: 2490,
    stock: 5,
    status: 'off',
    sellerName: '匿名卖家',
  },
]

describe('buildProductGroups', () => {
  it('优先按 spuId 聚合，并保留组内全部 listing', () => {
    const groups = buildProductGroups([
      { ...listings[0], title: '旧版耳机', spuId: 'headphone-x' },
      { ...listings[1], title: '新版耳机', spuId: 'headphone-x' },
    ])

    expect(groups).toHaveLength(1)
    expect(groups[0].groupingMode).toBe('spu')
    expect(groups[0].listingCount).toBe(2)
    expect(groups[0].listings.map((item) => item.id)).toEqual([1, 2])
  })

  it('没有 spuId 时用规范化标题做展示聚合', () => {
    const groups = buildProductGroups([
      { ...listings[0], title: '<b>同名</b>英语耳机' },
      { ...listings[1], title: '同名英语耳机' },
    ])

    expect(groups).toHaveLength(1)
    expect(groups[0].groupingMode).toBe('title')
    expect(groups[0].title).toBe('同名 英语耳机')
    expect(groups[0].listingCount).toBe(2)
  })

  it('计算价格、库存、状态和来源摘要', () => {
    const [group] = buildProductGroups(listings)

    expect(group.listingCount).toBe(3)
    expect(group.sourceCount).toBe(3)
    expect(group.identifiedSourceCount).toBe(2)
    expect(group.missingSellerIdCount).toBe(1)
    expect(group.priceMin).toBe(1990)
    expect(group.priceMax).toBe(2990)
    expect(group.totalStock).toBe(7)
    expect(group.onSaleCount).toBe(2)
    expect(group.availableCount).toBe(1)
    expect(group.offShelfCount).toBe(1)
    expect(group.soldOutCount).toBe(1)
  })

  it('未核验来源即使同名也按独立 listing 计数', () => {
    const [group] = buildProductGroups([
      { ...listings[0], sellerId: undefined, sellerName: '同名卖家' },
      { ...listings[1], sellerId: undefined, sellerName: '同名卖家' },
    ])

    expect(group.identifiedSourceCount).toBe(0)
    expect(group.missingSellerIdCount).toBe(2)
    expect(group.sourceCount).toBe(2)
  })

  it('过滤非法商品、重复 id，并为无标题商品保留独立组', () => {
    const groups = buildProductGroups([
      null,
      { id: 9, title: '' },
      { id: 9, title: '' },
      { id: 10, title: '   ' },
      { id: 11, title: '<img src=x>' },
    ])

    expect(groups).toHaveLength(3)
    expect(groups.map((group) => group.listingCount)).toEqual([1, 1, 1])
  })

  it('非数组输入安全返回空数组', () => {
    expect(buildProductGroups(null)).toEqual([])
    expect(buildProductGroups(undefined)).toEqual([])
    expect(buildProductGroups({})).toEqual([])
  })

  it('空字符串和非数字价格库存不会污染聚合汇总', () => {
    const [group] = buildProductGroups([
      { id: 21, title: '边界商品', price: '', stock: '' },
      { id: 22, title: '边界商品', price: 'invalid', stock: -3 },
    ])

    expect(group.priceMin).toBeNull()
    expect(group.priceMax).toBeNull()
    expect(group.totalStock).toBe(0)
    expect(group.soldOutCount).toBe(2)
  })
})

describe('summarizeGroupPermissions', () => {
  it('游客和买家没有管理权限', () => {
    expect(
      summarizeGroupPermissions(listings, {
        id: 42,
        role: USER_ROLES.visitor,
      }),
    ).toMatchObject({
      role: USER_ROLES.visitor,
      roleLabel: '游客',
      manageableCount: 0,
      totalCount: 3,
      scope: 'none',
      scopeLabel: '仅浏览',
      canManageAll: false,
      permissionLabel: '仅浏览，无管理权限',
    })

    expect(
      summarizeGroupPermissions(listings, {
        id: 42,
        role: USER_ROLES.buyer,
      }),
    ).toMatchObject({
      role: USER_ROLES.buyer,
      scope: 'none',
      scopeLabel: '仅浏览',
    })
  })

  it('卖家只管理自己的列表项，聚合权限为 partial', () => {
    expect(
      summarizeGroupPermissions(listings, {
        id: 42,
        role: USER_ROLES.seller,
      }),
    ).toMatchObject({
      role: USER_ROLES.seller,
      roleLabel: '卖家',
      manageableCount: 1,
      totalCount: 3,
      scope: 'partial',
      scopeLabel: '部分可管理',
      canManageAll: false,
      permissionLabel: '可管理 1/3 件',
    })
  })

  it('管理员管理全部列表项，聚合权限为 all', () => {
    expect(
      summarizeGroupPermissions(listings, {
        id: 'admin-1',
        role: USER_ROLES.admin,
      }),
    ).toMatchObject({
      role: USER_ROLES.admin,
      roleLabel: '管理员',
      manageableCount: 3,
      totalCount: 3,
      scope: 'all',
      scopeLabel: '全部可管理',
      canManageAll: true,
      permissionLabel: '可管理全部 3 件',
    })
  })

  it('空列表不授予管理权限', () => {
    expect(
      summarizeGroupPermissions([], { id: 1, role: USER_ROLES.admin }),
    ).toMatchObject({
      manageableCount: 0,
      totalCount: 0,
      scope: 'none',
      scopeLabel: '仅浏览',
      canManageAll: false,
    })
  })
})
