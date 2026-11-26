/**
 * 订单展示相关的纯函数（状态文案、时间格式化）。
 *
 * 单独放这里而不是写在组件里，是为了能直接单测：
 * 状态映射必须处理「后端以后加了新枚举值」的情况，不能只认 created / paid 两个。
 */

/** 后端订单状态枚举 → 中文。README L1-7 明确要求界面显示中文，不能露 paid / created 原文。 */
export const ORDER_STATUS_TEXT = Object.freeze({
  created: '待支付',
  paid: '已支付',
})

/**
 * 状态中文文案。未知枚举值走兜底，不显示 undefined，也不假装成已知状态。
 * @param {string} status
 * @returns {string}
 */
export function orderStatusText(status) {
  const key = String(status ?? '').trim()
  if (!key) return '未知状态'
  return ORDER_STATUS_TEXT[key] ?? `未知状态（${key}）`
}

/** 未知状态用中性灰，别用「已支付」的绿或「已下架」的灰混掉 */
export function orderStatusClass(status) {
  if (status === 'paid') return 'badge-on'
  if (status === 'created') return 'badge-pending'
  return 'badge-off'
}

/**
 * 后端把 LocalDateTime 序列化成不带时区的 ISO 串（`2026-11-25T10:12:33`），
 * 这种字符串 JS 会按本地时间解析，所以直接取本地字段展示，不做时区换算。
 * @param {string} value
 * @returns {string} `2026-11-25 10:12`，非法值兜底成 `—`
 */
export function formatDateTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = (n) => String(n).padStart(2, '0')
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
    `${pad(date.getHours())}:${pad(date.getMinutes())}`
  )
}
