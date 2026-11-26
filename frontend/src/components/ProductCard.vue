<script setup>
import { computed, ref, watch } from 'vue'
import { formatPrice } from '../utils/money.js'
import { normalizeProductTitle } from '../utils/product.js'

const props = defineProps({
  product: {
    type: Object,
    required: true,
  },
  ordering: {
    type: Boolean,
    default: false,
  },
  orderLocked: {
    type: Boolean,
    default: false,
  },
  statusUpdating: {
    type: Boolean,
    default: false,
  },
  canManage: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['buy', 'toggle-status'])

const quantity = ref(1)

const title = computed(() => normalizeProductTitle(props.product?.title))

const category = computed(() => {
  const value = props.product?.category
  return typeof value === 'string' && value.trim() ? value : '未分类'
})

const sellerName = computed(() => {
  const value = props.product?.sellerName
  return typeof value === 'string' && value.trim() ? value.trim() : '匿名卖家'
})

const productId = computed(() => {
  const value = props.product?.id
  return value === undefined || value === null || String(value).trim() === ''
    ? '未知'
    : String(value)
})

const stock = computed(() => {
  const value = Number(props.product?.stock)
  return Number.isFinite(value) ? Math.max(0, Math.floor(value)) : 0
})

const soldOut = computed(() => stock.value === 0)

const status = computed(() => (props.product?.status === 'on' ? 'on' : 'off'))

const statusText = computed(() => (status.value === 'on' ? '在售' : '已下架'))

const statusClass = computed(() =>
  status.value === 'on'
    ? 'product-card__status--on'
    : 'product-card__status--off',
)

const canBuy = computed(() => status.value === 'on' && !soldOut.value)

const buyText = computed(() => {
  if (props.ordering) return '提交中…'
  if (status.value === 'off') return '已下架'
  if (soldOut.value) return '已售罄'
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

function handleBuy() {
  if (!canBuy.value || props.ordering || props.orderLocked) return
  emit('buy', {
    product: props.product,
    quantity: Number(quantity.value),
  })
}

function handleToggleStatus() {
  if (!props.canManage || props.statusUpdating) return
  emit('toggle-status', props.product)
}
</script>

<template>
  <article class="product-card" :class="{ 'product-card--sold-out': soldOut }">
    <div class="product-card__header">
      <h3 class="product-card__title" :title="title">
        <RouterLink
          class="product-card__title-link"
          :to="{ name: 'product-detail', params: { id: product.id } }"
        >
          {{ title }}
        </RouterLink>
      </h3>
      <span class="product-card__status" :class="statusClass">
        {{ statusText }}
      </span>
    </div>

    <p class="product-card__price">{{ formatPrice(product.price) }}</p>

    <dl class="product-card__meta">
      <div class="product-card__meta-item">
        <dt>商品编号</dt>
        <dd>{{ productId }}</dd>
      </div>
      <div class="product-card__meta-item">
        <dt>库存</dt>
        <dd>{{ stock }}</dd>
      </div>
      <div class="product-card__meta-item">
        <dt>分类</dt>
        <dd>{{ category }}</dd>
      </div>
      <div class="product-card__meta-item">
        <dt>卖家</dt>
        <dd class="product-card__seller" :title="sellerName">
          <span class="product-card__seller-name">{{ sellerName }}</span>
        </dd>
      </div>
    </dl>

    <div class="product-card__quantity">
      <label :for="`product-quantity-${product.id}`">购买数量</label>
      <div class="product-card__quantity-control">
        <input
          :id="`product-quantity-${product.id}`"
          v-model.number="quantity"
          type="number"
          min="1"
          :max="stock"
          step="1"
          inputmode="numeric"
          :disabled="!canBuy || ordering || orderLocked"
        />
        <span>件</span>
      </div>
    </div>

    <div class="product-card__footer">
      <span v-if="status === 'off'" class="product-card__unavailable">
        已下架
      </span>
      <span v-else-if="soldOut" class="product-card__stock-mark">缺货</span>
      <span v-else class="product-card__available">可购买</span>
      <div class="product-card__actions">
        <button
          class="product-card__status-action"
          type="button"
          :disabled="statusUpdating || !canManage"
          :title="canManage ? undefined : '仅商品所有者或管理员可操作'"
          @click="handleToggleStatus"
        >
          {{
            statusUpdating
              ? '处理中…'
              : !canManage
                ? '无管理权限'
                : status === 'on'
                  ? '下架'
                  : '上架'
          }}
        </button>
        <RouterLink
          class="product-card__detail"
          :to="{ name: 'product-detail', params: { id: product.id } }"
        >
          详情
        </RouterLink>
        <button
          class="product-card__buy"
          type="button"
          :disabled="!canBuy || ordering || orderLocked"
          @click="handleBuy"
        >
          {{ buyText }}
        </button>
      </div>
    </div>
  </article>
</template>

<style scoped>
.product-card {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 16px;
  padding: 18px;
  border: 1px solid #e4e4e7;
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 8px 24px rgb(24 24 27 / 5%);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.product-card:not(.product-card--sold-out):hover {
  border-color: #a1a1aa;
  box-shadow: 0 12px 30px rgb(24 24 27 / 9%);
  transform: translateY(-2px);
}

.product-card--sold-out {
  border-color: #d4d4d8;
  background: #f4f4f5;
  filter: grayscale(1);
  opacity: 0.72;
}

.product-card__header {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
}

.product-card__title {
  min-width: 0;
  flex: 1;
  margin: 0;
  overflow: hidden;
  color: #18181b;
  font-size: 18px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-card__title-link {
  display: block;
  overflow: hidden;
  color: inherit;
  text-decoration: none;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-card__title-link:hover {
  color: #c2410c;
}

.product-card__status {
  flex: none;
  padding: 4px 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.2;
}

.product-card__status--on {
  background: #dcfce7;
  color: #166534;
}

.product-card__status--off {
  background: #e4e4e7;
  color: #52525b;
}

.product-card__price {
  margin: 0;
  color: #c2410c;
  font-size: 26px;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.product-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  margin: 0;
}

.product-card__meta-item {
  display: flex;
  min-width: 0;
  align-items: baseline;
  gap: 8px;
}

.product-card__meta dt {
  color: #71717a;
  font-size: 13px;
}

.product-card__meta dd {
  margin: 0;
  color: #3f3f46;
  font-size: 14px;
  font-weight: 600;
}

.product-card__seller {
  display: flex;
  min-width: 0;
  max-width: 18em;
  align-items: center;
  gap: 6px;
}

.product-card__seller-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-card__quantity {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 2px;
}

.product-card__quantity label {
  color: #71717a;
  font-size: 13px;
  font-weight: 600;
}

.product-card__quantity-control {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #71717a;
  font-size: 13px;
}

.product-card__quantity input {
  width: 58px;
  box-sizing: border-box;
  padding: 7px 8px;
  border: 1px solid #d4d4d8;
  border-radius: 8px;
  background: #ffffff;
  color: #18181b;
  font: inherit;
  font-size: 13px;
  text-align: center;
}

.product-card__quantity input:focus {
  border-color: #52525b;
  box-shadow: 0 0 0 3px rgb(82 82 91 / 12%);
  outline: none;
}

.product-card__quantity input:disabled {
  background: #e4e4e7;
  color: #71717a;
}

.product-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: auto;
  padding-top: 4px;
}

.product-card__stock-mark {
  flex: none;
  color: #b91c1c;
  font-size: 13px;
  font-weight: 700;
}

.product-card__available {
  flex: none;
  color: #15803d;
  font-size: 13px;
  font-weight: 600;
}

.product-card__unavailable {
  flex: none;
  color: #71717a;
  font-size: 13px;
  font-weight: 700;
}

.product-card__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-left: auto;
}

.product-card__status-action {
  padding: 7px 10px;
  border: 1px solid #d4d4d8;
  border-radius: 8px;
  background: #ffffff;
  color: #52525b;
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  font-weight: 700;
}

.product-card__status-action:hover:not(:disabled) {
  border-color: #a1a1aa;
  background: #fafafa;
}

.product-card__status-action:disabled {
  cursor: not-allowed;
  color: #a1a1aa;
}

.product-card__detail {
  color: #52525b;
  font-size: 13px;
  font-weight: 700;
  text-decoration: none;
  white-space: nowrap;
}

.product-card__detail:hover {
  color: #18181b;
}

.product-card__buy {
  padding: 8px 15px;
  border: 0;
  border-radius: 9px;
  background: #18181b;
  color: #ffffff;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
}

.product-card__buy:hover:not(:disabled) {
  background: #3f3f46;
}

.product-card__buy:disabled {
  cursor: not-allowed;
  background: #a1a1aa;
  color: #f4f4f5;
}

@media (max-width: 360px) {
  .product-card__footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .product-card__actions {
    width: 100%;
    justify-content: space-between;
    margin-left: 0;
  }
}
</style>
