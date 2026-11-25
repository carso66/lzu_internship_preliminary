/**
 * 订单状态的单一事实来源（L1-7）。
 *
 * 接口给的是英文枚举 created / paid，界面上必须显示中文「待支付 / 已支付」，
 * 所以这里集中做一次映射；和 product-status.js 一样，未知枚举有兜底文案，
 * 不会因为后端加了新状态就渲染出空白徽标。
 */

const ORDER_STATUS_STYLE = {
  created: { text: '待支付', className: 'badge badge-warn' },
  paid: { text: '已支付', className: 'badge badge-on' },
};
const UNKNOWN_ORDER_STATUS = { text: '未知状态', className: 'badge badge-unknown' };

/** 订单状态 -> { text, className } */
export function orderStatusMeta(status) {
  return ORDER_STATUS_STYLE[status] ?? UNKNOWN_ORDER_STATUS;
}

/** 订单状态 -> 中文文案（'created' -> '待支付'，'paid' -> '已支付'） */
export function orderStatusText(status) {
  return orderStatusMeta(status).text;
}
