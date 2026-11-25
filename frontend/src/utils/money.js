/**
 * 将用户输入的“元”金额转换为整数“分”。
 *
 * 不能使用 parseInt：
 *   parseInt('19.9', 10) 得到 19，小数部分会被直接丢掉；
 *   parseInt('0.01', 10) 甚至会得到 0。
 *
 * 也不能只做 `yuan * 100` 而不四舍五入：
 *   JavaScript 使用二进制浮点数，例如 19.9 * 100 可能得到
 *   1989.9999999999998 这样的结果，直接提交会变成非整数分。
 *   因此先乘 100，再用 Math.round 修正浮点误差并取整。
 *
 * 本函数只接受大于 0、最多两位小数的有效金额；负数、零、
 * 科学计数法、千分位和空值都会抛出明确错误，避免脏数据被静默
 * 转换后提交到后端。
 *
 * @param {string | number} yuan 用户输入的元金额
 * @returns {number} 整数分
 */
export function yuanToFen(yuan) {
  if (yuan === null || yuan === undefined) {
    throw new Error('金额不能为空')
  }

  if (typeof yuan !== 'string' && typeof yuan !== 'number') {
    throw new Error('金额必须是大于 0 的数字，最多两位小数')
  }

  const raw = String(yuan).trim()
  if (raw === '') {
    throw new Error('金额不能为空')
  }

  if (!/^[0-9]+(?:\.[0-9]{1,2})?$/.test(raw)) {
    throw new Error('金额必须是大于 0 的数字，最多两位小数')
  }

  const amount = Number(raw)
  if (!Number.isFinite(amount) || !(amount > 0)) {
    throw new Error('金额必须大于 0')
  }

  const fen = Math.round(amount * 100)
  if (!Number.isSafeInteger(fen)) {
    throw new Error('金额超出可处理范围')
  }

  return fen
}

/**
 * 将整数分转换为保留两位小数的元字符串，用于展示。
 * 非有限数或负数按 0 兜底，避免脏数据导致页面出现 NaN 或负价格。
 *
 * @param {number | string} fen 金额（分）
 * @returns {string} 例如 199 -> '1.99'
 */
export function fenToYuan(fen) {
  const amount = Number(fen)
  const safeAmount = Number.isFinite(amount) && amount >= 0 ? amount : 0
  return (safeAmount / 100).toFixed(2)
}

/**
 * 将金额（分）格式化为带人民币符号的展示字符串。
 *
 * @param {number | string} fen 金额（分）
 * @returns {string} 例如 199 -> '¥1.99'
 */
export function formatPrice(fen) {
  return `¥${fenToYuan(fen)}`
}
