<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { getProduct } from '../api/index.js'
import { formatPrice } from '../utils/money.js'
import { normalizeProductTitle } from '../utils/product.js'

const props = defineProps({
  orderingProductId: {
    type: [Number, String],
    default: null,
  },
})

const emit = defineEmits(['buy'])
const route = useRoute()

const product = ref(null)
const loading = ref(true)
const errorMessage = ref('')
const quantity = ref(1)

let requestId = 0

const viewState = computed(() => {
  if (loading.value) return 'loading'
  if (errorMessage.value) return 'error'
  if (!product.value) return 'error'
  return 'ready'
})

const stock = computed(() => {
  const value = Number(product.value?.stock)
  return Number.isFinite(value) ? Math.max(0, Math.floor(value)) : 0
})

const soldOut = computed(() => stock.value === 0)

const status = computed(() =>
  product.value?.status === 'on' ? 'on' : 'off',
)

const statusText = computed(() =>
  status.value === 'on' ? '在售' : '已下架',
)

const category = computed(() => {
  const value = product.value?.category
  return typeof value === 'string' && value.trim() ? value : '未分类'
})

const sellerName = computed(() => {
  const value = product.value?.sellerName
  return typeof value === 'string' && value.trim() ? value : '匿名卖家'
})

const title = computed(() => normalizeProductTitle(product.value?.title))

const description = computed(() => {
  const value = product.value?.description
  return typeof value === 'string' && value.trim() ? value : '暂无描述'
})

const canBuy = computed(
  () => status.value === 'on' && !soldOut.value,
)

const isOrdering = computed(
  () => String(props.orderingProductId) === String(product.value?.id),
)

const buyText = computed(() => {
  if (isOrdering.value) return '提交中…'
  if (soldOut.value) return '已售罄'
  if (status.value === 'off') return '已下架'
  return '购买'
})

watch(
  stock,
  (currentStock) => {
    const currentQuantity = Math.floor(Number(quantity.value))

    if (currentStock <= 0) {
      quantity.value = 1
    } else if (!Number.isFinite(currentQuantity) || currentQuantity < 1) {
      quantity.value = 1
    } else if (currentQuantity > currentStock) {
      quantity.value = currentStock
    }
  },
  { immediate: true },
)

async function loadProduct() {
  const currentRequestId = ++requestId
  loading.value = true
  errorMessage.value = ''
  product.value = null

  try {
    const data = await getProduct(route.params.id)
    if (currentRequestId !== requestId) return
    product.value = data
    if (!data) errorMessage.value = '商品不存在'
  } catch (error) {
    if (currentRequestId !== requestId) return
    errorMessage.value =
      error instanceof Error && error.message
        ? error.message
        : '商品加载失败，请稍后重试'
  } finally {
    if (currentRequestId === requestId) loading.value = false
  }
}

function handleBuy() {
  if (!canBuy.value || isOrdering.value || !product.value) return
  emit('buy', {
    product: product.value,
    quantity: Number(quantity.value),
  })
}

watch(() => route.params.id, loadProduct, { immediate: true })

onUnmounted(() => {
  // 让卸载前已发出的详情请求失效，避免返回后继续改写状态。
  requestId += 1
})
</script>

<template>
  <main class="product-detail-view">
    <RouterLink class="product-detail-view__back" :to="{ name: 'products' }">
      ← 返回商品列表
    </RouterLink>

    <p
      v-if="viewState === 'loading'"
      class="product-detail-view__state"
      role="status"
    >
      加载中…
    </p>

    <section
      v-else-if="viewState === 'error'"
      class="product-detail-view__state product-detail-view__state--error"
      role="alert"
    >
      <h1>{{ errorMessage }}</h1>
      <div class="product-detail-view__state-actions">
        <button type="button" @click="loadProduct">重试</button>
        <RouterLink :to="{ name: 'products' }">返回商品列表</RouterLink>
      </div>
    </section>

    <article v-else class="product-detail">
      <header class="product-detail__header">
        <div class="product-detail__heading">
          <p class="product-detail__eyebrow">商品详情</p>
          <h1>{{ title }}</h1>
        </div>
        <span
          class="product-detail__status"
          :class="`product-detail__status--${status}`"
        >
          {{ statusText }}
        </span>
      </header>

      <p class="product-detail__price">{{ formatPrice(product.price) }}</p>

      <dl class="product-detail__meta">
        <div>
          <dt>库存</dt>
          <dd>{{ stock }}</dd>
        </div>
        <div>
          <dt>分类</dt>
          <dd>{{ category }}</dd>
        </div>
        <div>
          <dt>卖家</dt>
          <dd>{{ sellerName }}</dd>
        </div>
      </dl>

      <span v-if="soldOut" class="product-detail__sold-out">缺货</span>

      <section class="product-detail__description">
        <h2>商品描述</h2>
        <p>{{ description }}</p>
      </section>

      <footer class="product-detail__footer">
        <div class="product-detail__purchase">
          <div class="product-detail__quantity">
            <label for="product-detail-quantity">购买数量</label>
            <input
              id="product-detail-quantity"
              v-model.number="quantity"
              type="number"
              min="1"
              :max="stock"
              step="1"
              inputmode="numeric"
              :disabled="soldOut || isOrdering"
            />
            <span>件</span>
          </div>
          <p v-if="soldOut">该商品暂时缺货，请稍后再来看看。</p>
          <p v-else-if="status === 'off'">该商品已下架，暂不可购买。</p>
          <p v-else>库存充足，可立即下单。</p>
        </div>
        <button
          type="button"
          :disabled="!canBuy || isOrdering"
          @click="handleBuy"
        >
          {{ buyText }}
        </button>
      </footer>
    </article>
  </main>
</template>

<style scoped>
.product-detail-view {
  width: min(920px, 100%);
  margin: 0 auto;
  padding: 30px 20px 56px;
  color: #27272a;
}

.product-detail-view__back {
  display: inline-flex;
  margin-bottom: 20px;
  color: #52525b;
  font-size: 14px;
  font-weight: 700;
  text-decoration: none;
}

.product-detail-view__back:hover {
  color: #18181b;
}

.product-detail-view__state {
  display: grid;
  justify-items: center;
  gap: 18px;
  margin: 0;
  padding: 72px 20px;
  border: 1px dashed #d4d4d8;
  border-radius: 16px;
  background: #fafafa;
  color: #52525b;
  text-align: center;
}

.product-detail-view__state h1 {
  margin: 0;
  color: #991b1b;
  font-size: 24px;
}

.product-detail-view__state--error {
  border-color: #fecaca;
  background: #fef2f2;
}

.product-detail-view__state-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.product-detail-view__state-actions button,
.product-detail-view__state-actions a {
  padding: 9px 16px;
  border: 1px solid #d4d4d8;
  border-radius: 9px;
  background: #ffffff;
  color: #3f3f46;
  cursor: pointer;
  font: inherit;
  font-size: 14px;
  font-weight: 700;
  text-decoration: none;
}

.product-detail {
  padding: clamp(22px, 5vw, 42px);
  border: 1px solid #e4e4e7;
  border-radius: 18px;
  background: #ffffff;
  box-shadow: 0 12px 36px rgb(24 24 27 / 7%);
}

.product-detail__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.product-detail__heading {
  min-width: 0;
}

.product-detail__eyebrow {
  margin: 0 0 6px;
  color: #71717a;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.product-detail__header h1 {
  margin: 0;
  overflow-wrap: anywhere;
  color: #18181b;
  font-size: clamp(28px, 5vw, 42px);
  line-height: 1.16;
}

.product-detail__status {
  flex: none;
  padding: 6px 11px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}

.product-detail__status--on {
  background: #dcfce7;
  color: #166534;
}

.product-detail__status--off {
  background: #e4e4e7;
  color: #52525b;
}

.product-detail__price {
  margin: 28px 0 22px;
  color: #c2410c;
  font-size: clamp(32px, 6vw, 48px);
  font-weight: 850;
  letter-spacing: -0.03em;
}

.product-detail__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px 36px;
  margin: 0;
  padding: 20px 0;
  border-top: 1px solid #e4e4e7;
  border-bottom: 1px solid #e4e4e7;
}

.product-detail__meta div {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.product-detail__meta dt {
  color: #71717a;
  font-size: 13px;
}

.product-detail__meta dd {
  margin: 0;
  color: #3f3f46;
  font-size: 14px;
  font-weight: 700;
}

.product-detail__sold-out {
  display: inline-flex;
  margin-top: 18px;
  padding: 5px 10px;
  border-radius: 999px;
  background: #fee2e2;
  color: #b91c1c;
  font-size: 13px;
  font-weight: 800;
}

.product-detail__description {
  margin-top: 30px;
}

.product-detail__description h2 {
  margin: 0 0 10px;
  color: #18181b;
  font-size: 18px;
}

.product-detail__description p {
  margin: 0;
  color: #52525b;
  line-height: 1.8;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.product-detail__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-top: 32px;
  padding-top: 22px;
  border-top: 1px solid #e4e4e7;
}

.product-detail__purchase {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px 20px;
}

.product-detail__purchase p {
  margin: 0;
  color: #71717a;
  font-size: 14px;
}

.product-detail__quantity {
  display: flex;
  align-items: center;
  gap: 7px;
  color: #52525b;
  font-size: 13px;
  font-weight: 700;
}

.product-detail__quantity input {
  width: 62px;
  box-sizing: border-box;
  padding: 8px;
  border: 1px solid #d4d4d8;
  border-radius: 8px;
  background: #ffffff;
  color: #18181b;
  font: inherit;
  font-size: 13px;
  text-align: center;
}

.product-detail__quantity input:focus {
  border-color: #52525b;
  box-shadow: 0 0 0 3px rgb(82 82 91 / 12%);
  outline: none;
}

.product-detail__quantity input:disabled {
  background: #e4e4e7;
  color: #71717a;
}

.product-detail__footer button {
  min-width: 132px;
  padding: 12px 24px;
  border: 0;
  border-radius: 10px;
  background: #18181b;
  color: #ffffff;
  cursor: pointer;
  font: inherit;
  font-size: 15px;
  font-weight: 800;
}

.product-detail__footer button:hover:not(:disabled) {
  background: #3f3f46;
}

.product-detail__footer button:disabled {
  cursor: not-allowed;
  background: #a1a1aa;
}

@media (max-width: 560px) {
  .product-detail-view {
    padding: 22px 14px 40px;
  }

  .product-detail__header,
  .product-detail__footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .product-detail__footer button {
    width: 100%;
  }
}
</style>
