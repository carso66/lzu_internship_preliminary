import { canManageProduct, ROLE_LABELS, USER_ROLES } from './permissions.js'
import { normalizeProductTitle } from './product.js'

function identifier(value) {
  if (value === undefined || value === null) return ''
  return String(value).trim()
}

function listingIdentity(product, index) {
  const id = identifier(product?.id)
  return id ? `id:${id}` : `index:${index}`
}

function groupingDescriptor(product, index) {
  const spuId = identifier(product?.spuId)
  if (spuId) return { key: `spu:${spuId}`, mode: 'spu' }

  const title = normalizeProductTitle(product?.title, '')
  // 标题标签清洗后可能留下空格；展示聚合键忽略空白，但不改变 listing 身份。
  const titleKey = title.toLowerCase().replace(/\s+/g, '')
  if (titleKey) return { key: `title:${titleKey}`, mode: 'title' }

  return { key: `listing:${listingIdentity(product, index)}`, mode: 'listing' }
}

function safePrice(value) {
  if (value === undefined || value === null || value === '') return null
  const price = Number(value)
  return Number.isFinite(price) && price >= 0 ? price : null
}

function safeStock(value) {
  if (value === undefined || value === null || value === '') return 0
  const stock = Number(value)
  return Number.isFinite(stock) && stock >= 0 ? Math.floor(stock) : 0
}

const PERMISSION_SCOPE_LABELS = Object.freeze({
  all: '全部可管理',
  partial: '部分可管理',
  none: '仅浏览',
})

/**
 * 汇总一组商品的权限状态，供聚合卡片展示。
 *
 * scope:
 * - all: 当前会话可管理组内全部 listing
 * - partial: 只能管理其中一部分（例如卖家只拥有部分商品）
 * - none: 只能浏览，不能管理
 *
 * @param {Array<object>} listings 商品 listing
 * @param {object | null | undefined} session 当前会话
 * @returns {{
 *   role: string,
 *   roleLabel: string,
 *   manageableCount: number,
 *   totalCount: number,
 *   scope: 'all' | 'partial' | 'none',
 *   scopeLabel: string,
 *   canManageAll: boolean,
 *   permissionLabel: string
 * }}
 */
export function summarizeGroupPermissions(listings = [], session = null) {
  const validListings = Array.isArray(listings)
    ? listings.filter((listing) => listing && typeof listing === 'object')
    : []
  const totalCount = validListings.length
  const manageableCount = validListings.filter((listing) =>
    canManageProduct(listing, session),
  ).length
  const scope =
    totalCount > 0 && manageableCount === totalCount
      ? 'all'
      : manageableCount > 0
        ? 'partial'
        : 'none'
  const role =
    session?.role && ROLE_LABELS[session.role]
      ? session.role
      : USER_ROLES.visitor
  const permissionLabel =
    scope === 'all'
      ? `可管理全部 ${totalCount} 件`
      : scope === 'partial'
        ? `可管理 ${manageableCount}/${totalCount} 件`
        : '仅浏览，无管理权限'

  return {
    role,
    roleLabel: ROLE_LABELS[role] ?? '游客',
    manageableCount,
    totalCount,
    scope,
    scopeLabel: PERMISSION_SCOPE_LABELS[scope],
    canManageAll: scope === 'all',
    permissionLabel,
  }
}

/**
 * 构造商品聚合展示模型。
 *
 * 优先使用显式 spuId 分组；没有 spuId 时，为兼容当前 Mock 数据，
 * 用规范化标题做“展示聚合”。这不会改变 listing 的身份：
 * 每条商品仍以自身 id、sellerId 和状态独立展示和管理。
 *
 * @param {Array<object> | null | undefined} products 商品列表
 * @param {object | null | undefined} session 当前会话
 * @returns {Array<object>} 聚合展示组
 */
export function buildProductGroups(products, session = null) {
  if (!Array.isArray(products)) return []

  const groupsByKey = new Map()
  const seenListingKeys = new Set()

  products.forEach((product, index) => {
    if (!product || typeof product !== 'object') return

    const listingKey = listingIdentity(product, index)
    if (seenListingKeys.has(listingKey)) return
    seenListingKeys.add(listingKey)

    const descriptor = groupingDescriptor(product, index)
    let group = groupsByKey.get(descriptor.key)

    if (!group) {
      group = {
        key: descriptor.key,
        groupingMode: descriptor.mode,
        title: normalizeProductTitle(product.title),
        entries: [],
      }
      groupsByKey.set(descriptor.key, group)
    }

    group.entries.push({ product, listingKey })
  })

  return [...groupsByKey.values()].map((group) => {
    const listings = group.entries.map((entry) => entry.product)
    const sellerIds = new Set()
    const sourceKeys = new Set()
    let priceMin = null
    let priceMax = null
    let totalStock = 0
    let onSaleCount = 0
    let availableCount = 0
    let offShelfCount = 0
    let soldOutCount = 0
    let missingSellerIdCount = 0

    group.entries.forEach(({ product, listingKey }) => {
      const sellerId = identifier(product.sellerId)
      if (sellerId) {
        sellerIds.add(sellerId)
        sourceKeys.add(`seller:${sellerId}`)
      } else {
        missingSellerIdCount += 1
        // 未核验来源不能因为名称相同就合并身份，按 listing 计数。
        sourceKeys.add(`unverified:${listingKey}`)
      }

      const price = safePrice(product.price)
      if (price !== null) {
        priceMin = priceMin === null ? price : Math.min(priceMin, price)
        priceMax = priceMax === null ? price : Math.max(priceMax, price)
      }

      const stock = safeStock(product.stock)
      totalStock += stock

      if (product.status === 'on') {
        onSaleCount += 1
        if (stock > 0) availableCount += 1
      } else {
        offShelfCount += 1
      }

      if (stock <= 0) soldOutCount += 1
    })

    return {
      key: group.key,
      groupingMode: group.groupingMode,
      title: group.title,
      listings,
      listingCount: listings.length,
      sourceCount: sourceKeys.size,
      identifiedSourceCount: sellerIds.size,
      missingSellerIdCount,
      priceMin,
      priceMax,
      totalStock,
      onSaleCount,
      availableCount,
      offShelfCount,
      soldOutCount,
      permissions: summarizeGroupPermissions(listings, session),
    }
  })
}
