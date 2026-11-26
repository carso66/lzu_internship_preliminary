<script setup>
import { RouterLink } from 'vue-router'
</script>

<template>
  <main class="not-found-view">
    <section class="not-found-view__panel" role="alert">
      <p class="not-found-view__code">404</p>
      <h1>页面不存在</h1>
      <p class="not-found-view__description">
        你访问的地址可能已失效，或链接输入有误。
      </p>
      <div class="not-found-view__actions">
        <RouterLink class="not-found-view__primary" :to="{ name: 'products' }">
          返回商品列表
        </RouterLink>
        <RouterLink class="not-found-view__secondary" :to="{ name: 'orders' }">
          查看我的订单
        </RouterLink>
      </div>
    </section>
  </main>
</template>

<style scoped>
.not-found-view {
  display: grid;
  width: min(760px, 100%);
  min-height: calc(100vh - 69px);
  box-sizing: border-box;
  margin: 0 auto;
  padding: 32px 20px 56px;
  place-items: center;
}

.not-found-view__panel {
  width: 100%;
  padding: clamp(32px, 7vw, 64px);
  border: 1px solid #e4e4e7;
  border-radius: 20px;
  background:
    radial-gradient(
      circle at top right,
      rgb(249 115 22 / 10%),
      transparent 42%
    ),
    #ffffff;
  box-shadow: 0 16px 42px rgb(24 24 27 / 8%);
  text-align: center;
}

.not-found-view__code {
  margin: 0 0 8px;
  color: #c2410c;
  font-size: clamp(56px, 14vw, 104px);
  font-weight: 900;
  letter-spacing: -0.06em;
  line-height: 0.9;
}

.not-found-view h1 {
  margin: 18px 0 10px;
  color: #18181b;
  font-size: clamp(26px, 5vw, 38px);
}

.not-found-view__description {
  margin: 0;
  color: #71717a;
  line-height: 1.7;
}

.not-found-view__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  margin-top: 28px;
}

.not-found-view__actions a {
  padding: 11px 18px;
  border: 1px solid #d4d4d8;
  border-radius: 10px;
  color: #3f3f46;
  font-size: 14px;
  font-weight: 700;
  text-decoration: none;
}

.not-found-view__primary {
  border-color: #18181b !important;
  background: #18181b;
  color: #ffffff !important;
}

.not-found-view__actions a:hover {
  border-color: #a1a1aa;
  background: #f4f4f5;
}

.not-found-view__actions .not-found-view__primary:hover {
  border-color: #3f3f46;
  background: #3f3f46;
}

@media (max-width: 560px) {
  .not-found-view {
    padding: 24px 14px 40px;
  }

  .not-found-view__actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
