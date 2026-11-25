const apiBase = 'http://127.0.0.1:3001/api';

const searchInput = document.querySelector('#shop-search');
const productTable = document.querySelector('#shop-products');
const productRows = document.querySelector('#shop-product-rows');
const productMessage = document.querySelector('#shop-message');
const orderTable = document.querySelector('#orders-table');
const orderRows = document.querySelector('#order-rows');
const orderMessage = document.querySelector('#orders-message');

const dialog = document.querySelector('#purchase-dialog');
const heading = document.querySelector('#purchase-heading');
const form = document.querySelector('#purchase-form');
const productName = document.querySelector('#purchase-product');
const details = document.querySelector('#purchase-details');
const quantityInput = document.querySelector('#purchase-quantity');
const estimate = document.querySelector('#purchase-estimate');
const purchaseError = document.querySelector('#purchase-error');
const confirmButton = document.querySelector('#confirm-purchase');
const cancelButton = document.querySelector('#cancel-purchase');
const result = document.querySelector('#purchase-result');
const resultMessage = document.querySelector('#purchase-result-message');
const closeResult = document.querySelector('#close-result');

let products = [];
let productsReady = false;
let selectedProduct;
let submitting = false;

async function request(path, options) {
  let response;

  try {
    response = await fetch(`${apiBase}${path}`, options);
  } catch {
    throw new Error('无法连接 Mock 服务，请检查 3001 端口。');
  }

  const result = await response.json();
  if (!response.ok || result.code !== 0) {
    const error = new Error(result.message || `请求失败（HTTP ${response.status}）`);
    error.code = result.code;
    throw error;
  }
  return result.data;
}

async function getAll(path) {
  const items = [];
  const pageSize = 50;

  for (let page = 1; ; page += 1) {
    const data = await request(`${path}?page=${page}&pageSize=${pageSize}`);
    items.push(...data.items);

    if (items.length >= data.total || data.items.length === 0) {
      return items;
    }
  }
}

function money(cents) {
  const yuan = Math.trunc(cents / 100);
  const fraction = String(cents % 100).padStart(2, '0');
  return `¥${yuan}.${fraction}`;
}

function displayTitle(title) {
  return title.replace(/<script\b[^>]*>[\s\S]*?<\/script\s*>/gi, '').trim() || title;
}

function showMessage(element, text, isError = false) {
  element.textContent = text;
  element.classList.toggle('error', isError);
}

function addCell(row, value) {
  const cell = row.insertCell();
  cell.textContent = value;
  return cell;
}

function addBadge(row, text, className) {
  const badge = document.createElement('span');
  badge.className = `badge ${className}`;
  badge.textContent = text;
  row.insertCell().append(badge);
}

function addTitle(row, rawTitle) {
  const title = document.createElement('span');
  title.className = 'product-title order-product';
  title.textContent = displayTitle(rawTitle);
  title.title = title.textContent;
  row.insertCell().append(title);
}

function renderProducts() {
  if (!productsReady) return;

  const query = searchInput.value.trim().toLowerCase();
  const matches = products.filter((product) =>
    displayTitle(product.title).toLowerCase().includes(query),
  );

  const rows = matches.map((product) => {
    const row = document.createElement('tr');
    const onSale = product.status === 'on';

    addTitle(row, product.title);
    addCell(row, money(product.price));
	const stockCell = addCell(row, product.stock);
    if (product.stock === 0) {
		const marker = document.createElement('span');
		marker.className = 'out-of-stock';
		marker.textContent = '缺货';
		stockCell.append(' ', marker);
	}
    addBadge(row, onSale ? '在售' : '已下架', onSale ? 'on-sale' : 'off-sale');

    const button = document.createElement('button');
    button.type = 'button';
    button.textContent = onSale ? '购买' : '尝试下单';
    if (!onSale) button.className = 'secondary';
    button.addEventListener('click', () => openPurchase(product));

    row.insertCell().append(button);
    return row;
  });

  productRows.replaceChildren(...rows);
  productTable.hidden = rows.length === 0;
  showMessage(productMessage, rows.length ? `共 ${rows.length} 件商品` : '没找到商品');
}

async function loadProducts() {
  try {
    products = await getAll('/products');
    productsReady = true;
    renderProducts();
  } catch (error) {
    showMessage(productMessage, `商品加载失败：${error.message}`, true);
  }
}

async function loadOrders() {
  try {
    const orders = await getAll('/orders');

    const rows = orders.map((order) => {
      const row = document.createElement('tr');
      const paid = order.status === 'paid';

      addCell(row, order.orderNo);
      addTitle(row, order.productTitle);
      addCell(row, order.quantity);
      addCell(row, money(order.totalAmount));
      addBadge(row, paid ? '已支付' : '待支付', paid ? 'on-sale' : 'order-pending');
      row.insertCell().append(createPaymentButton(order));

      return row;
    });

    orderRows.replaceChildren(...rows);
    orderTable.hidden = rows.length === 0;
    showMessage(orderMessage, rows.length ? '' : '暂无订单');
  } catch (error) {
    showMessage(orderMessage, `订单加载失败：${error.message}`, true);
  }
}

function getQuantity() {
  const quantity = quantityInput.valueAsNumber;
  return Number.isSafeInteger(quantity) && quantity > 0 ? quantity : null;
}

function updateEstimate() {
  const quantity = getQuantity();
  const totalCents = quantity === null ? null : selectedProduct.price * quantity;

  estimate.textContent = Number.isSafeInteger(totalCents) ? money(totalCents) : '—';
  purchaseError.textContent = '';
  quantityInput.removeAttribute('aria-invalid');
}

function openPurchase(product) {
  selectedProduct = product;
  heading.textContent = '确认下单';
  productName.textContent = displayTitle(product.title);
  details.textContent = `单价 ${money(product.price)} · 库存 ${product.stock}`;

  quantityInput.value = '1';
  form.hidden = false;
  result.hidden = true;
  updateEstimate();
  dialog.showModal();
  quantityInput.focus();
}

function setSubmitting(value) {
  submitting = value;
  confirmButton.disabled = value;
  cancelButton.disabled = value;
  quantityInput.disabled = value;
  confirmButton.textContent = value ? '提交中…' : '确认下单';
}

searchInput.addEventListener('input', renderProducts);
quantityInput.addEventListener('input', updateEstimate);
cancelButton.addEventListener('click', () => dialog.close());
closeResult.addEventListener('click', () => dialog.close());
dialog.addEventListener('cancel', (event) => {
  if (submitting) event.preventDefault();
});

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  if (submitting) return;

  const quantity = getQuantity();
  if (quantity === null || !Number.isSafeInteger(selectedProduct.price * quantity)) {
    quantityInput.setAttribute('aria-invalid', 'true');
    purchaseError.textContent = '请输入有效的正整数购买数量。';
    quantityInput.focus();
    return;
  }

  purchaseError.textContent = '';
  setSubmitting(true);

  try {
    const order = await request('/orders', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ productId: selectedProduct.id, quantity }),
    });

    heading.textContent = '下单成功';
    resultMessage.textContent = `订单号 ${order.orderNo}，金额 ${money(order.totalAmount)}。`;
    form.hidden = true;
    result.hidden = false;
    closeResult.focus();

    await Promise.all([loadProducts(), loadOrders()]);
  } catch (error) {
    purchaseError.textContent = error.message;
  } finally {
    setSubmitting(false);
  }
});


const paymentDialog = document.querySelector('#payment-dialog');
const paymentForm = document.querySelector('#payment-form');
const paymentMethods = document.querySelector('#payment-methods');
const paymentOrder = document.querySelector('#payment-order');
const paymentAmount = document.querySelector('#payment-amount');
const paymentQr = document.querySelector('#payment-qr');
const paymentQrLabel = document.querySelector('#payment-qr-label');
const paymentMessage = document.querySelector('#payment-message');
const confirmPayment = document.querySelector('#confirm-payment');
const closePayment = document.querySelector('#close-payment');

const paymentsByOrder = new Map();
const paymentButtons = new Map();
let selectedOrder;
let polling;
let startingPayment;

const duplicatePaymentDialog = document.querySelector('#duplicate-payment-dialog');
const duplicatePaymentMessage = document.querySelector('#duplicate-payment-message');

function showDuplicatePayment(message = '订单已支付，请勿重复支付') {
  duplicatePaymentMessage.textContent = message;
  if (!duplicatePaymentDialog.open) duplicatePaymentDialog.showModal();
}

function createPaymentButton(order) {
  const action = document.createElement('span');
  action.className = 'payment-action';

  const button = document.createElement('button');
  button.type = 'button';
  button.addEventListener('click', () => openPayment(order));

  action.addEventListener('click', () => {
    if (order.status === 'paid') showDuplicatePayment();
  });

  action.addEventListener('keydown', (event) => {
    if (event.target !== action || order.status !== 'paid') return;
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      showDuplicatePayment();
    }
  });

  action.append(button);
  paymentButtons.set(order.id, button);
  updatePaymentButton(order);
  return action;
}

function updatePaymentButton(order) {
  const button = paymentButtons.get(order.id);
  if (!button) return;

  const action = button.parentElement;
  const paid = order.status === 'paid';
  const dialogOpenForOrder = paymentDialog.open && selectedOrder?.id === order.id;
  const busy = dialogOpenForOrder || startingPayment === order.id || polling?.orderId === order.id;

  button.disabled = paid || busy;
  button.textContent = paid ? '已支付' : busy ? '支付中…' : '去支付';
  action.classList.toggle('is-paid', paid);

  if (paid) {
    action.tabIndex = 0;
    action.setAttribute('role', 'button');
    action.setAttribute('aria-label', '已支付，查看支付提示');
  } else {
    action.removeAttribute('tabindex');
    action.removeAttribute('role');
    action.removeAttribute('aria-label');
  }
}

function showPaymentPreview() {
  const method = paymentForm.elements.namedItem('method').value;
  const wechat = method === 'wechat';
  const context = paymentQr.getContext('2d');
  paymentQrLabel.textContent = wechat ? '微信支付 · 二维码示意' : '支付宝 · 二维码示意';
  if (!context) return;

  // Mock 不提供付款链接。这里绘制可区分渠道的示意图。
  context.fillStyle = '#fff';
  context.fillRect(0, 0, 132, 132);
  context.fillStyle = wechat ? '#087e3e' : '#1466c6';
  const seed = wechat ? 13 : 7;
  for (let row = 0; row < 21; row += 1) {
    for (let column = 0; column < 21; column += 1) {
      const inCorner = (row < 7 && (column < 7 || column > 13))
        || (row > 13 && column < 7);
      if (!inCorner && ((row * 17 + column * 11 + row * column * seed) % 13) < 6) {
        context.fillRect(13 + column * 5, 13 + row * 5, 5, 5);
      }
    }
  }

  for (const [x, y] of [[13, 13], [83, 13], [13, 83]]) {
    context.fillRect(x, y, 35, 35);
    context.clearRect(x + 5, y + 5, 25, 25);
    context.fillRect(x + 10, y + 10, 15, 15);
  }
}

function openPayment(order) {
  selectedOrder = order;
  paymentOrder.textContent = order.orderNo;
  paymentAmount.textContent = money(order.totalAmount);

  const existing = paymentsByOrder.get(order.id);
  paymentForm.reset();
  if (existing) paymentForm.elements.namedItem('method').value = existing.method;

  paymentMethods.disabled = Boolean(existing);
  confirmPayment.disabled = false;
  confirmPayment.textContent = existing ? '继续查询支付状态' : '开始模拟支付';
  showMessage(paymentMessage, '');
  showPaymentPreview();
  paymentDialog.showModal();
  updatePaymentButton(order);
}

function stopPolling() {
  if (!polling) return;
  const current = polling;
  polling = undefined;
  clearTimeout(current.timer);
  current.controller.abort();
  updatePaymentButton(current.order);
}

function startPolling(order, paymentId) {
  const current = {
    order,
    orderId: order.id,
    controller: new AbortController(),
    attempts: 0,
    timer: undefined,
  };
  polling = current;
  showMessage(paymentMessage, '支付中…');
  updatePaymentButton(order);

  async function checkStatus() {
    if (polling !== current || !paymentDialog.open) return;
    current.attempts += 1;

    try {
      const payment = await request(`/payments/${encodeURIComponent(paymentId)}`, {
        signal: current.controller.signal,
      });

      if (polling !== current || !paymentDialog.open) return;
      if (payment.status === 'paid') {
        order.status = 'paid';
        paymentsByOrder.delete(order.id);
        stopPolling();
        confirmPayment.disabled = true;
        confirmPayment.textContent = '已支付';
        showMessage(paymentMessage, '支付成功');
        await loadOrders();
        return;
      }
    } catch (error) {
      if (polling !== current || !paymentDialog.open) return;
      stopPolling();
      confirmPayment.disabled = false;
      confirmPayment.textContent = '继续查询支付状态';
      showMessage(paymentMessage, error.message, true);
      return;
    }

    if (current.attempts >= 10) {
      stopPolling();
      confirmPayment.disabled = false;
      confirmPayment.textContent = '继续查询支付状态';
      showMessage(paymentMessage, '支付状态未确认，请稍后在订单列表查看', true);
      return;
    }

    current.timer = setTimeout(checkStatus, 1000);
  }

  current.timer = setTimeout(checkStatus, 1000);
}

paymentForm.addEventListener('change', showPaymentPreview);
closePayment.addEventListener('click', () => {
  stopPolling();
  paymentDialog.close();
});
paymentDialog.addEventListener('cancel', stopPolling);
paymentDialog.addEventListener('close', () => {
  stopPolling();
  if (selectedOrder) updatePaymentButton(selectedOrder);
});
window.addEventListener('pagehide', stopPolling);

paymentForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  const order = selectedOrder;
  if (!order || startingPayment !== undefined || polling || order.status === 'paid') return;

  const method = paymentForm.elements.namedItem('method').value;
  confirmPayment.disabled = true;
  confirmPayment.textContent = '支付中…';
  paymentMethods.disabled = true;
  showMessage(paymentMessage, '支付中…');

  startingPayment = order.id;
  updatePaymentButton(order);

  try {
    let payment = paymentsByOrder.get(order.id);
    if (!payment) {
      const created = await request('/payments', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ orderId: order.id, method }),
      });
      payment = { paymentId: created.paymentId, method };
      paymentsByOrder.set(order.id, payment);
    }

    if (paymentDialog.open && selectedOrder === order) {
      startPolling(order, payment.paymentId);
    }
  } catch (error) {
    if (error.code === 40903) {
      order.status = 'paid';
      paymentsByOrder.delete(order.id);
      updatePaymentButton(order);

      if (paymentDialog.open && selectedOrder === order) {
        paymentDialog.close();
        showDuplicatePayment(error.message);
      }

      await loadOrders();
    } else if (paymentDialog.open && selectedOrder === order) {
      paymentMethods.disabled = false;
      confirmPayment.disabled = false;
      confirmPayment.textContent = '开始模拟支付';
      showMessage(paymentMessage, error.message, true);
    }
  } finally {
    startingPayment = undefined;
    updatePaymentButton(order);
  }
});

void loadProducts();
void loadOrders();