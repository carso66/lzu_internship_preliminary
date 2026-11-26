/**
 * 金额换算（全场唯一口径）。
 *
 * - 接口里的钱永远是「分」，整数；
 * - 界面上永远是「元」，`¥xx.xx` 两位小数、带 ¥；
 * - 用户输入用「元」，提交前转成「分」。
 *
 * 全程整数运算：绝不出现 `19.9 * 100 = 1989.9999999999998` 这类浮点尾巴。
 */

/** 合法的「元」输入：123 / 12.5 / 12.50 / .5，最多两位小数，不接受负号、科学计数法 */
const YUAN_PATTERN = /^(?:\d+(?:\.\d{1,2})?|\.\d{1,2})$/

/**
 * 分 → 元字符串（不带 ¥）。非整数或非法值一律兜底成 `0.00`，保证界面不出现 NaN。
 * @param {number|string} fen 金额（分）
 * @returns {string} 例如 `12.50`
 */
export function fenToYuan(fen) {
  const value = Number(fen)
  if (!Number.isFinite(value)) return '0.00'
  const rounded = Math.round(value)
  const sign = rounded < 0 ? '-' : ''
  const abs = Math.abs(rounded)
  return `${sign}${Math.floor(abs / 100)}.${String(abs % 100).padStart(2, '0')}`
}

/**
 * 分 → `¥xx.xx`，列表/详情的唯一展示入口。
 * @param {number|string} fen 金额（分）
 * @returns {string} 例如 `¥12.50`
 */
export function formatPrice(fen) {
  return `¥${fenToYuan(fen)}`
}

/**
 * 元 → 分（整数）。输入不合法返回 `null`，由调用方给字段级错误提示。
 * 字符串按小数点切开、整数端 ×100、小数端左对齐补零，避免浮点乘法。
 * @param {string|number} input 用户输入的「元」
 * @returns {number|null} 整数分，例如 `19.9` → `1990`
 */
export function yuanToFen(input) {
  const text = String(input ?? '').trim()
  if (!YUAN_PATTERN.test(text)) return null
  const [yuanPart, centPart = ''] = text.split('.')
  const yuan = yuanPart === '' ? 0 : Number(yuanPart)
  const fen = yuan * 100 + Number(centPart.padEnd(2, '0'))
  return Number.isSafeInteger(fen) ? fen : null
}

/** 元 → 分，同时要求「必须是正数」，供发布表单校验用 */
export function yuanToPositiveFen(input) {
  const fen = yuanToFen(input)
  return fen !== null && fen > 0 ? fen : null
}

/**
 * 下单预估总价：单价（分）× 数量。
 *
 * 单位是「分」，两个整数相乘本来就不会有浮点误差，所以这里仍然只做整数运算；
 * 但入参必须自己保证是整数（数量来自输入框，是字符串），
 * 否则 `Number('2.5')` 这种会算出小数分，预估金额和服务端对不上。
 *
 * 注意 `Number(null)` 和 `Number('')` 都是 0：如果直接转数字，
 * 「商品价格还没加载出来」会被算成 0 元免费，所以先把这类空值挡掉。
 *
 * @param {number} unitPrice 单价（分）
 * @param {number|string} quantity 数量
 * @returns {number|null} 总价（分），任一侧非法一律 null
 */
export function multiplyFen(unitPrice, quantity) {
  if (unitPrice === null || unitPrice === undefined || unitPrice === '') return null
  if (quantity === null || quantity === undefined || quantity === '') return null
  const price = Number(unitPrice)
  const count = Number(quantity)
  if (!Number.isSafeInteger(price) || price < 0) return null
  if (!Number.isSafeInteger(count) || count < 0) return null
  const total = price * count
  return Number.isSafeInteger(total) ? total : null
}
