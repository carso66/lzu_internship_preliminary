import { createRouter, createWebHistory } from 'vue-router'

/**
 * 路由表。
 *
 * 三个刻意的选择：
 * 1. 页面组件全部 `() => import(...)` 懒加载 —— 首屏只下载当前页面，
 *    单测 import 本文件时也不会连带把 .vue 编译进来。
 * 2. 用 history 模式而不是 hash —— README L2-13 B 要求 `/products/3` 这种地址
 *    **直接粘到新标签页也能打开**，hash 模式会变成 `/#/products/3`，不满足。
 *    Vite dev server 与 `vite preview` 都会把未知路径回落到 index.html。
 * 3. `/products/:id` 用 `props: true` —— 详情页拿 id 不需要 `useRoute()`，
 *    也方便以后把 route 和组件解耦（单测可以直接给 props）。
 */
export const routes = [
  // 根路径不留白屏，直接进商品列表
  { path: '/', redirect: { name: 'products' } },
  { path: '/products', name: 'products', component: () => import('../views/ProductListView.vue') },
  {
    path: '/products/:id',
    name: 'product-detail',
    component: () => import('../views/ProductDetailView.vue'),
    props: true,
  },
  { path: '/publish', name: 'publish', component: () => import('../views/PublishView.vue') },
  { path: '/orders', name: 'orders', component: () => import('../views/OrderListView.vue') },
  // 兜底：地址写错要给「页面不存在」，不能白屏
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('../views/NotFoundView.vue'),
  },
]

/**
 * 允许注入 history：单测传 `createMemoryHistory()` 就能在 node 环境里跑，
 * 不用为了测试去装 jsdom。默认参数只在真正调用时才求值，
 * 所以 import 本文件本身不会碰 `window`。
 */
export function createAppRouter(history = createWebHistory()) {
  return createRouter({
    history,
    routes,
    // 换页回到顶部，别把上一页的滚动位置带过去
    scrollBehavior: () => ({ top: 0 }),
  })
}
