/**
 * 校验购买数量是否为正整数，且不超过当前库存。
 *
 * HTML 的 min/max 只是输入提示，用户仍可手动输入越界值；
 * 因此提交前必须在页面和全局下单入口再次校验。
 *
 * @param {unknown} quantity 用户输入的数量
 * @param {unknown} stock 商品库存
 * @returns {string} 校验通过时返回空字符串
 */
function parseQuantity(value) {
  if (typeof value === 'number') {
    return Number.isSafeInteger(value) && value >= 1 ? value : null
  }

  if (typeof value === 'string') {
    const text = value.trim()
    // 只接受十进制正整数，避免 '1e2'、'0x10'、'+1'、'1.0' 这类
    // HTML number 不会产生、但手动传入时容易被误当成数量的字符串。
    if (!/^[0-9]+$/.test(text)) return null

    const parsed = Number(text)
    return Number.isSafeInteger(parsed) && parsed >= 1 ? parsed : null
  }

  return null
}

function parseStock(value) {
  if (typeof value === 'number') {
    return Number.isFinite(value) && value >= 0 ? Math.floor(value) : null
  }

  if (typeof value === 'string') {
    const text = value.trim()
    if (!/^(?:[0-9]+(?:\.[0-9]+)?)$/.test(text)) return null

    const parsed = Number(text)
    return Number.isFinite(parsed) && parsed >= 0 ? Math.floor(parsed) : null
  }

  return null
}

export function getQuantityError(quantity, stock) {
  const value = parseQuantity(quantity)
  if (value === null) {
    return '购买数量必须是正整数'
  }

  const availableStock = parseStock(stock)
  if (availableStock === null) {
    return '商品库存信息异常'
  }

  if (value > availableStock) {
    return `购买数量不能超过库存（${availableStock} 件）`
  }

  return ''
}
