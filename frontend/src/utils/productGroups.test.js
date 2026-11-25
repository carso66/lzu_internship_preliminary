import { describe, expect, it } from 'vitest'
import {
  buildProductGroups,
  summarizeGroupPermissions,
} from './productGroups.js'
import { USER_ROLES } from './permissions.js'

const products = [
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
  it('优先按 spuId 聚合，并保留组内全部商品', () => {
    const groups = buildProductGroups([
      { ...products[0], title: '降噪蓝牙耳机', spuId: 'headphone-x' },
      { ...products[1], title: '英语听力耳机', spuId: 'headphone-x' },
    ])

    expect(groups).toHaveLength(1)
    expect(groups[0].groupingMode).toBe('spu')
    expect(groups[0].productCount).toBe(2)
    expect(groups[0].products.map((item) => item.id)).toEqual([1, 2])
  })

  it('把不同标题的耳机归为同一购买意图，组名由商品类型规则决定', () => {
    const groups = buildProductGroups([
      {
        id: 101,
        title: '降噪蓝牙耳机 Pro',
        price: 19900,
        stock: 3,
        status: 'on',
        sellerId: 7,
      },
      {
        id: 102,
        title: '英语听力耳机',
        price: 8990,
        stock: 4,
        status: 'on',
        sellerId: 8,
      },
      {
        id: 103,
        title: '头戴式耳机',
        price: 12900,
        stock: 0,
        status: 'on',
        sellerId: 9,
      },
    ])

    expect(groups).toHaveLength(1)
    expect(groups[0]).toMatchObject({
      groupingMode: 'type',
      type: '耳机',
      title: '耳机',
      productCount: 3,
      sellerCount: 3,
    })
    expect(groups[0].tags).toEqual(
      expect.arrayContaining(['降噪', '蓝牙', '头戴式', '英语听力']),
    )
    expect(groups[0].title).not.toBe('降噪蓝牙耳机 Pro')
  })

  it('纸笔记本、笔记本电脑和内胆包按购买用途分开', () => {
    const groups = buildProductGroups([
      {
        id: 201,
        title: 'A4 笔记本 3 本装',
        category: '文具',
        price: 2490,
        stock: 25,
        status: 'on',
      },
      {
        id: 202,
        title: '笔记本内胆包 14 寸',
        category: '数码',
        price: 6900,
        stock: 15,
        status: 'on',
      },
      {
        id: 203,
        title: '轻薄笔记本电脑',
        category: '数码',
        price: 399900,
        stock: 2,
        status: 'on',
      },
    ])

    expect(groups).toHaveLength(3)
    expect(groups.map((group) => [group.groupingMode, group.title])).toEqual([
      ['type', '笔记本'],
      ['type', '电脑包'],
      ['type', '笔记本电脑'],
    ])
    expect(
      groups.map((group) => group.products.map((item) => item.id)),
    ).toEqual([[201], [202], [203]])
  })

  it('耳机套、键盘膜和显示器支架等配件不会并入主商品', () => {
    const groups = buildProductGroups([
      { id: 501, title: '降噪蓝牙耳机', category: '数码' },
      { id: 502, title: '耳机收纳盒', category: '数码' },
      { id: 503, title: '机械键盘', category: '数码' },
      { id: 504, title: '键盘膜', category: '数码' },
      { id: 505, title: '27 寸显示器', category: '数码' },
      { id: 506, title: '显示器支架', category: '数码' },
    ])

    expect(groups).toHaveLength(6)
    expect(groups.map((group) => [group.groupingMode, group.title])).toEqual([
      ['type', '耳机'],
      ['product', '耳机收纳盒'],
      ['type', '键盘'],
      ['product', '键盘膜'],
      ['type', '显示器'],
      ['product', '显示器支架'],
    ])
  })
  it('主商品标题提到随附配件时仍归入主商品类型', () => {
    const groups = buildProductGroups([
      { id: 511, title: '降噪蓝牙耳机', category: '数码' },
      {
        id: 512,
        title:
          '九成新英语听力耳机二手转让含原装收纳盒与两副耳帽可小刀兰州大学本部自提优先',
        category: '数码',
      },
    ])

    expect(groups).toHaveLength(1)
    expect(groups[0]).toMatchObject({
      groupingMode: 'type',
      title: '耳机',
      productCount: 2,
    })
    expect(groups[0].products.map((item) => item.id)).toEqual([511, 512])
  })

  it('无法可靠识别的商品即使标题相同也保持独立，不按同名强行聚合', () => {
    const groups = buildProductGroups([
      {
        id: 301,
        title: '限量联名款 A',
        category: '数码',
        price: 100,
        stock: 1,
        status: 'on',
      },
      {
        id: 302,
        title: '限量联名款 A',
        category: '数码',
        price: 200,
        stock: 2,
        status: 'on',
      },
    ])

    expect(groups).toHaveLength(2)
    expect(groups.map((group) => group.groupingMode)).toEqual([
      'product',
      'product',
    ])
    expect(groups.map((group) => group.productCount)).toEqual([1, 1])
  })

  it('具体分类可以作为保守回退，宽泛分类不会合并无关商品', () => {
    const groups = buildProductGroups([
      { id: 401, title: 'AX3000 路由器', category: '路由器' },
      { id: 402, title: 'AX6000 路由器', category: '路由器' },
      { id: 403, title: '未知数码配件 A', category: '数码' },
      { id: 404, title: '未知数码配件 B', category: '数码' },
    ])

    expect(groups).toHaveLength(3)
    expect(groups[0]).toMatchObject({
      groupingMode: 'category',
      title: '路由器',
      productCount: 2,
    })
    expect(groups.slice(1).map((group) => group.groupingMode)).toEqual([
      'product',
      'product',
    ])
  })

  it('计算价格、库存、状态和卖家摘要，组内商品仍各自独立', () => {
    const [group] = buildProductGroups(products)

    expect(group.productCount).toBe(3)
    expect(group.sellerCount).toBe(3)
    expect(group.identifiedSellerCount).toBe(2)
    expect(group.missingSellerIdCount).toBe(1)
    expect(group.priceMin).toBe(1990)
    expect(group.priceMax).toBe(2990)
    expect(group.totalStock).toBe(7)
    expect(group.onSaleCount).toBe(2)
    expect(group.availableCount).toBe(1)
    expect(group.offShelfCount).toBe(1)
    expect(group.soldOutCount).toBe(1)
    expect(group.products.map((item) => item.id)).toEqual([1, 2, 3])
  })

  it('没有卖家编号时，即使卖家名称相同也按独立卖家计数', () => {
    const [group] = buildProductGroups([
      { ...products[0], sellerId: undefined, sellerName: '同名卖家' },
      { ...products[1], sellerId: undefined, sellerName: '同名卖家' },
    ])

    expect(group.identifiedSellerCount).toBe(0)
    expect(group.missingSellerIdCount).toBe(2)
    expect(group.sellerCount).toBe(2)
  })

  it('聚合组权限仅作摘要，卖家只管理自己的商品，管理员管理全部', () => {
    const [sellerGroup] = buildProductGroups(products, {
      id: 42,
      role: USER_ROLES.seller,
    })
    const [adminGroup] = buildProductGroups(products, {
      id: 'admin-1',
      role: USER_ROLES.admin,
    })

    expect(sellerGroup.permissions).toMatchObject({
      manageableCount: 1,
      totalCount: 3,
      scope: 'partial',
      permissionLabel: '可管理 1/3 件',
    })
    expect(adminGroup.permissions).toMatchObject({
      manageableCount: 3,
      totalCount: 3,
      scope: 'all',
      permissionLabel: '可管理全部 3 件',
    })
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
    expect(groups.map((group) => group.productCount)).toEqual([1, 1, 1])
    expect(groups.map((group) => group.title)).toEqual([
      '未命名商品',
      '未命名商品',
      '未命名商品',
    ])
  })

  it('非数组输入安全返回空数组', () => {
    expect(buildProductGroups(null)).toEqual([])
    expect(buildProductGroups(undefined)).toEqual([])
    expect(buildProductGroups({})).toEqual([])
  })

  it('空字符串和非数字价格库存不会污染聚合汇总', () => {
    const [group] = buildProductGroups([
      { id: 21, title: '边界耳机', price: '', stock: '' },
      { id: 22, title: '边界耳机', price: 'invalid', stock: -3 },
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
      summarizeGroupPermissions(products, {
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
      summarizeGroupPermissions(products, {
        id: 42,
        role: USER_ROLES.buyer,
      }),
    ).toMatchObject({
      role: USER_ROLES.buyer,
      scope: 'none',
      scopeLabel: '仅浏览',
    })
  })

  it('卖家只管理自己的商品，聚合权限为 partial', () => {
    expect(
      summarizeGroupPermissions(products, {
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

  it('管理员管理全部商品，聚合权限为 all', () => {
    expect(
      summarizeGroupPermissions(products, {
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
