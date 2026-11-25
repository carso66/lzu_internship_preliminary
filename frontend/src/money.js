/**
 * 金额工具：接口一律用「分」（整数），界面一律显示「元」（两位小数）。
 * 这里全部用整数/字符串运算，不出现 19.9 * 100 这类浮点乘法。
 */

// 归一化之后的合法形态：整数元，或最多两位小数（"12.5" / "12.50" / "12"）
const YUAN_PATTERN = /^\d+(\.\d{1,2})?$/;

/**
 * 把用户输入整理成标准写法。只做两件"显然正确"的事：
 *   '.5'  -> '0.5'   （省略整数部分）
 *   '12.' -> '12'    （输入到一半、结尾还挂着小数点）
 * 其它一律原样返回，交给 YUAN_PATTERN 判定，免得"帮忙"帮出静默改数据的坑。
 */
export function normalizeYuan(input) {
  const text = String(input ?? '').trim();
  if (/^\.\d{1,2}$/.test(text)) return `0${text}`;
  if (/^\d+\.$/.test(text)) return text.slice(0, -1);
  return text;
}

/**
 * 元 -> 分。输入非法时抛错，调用方负责显示提示。
 * yuanToCents('19.9') === 1990
 * yuanToCents('0.01') === 1
 * yuanToCents('.5')   === 50
 * yuanToCents('12.')  === 1200
 *
 * 注意：它只保证"换算正确"，不保证"业务合法"——0 元、负数元会算出 0 / 负数，
 * 价格必须 > 0 属于表单校验，用下面的 priceInputError()。
 */
export function yuanToCents(input) {
  const text = normalizeYuan(input);
  if (!YUAN_PATTERN.test(text)) {
    throw new Error('金额格式不正确：只能填数字，最多两位小数');
  }
  // 拆成「整数部分」和「小数部分」分别处理，避开浮点误差
  const [yuanPart, fractionPart = ''] = text.split('.');
  const fraction = fractionPart.padEnd(2, '0'); // "5" -> "50"
  const cents = Number(yuanPart) * 100 + Number(fraction);
  // 超过 2^53 分之后 Number 已经不精确了，宁可报错也不要悄悄给出错的金额
  if (!Number.isSafeInteger(cents)) {
    throw new Error('金额过大，请检查输入的位数');
  }
  return cents;
}

/**
 * 价格输入框的校验（L0-2 发布表单 / L1-9 表单校验直接用）。
 * 通过返回 ''，不通过返回给用户看的文案（由调用方渲染到对应字段下方）。
 *   priceInputError('')       === '价格不能为空'
 *   priceInputError('19.999') === '金额格式不正确：只能填数字，最多两位小数'
 *   priceInputError('0')      === '价格必须大于 0'
 *   priceInputError('19.9')   === ''
 */
export function priceInputError(input) {
  if (String(input ?? '').trim() === '') return '价格不能为空';
  let cents;
  try {
    cents = yuanToCents(input);
  } catch (error) {
    return error.message;
  }
  // 接口只收正整数分：0 元会被后端 400 打回来，前端先拦住，不要白发一次请求
  if (cents <= 0) return '价格必须大于 0';
  return '';
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

/**
 * 视图层用的兜底版本：字段缺失 / 脏数据时显示占位符，而不是抛错。
 * 为什么需要：一条商品的 price 是 undefined，formatCny 会抛，整个列表渲染直接崩、
 * 页面只剩一行错误提示——为了一个坏字段把其余 23 条都丢掉不划算。
 * formatCnySafe(undefined) === '¥--'，formatCnySafe(null) === '¥--'
 * formatCnySafe(1250)      === '¥12.50'
 */
export function formatCnySafe(cents, placeholder = '¥--') {
  if (cents === null || cents === undefined || cents === '') return placeholder;
  const value = Number(cents);
  if (!Number.isFinite(value)) return placeholder;
  return formatCny(value);
}

/**
 * 单价 × 数量 = 总价（全部是整数分，不经过浮点）。
 * L1-8 的验收要求「前端预估总价必须等于服务端返回的 totalAmount」：
 * 服务端算的是 price * quantity，两边都只用整数乘法，所以结果必然一致。
 * multiplyCents(1999, 2) === 3998   -> ¥39.98
 * multiplyCents(1, 3)    === 3      -> ¥0.03
 * 参数不合法时抛错（调用方负责把提示显示到界面上），不返回一个看起来正常的错数。
 */
export function multiplyCents(unitCents, quantity) {
  // 先挡掉 null / undefined / 空串：Number(null) 是 0，会算出一个看起来正常的 ¥0.00
  if (unitCents === null || unitCents === undefined || unitCents === '') {
    throw new Error('单价必须是整数分');
  }
  const unit = Number(unitCents);
  const count = Number(quantity);
  if (!Number.isInteger(unit) || unit < 0) throw new Error('单价必须是整数分');
  if (!Number.isInteger(count) || count <= 0) throw new Error('数量必须是正整数');
  const total = unit * count;
  // 超过 2^53 分之后 Number 已经不精确了，宁可报错也不要悄悄给出错的金额
  if (!Number.isSafeInteger(total)) throw new Error('金额过大，请检查数量');
  return total;
}
