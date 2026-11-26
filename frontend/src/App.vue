<script setup>
import AppNav from './components/AppNav.vue'
import { useToasts } from './composables/useToasts.js'

// 外壳只负责「页头 + 导航 + 当前页面 + toast」，
// 具体业务状态归到各页面自己的组件里，换页时自然重建。
const { toasts, dismiss } = useToasts()
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h1>校园好物市集</h1>
        <p class="page-sub">L0 + L1：商品发布 / 上下架 · 下单 · 订单列表 · 加载 / 空态</p>
      </div>
      <p class="page-api">数据来自 <code>/api/products</code> 与 <code>/api/orders</code></p>
    </header>

    <AppNav />

    <main class="layout">
      <!-- 路由出口：/products、/products/:id、/publish、/orders、404 -->
      <RouterView />
    </main>

    <!-- toast 区：成功/失败都从这里露出来，业务错误不会被吞掉 -->
    <div class="toasts" aria-live="polite">
      <div
        v-for="item in toasts"
        :key="item.id"
        class="toast"
        :class="`toast-${item.type}`"
        data-testid="toast"
        @click="dismiss(item.id)"
      >
        {{ item.message }}
      </div>
    </div>
  </div>
</template>