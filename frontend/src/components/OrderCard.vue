<script setup>
import { computed, ref } from 'vue'
import { formatPrice } from '../utils/money.js'
import { normalizeProductTitle } from '../utils/product.js'

const props = defineProps({
  order: {
    type: Object,
    required: true,
  },
  paying: {
    type: Boolean,
    default: false,
  },
  paymentLocked: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['pay'])

const method = ref('alipay')

const orderNo = computed(() => {
  const value = props.order?.orderNo
  return typeof value === 'string' && value.trim() ? value : '—'
})

const productTitle = computed(() => normalizeProductTitle(props.order?.productTitle))

const quantity = computed(() => {
  const value = Number(props.order?.quantity)
  return Number.isFinite(value) && value > 0 ? Math.floor(value) : 0
})

const totalPrice = computed(() => formatPrice(props.order?.totalAmount ?? 0))

const isPaid = computed(() => props.order?.status === 'paid')
const isCreated = computed(() => props.order?.status === 'created')

const statusText = computed(() => {
  if (isPaid.value) return '已支付'
  if (isCreated.value) return '待支付'
  return '状态未知'
})

const statusClass = computed(() => {
  if (isPaid.value) return 'order-card__status--paid'
  if (isCreated.value) return 'order-card__status--created'
  return 'order-card__status--unknown'
})

const buttonText = computed(() => {
  if (isPaid.value) return '已支付'
  if (props.paying) return '支付中…'
  if (isCreated.value) return '去支付'
  return '不可支付'
})

function handlePay() {
  if (props.paying || props.paymentLocked || !isCreated.value) return
  emit('pay', {
    orderId: props.order.id,
    method: method.value,
  })
}
</script>

<template>
  <article class="order-card">
    <header class="order-card__header">
      <div class="order-card__heading">
        <p class="order-card__number">订单号：{{ orderNo }}</p>
        <h2 class="order-card__title" :title="productTitle">
          {{ productTitle }}
        </h2>
      </div>
      <span class="order-card__status" :class="statusClass">
        {{ statusText }}
      </span>
    </header>

    <dl class="order-card__meta">
      <div class="order-card__meta-item">
        <dt>数量</dt>
        <dd>×{{ quantity }}</dd>
      </div>
      <div class="order-card__meta-item order-card__meta-item--amount">
        <dt>订单金额</dt>
        <dd>{{ totalPrice }}</dd>
      </div>
    </dl>

    <footer class="order-card__footer">
      <div v-if="isCreated" class="order-card__method">
        <label :for="`payment-method-${order.id}`">支付方式</label>
        <select
          :id="`payment-method-${order.id}`"
          v-model="method"
          :disabled="paying"
        >
          <option value="alipay">支付宝</option>
          <option value="wechat">微信</option>
        </select>
      </div>
      <p v-else-if="isPaid" class="order-card__paid-hint">
        该订单已完成支付
      </p>

      <button
        class="order-card__pay"
        type="button"
        :disabled="isPaid || paying || paymentLocked || !isCreated"
        @click="handlePay"
      >
        {{ buttonText }}
      </button>
    </footer>
  </article>
</template>

<style scoped>
.order-card {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 20px;
  padding: 20px;
  border: 1px solid #e4e4e7;
  border-radius: 15px;
  background: #ffffff;
  box-shadow: 0 8px 24px rgb(24 24 27 / 5%);
}

.order-card__header {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.order-card__heading {
  min-width: 0;
}

.order-card__number {
  margin: 0 0 7px;
  color: #71717a;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.order-card__title {
  margin: 0;
  overflow: hidden;
  color: #18181b;
  font-size: 19px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-card__status {
  flex: none;
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}

.order-card__status--created {
  background: #fef3c7;
  color: #92400e;
}

.order-card__status--paid {
  background: #dcfce7;
  color: #166534;
}

.order-card__status--unknown {
  background: #e4e4e7;
  color: #52525b;
}

.order-card__meta {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin: 0;
  padding: 16px 0;
  border-top: 1px solid #f4f4f5;
  border-bottom: 1px solid #f4f4f5;
}

.order-card__meta-item {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.order-card__meta-item--amount {
  margin-left: auto;
}

.order-card__meta dt {
  color: #71717a;
  font-size: 13px;
}

.order-card__meta dd {
  margin: 0;
  color: #3f3f46;
  font-size: 15px;
  font-weight: 700;
}

.order-card__meta-item--amount dd {
  color: #c2410c;
  font-size: 23px;
  letter-spacing: -0.02em;
}

.order-card__footer {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
}

.order-card__method {
  display: grid;
  gap: 6px;
}

.order-card__method label {
  color: #52525b;
  font-size: 12px;
  font-weight: 700;
}

.order-card__method select {
  min-width: 132px;
  padding: 9px 11px;
  border: 1px solid #d4d4d8;
  border-radius: 8px;
  background: #ffffff;
  color: #27272a;
  font: inherit;
  font-size: 14px;
}

.order-card__method select:disabled {
  background: #f4f4f5;
  color: #71717a;
}

.order-card__paid-hint {
  margin: 0;
  color: #15803d;
  font-size: 13px;
  font-weight: 600;
}

.order-card__pay {
  min-width: 116px;
  margin-left: auto;
  padding: 10px 20px;
  border: 0;
  border-radius: 9px;
  background: #18181b;
  color: #ffffff;
  cursor: pointer;
  font: inherit;
  font-size: 14px;
  font-weight: 700;
}

.order-card__pay:hover:not(:disabled) {
  background: #3f3f46;
}

.order-card__pay:disabled {
  cursor: not-allowed;
  background: #a1a1aa;
  color: #f4f4f5;
}

@media (max-width: 560px) {
  .order-card__header,
  .order-card__footer {
    align-items: stretch;
    flex-direction: column;
  }

  .order-card__status {
    align-self: flex-start;
  }

  .order-card__pay {
    width: 100%;
  }
}
</style>
