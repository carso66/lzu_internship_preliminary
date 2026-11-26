<script setup>
import { computed } from 'vue'
import ProductCard from './ProductCard.vue'
import { formatPrice } from '../utils/money.js'
import { canManageProduct } from '../utils/permissions.js'

const props = defineProps({
  group: {
    type: Object,
    required: true,
  },
  session: {
    type: Object,
    default: null,
  },
  orderingProductId: {
    type: [Number, String],
    default: null,
  },
  statusUpdatingIds: {
    type: Object,
    default: () => new Set(),
  },
})

const emit = defineEmits(['buy', 'toggle-status'])

const expandedByDefault = computed(() => props.group.productCount === 1)

const groupingLabel = computed(() =>
  props.group.productCount > 1 ? '同类商品' : '商品',
)

const priceRange = computed(() => {
  const min = props.group.priceMin
  const max = props.group.priceMax

  if (min === null) return '价格待确认'
  if (min === max) return formatPrice(min)
  return `${formatPrice(min)}–${formatPrice(max)}`
})

const scopeClass = computed(
  () => `product-group__scope--${props.group.permissions?.scope ?? 'none'}`,
)

function canManage(product) {
  return canManageProduct(product, props.session)
}

function isStatusUpdating(id) {
  return props.statusUpdatingIds.has(String(id))
}
</script>

<template>
  <details class="product-group" :open="expandedByDefault">
    <summary class="product-group__summary">
      <div class="product-group__overview">
        <div class="product-group__badges">
          <span class="product-group__mode">{{ groupingLabel }}</span>
          <span class="product-group__badge">
            {{ group.productCount }} 件商品
          </span>
          <span class="product-group__badge">
            {{ group.sellerCount }} 个卖家信息
          </span>
        </div>

        <h2 class="product-group__title">{{ group.title }}</h2>

        <ul
          v-if="group.productCount > 1 && group.tags?.length"
          class="product-group__tags"
          aria-label="商品特点"
        >
          <li v-for="tag in group.tags" :key="tag">{{ tag }}</li>
        </ul>

        <p class="product-group__stats">
          <span>价格 {{ priceRange }}</span>
          <span>库存合计 {{ group.totalStock }}</span>
          <span>在售 {{ group.onSaleCount }}</span>
          <span>可购买 {{ group.availableCount }}</span>
          <span>下架 {{ group.offShelfCount }}</span>
          <span v-if="group.soldOutCount">缺货 {{ group.soldOutCount }}</span>
        </p>
      </div>

      <div class="product-group__permission" aria-label="商品管理权限">
        <span class="product-group__role">
          当前身份：{{ group.permissions.roleLabel }}
        </span>
        <span class="product-group__scope" :class="scopeClass">
          {{ group.permissions.scopeLabel }}
        </span>
        <span class="product-group__permission-detail">
          {{ group.permissions.permissionLabel }}
        </span>
      </div>
    </summary>

    <div class="product-group__body">
      <p v-if="group.productCount > 1" class="product-group__note">
        以下商品单独出售，价格、库存和操作互不影响。
      </p>

      <div class="product-group__products">
        <ProductCard
          v-for="product in group.products"
          :key="product.id"
          :product="product"
          :ordering="String(orderingProductId) === String(product.id)"
          :order-locked="orderingProductId !== null"
          :status-updating="isStatusUpdating(product.id)"
          :can-manage="canManage(product)"
          @buy="emit('buy', $event)"
          @toggle-status="emit('toggle-status', $event)"
        />
      </div>
    </div>
  </details>
</template>

<style scoped>
.product-group {
  overflow: clip;
  border: 1px solid #dedee3;
  border-radius: 18px;
  background: #fbfbfc;
}

.product-group[open] {
  border-color: #c7c7cd;
  box-shadow: 0 14px 36px rgb(24 24 27 / 7%);
}

.product-group__summary {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 22px;
  padding: 18px 20px;
  cursor: pointer;
  list-style-position: inside;
}

.product-group__summary:hover {
  background: #f7f7f8;
}

.product-group__summary:focus-visible {
  border-radius: 17px;
  box-shadow: inset 0 0 0 3px rgb(82 82 91 / 18%);
  outline: none;
}

.product-group__overview {
  min-width: 0;
  flex: 1;
}

.product-group__badges {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.product-group__mode,
.product-group__badge {
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 800;
  line-height: 1.3;
}

.product-group__mode {
  background: #18181b;
  color: #ffffff;
}

.product-group__badge {
  border: 1px solid #e4e4e7;
  background: #ffffff;
  color: #52525b;
}

.product-group__title {
  margin: 0;
  overflow: hidden;
  color: #18181b;
  font-size: 22px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-group__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 9px 0 0;
  padding: 0;
  list-style: none;
}

.product-group__tags li {
  padding: 3px 8px;
  border: 1px solid #e4e4e7;
  border-radius: 999px;
  background: #ffffff;
  color: #52525b;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.35;
}

.product-group__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 5px 14px;
  margin: 9px 0 0;
  color: #71717a;
  font-size: 13px;
  line-height: 1.5;
}

.product-group__permission {
  display: grid;
  flex: none;
  justify-items: end;
  gap: 5px;
  max-width: 220px;
  text-align: right;
}

.product-group__role,
.product-group__permission-detail {
  color: #71717a;
  font-size: 12px;
  font-weight: 700;
}

.product-group__scope {
  padding: 4px 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}

.product-group__scope--all {
  background: #dcfce7;
  color: #166534;
}

.product-group__scope--partial {
  background: #fef3c7;
  color: #92400e;
}

.product-group__scope--none {
  background: #e4e4e7;
  color: #52525b;
}

.product-group__body {
  padding: 4px 18px 18px;
  border-top: 1px solid #e4e4e7;
}

.product-group__note {
  margin: 14px 2px 12px;
  color: #71717a;
  font-size: 12px;
  line-height: 1.6;
}

.product-group__products {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(235px, 1fr));
  gap: 14px;
}

@media (max-width: 720px) {
  .product-group__summary {
    align-items: stretch;
    flex-direction: column;
    gap: 14px;
  }

  .product-group__permission {
    justify-items: start;
    max-width: none;
    text-align: left;
  }
}

@media (max-width: 560px) {
  .product-group__summary {
    padding: 16px;
  }

  .product-group__title {
    font-size: 19px;
  }

  .product-group__body {
    padding: 4px 12px 12px;
  }

  .product-group__products {
    grid-template-columns: 1fr;
  }
}
</style>
