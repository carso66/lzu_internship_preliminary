import { el } from './dom.js';
import { formatCny } from './money.js';
import { displayTitle } from './text.js';

/**
 * 商品列表（L0-1）。
 * 每条显示：标题、价格（¥xx.xx）、库存、分类、状态徽标（在售 / 已下架）；
 * 库存为 0 的商品额外挂「缺货」标签，并把整行灰化。
 */

// 状态枚举 -> 文案 + 样式。接口只约定 on / off，
// 但万一后端加了新枚举（如 offline、banned），列表不能出现空白徽标
const STATUS_STYLE = {
  on: { text: '在售', className: 'badge badge-on' },
  off: { text: '已下架', className: 'badge badge-off' },
};
const UNKNOWN_STATUS = { text: '未知状态', className: 'badge badge-unknown' };

const COLUMNS = [
  { key: 'id', label: 'ID', className: 'w-id' },
  { key: 'title', label: '标题', className: 'w-title' },
  { key: 'price', label: '价格', className: 'w-price' },
  { key: 'stock', label: '库存', className: 'w-stock' },
  { key: 'category', label: '分类', className: 'w-category' },
  { key: 'status', label: '状态', className: 'w-status' },
];

function statusBadge(status) {
  const { text, className } = STATUS_STYLE[status] ?? UNKNOWN_STATUS;
  return el('span', { className, textContent: text });
}

/** 标题单元格：外层 td 定宽，内层 span 负责省略号（td 上的 text-overflow 不可靠） */
function titleCell(title) {
  const text = displayTitle(title);
  return el('td', { className: 'col-title' }, [
    el('span', { className: 'ellipsis', title: text, textContent: text }),
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

function productRow(item) {
  const soldOut = item.stock === 0;
  return el('tr', { className: soldOut ? 'is-sold-out' : '' }, [
    el('td', { className: 'col-id', textContent: String(item.id) }),
    titleCell(item.title),
    el('td', { className: 'col-price', textContent: formatCny(item.price) }),
    stockCell(item.stock),
    el('td', { className: 'col-category', textContent: item.category }),
    el('td', { className: 'col-status' }, [statusBadge(item.status)]),
  ]);
}

/**
 * 生成列表视图（summary + table），返回 DocumentFragment。
 * 数据全部来自 GET /api/products，没有任何写死的商品。
 */
export function productList(items) {
  const soldOutCount = items.filter((item) => item.stock === 0).length;
  const summary = el('p', {
    className: 'list-summary',
    textContent: `共 ${items.length} 件商品，其中 ${soldOutCount} 件缺货`,
  });

  const table = el('table', { className: 'product-table' }, [
    el(
      'colgroup',
      {},
      COLUMNS.map(({ className }) => el('col', { className })),
    ),
    el(
      'thead',
      {},
      [el(
        'tr',
        {},
        COLUMNS.map(({ label, className }) => el('th', { className, textContent: label })),
      )],
    ),
    el('tbody', {}, items.map(productRow)),
  ]);

  const fragment = document.createDocumentFragment();
  fragment.append(summary, table);
  return fragment;
}
