import { canManageProduct, ROLE_LABELS, USER_ROLES } from './permissions.js'
import { normalizeProductTitle } from './product.js'

const GENERIC_CATEGORY_KEYS = new Set([
  '数码',
  '图书',
  '服饰',
  '食品',
  '文具',
  '其他',
  '未分类',
])

// 具体词优先：例如“笔记本内胆包”必须先于“笔记本”命中。
const PRODUCT_TYPE_RULES = Object.freeze([
  {
    type: '电脑包',
    keywords: [
      '笔记本内胆包',
      '笔记本电脑包',
      '电脑双肩包',
      '内胆包',
      '电脑包',
    ],
  },
  {
    type: '笔记本电脑',
    keywords: ['笔记本电脑', '轻薄本', '游戏本', '笔记本'],
    categories: ['数码'],
  },
  {
    type: '耳机',
    keywords: [
      '头戴式耳机',
      '蓝牙耳机',
      '降噪耳机',
      '英语听力耳机',
      '耳机',
      '耳麦',
    ],
    excludeKeywords: [
      '耳机套',
      '耳机盒',
      '耳机收纳',
      '耳罩',
      '耳帽',
      '耳机线',
      '耳机架',
    ],
  },
  {
    type: '显示器',
    keywords: ['显示器', '显示屏'],
    excludeKeywords: ['显示器支架', '显示器挂灯', '显示器保护膜'],
  },
  {
    type: '键盘',
    keywords: ['机械键盘', '键盘'],
    excludeKeywords: ['键盘膜', '键帽', '键盘托', '键盘架', '键盘清洁'],
  },
  {
    type: '鼠标',
    keywords: ['鼠标'],
    excludeKeywords: ['鼠标垫', '鼠标贴', '鼠标线'],
  },
  { type: '台灯', keywords: ['护眼台灯', '阅读灯', '台灯'] },
  { type: '保温杯', keywords: ['保温杯', '水杯'] },
  {
    type: '手机',
    keywords: ['智能手机', '手机'],
    excludeKeywords: ['手机壳', '手机膜', '手机支架', '手机挂绳', '手机指环'],
  },
  {
    type: '平板电脑',
    keywords: ['平板电脑', '平板'],
    excludeKeywords: ['平板壳', '平板膜', '平板支架', '平板保护套'],
  },
  {
    type: '充电器',
    keywords: ['充电器', '充电头'],
    excludeKeywords: ['充电器线', '充电线', '数据线'],
  },
  {
    type: '备考资料',
    keywords: [
      '考研数学',
      '考研英语',
      '考研政治',
      '英语四级',
      '英语六级',
      '复习全书',
      '词汇书',
      '考研',
      '四级',
      '六级',
      '真题',
      '备考',
      '教辅',
    ],
  },
  {
    type: '教材',
    keywords: ['高等数学', '线性代数', '概率论', '大学英语', '教材', '课本'],
  },
  { type: '小说', keywords: ['小说', '名著', '文学'] },
  { type: '坚果', keywords: ['坚果'] },
  { type: '辣椒酱', keywords: ['辣椒酱'] },
  { type: '咖啡', keywords: ['咖啡'] },
  { type: '速食食品', keywords: ['速食', '方便面', '牛肉面', '自热'] },
  { type: '卫衣', keywords: ['卫衣'] },
  { type: 'T 恤', keywords: ['t恤', '短袖'] },
  { type: '衬衫', keywords: ['衬衫'] },
  { type: '帽子', keywords: ['棒球帽', '鸭舌帽', '帽子'] },
  {
    type: '鞋',
    keywords: ['运动鞋', '帆布鞋', '跑鞋', '鞋'],
    excludeKeywords: ['鞋带', '鞋垫', '鞋盒', '鞋刷'],
  },
  { type: '贴纸', keywords: ['贴纸'] },
  { type: '笔记本', keywords: ['a4笔记本', '笔记本'], categories: ['文具'] },
  {
    type: '笔',
    keywords: ['中性笔', '圆珠笔', '签字笔', '钢笔', '铅笔', '马克笔'],
  },
  { type: '便签', keywords: ['便签', '便利贴'] },
  { type: '文件夹', keywords: ['文件夹'] },
])

// 只收录会影响购买选择的稳定特征，不使用卖家、价格和库存生成标签。
const ATTRIBUTE_RULES = Object.freeze([
  { tag: '降噪', keywords: ['降噪'] },
  { tag: '蓝牙', keywords: ['蓝牙'] },
  { tag: '无线', keywords: ['无线'] },
  { tag: '有线', keywords: ['有线'] },
  { tag: '头戴式', keywords: ['头戴式'] },
  { tag: '入耳式', keywords: ['入耳式'] },
  { tag: '英语听力', keywords: ['英语听力'] },
  { tag: '机械', keywords: ['机械'] },
  { tag: '护眼', keywords: ['护眼'] },
  { tag: '14 寸', keywords: ['14寸'] },
  { tag: '27 寸', keywords: ['27寸'] },
  { tag: '2K', keywords: ['2k'] },
  { tag: '87 键', keywords: ['87键'] },
  { tag: '考研', keywords: ['考研'] },
  { tag: '四级', keywords: ['四级'] },
  { tag: '六级', keywords: ['六级'] },
  { tag: '真题', keywords: ['真题'] },
  { tag: '词汇', keywords: ['词汇'] },
  { tag: '速干', keywords: ['速干'] },
  { tag: '加绒', keywords: ['加绒'] },
  { tag: '精装', keywords: ['精装'] },
  { tag: '二手', keywords: ['二手'] },
  { tag: '九成新', keywords: ['九成新'] },
  { tag: '全新', keywords: ['全新'] },
  { tag: 'A4', keywords: ['a4'] },
  { tag: '3 本装', keywords: ['3本装'] },
  { tag: '500ml', keywords: ['500ml'] },
  { tag: '10 包', keywords: ['10包'] },
  { tag: '防泼水', keywords: ['防泼水'] },
  { tag: '校徽', keywords: ['校徽'] },
])

const MAX_GROUP_TAGS = 6

function identifier(value) {
  if (value === undefined || value === null) return ''
  return String(value).trim()
}

function normalizeText(value) {
  if (typeof value !== 'string') return ''
  return value.replace(/\s+/g, ' ').trim()
}

function compactText(value) {
  return normalizeText(value).toLowerCase().replace(/\s+/g, '')
}

function productIdentity(product, index) {
  const id = identifier(product?.id)
  return id ? `id:${id}` : `index:${index}`
}

function findProductType(title, category) {
  const searchableTitle = compactText(title)
  const categoryKey = compactText(category)
  if (!searchableTitle) return null

  const matches = []
  PRODUCT_TYPE_RULES.forEach((rule, ruleIndex) => {
    if (rule.categories && !rule.categories.includes(categoryKey)) return

    const ruleMatches = []
    rule.keywords.forEach((keyword) => {
      const searchableKeyword = compactText(keyword)
      const keywordIndex = searchableTitle.indexOf(searchableKeyword)
      if (!searchableKeyword || keywordIndex < 0) return

      ruleMatches.push({
        type: rule.type,
        keywordLength: searchableKeyword.length,
        keywordIndex,
        keywordEnd: keywordIndex + searchableKeyword.length,
        ruleIndex,
      })
    })
    if (ruleMatches.length === 0) return

    // 只有配件词与标题最前面的主商品词重叠时才排除。
    // 例如“耳机收纳盒”是配件，而“耳机含收纳盒”仍应识别为主商品。
    const firstKeywordIndex = Math.min(
      ...ruleMatches.map((match) => match.keywordIndex),
    )
    const firstKeywordEnd = Math.max(
      ...ruleMatches
        .filter((match) => match.keywordIndex === firstKeywordIndex)
        .map((match) => match.keywordEnd),
    )
    const excluded = rule.excludeKeywords?.some((keyword) => {
      const excludedIndex = searchableTitle.indexOf(compactText(keyword))
      return excludedIndex >= 0 && excludedIndex < firstKeywordEnd
    })
    if (excluded) return

    matches.push(...ruleMatches)
  })

  matches.sort(
    (left, right) =>
      right.keywordLength - left.keywordLength ||
      left.ruleIndex - right.ruleIndex,
  )

  return matches[0]?.type ?? null
}

function groupingDescriptor(product, index) {
  const spuId = identifier(product?.spuId)
  const title = normalizeProductTitle(product?.title, '')
  const category = normalizeText(product?.category)
  const categoryKey = compactText(category)
  const type = findProductType(title, category)

  if (spuId) {
    return {
      key: `spu:${spuId}`,
      mode: 'spu',
      type,
      title: type,
    }
  }

  if (type) {
    return {
      key: `type:${type}`,
      mode: 'type',
      type,
      title: type,
    }
  }

  // 宽泛分类不足以证明购买意图相同；只有具体分类才作为保守回退。
  if (categoryKey && !GENERIC_CATEGORY_KEYS.has(categoryKey)) {
    return {
      key: `category:${categoryKey}`,
      mode: 'category',
      type: null,
      title: category,
    }
  }

  return {
    key: `product:${productIdentity(product, index)}`,
    mode: 'product',
    type: null,
    title: title || '未命名商品',
  }
}

function longestCommonPrefix(values) {
  if (values.length === 0) return ''
  let prefix = values[0]

  for (let index = 1; index < values.length; index += 1) {
    const value = values[index]
    while (prefix && !value.startsWith(prefix)) {
      prefix = prefix.slice(0, -1)
    }
    if (!prefix) return ''
  }

  return prefix
}

function resolveGroupTitle(group) {
  if (group.groupingMode !== 'spu') return group.title

  if (group.type) return group.type

  const titles = group.entries
    .map(({ product }) => normalizeProductTitle(product?.title, ''))
    .filter(Boolean)

  if (titles.length === 1) return titles[0]

  // 同一商品编号下没有明确类型时，用共同标题前缀作为稳定名称。
  const prefix = longestCommonPrefix(titles.map(compactText))
  return prefix.length >= 2 ? prefix : '同款商品'
}

function collectTags(products, type) {
  const presentTags = new Set()

  products.forEach((product) => {
    const searchableTitle = compactText(
      normalizeProductTitle(product?.title, ''),
    )
    if (!searchableTitle) return

    ATTRIBUTE_RULES.forEach((rule) => {
      if (rule.tag === type) return
      if (
        rule.keywords.some((keyword) =>
          searchableTitle.includes(compactText(keyword)),
        )
      ) {
        presentTags.add(rule.tag)
      }
    })
  })

  return ATTRIBUTE_RULES.map((rule) => rule.tag)
    .filter((tag) => presentTags.has(tag))
    .slice(0, MAX_GROUP_TAGS)
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
 * - all: 当前会话可管理组内全部商品
 * - partial: 只能管理其中一部分（例如卖家只拥有部分商品）
 * - none: 只能浏览，不能管理
 *
 * @param {Array<object>} products 商品列表
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
export function summarizeGroupPermissions(products = [], session = null) {
  const validProducts = Array.isArray(products)
    ? products.filter((product) => product && typeof product === 'object')
    : []
  const totalCount = validProducts.length
  const manageableCount = validProducts.filter((product) =>
    canManageProduct(product, session),
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
 * 构造按相似购买意图聚合的商品展示模型。
 *
 * 分组优先级：
 * 1. 显式 spuId：明确同款；
 * 2. 标题中的标准商品类型：如不同型号的耳机归为“耳机”；
 * 3. 非宽泛的具体分类；
 * 4. 无法可靠识别时保持为独立商品组。
 *
 * 分组不改变商品身份，每条商品仍独立计算价格、库存、状态和权限。
 *
 * @param {Array<object> | null | undefined} products 商品列表
 * @param {object | null | undefined} session 当前会话
 * @returns {Array<object>} 聚合展示组
 */
export function buildProductGroups(products, session = null) {
  if (!Array.isArray(products)) return []

  const groupsByKey = new Map()
  const seenProductKeys = new Set()

  products.forEach((product, index) => {
    if (!product || typeof product !== 'object') return

    const productKey = productIdentity(product, index)
    if (seenProductKeys.has(productKey)) return
    seenProductKeys.add(productKey)

    const descriptor = groupingDescriptor(product, index)
    let group = groupsByKey.get(descriptor.key)

    if (!group) {
      group = {
        key: descriptor.key,
        groupingMode: descriptor.mode,
        type: descriptor.type,
        title: descriptor.title,
        entries: [],
      }
      groupsByKey.set(descriptor.key, group)
    }

    group.entries.push({ product, productKey })
  })

  return [...groupsByKey.values()].map((group) => {
    const products = group.entries.map((entry) => entry.product)
    const sellerIds = new Set()
    const sellerKeys = new Set()
    let priceMin = null
    let priceMax = null
    let totalStock = 0
    let onSaleCount = 0
    let availableCount = 0
    let offShelfCount = 0
    let soldOutCount = 0
    let missingSellerIdCount = 0

    group.entries.forEach(({ product, productKey }) => {
      const sellerId = identifier(product.sellerId)
      if (sellerId) {
        sellerIds.add(sellerId)
        sellerKeys.add(`seller:${sellerId}`)
      } else {
        missingSellerIdCount += 1
        // 没有卖家编号时不能因为展示名称相同就合并卖家身份。
        sellerKeys.add(`unidentified:${productKey}`)
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

    const productCount = products.length
    const sellerCount = sellerKeys.size

    return {
      key: group.key,
      groupingMode: group.groupingMode,
      type: group.type,
      title: resolveGroupTitle(group),
      tags: collectTags(products, group.type),
      products,
      productCount,
      sellerCount,
      identifiedSellerCount: sellerIds.size,
      missingSellerIdCount,
      priceMin,
      priceMax,
      totalStock,
      onSaleCount,
      availableCount,
      offShelfCount,
      soldOutCount,
      permissions: summarizeGroupPermissions(products, session),
    }
  })
}
