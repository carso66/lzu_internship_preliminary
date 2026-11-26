// 接口里的钱永远是「分」的整数，界面永远是「元」的两位小数。
// 全项目只允许这两个函数做换算，别处不许出现 *100 或 /100。

export function fenToYuan(fen) {
  const n = Number(fen)
  if (!Number.isFinite(n)) return '—'
  return `¥${(n / 100).toFixed(2)}`
}

// 元 → 分。空、非数字、超过两位小数、换算后 <= 0 都算非法，返回 null 交给表单报错。
// 必须 Math.round：19.99 * 100 在浮点下是 1998.9999999999998。
export function yuanToFen(text) {
  const s = String(text ?? '').trim()
  if (!/^\d+(\.\d{1,2})?$/.test(s)) return null
  const fen = Math.round(Number(s) * 100)
  return fen > 0 ? fen : null
}
