<script setup>
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<template>
  <section class="panel" data-testid="not-found">
    <div class="state state-empty">
      <p class="state-title">页面不存在</p>
      <p class="state-desc">
        没有找到地址 <code>{{ route.fullPath }}</code>，可能是链接写错了。
      </p>
      <RouterLink class="btn btn-primary" :to="{ name: 'products' }">回到商品列表</RouterLink>
    </div>
  </section>
</template>
