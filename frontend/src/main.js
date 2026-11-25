import { listProducts } from './api.js';
import { el } from './dom.js';
import { productList } from './product-list.js';

/**
 * 入口：拉数据 -> 渲染列表。
 * 加载态 / 错误态留在这里；列表本身交给 product-list.js（L0-1）。
 */

const app = document.querySelector('#app');

function renderLoading() {
  app.replaceChildren(el('p', { className: 'state', textContent: '加载中…' }));
}

function renderError(message) {
  app.replaceChildren(el('p', { className: 'state state-error', textContent: message }));
}

async function boot() {
  renderLoading();
  try {
    // mock 的 pageSize：文档写默认 8、代码实际是 50。这里显式传 50 一次取全 24 条，
    // 不依赖服务端默认值（验收要求：列表必须能访问到全部商品）
    const { total, items } = await listProducts({ pageSize: 50 });
    console.log(`接口返回 ${items.length} 条 / 共 ${total} 条`);
    app.replaceChildren(productList(items));
  } catch (error) {
    renderError(error.message);
    console.error(error);
  }
}

boot();
