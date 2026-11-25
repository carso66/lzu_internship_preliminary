import { el, fragment } from './dom.js';
import { formatCnySafe } from './money.js';
import { orderStatusMeta } from './order-status.js';
import { displayTitle } from './text.js';

/**
 * 订单列表（L1-7）。
 * 每条订单显示五项：订单号、商品名、数量、金额、状态（中文）。
 * 金额一律 ¥xx.xx（单位是分，展示时换元）；状态只在 order-status.js 里映射一次。
 *
 * 和商品列表一样：tr 上带 data-order-id，方便「刚下的单」高亮，也方便以后做支付按钮的委托。
 */

const COLUMNS = [
  { label: '订单号', className: 'w-order-no' },
  { label: '商品', className: 'w-order-title' },
  { label: '数量', className: 'w-order-qty' },
  { label: '金额', className: 'w-order-amount' },
  { label: '状态', className: 'w-order-status' },
  { label: '操作', className: 'w-order-actions' },
];

function statusBadge(status) {
  const { text, className } = orderStatusMeta(status);
  return el('span', { className, textContent: text });
}

/**
 * 操作列（L2-10）：
 *  - 正在轮询：按钮「支付中…」+ disabled（不能连点，也不会重复发起支付）；
 *  - 本次会话里已经支付成功：按钮「已支付」+ **永久** disabled；
 *  - 其余：按钮「去支付」。
 *
 * 为什么服务端本来就 paid 的订单（如种子数据）还留着可点的「去支付」：
 * README 最后一条验收是「对已支付订单点支付 → 显示后端返回的『订单已支付，请勿重复支付』」，
 * 这是唯一能触发它的入口。徽标已经写着「已支付」，不会误导。
 */
function payCell(order, payingIds, settledIds) {
  const orderId = Number(order.id);
  const paying = payingIds.has(orderId);
  const settled = settledIds.has(orderId);

  if (settled) {
    return el('td', { className: 'col-order-actions' }, [
      el('button', {
        type: 'button',
        className: 'btn-row',
        textContent: '已支付',
        disabled: true,
        dataset: { orderId: String(orderId), action: 'pay' },
      }),
    ]);
  }

  return el('td', { className: 'col-order-actions' }, [
    el('button', {
      type: 'button',
      className: paying ? 'btn-row is-busy' : 'btn-row btn-buy',
      textContent: paying ? '支付中…' : '去支付',
      disabled: paying,
      dataset: { orderId: String(orderId), action: 'pay' },
      title: order.status === 'paid' ? '该订单已支付，点它会显示后端的重复支付校验' : '选择支付方式并付款',
    }),
  ]);
}

function orderRow(order, highlightId, payingIds, settledIds) {
  const isNew = highlightId > 0 && order.id === highlightId;
  return el('tr', { className: isNew ? 'is-new' : '', dataset: { orderId: String(order.id) } }, [
    el('td', { className: 'col-order-no', textContent: String(order.orderNo ?? `#${order.id}`) }),
    // 订单里的商品名是下单时的快照，同样走展示层清洗 + 单行省略
    el('td', { className: 'col-order-title' }, [
      el('span', {
        className: 'ellipsis',
        title: String(order.productTitle ?? ''),
        textContent: displayTitle(order.productTitle),
      }),
    ]),
    el('td', { className: 'col-order-qty', textContent: String(order.quantity ?? '--') }),
    // Safe 版本：金额字段缺失时显示 ¥--，不让一条坏数据把整张订单表打崩
    el('td', { className: 'col-order-amount', textContent: formatCnySafe(order.totalAmount) }),
    el('td', { className: 'col-order-status' }, [statusBadge(order.status)]),
    payCell(order, payingIds, settledIds),
  ]);
}

/**
 * 生成订单列表视图（summary + table），返回 DocumentFragment。
 * highlightId：刚下单成功的订单 id，命中的那一行带 is-new 样式。
 * payingIds / settledIds：支付中的订单、本会话支付成功的订单（决定按钮文案与禁用）。
 */
export function orderList(
  orders,
  { highlightId = 0, payingIds = new Set(), settledIds = new Set() } = {},
) {
  const pendingCount = orders.filter((order) => order.status === 'created').length;
  const summary = el('p', {
    className: 'list-summary',
    textContent: `共 ${orders.length} 笔订单，其中 ${pendingCount} 笔待支付`,
  });

  const table = el('table', { className: 'product-table order-table' }, [
    el('colgroup', {}, COLUMNS.map(({ className }) => el('col', { className }))),
    el('thead', {}, [
      el('tr', {}, COLUMNS.map(({ label, className }) => el('th', { className, textContent: label }))),
    ]),
    el('tbody', {}, orders.map((order) => orderRow(order, highlightId, payingIds, settledIds))),
  ]);

  return fragment([summary, table]);
}
