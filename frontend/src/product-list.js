import { el, fragment } from './dom.js';
import { formatCnySafe } from './money.js';
import { actionLabel, statusMeta, toggleTarget } from './product-status.js';
import { displayTitle } from './text.js';

/**
 * 商品列表（L0-1 + L0-4 + L0-5）。
 * 每条显示：标题、价格（¥xx.xx）、库存、分类、状态徽标（在售 / 已下架）；
 * 库存为 0 的商品额外挂「缺货」标签，并把整行灰化。
 *
 * L0-4 的上下架按钮：
 *  - 每行 tr 上带 data-id、按钮上带 data-id / data-action / data-label，
 *    main.js 用 tbody 的事件委托读它们（列表重渲染后监听不会丢，也不用给 N 个按钮各挂一次）
 *  - 徽标文案和按钮文案都来自 product-status.js，保证「在售 <-> 下架」永远对得上
 *  - pendingIds 里的商品正有一个 PATCH 在飞：按钮渲染成「处理中…」且 disabled，
 *    重渲染也保持禁用，连点不会发出第二个请求
 *
 * L0-5 的筛选反馈：keyword 有值时 summary 改成「搜索「x」：命中 n 件商品」，
 * 让用户能看出「列表变短是因为筛选」，而不是以为数据丢了。
 */

const COLUMNS = [
  { label: 'ID', className: 'w-id' },
  { label: '标题', className: 'w-title' },
  { label: '价格', className: 'w-price' },
  { label: '库存', className: 'w-stock' },
  { label: '分类', className: 'w-category' },
  { label: '状态', className: 'w-status' },
  { label: '操作', className: 'w-actions' },
];

function statusBadge(status) {
  const { text, className } = statusMeta(status);
  return el('span', { className, textContent: text });
}

/**
 * 标题单元格：外层 td 定宽，内层 span 负责省略号（td 上的 text-overflow 不可靠）。
 * tooltip 用「原文」：清洗只影响眼睛看到的字，数据本身一个字都不丢，
 * 鼠标悬停还能看到接口里到底存的什么。
 */
function titleCell(item) {
  return el('td', { className: 'col-title' }, [
    el('span', {
      className: 'ellipsis',
      title: String(item.title ?? ''),
      textContent: displayTitle(item.title),
    }),
  ]);
}

/** 库存单元格：0 件时多挂一个「缺货」标签 */
function stockCell(stock) {
  const children = [el('span', { className: 'stock-num', textContent: String(stock) })];
  if (stock === 0) {
    children.push(el('span', { className: 'badge badge-out', textContent: '缺货' }));
  }
  return el('td', { className: 'col-stock' }, children);
}

/**
 * 操作单元格（L0-4 上下架 + L1-6 购买入口）。
 * 在售显示「下架」，已下架显示「上架」；未知状态不给上下架按钮（只留占位），
 * 避免发出一个后端一定拒绝的请求。
 *
 * 「购买」按钮**不**按状态 / 库存禁用：L1-6 要求「超过库存」「已下架」时
 * 显示后端返回的 message，前端提前禁用就把这两条验收项测掉了。
 *
 * data-label 存的是「真正该显示的文案」：请求进行中按钮会临时变成「处理中…」，
 * 失败或重渲染时靠它恢复成「上架 / 下架」，不用再算一遍。
 */
function actionCell(item, pendingIds) {
  const id = String(item.id);
  const buttons = [
    el('button', {
      type: 'button',
      className: 'btn-row btn-buy',
      textContent: '购买',
      dataset: { id, action: 'buy' },
    }),
  ];

  const target = toggleTarget(item.status);
  if (target) {
    const label = actionLabel(item.status);
    const busy = pendingIds.has(item.id);
    buttons.push(
      el('button', {
        type: 'button',
        className: busy ? 'btn-row is-busy' : 'btn-row',
        textContent: busy ? '处理中…' : label,
        disabled: busy,
        dataset: { id, action: 'toggle-status', label, target },
      }),
    );
  } else {
    buttons.push(el('span', { className: 'text-muted', textContent: '—' }));
  }

  return el('td', { className: 'col-actions' }, [el('span', { className: 'action-group' }, buttons)]);
}

function productRow(item, highlightId, pendingIds) {
  const soldOut = item.stock === 0;
  const isNew = highlightId > 0 && item.id === highlightId;
  // 刚发布的那行单独给个 is-new：L0-3 的「列表里能找到刚发布的商品」靠它一眼看见
  const className = [soldOut ? 'is-sold-out' : '', isNew ? 'is-new' : ''].filter(Boolean).join(' ');
  return el('tr', { className, dataset: { id: String(item.id) } }, [
    el('td', { className: 'col-id', textContent: String(item.id) }),
    titleCell(item),
    // 视图层用 Safe 版本：某个字段缺失时显示 ¥--，而不是让整个列表渲染崩掉
    el('td', { className: 'col-price', textContent: formatCnySafe(item.price) }),
    stockCell(item.stock),
    el('td', { className: 'col-category', textContent: item.category }),
    el('td', { className: 'col-status' }, [statusBadge(item.status)]),
    actionCell(item, pendingIds),
  ]);
}

/**
 * 生成列表视图（summary + table），返回 DocumentFragment。
 * 数据全部来自 GET /api/products，没有任何写死的商品。
 * highlightId：刚发布的商品 id，命中时那一行会带 is-new 样式。
 * keyword：当前生效的搜索词，只用于 summary 文案（筛选本身是接口做的）。
 */
export function productList(items, { highlightId = 0, pendingIds = new Set(), keyword = '' } = {}) {
  const soldOutCount = items.filter((item) => item.stock === 0).length;
  const summaryText = keyword
    ? `搜索「${keyword}」：命中 ${items.length} 件商品，其中 ${soldOutCount} 件缺货`
    : `共 ${items.length} 件商品，其中 ${soldOutCount} 件缺货`;
  const summary = el('p', { className: 'list-summary', textContent: summaryText });

  const table = el('table', { className: 'product-table' }, [
    el('colgroup', {}, COLUMNS.map(({ className }) => el('col', { className }))),
    el('thead', {}, [
      el('tr', {}, COLUMNS.map(({ label, className }) => el('th', { className, textContent: label }))),
    ]),
    el('tbody', {}, items.map((item) => productRow(item, highlightId, pendingIds))),
  ]);

  return fragment([summary, table]);
}
