<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

/**
 * 高亮自己算，不用 RouterLink 的 `router-link-active`：
 * `/products/3` 的 matched 里只有 `product-detail` 这条记录，
 * `/products` 不会被算成 active —— 用户在详情页时导航就一条都不亮，很怪。
 * 这里明确「详情页也算商品列表这一区」。
 */
const links = computed(() => [
  {
    name: 'products',
    label: '商品列表',
    active: route.path === '/products' || route.path.startsWith('/products/'),
  },
  { name: 'publish', label: '发布商品', active: route.path === '/publish' },
  { name: 'orders', label: '我的订单', active: route.path === '/orders' },
])
</script>

<template>
  <nav class="nav" aria-label="主导航">
    <RouterLink
      v-for="link in links"
      :key="link.name"
      :to="{ name: link.name }"
      class="nav-link"
      :class="{ 'is-active': link.active }"
      :data-testid="`nav-${link.name}`"
    >
      {{ link.label }}
    </RouterLink>
  </nav>
</template>
