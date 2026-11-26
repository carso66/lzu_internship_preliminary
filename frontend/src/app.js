/**
 * 应用主逻辑（原生 ES Modules，无框架、无构建步骤）
 *
 * 模块拆分：
 *   money.js  —— 纯函数：元<->分、金额格式化、预估总价（可单独测试）
 *   api.js    —— 统一请求：拼 /api 前缀、解包 {code,message,data}、抛出业务错误
 *   app.js    —— 状态 + 渲染 + 交互
 *   server.mjs—— 静态服务 + /api 代理（一条命令起服务）
 *
 * 安全约定：所有来自接口的文本一律用 textContent 写入 DOM，不拼 innerHTML。
 * mock 数据里有一条商品标题是 <script>alert('xss')</script>，这是题目埋的边界。
 */
import { api } from './api.js';
import { toFen, formatYuan, estimateTotal } from './money.js';

const $ = (id) => document.getElementById(id);
const CATEGORIES = ['数码', '图书', '服饰', '食品', '文具'];
const STATUS_TEXT = { on: '在售', off: '已下架' };
const ORDER_STATUS_TEXT = { created: '待支付', paid: '已支付' };

const state = {
  products: [],
  orders: [],
  loading: false,
  filters: { keyword: '', status: '', category: '' },
  pendingOrderId: null,
  publishing: false,
};

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined && text !== null) node.textContent = String(text);
  return node;
}

let toastTimer;
function toast(message, type = 'info') {
  const box = $('toast');
  box.textContent = message;
  box.className = `toast show ${type}`;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { box.className = 'toast'; }, 3400);
}

function setFieldError(field, message) {
  const node = $(`e-${field}`);
  if (node) node.textContent = message || '';
}
function clearFieldErrors() {
  ['title', 'price', 'stock', 'category', 'description'].forEach((f) => setFieldError(f, ''));
}

/* ---------------- 商品列表 ---------------- */

function productRow(p) {
  const row = el('div', `row${p.stock === 0 ? ' row-out' : ''}`);

  const title = el('div', 'title', p.title);
  title.title = p.title;
  row.appendChild(title);
  row.appendChild(el('div', 'price', formatYuan(p.price)));

  const stockCell = el('div', 'cell');
  if (p.stock === 0) stockCell.appendChild(el('span', 'badge badge-out', '缺货'));
  else stockCell.textContent = String(p.stock);
  row.appendChild(stockCell);

  row.appendChild(el('div', 'cell cell-hide-sm', p.category));

  const statusCell = el('div', 'cell');
  statusCell.appendChild(el('span', `badge badge-${p.status === 'on' ? 'on' : 'off'}`, STATUS_TEXT[p.status] || '未知状态'));
  row.appendChild(statusCell);

  const actions = el('div', 'row-actions');
  const toggle = el('button', 'btn btn-sm', p.status === 'on' ? '下架' : '上架');
  toggle.dataset.action = 'toggle';
  toggle.dataset.id = String(p.id);
  actions.appendChild(toggle);

  if (p.status === 'on' && p.stock > 0) {
    const qty = el('input', 'qty');
    qty.type = 'text';
    qty.inputMode = 'numeric';
    qty.value = '1';
    qty.dataset.role = 'qty';
    qty.dataset.id = String(p.id);
    qty.setAttribute('aria-label', '购买数量');
    actions.appendChild(qty);

    const est = el('span', 'estimate', `预计 ${formatYuan(estimateTotal(p.price, 1))}`);
    est.dataset.role = 'estimate';
    est.dataset.id = String(p.id);
    actions.appendChild(est);

    const buy = el('button', 'btn btn-sm btn-primary', '购买');
    buy.dataset.action = 'buy';
    buy.dataset.id = String(p.id);
    actions.appendChild(buy);
  } else {
    actions.appendChild(el('span', 'estimate', p.status === 'off' ? '已下架，不可购买' : '缺货，不可购买'));
  }
  row.appendChild(actions);
  return row;
}

function renderProducts() {
  const area = $('product-area');
  area.replaceChildren();

  // 加载态与空态互斥：loading 直接返回，绝不落到「没找到」分支
  if (state.loading) {
    const box = el('div', 'state');
    box.append(el('span', 'loading-dot'), document.createTextNode('加载中…'));
    area.appendChild(box);
    $('product-count').textContent = '';
    return;
  }

  $('product-count').textContent = `共 ${state.products.length} 件`;

  if (state.products.length === 0) {
    const box = el('div', 'state state-empty');
    box.appendChild(el('p', '', '没找到相关商品'));
    const btn = el('button', 'btn btn-ghost', '清空筛选');
    btn.addEventListener('click', clearFilters);
    box.appendChild(btn);
    area.appendChild(box);
    return;
  }

  const head = el('div', 'row row-head');
  ['商品标题', '价格', '库存', '分类', '状态', '操作'].forEach((t) => head.appendChild(el('div', 'cell', t)));
  area.appendChild(head);
  state.products.forEach((p) => area.appendChild(productRow(p)));
}

async function loadProducts() {
  state.loading = true;
  renderProducts();
  try {
    // pageSize=50 一次取全，保证能访问到全部商品（含刚发布、排在最后的）
    const data = await api.products({ pageSize: 50, ...state.filters });
    state.products = data.items || [];
  } catch (err) {
    state.products = [];
    toast(err.message, 'error');
  } finally {
    state.loading = false;
    renderProducts();
  }
}

/* ---------------- 订单列表 ---------------- */

function renderOrders() {
  const area = $('order-area');
  area.replaceChildren();
  $('order-count').textContent = `共 ${state.orders.length} 单`;

  if (state.orders.length === 0) {
    area.appendChild(el('div', 'state', '暂无订单'));
    return;
  }

  const head = el('div', 'row row-head order-row');
  ['订单号', '商品', '数量', '金额', '状态'].forEach((t) => head.appendChild(el('div', 'cell', t)));
  area.appendChild(head);

  state.orders.forEach((o) => {
    const row = el('div', 'row order-row');
    row.appendChild(el('div', 'cell', o.orderNo));
    const title = el('div', 'cell', o.productTitle);
    title.title = o.productTitle;
    row.appendChild(title);
    row.appendChild(el('div', 'cell', String(o.quantity)));
    row.appendChild(el('div', 'price', formatYuan(o.totalAmount)));
    const statusCell = el('div', 'cell');
    statusCell.appendChild(el('span', `badge badge-${o.status === 'paid' ? 'paid' : 'created'}`, ORDER_STATUS_TEXT[o.status] || '未知状态'));
    row.appendChild(statusCell);
    area.appendChild(row);
  });
}

async function loadOrders() {
  try {
    const data = await api.orders({ pageSize: 50 });
    state.orders = data.items || [];
    renderOrders();
  } catch (err) {
    toast(err.message, 'error');
  }
}

/* ---------------- 交互 ---------------- */

async function toggleStatus(id, btn) {
  if (btn.disabled) return;
  const p = state.products.find((x) => x.id === id);
  if (!p) return;
  const next = p.status === 'on' ? 'off' : 'on';
  btn.disabled = true; // 连点/快速重复点击只在第一次发请求
  try {
    await api.setStatus(id, next);
    p.status = next;   // 徽标与按钮文案同步变化
    renderProducts();
    toast(`已${next === 'off' ? '下架' : '上架'}：${p.title}`, 'success');
  } catch (err) {
    btn.disabled = false;
    toast(err.message, 'error');
  }
}

async function buy(id, btn) {
  if (state.pendingOrderId !== null) return; // 防重复提交：全局只允许一个在途下单
  const p = state.products.find((x) => x.id === id);
  const qtyInput = $('product-area').querySelector(`[data-role="qty"][data-id="${id}"]`);
  const qty = Number(qtyInput && qtyInput.value);
  if (!Number.isInteger(qty) || qty <= 0) {
    toast('购买数量必须是正整数', 'error');
    return;
  }
  if (!p) return;

  state.pendingOrderId = id;
  btn.disabled = true;
  btn.textContent = '提交中…';
  const estimate = estimateTotal(p.price, qty);
  try {
    const order = await api.createOrder({ productId: id, quantity: qty }); // quantity 是数字
    const match = order.totalAmount === estimate;
    toast(
      `下单成功 ${order.orderNo}，合计 ${formatYuan(order.totalAmount)}${match ? '（与预估一致）' : '（与预估不一致，请检查）'}`,
      'success',
    );
    await Promise.all([loadProducts(), loadOrders()]);
  } catch (err) {
    toast(err.message, 'error'); // 库存不足 / 商品已下架 等后端 message 显示给用户
  } finally {
    state.pendingOrderId = null;
    const current = $('product-area').querySelector(`button[data-action="buy"][data-id="${id}"]`);
    if (current) { current.disabled = false; current.textContent = '购买'; }
  }
}

$('product-area').addEventListener('click', (e) => {
  const btn = e.target.closest('button[data-action]');
  if (!btn) return;
  const id = Number(btn.dataset.id);
  if (btn.dataset.action === 'toggle') toggleStatus(id, btn);
  else if (btn.dataset.action === 'buy') buy(id, btn);
});

$('product-area').addEventListener('input', (e) => {
  if (e.target.dataset.role !== 'qty') return;
  const p = state.products.find((x) => x.id === Number(e.target.dataset.id));
  const est = $('product-area').querySelector(`[data-role="estimate"][data-id="${e.target.dataset.id}"]`);
  if (!p || !est) return;
  const q = Number(e.target.value);
  est.textContent = Number.isInteger(q) && q > 0
    ? `预计 ${formatYuan(estimateTotal(p.price, q))}`
    : '请输入正整数数量';
});

/* ---------------- 发布表单 ---------------- */

function validatePublish() {
  const errors = {};
  const title = $('p-title').value.trim();
  const priceRaw = $('p-price').value.trim();
  const stockRaw = $('p-stock').value.trim();
  const category = $('p-category').value;
  const description = $('p-description').value.trim();

  if (!title || title.length > 50) errors.title = '标题需为 1~50 个字符';

  let priceFen = null;
  if (!priceRaw) errors.price = '请输入价格（元）';
  else {
    try { priceFen = toFen(priceRaw); } catch (e) { errors.price = e.message; }
  }

  if (!/^\d+$/.test(stockRaw)) errors.stock = '库存需为 0~999 的整数';
  else if (Number(stockRaw) > 999) errors.stock = '库存需为 0~999 的整数';

  if (!CATEGORIES.includes(category)) errors.category = '请选择合法分类';
  if (description.length > 200) errors.description = '描述不能超过 200 字';

  return {
    ok: Object.keys(errors).length === 0,
    errors,
    value: { title, price: priceFen, stock: Number(stockRaw), category, description },
  };
}

$('publish-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  if (state.publishing) return;
  clearFieldErrors();
  $('publish-msg').textContent = '';

  const v = validatePublish();
  if (!v.ok) {
    Object.entries(v.errors).forEach(([f, m]) => setFieldError(f, m)); // 错误显示在对应字段下方
    $('publish-msg').textContent = '请修正标红字段后再提交';
    $('publish-msg').className = 'form-msg bad';
    return; // 校验失败不发 POST
  }

  state.publishing = true;
  const btn = $('p-submit');
  btn.disabled = true;
  btn.textContent = '发布中…';
  try {
    const created = await api.createProduct(v.value);
    $('publish-form').reset();           // 5 个输入框清空 / 回默认值
    clearFieldErrors();
    $('publish-msg').textContent = `已发布「${created.title}」，列表已刷新`;
    $('publish-msg').className = 'form-msg ok';
    toast(`发布成功：${created.title}（${formatYuan(created.price)}）`, 'success');
    await loadProducts();                // 重新拉列表，能找到刚发布的商品
  } catch (err) {
    toast(err.message, 'error');
    $('publish-msg').textContent = err.message;
    $('publish-msg').className = 'form-msg bad';
  } finally {
    state.publishing = false;
    btn.disabled = false;
    btn.textContent = '发布商品';
  }
});

/* ---------------- 筛选 ---------------- */

function readFilters() {
  state.filters = {
    keyword: $('f-keyword').value.trim(),
    status: $('f-status').value,
    category: $('f-category').value,
  };
}

function clearFilters() {
  $('f-keyword').value = '';
  $('f-status').value = '';
  $('f-category').value = '';
  readFilters();
  loadProducts();
}

let filterTimer;
function scheduleFilter() {
  clearTimeout(filterTimer);
  filterTimer = setTimeout(() => { readFilters(); loadProducts(); }, 250);
}
$('f-keyword').addEventListener('input', scheduleFilter);
$('f-status').addEventListener('change', scheduleFilter);
$('f-category').addEventListener('change', scheduleFilter);
$('f-clear').addEventListener('click', clearFilters);

/* ---------------- 启动 ---------------- */

readFilters();
Promise.all([loadProducts(), loadOrders()]);
