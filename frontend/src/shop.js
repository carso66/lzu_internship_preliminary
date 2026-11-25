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
    throw new Error(result.message || `请求失败（HTTP ${response.status}）`);
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

void loadProducts();
void loadOrders();