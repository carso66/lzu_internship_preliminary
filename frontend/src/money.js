/**
 * 金额口径（全场唯一规则）
 *  - 接口里的钱永远是「分」整数；界面上一律 ¥xx.xx。
 *  - 用户输入「元」，提交前转「分」。
 *
 * 为什么用字符串解析而不是 Math.round(元 * 100)：
 * 19.99 * 100 在 IEEE 754 下是 1998.9999999999998，取整虽能修正，但
 * 更大金额或连续运算仍可能出现偏差。按小数位拆成整数相加，全程不碰浮点。
 */

const YUAN_RE = /^(\d+)(?:\.(\d{1,2}))?$/;

/** 元（字符串或数字）-> 分（整数）。不合规则抛 Error，供表单校验使用。 */
export function toFen(yuan) {
  const s = String(yuan ?? '').trim();
  const m = YUAN_RE.exec(s);
  if (!m) throw new Error('金额需为大于 0 的数字，且最多两位小数');
  const yuanPart = Number(m[1]);
  const centPart = Number((m[2] || '').padEnd(2, '0'));
  const fen = yuanPart * 100 + centPart;
  if (!Number.isSafeInteger(fen) || fen <= 0) throw new Error('金额需大于 0 且在安全范围内');
  return fen;
}

/** 分（整数）-> ¥xx.xx。整数拆分，杜绝浮点尾巴与 NaN。 */
export function formatYuan(fen) {
  const n = Number(fen);
  if (!Number.isFinite(n)) return '¥0.00';
  const sign = n < 0 ? '-' : '';
  const abs = Math.abs(Math.trunc(n));
  return `¥${sign}${Math.floor(abs / 100)}.${String(abs % 100).padStart(2, '0')}`;
}

/** 前端预估总价（分），与服务端 totalAmount 比对时用。 */
export function estimateTotal(unitPriceFen, quantity) {
  return unitPriceFen * quantity;
}
