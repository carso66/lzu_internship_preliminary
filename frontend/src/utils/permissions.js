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

/**
 * 仅用于本地演示的固定会话。
 *
 * 这不是登录认证结果，也不能替代服务端授权。真实项目必须由后端从
 * token / session 推导当前用户身份，并在每次写操作时校验商品归属。
 */
export const DEMO_SESSION = Object.freeze({
  id: 'demo-admin',
  name: '演示管理员',
  role: USER_ROLES.admin,
  isDemo: true,
})

function hasIdentifier(value) {
  return value !== undefined && value !== null && String(value).trim() !== ''
}

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
