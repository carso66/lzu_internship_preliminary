<script setup>
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import ProductForm from '../components/ProductForm.vue'
import { normalizeProductTitle } from '../utils/product.js'

const createdProduct = ref(null)

function handleCreated(product) {
  createdProduct.value = product ?? null
}

function dismissCreated() {
  createdProduct.value = null
}
</script>

<template>
  <main class="product-create-view">
    <header class="product-create-view__header">
      <div class="product-create-view__heading">
        <p class="product-create-view__eyebrow">校园好物市集</p>
        <h1>上架商品</h1>
        <p class="product-create-view__hint">
          补全商品信息后即可发布；发布成功后可以在商品列表或详情页查看。
        </p>
      </div>
      <RouterLink class="product-create-view__back" :to="{ name: 'products' }">
        返回商品列表
      </RouterLink>
    </header>

    <section
      v-if="createdProduct"
      class="product-create-view__success"
      role="status"
      aria-live="polite"
    >
      <div class="product-create-view__success-text">
        <strong>
          《{{ normalizeProductTitle(createdProduct.title) }}》已上架
        </strong>
        <span>商品编号 {{ createdProduct.id }}。</span>
      </div>
      <div class="product-create-view__success-actions">
        <RouterLink
          class="product-create-view__success-primary"
          :to="{ name: 'product-detail', params: { id: createdProduct.id } }"
        >
          查看详情
        </RouterLink>
        <button type="button" @click="dismissCreated">继续上架</button>
      </div>
    </section>

    <ProductForm @created="handleCreated" />
  </main>
</template>

<style scoped>
.product-create-view {
  width: min(760px, 100%);
  margin: 0 auto;
  padding: 32px 20px 56px;
  color: #27272a;
}

.product-create-view__header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 24px;
}

.product-create-view__eyebrow {
  margin: 0 0 6px;
  color: #c2410c;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.product-create-view__heading h1 {
  margin: 0;
  color: #18181b;
  font-size: clamp(28px, 4vw, 38px);
  line-height: 1.1;
}

.product-create-view__hint {
  max-width: 460px;
  margin: 10px 0 0;
  color: #71717a;
  font-size: 14px;
  line-height: 1.6;
}

.product-create-view__back {
  flex: none;
  padding: 9px 16px;
  border: 1px solid #d4d4d8;
  border-radius: 9px;
  background: #ffffff;
  color: #3f3f46;
  font-size: 14px;
  font-weight: 700;
  text-decoration: none;
}

.product-create-view__back:hover {
  border-color: #a1a1aa;
  background: #fafafa;
}

.product-create-view__success {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 22px;
  padding: 14px 18px;
  border: 1px solid #bbf7d0;
  border-radius: 12px;
  background: #f0fdf4;
  color: #166534;
}

.product-create-view__success-text {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.product-create-view__success-text strong {
  overflow: hidden;
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-create-view__success-text span {
  color: #15803d;
  font-size: 13px;
}

.product-create-view__success-actions {
  display: flex;
  flex: none;
  align-items: center;
  gap: 10px;
}

.product-create-view__success-actions button,
.product-create-view__success-primary {
  padding: 8px 13px;
  border: 1px solid #86efac;
  border-radius: 8px;
  background: #ffffff;
  color: #166534;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  text-decoration: none;
}

.product-create-view__success-primary {
  border-color: #166534;
  background: #166534;
  color: #ffffff;
}

.product-create-view__success-actions button:hover {
  background: #dcfce7;
}

.product-create-view__success-primary:hover {
  background: #14532d;
}

@media (max-width: 640px) {
  .product-create-view {
    padding: 24px 14px 40px;
  }

  .product-create-view__header,
  .product-create-view__success {
    align-items: flex-start;
    flex-direction: column;
  }

  .product-create-view__back {
    align-self: flex-start;
  }
}
</style>
