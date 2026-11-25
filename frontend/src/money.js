/**
 * 金额工具：接口一律用「分」（整数），界面一律显示「元」（两位小数）。
 * 这里全部用整数/字符串运算，不出现 19.9 * 100 这类浮点乘法。
 */

// 允许的输入形态：整数元，或最多两位小数（"12.5" / "12.50" / "12"）
const YUAN_PATTERN = /^\d+(\.\d{1,2})?$/;

/**
 * 元 -> 分。输入非法时抛错，调用方负责显示提示。
 * yuanToCents('19.9') === 1990
 * yuanToCents('0.01') === 1
 */
export function yuanToCents(input) {
  const text = String(input ?? '').trim();
  if (!YUAN_PATTERN.test(text)) {
    throw new Error('金额格式不正确：只能填数字，最多两位小数');
  }
  // 拆成「整数部分」和「小数部分」分别处理，避开浮点误差
  const [yuanPart, fractionPart = ''] = text.split('.');
  const fraction = fractionPart.padEnd(2, '0'); // "5" -> "50"
  return Number(yuanPart) * 100 + Number(fraction);
}

/**
 * 分 -> 元的字符串，固定两位小数（不带 ¥）。
 * centsToYuan(1990) === '19.90'
 * centsToYuan(1) === '0.01'
 */
export function centsToYuan(cents) {
  const value = Number(cents);
  if (!Number.isFinite(value)) {
    throw new Error('金额必须是数字（单位：分）');
  }
  const sign = value < 0 ? '-' : '';
  const abs = Math.abs(Math.round(value));
  const yuan = Math.floor(abs / 100);
  const fraction = String(abs % 100).padStart(2, '0');
  return `${sign}${yuan}.${fraction}`;
}

/**
 * 分 -> 界面展示的金额，带 ¥ 和两位小数。
 * formatCny(1250) === '¥12.50'
 */
export function formatCny(cents) {
  return `¥${centsToYuan(cents)}`;
}
