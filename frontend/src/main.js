import { listProducts } from './api.js';
import { formatCny } from './money.js';

/**
 * 阶段 0：只验证「mock -> fetch -> 渲染」这条链路通不通。
 * 加载态 / 空态 / 发布 / 上下架 / 下单 都还没做，下一步再补。
 */

const app = document.querySelector('#app');

/** 用 createElement + textContent 建 DOM，不拼 innerHTML：
 *  db.json 里商品 24 的标题就是 <script>alert('xss')</script>，拼字符串会直接踩坑。
 */
function el(tag, props = {}, children = []) {
  const node = document.createElement(tag);
  Object.assign(node, props);
  for (const child of children) node.append(child);
  return node;
}

function renderLoading() {
  app.replaceChildren(el('p', { className: 'state', textContent: '加载中…' }));
}

function renderError(message) {
  app.replaceChildren(el('p', { className: 'state', textContent: message }));
}

function renderProducts(items) {
  const head = el('tr', {}, [
    el('th', { textContent: 'ID' }),
    el('th', { textContent: '标题' }),
    el('th', { textContent: '价格' }),
    el('th', { textContent: '库存' }),
    el('th', { textContent: '分类' }),
    el('th', { textContent: '状态' }),
  ]);

  const rows = items.map((item) =>
    el('tr', {}, [
      el('td', { textContent: String(item.id) }),
      el('td', { className: 'col-title', textContent: item.title, title: item.title }),
      el('td', { className: 'price', textContent: formatCny(item.price) }),
      el('td', { textContent: String(item.stock) }),
      el('td', { textContent: item.category }),
      el('td', { textContent: item.status === 'on' ? '在售' : '已下架' }),
    ]),
  );

  app.replaceChildren(el('table', { className: 'product-table' }, [el('thead', {}, [head]), el('tbody', {}, rows)]));
}

async function boot() {
  renderLoading();
  try {
    const { total, items } = await listProducts({ pageSize: 50 });
    console.log(`接口返回 ${items.length} 条 / 共 ${total} 条`);
    renderProducts(items);
  } catch (error) {
    renderError(error.message);
    console.error(error);
  }
}

boot();
