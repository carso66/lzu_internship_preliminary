/**
 * 商品状态的单一事实来源：徽标文案 / 样式、上下架按钮文案、按钮要发的目标状态。
 *
 * 抽出来的原因：L0-4 要求「在售的按钮写『下架』、已下架的按钮写『上架』」，
 * 也就是徽标和按钮必须由同一份映射推出来。两边各写一套三元表达式，
 * 迟早会出现「徽标说在售、按钮说上架」这种对不上的情况。
 *
 * 这里不碰 DOM，所以可以直接写单测（tests/product-status.test.js）。
 */

// 接口只约定 on / off；万一后端加了新枚举（offline、banned），列表不能出现空白徽标
const STATUS_STYLE = {
  on: { text: '在售', className: 'badge badge-on' },
  off: { text: '已下架', className: 'badge badge-off' },
};
const UNKNOWN_STATUS = { text: '未知状态', className: 'badge badge-unknown' };

/** 状态 -> { text, className }；未知枚举走兜底，不返回 undefined */
export function statusMeta(status) {
  return STATUS_STYLE[status] ?? UNKNOWN_STATUS;
}

/** 状态 -> 中文文案（'on' -> '在售'，'off' -> '已下架'） */
export function statusText(status) {
  return statusMeta(status).text;
}

/**
 * 点上下架按钮时该发过去的目标状态：在售 -> off，已下架 -> on。
 * 未知状态返回 ''，表示「这行不提供操作」——宁可少一个按钮，也不要发一个后端必拒的请求。
 */
export function toggleTarget(status) {
  if (status === 'on') return 'off';
  if (status === 'off') return 'on';
  return '';
}

/** 按钮文案：和在售状态相反（在售显示「下架」，已下架显示「上架」）；未知状态返回 '' */
export function actionLabel(status) {
  const target = toggleTarget(status);
  if (target === 'off') return '下架';
  if (target === 'on') return '上架';
  return '';
}
