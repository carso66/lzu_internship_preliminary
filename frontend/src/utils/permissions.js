export const USER_ROLES = Object.freeze({
  visitor: 'visitor',
  buyer: 'buyer',
  seller: 'seller',
  admin: 'admin',
})

export const ROLE_LABELS = Object.freeze({
  [USER_ROLES.visitor]: '游客',
  [USER_ROLES.buyer]: '买家',
  [USER_ROLES.seller]: '卖家',
  [USER_ROLES.admin]: '管理员',
})

function hasIdentifier(value) {
  return value !== undefined && value !== null && String(value).trim() !== ''
}

const VALID_ROLES = new Set(Object.values(USER_ROLES))

/**
 * 构造仅用于本地权限演示的会话。
 *
 * 卖家会话必须携带 sellerId 才能命中商品归属；未填写编号时保持空 ID，
 * 从而由 canManageProduct() 按失败关闭处理。未知角色降级为游客。
 * 该会话不是登录认证结果，也不能替代服务端授权。
 *
 * @param {string} role 演示角色
 * @param {string | number | null | undefined} sellerId 卖家用户编号
 * @returns {Readonly<{id: string, name: string, role: string, isDemo: true}>}
 */
export function createDemoSession(role = USER_ROLES.admin, sellerId = '') {
  const normalizedRole = VALID_ROLES.has(role) ? role : USER_ROLES.visitor
  const normalizedSellerId = hasIdentifier(sellerId)
    ? String(sellerId).trim()
    : ''
  const isSeller = normalizedRole === USER_ROLES.seller

  return Object.freeze({
    id: isSeller ? normalizedSellerId : `demo-${normalizedRole}`,
    name: `演示${ROLE_LABELS[normalizedRole]}`,
    role: normalizedRole,
    isDemo: true,
  })
}

/**
 * 默认进入管理员演示，方便首次打开页面时查看全部管理状态。
 */
export const DEMO_SESSION = createDemoSession(USER_ROLES.admin)

/**
 * 判断当前会话是否有权管理商品。
 *
 * - 管理员可以管理全部商品；
 * - 卖家只能管理 sellerId 与当前用户 ID 完全一致的商品；
 * - 游客、买家、缺失会话、缺失 sellerId 或仅有 sellerName 时均拒绝。
 *
 * 这里只负责前端体验控制。真正安全边界必须在服务端再次执行同样的
 * 归属校验，不能信任前端传入的 sellerId 或角色。
 *
 * @param {object | null | undefined} product 商品数据
 * @param {object | null | undefined} session 当前会话
 * @returns {boolean}
 */
export function canManageProduct(product, session) {
  if (!session || !hasIdentifier(session.id)) return false

  if (session.role === USER_ROLES.admin) return true
  if (session.role !== USER_ROLES.seller) return false
  if (!product || !hasIdentifier(product.sellerId)) return false

  return String(product.sellerId) === String(session.id)
}
