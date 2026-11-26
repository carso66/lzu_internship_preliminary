import { fetchAllOrders, fetchAllProducts, updateProductStatus } from './api.js';
import { el } from './dom.js';
import { orderList } from './order-list.js';
import { createPaymentPanel } from './payment-form.js';
import { productList } from './product-list.js';
import { createPublishPanel } from './publish-form.js';
import { createPurchasePanel } from './purchase-form.js';
import { createSearchBox } from './search-box.js';
import { statusText, toggleTarget } from './product-status.js';
import { displayTitle } from './text.js';
import { createToast } from './toast.js';

/**
 * 入口 + 视图状态机。
 *
 * 商品列表（L0-1 ~ L0-5）和订单列表（L1-7）各有一块区域、各有一套
 * 「加载态 / 错误态 / 空态 / 数据」渲染，互不干扰：
 *  - 上下架只重画商品表；
 *  - 下单成功要两块都刷（库存变了 + 多了一笔订单）。
 *
 * 每块内部只有一条渲染路径（几个 else-if 分支里只走一个），
 * 所以「加载中不显示空态、空态不显示加载中」是结构上成立的，不用靠 if 互相防。
 */

const app = document.querySelector('#app');

// 商品列表容器：render() 只替换它的内容。
const listRegion = el('div', { className: 'list-region' });

// 订单列表容器：标题 + 刷新按钮 + 内容区（内容区由 renderOrders() 换）。
const ordersBody = el('div', { className: 'orders-body' });
const ordersSection = el('section', { className: 'orders-section' }, [
  el('div', { className: 'section-head' }, [
    el('h2', { className: 'section-title', textContent: '订单列表' }),
    el('button', {
      type: 'button',
      className: 'btn-plain',
      textContent: '刷新',
      on: { click: () => loadOrders() },
    }),
  ]),
  ordersBody,
]);
app.replaceChildren(listRegion, ordersSection);

// 右上角：搜索框在左、「+ 发布商品」在右（按钮由发布面板自己带，位置由这里决定）。
// 全页只用一条提示条（toast.js），把 show 注入给两个面板，避免两条 toast 叠在同一位置。
const topbarActions = document.querySelector('#topbar-actions') ?? document.body;
const search = createSearchBox({ onSearch: handleSearch });
const toast = createToast();
const publish = createPublishPanel({ onCreated: handleCreated, notify: toast.show });
const purchase = createPurchasePanel({ onOrdered: handleOrdered, notify: toast.show });

/**
 * 支付相关的两把锁（L2-10），由订单列表和支付面板共用：
 *  - payingOrderIds：正在轮询的订单，按钮显示「支付中…」并禁用（不能连点、不能重复发起）；
 *  - settledOrderIds：本次会话里已确认支付的订单，按钮永久禁用成「已支付」。
 * 之所以要记在 main.js 而不是每条订单上：订单表每次刷新都是新对象，
 * 「已经付过」这件事必须由页面层面记住，否则刷新后又变回可点的「去支付」。
 */
const payingOrderIds = new Set();
const settledOrderIds = new Set();

const payment = createPaymentPanel({
  notify: toast.show,
  onStart: (paidOrder) => {
    payingOrderIds.add(paidOrder.id);
    renderOrders();
  },
  onSettled: (paidOrder) => {
    payingOrderIds.delete(paidOrder.id);
    settledOrderIds.add(paidOrder.id);
    renderOrders(); // 立刻把这一行变成「已支付」+ 禁用
    loadOrders({ highlightId: paidOrder.id });
  },
  onAbort: (paidOrder) => {
    // 轮询没确认成功（超时 / 查询失败 / 用户关掉弹窗）：解锁，允许重新发起
    payingOrderIds.delete(paidOrder.id);
    renderOrders();
  },
});

topbarActions.append(search.form, publish.trigger);
document.body.append(toast.node, publish.mask, purchase.mask, payment.mask);

/**
 * 发布成功（201）之后：重新拉一次列表，并让刚发布的那一行高亮 + 滚到可见处。
 * 这三件事（提示 / 表单重置 / 列表里能找到新商品）就是 L0-3 的验收内容，
 * 其中「提示」和「表单重置」在 publish-form.js 里完成，这里负责列表。
 *
 * 如果发布时还挂着搜索词，新商品很容易刚好被筛掉（L0-3 要求「列表里能找到刚发布的商品」），
 * 所以先把筛选清空、搜索框一起清掉，再带着 highlightId 重新拉。
 */
async function handleCreated(product) {
  if (hasFilter()) clearFilters({ reload: false });
  await load({ highlightId: product.id });
}

/**
 * 下单成功（201）之后（L1-6 / L1-7）：
 *  - 商品表要重新拉一次，库存才是服务端扣完之后的真实值；
 *  - 订单表也要重新拉，刚下的单才会出现在列表里（带上 highlightId 高亮）。
 * 两个请求并行发，互不依赖。
 */
async function handleOrdered(order) {
  await Promise.all([load(), loadOrders({ highlightId: order.id })]);
}

/** 点某一行的「购买」：把这一行当前的商品数据交给购买弹窗 */
function openPurchase(id) {
  const item = state.items.find((entry) => entry.id === id);
  if (item) purchase.open(item);
}

/**
 * 搜索框（L0-5）的回调：关键词变了才发请求。
 * 关键词交给接口筛（GET /api/products?keyword=...），而不是在已加载的数据里本地过一遍——
 * 后者只能搜到「当前已经拿到的」那部分，商品一多就会出现「明明有却搜不到」。
 */
function handleSearch(keyword) {
  if (keyword === state.keyword) return;
  state.keyword = keyword;
  load();
}

const state = {
  items: [],
  total: 0,
  loading: false,
  error: '',
  keyword: '', // L0-5：搜索框里的关键词，直接作为 keyword 参数发给接口
  category: '', // 预留：分类筛选（当前没有 UI，清空筛选时一起复位）
};

const hasFilter = () => Boolean(state.keyword.trim() || state.category);

/**
 * 正在改状态的商品 id（L0-4）。
 * 同一件商品的 PATCH 没回来之前，按钮禁用且再点直接 return——
 * 快速连点只会留下 1 个请求，也不会出现「上架请求和下单请求同时在飞」那种两条相反状态。
 */
const pendingStatusIds = new Set();

function loadingView() {
  const box = el('div', { className: 'state state-loading' }, [
    el('span', { className: 'spinner' }),
    el('span', { textContent: '加载中…' }),
  ]);
  box.setAttribute('role', 'status'); // 读屏软件也能知道「在等数据」
  return box;
}

function errorView(message) {
  return el('div', { className: 'state state-error' }, [
    el('p', { className: 'state-text', textContent: message }),
    el('button', {
      type: 'button',
      className: 'btn-retry',
      textContent: '重试',
      on: { click: () => load() },
    }),
  ]);
}

/**
 * 空态（L0-5）：
 *  - 有筛选时说的是「没找到匹配的商品」+ 具体关键词 + 「清空筛选」按钮；
 *  - 真的一件商品都没有时才说「还没有商品」+ 「刷新」。
 * 两种情况都不是一片纯白：有标题、有说明、有可点的下一步操作。
 */
function emptyView() {
  const keyword = state.keyword.trim();
  const filtered = hasFilter();
  const title = filtered ? '没有找到匹配的商品' : '还没有商品';
  const text = keyword
    ? `关键词「${keyword}」没有匹配到商品，换个词试试，或者点「清空筛选」看全部商品`
    : filtered
      ? '换个筛选条件，或者清空筛选看看全部商品'
      : '刷新一下，或者等发布第一件商品';
  return el('div', { className: 'state empty-state' }, [
    el('p', { className: 'state-title', textContent: title }),
    el('p', { className: 'state-text', textContent: text }),
    el('button', {
      type: 'button',
      className: 'btn-retry',
      textContent: filtered ? '清空筛选' : '刷新',
      on: { click: filtered ? () => clearFilters() : () => load() },
    }),
  ]);
}

function render(highlightId = 0) {
  if (state.loading) return listRegion.replaceChildren(loadingView());
  if (state.error) return listRegion.replaceChildren(errorView(state.error));
  if (state.items.length === 0) return listRegion.replaceChildren(emptyView());
  listRegion.replaceChildren(
    productList(state.items, {
      highlightId,
      pendingIds: pendingStatusIds,
      keyword: state.keyword.trim(),
    }),
  );
}

/** 把刚发布的那一行滚到可见处（环境不支持 scrollIntoView 时静默跳过） */
function focusRow(id) {
  const row = listRegion.querySelector?.(`tr[data-id="${id}"]`);
  row?.scrollIntoView?.({ block: 'center', behavior: 'smooth' });
}

/**
 * 清空筛选（空态里的「清空筛选」按钮 / 发布成功前顺手清一次）。
 * 搜索框的值必须一起清：只改 state 不清输入框，界面会出现「框里还写着 zzz、列表却是全部商品」。
 */
function clearFilters({ reload = true } = {}) {
  state.keyword = '';
  state.category = '';
  search.setValue('');
  if (reload) load();
}

/**
 * 上下架（L0-4）。
 *
 * 三件事防「连点 / 点错」后出现两条相反状态：
 *  1. pendingStatusIds：这件商品有请求在飞时直接 return，第二次点击连请求都不发；
 *  2. 按钮当刻就 disabled + 文案变「处理中…」，不给连点留窗口；
 *  3. 成功后用**服务端返回的 status** 写回 state（而不是自己拿旧值取反），再 render() 重画——
 *     徽标和按钮文案都从这一份 state 推出来，不可能一个说「在售」一个说「上架」。
 *
 * 失败：状态保持原样，把后端 message 原样提示出来（README 4.3：业务错误必须显示 message）。
 */
async function toggleStatus(id, button) {
  const item = state.items.find((entry) => entry.id === id);
  if (!item || pendingStatusIds.has(id)) return;

  const target = toggleTarget(item.status);
  if (!target) return; // 未知状态没有可执行的动作，不发请求

  pendingStatusIds.add(id);
  setButtonBusy(button, true);
  try {
    const updated = await updateProductStatus(id, target);
    item.status = updated?.status ?? target; // 以服务端返回的状态为准
    const done = item.status === 'on' ? '上架' : '下架';
    toast.show(`#${item.id} ${displayTitle(item.title)} 已${done}，当前状态：${statusText(item.status)}`);
  } catch (error) {
    toast.show(error?.message || '操作失败，请稍后重试', 'error');
  } finally {
    pendingStatusIds.delete(id);
    render(); // 整表重画：徽标、按钮文案、禁用态都以最新 state + pendingStatusIds 为准
  }
}

/** 请求在飞时先就地改按钮（不等重渲染），避免用户以为「点了没反应」 */
function setButtonBusy(button, busy) {
  if (!button) return;
  button.disabled = busy;
  button.classList.toggle('is-busy', busy);
  button.textContent = busy ? '处理中…' : button.dataset.label || '';
}

// 事件委托挂在列表容器上：render() 每次整块替换 tbody，逐行挂监听要重挂，
// 委托一次就够，而且重渲染之后依然有效。
// 「购买」和「上架 / 下架」两种按钮共用这一个监听，靠 data-action 分流。
listRegion.addEventListener('click', (event) => {
  const toggleBtn = event.target?.closest?.('button[data-action="toggle-status"]');
  if (toggleBtn) return toggleStatus(Number(toggleBtn.dataset.id), toggleBtn);

  const buyBtn = event.target?.closest?.('button[data-action="buy"]');
  if (buyBtn) return openPurchase(Number(buyBtn.dataset.id));
});

// 订单区的「去支付」同样用委托：renderOrders() 每次都整表重画，委托一次就够。
ordersBody.addEventListener('click', (event) => {
  const payBtn = event.target?.closest?.('button[data-action="pay"]');
  if (!payBtn) return;
  openPayment(Number(payBtn.dataset.orderId));
});

/** 点某一行的「去支付」：把这一笔订单交给支付弹窗 */
function openPayment(orderId) {
  const target = orderState.items.find((entry) => entry.id === orderId);
  if (target) payment.open(target);
}

/**
 * 重新拉列表。失败不往外抛：错误落到 state.error 由 render() 显示，
 * 页面不会白屏，也不用每个调用方都包一层 try/catch（发布成功、搜索、上下架之后都能直接 await load()）。
 * highlightId：发布成功时传新商品 id，渲染完把它高亮并滚到可见处。
 */
let loadSeq = 0;

async function load({ highlightId = 0 } = {}) {
  // 每次请求发一个序号：连续打字时先发的慢请求返回后会被丢掉，
  // 否则「zzz 空结果」可能盖掉刚回来的「耳」结果。
  const seq = ++loadSeq;
  state.loading = true;
  state.error = '';
  render();
  try {
    const { total, items } = await fetchAllProducts({ keyword: state.keyword, category: state.category });
    if (seq !== loadSeq) return;
    state.items = items;
    state.total = total;
    console.log(`GET /api/products?keyword=${state.keyword || '(空)'} -> ${items.length}/${total} 条`);
  } catch (error) {
    if (seq !== loadSeq) return;
    state.items = [];
    state.error = error?.message || '加载失败，请稍后重试';
    console.error(error);
  } finally {
    if (seq === loadSeq) {
      state.loading = false;
      render(highlightId);
      if (highlightId) focusRow(highlightId);
    }
  }
}

// ---------- 订单列表（L1-7） ----------
// 和商品列表同一套写法：加载 / 错误 / 空 / 数据 四选一，互斥由结构保证。
const orderState = { items: [], total: 0, loading: false, error: '' };

function ordersLoadingView() {
  const box = el('div', { className: 'state state-loading' }, [
    el('span', { className: 'spinner' }),
    el('span', { textContent: '订单加载中…' }),
  ]);
  box.setAttribute('role', 'status');
  return box;
}

function ordersErrorView(message) {
  return el('div', { className: 'state state-error' }, [
    el('p', { className: 'state-text', textContent: message }),
    el('button', {
      type: 'button',
      className: 'btn-retry',
      textContent: '重试',
      on: { click: () => loadOrders() },
    }),
  ]);
}

function ordersEmptyView() {
  return el('div', { className: 'state empty-state' }, [
    el('p', { className: 'state-title', textContent: '还没有订单' }),
    el('p', { className: 'state-text', textContent: '在上面商品列表点「购买」，下单成功后这里就会出现' }),
  ]);
}

function renderOrders(highlightId = 0) {
  if (orderState.loading) return ordersBody.replaceChildren(ordersLoadingView());
  if (orderState.error) return ordersBody.replaceChildren(ordersErrorView(orderState.error));
  if (orderState.items.length === 0) return ordersBody.replaceChildren(ordersEmptyView());
  ordersBody.replaceChildren(
    orderList(orderState.items, {
      highlightId,
      payingIds: payingOrderIds,
      settledIds: settledOrderIds,
    }),
  );
}

let ordersSeq = 0;

/** 拉订单列表。失败落到 orderState.error 由 renderOrders() 显示，页面不会白屏 */
async function loadOrders({ highlightId = 0 } = {}) {
  const seq = ++ordersSeq; // 和商品列表一样：只认最后一次请求的结果
  orderState.loading = true;
  orderState.error = '';
  renderOrders();
  try {
    const { total, items } = await fetchAllOrders();
    if (seq !== ordersSeq) return;
    orderState.items = items;
    orderState.total = total;
  } catch (error) {
    if (seq !== ordersSeq) return;
    orderState.items = [];
    orderState.error = error?.message || '订单加载失败，请稍后重试';
    console.error(error);
  } finally {
    if (seq === ordersSeq) {
      orderState.loading = false;
      renderOrders(highlightId);
    }
  }
}

load();
loadOrders();

