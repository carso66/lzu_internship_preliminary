const apiBase = 'http://127.0.0.1:3001/api';

const searchForm = document.querySelector('#search-form');
const keywordInput = document.querySelector('#keyword');
const suggestions = document.querySelector('#product-suggestions');
const clearSearchButton = document.querySelector('#clear-search');
const categoryButtons = document.querySelectorAll('[data-category]');
const listMessage = document.querySelector('#list-message');
const tableWrapper = document.querySelector('#table-wrapper');
const productRows = document.querySelector('#product-rows');
const actionMessage = document.querySelector('#action-message');

const publishForm = document.querySelector('#publish-form');
const publishButton = document.querySelector('#publish-button');
const publishMessage = document.querySelector('#publish-message');
const publishDialog = document.querySelector('#publish-dialog');
const publishedProduct = document.querySelector('#published-product');

const pendingProducts = new Set();

let activeCategory = '';
let currentListRequest;
let searchTimer;

async function request(path, { method = 'GET', body, signal } = {}) {
  const options = { method, signal };

  if (body !== undefined) {
    options.headers = { 'Content-Type': 'application/json' };
    options.body = JSON.stringify(body);
  }

  let response;
  try {
    response = await fetch(`${apiBase}${path}`, options);
  } catch (error) {
    if (error.name === 'AbortError') throw error;
    throw new Error('无法连接商品服务，请确认 Mock 正在运行。');
  }

  const result = await response.json();

  if (!response.ok || result.code !== 0) {
    throw new Error(result.message || `请求失败（HTTP ${response.status}）`);
  }

  return result.data;
}

function showMessage(element, message, isError = false) {
  element.textContent = message;
  element.classList.toggle('error', isError);
}

function formatPrice(cents) {
  return `¥${(cents / 100).toFixed(2)}`;
}

function yuanToCents(value) {
  const input = value.trim();

  if (!/^\d+(?:\.\d{1,2})?$/.test(input)) {
    throw new Error('请输入大于 0、最多两位小数的价格，例如 19.90。');
  }

  const [yuan, fraction = ''] = input.split('.');
  const cents = Number(`${yuan}${fraction.padEnd(2, '0')}`);

  if (!Number.isSafeInteger(cents) || cents <= 0) {
    throw new Error('请输入有效的正数价格。');
  }

  return cents;
}

function displayTitle(title) {
  // 省略 Mock 样例标题里的脚本片段，显示结果仍用 textContent。
  return title.replace(/<script\b[^>]*>[\s\S]*?<\/script\s*>/gi, '').trim() || title;
}

function appendCell(row, text) {
  const cell = row.insertCell();
  cell.textContent = text;
  return cell;
}

function createProductRow(product) {
  const row = document.createElement('tr');
  const isOnSale = product.status === 'on';
  row.dataset.productId = product.id;

  const title = document.createElement('span');
  title.className = 'product-title';
  title.textContent = displayTitle(product.title);
  title.title = title.textContent;
  row.insertCell().append(title);

  appendCell(row, formatPrice(product.price));

  const stockCell = appendCell(row, product.stock);
  if (product.stock === 0) {
    const marker = document.createElement('span');
    marker.className = 'out-of-stock';
    marker.textContent = '缺货';
    stockCell.append(' ', marker);
  }

  appendCell(row, product.category);

  const badge = document.createElement('span');
  badge.className = isOnSale ? 'badge on-sale' : 'badge off-sale';
  badge.textContent = isOnSale ? '在售' : '已下架';
  row.insertCell().append(badge);

  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'secondary';
  button.disabled = pendingProducts.has(product.id);
  button.textContent = isOnSale ? '下架' : '上架';
  if (button.disabled) button.textContent = '处理中…';

  button.addEventListener('click', () => changeStatus(product, button));
  row.insertCell().append(button);

  return row;
}

function selectCategory(category) {
  activeCategory = category;

  for (const button of categoryButtons) {
    button.setAttribute('aria-pressed', String(button.dataset.category === category));
  }
}

function updateSuggestions(products, keyword) {
  const titles = keyword
    ? [...new Set(products.map((product) => displayTitle(product.title)))]
    : [];

  suggestions.replaceChildren(...titles.slice(0, 6).map((title) => new Option(title)));
}

async function loadProducts({ revealId } = {}) {
  clearTimeout(searchTimer);
  currentListRequest?.abort();

  const controller = new AbortController();
  currentListRequest = controller;

  const keyword = keywordInput.value.trim();
  const category = activeCategory;
  const hasFilters = Boolean(keyword || category);
  const products = [];
  const pageSize = 50;

  tableWrapper.hidden = true;
  clearSearchButton.hidden = !hasFilters;
  suggestions.replaceChildren();
  showMessage(listMessage, '加载中…');

  try {
    for (let page = 1; ; page += 1) {
      const query = new URLSearchParams({ keyword, category, page, pageSize });
      const data = await request(`/products?${query}`, {
        signal: controller.signal,
      });

      products.push(...data.items);
      if (products.length >= data.total || data.items.length === 0) break;
    }

    if (controller.signal.aborted) return;

    products.sort((first, second) => first.id - second.id);
    productRows.replaceChildren(...products.map(createProductRow));
    updateSuggestions(products, keyword);

    tableWrapper.hidden = products.length === 0;
    showMessage(
      listMessage,
      products.length ? `共 ${products.length} 件商品` : '没找到商品',
    );

    tableWrapper.scrollTop = 0;

    if (revealId !== undefined) {
      const newRow = productRows.querySelector(`[data-product-id="${revealId}"]`);
      newRow?.classList.add('new-product');
      tableWrapper.scrollTop = tableWrapper.scrollHeight;
    }
  } catch (error) {
    if (controller.signal.aborted) return;
    showMessage(listMessage, `加载失败：${error.message}`, true);
  }
}

async function changeStatus(product, button) {
  if (pendingProducts.has(product.id)) return;
  pendingProducts.add(product.id);

  const previousLabel = product.status === 'on' ? '下架' : '上架';
  const nextStatus = product.status === 'on' ? 'off' : 'on';

  button.disabled = true;
  button.textContent = '处理中…';
  showMessage(actionMessage, '');

  try {
    await request(`/products/${product.id}/status`, {
      method: 'PATCH',
      body: { status: nextStatus },
    });

    pendingProducts.delete(product.id);
    showMessage(actionMessage, `已${previousLabel}：${displayTitle(product.title)}`);
    await loadProducts();
  } catch (error) {
    showMessage(actionMessage, error.message, true);
  } finally {
    pendingProducts.delete(product.id);

    const currentButton = productRows.querySelector(
      `[data-product-id="${product.id}"] button`,
    );

    if (currentButton?.disabled) {
      currentButton.disabled = false;
      currentButton.textContent = previousLabel;
    }
  }
}

searchForm.addEventListener('submit', (event) => {
  event.preventDefault();
  void loadProducts();
});

keywordInput.addEventListener('input', (event) => {
  currentListRequest?.abort();
  clearTimeout(searchTimer);
  if (event.isComposing) return;
  searchTimer = setTimeout(() => void loadProducts(), 250);
});

keywordInput.addEventListener('compositionend', () => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => void loadProducts(), 250);
});

for (const button of categoryButtons) {
  button.addEventListener('click', () => {
    selectCategory(button.dataset.category);
    void loadProducts();
  });
}

clearSearchButton.addEventListener('click', () => {
  searchForm.reset();
  selectCategory('');
  void loadProducts();
});

publishForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  if (publishButton.disabled) return;

  const fields = new FormData(publishForm);
  publishButton.disabled = true;
  publishButton.textContent = '发布中…';
  showMessage(publishMessage, '');

  try {
    const product = await request('/products', {
      method: 'POST',
      body: {
        title: fields.get('title').trim(),
        price: yuanToCents(fields.get('price')),
        stock: Number(fields.get('stock')),
        category: fields.get('category'),
        description: fields.get('description').trim(),
      },
    });

    publishForm.reset();
    searchForm.reset();
    selectCategory('');
    publishedProduct.textContent = displayTitle(product.title);
    publishDialog.showModal();

    await loadProducts({ revealId: product.id });
  } catch (error) {
    showMessage(publishMessage, error.message, true);
  } finally {
    publishButton.disabled = false;
    publishButton.textContent = '发布商品';
  }
});

void loadProducts();