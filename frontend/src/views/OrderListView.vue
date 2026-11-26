<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { createPayment, getOrders, getPayment } from '../api/index.js'
import OrderCard from '../components/OrderCard.vue'

const POLL_INTERVAL_MS = 1000
const MAX_POLL_ATTEMPTS = 10

const props = defineProps({
  refreshKey: {
    type: Number,
    default: 0,
  },
})

const orders = ref([])
const loading = ref(true)
const errorMessage = ref('')
const payingOrderId = ref(null)
const notice = ref(null)

let pollTimer = null
let pollToken = 0
let cancelled = false
let loadRequestId = 0

const viewState = computed(() => {
  if (loading.value) return 'loading'
  if (errorMessage.value) return 'error'
  if (orders.value.length === 0) return 'empty'
  return 'ready'
})

function showNotice(message, type = 'success') {
  notice.value = { message, type }
}

function closeNotice() {
  notice.value = null
}

async function loadOrders() {
  const currentRequestId = ++loadRequestId
  loading.value = true
  errorMessage.value = ''

  try {
    const data = await getOrders()
    if (cancelled || currentRequestId !== loadRequestId) return

    // mock/server.mjs 的 GET /api/orders 返回 { total, page, pageSize, items }。
    // 同时兼容 getOrders() 未来直接返回数组的情况。
    orders.value = Array.isArray(data?.items)
      ? data.items
      : Array.isArray(data)
        ? data
        : []
  } catch (error) {
    if (cancelled || currentRequestId !== loadRequestId) return
    orders.value = []
    errorMessage.value =
      error instanceof Error && error.message
        ? error.message
        : '订单加载失败，请稍后重试'
  } finally {
    if (!cancelled && currentRequestId === loadRequestId) {
      loading.value = false
    }
  }
}

function stopPolling() {
  // 自增 token 使已经发出的旧轮询回调失效。
  pollToken += 1
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

function markOrderPaid(orderId) {
  const order = orders.value.find((item) => String(item.id) === String(orderId))
  if (!order) return

  order.status = 'paid'
  order.paidAt = new Date().toISOString()
}

function pollPayment(orderId, paymentId) {
  stopPolling()

  const token = pollToken
  let attempts = 0
  let requestInFlight = false

  pollTimer = window.setInterval(async () => {
    if (cancelled || token !== pollToken || requestInFlight) return

    requestInFlight = true
    attempts += 1

    try {
      const payment = await getPayment(paymentId)

      // 请求返回时页面可能已经卸载，此时禁止继续 setState 或发起后续轮询。
      if (cancelled || token !== pollToken) return

      if (payment?.status === 'paid') {
        stopPolling()
        markOrderPaid(orderId)
        payingOrderId.value = null
        showNotice('支付成功', 'success')
        return
      }

      if (payment?.status === 'processing') {
        if (attempts >= MAX_POLL_ATTEMPTS) {
          stopPolling()
          payingOrderId.value = null
          showNotice('支付状态未确认，请稍后在订单列表查看', 'warning')
        }
        return
      }

      stopPolling()
      payingOrderId.value = null
      showNotice('支付状态异常，请稍后在订单列表查看', 'error')
    } catch (error) {
      if (cancelled || token !== pollToken) return

      stopPolling()
      payingOrderId.value = null
      showNotice(
        error instanceof Error && error.message
          ? error.message
          : '支付状态查询失败，请稍后重试',
        'error',
      )
    } finally {
      requestInFlight = false
    }
  }, POLL_INTERVAL_MS)
}

async function handlePay({ orderId, method }) {
  if (cancelled || payingOrderId.value !== null) return

  stopPolling()
  payingOrderId.value = orderId
  closeNotice()

  try {
    const payment = await createPayment({ orderId, method })
    if (cancelled) return

    const paymentId = payment?.paymentId
    if (!paymentId) {
      throw new Error('未获取到支付单号，请稍后重试')
    }

    pollPayment(orderId, paymentId)
  } catch (error) {
    if (cancelled) return

    stopPolling()
    payingOrderId.value = null
    showNotice(
      error instanceof Error && error.message
        ? error.message
        : '支付创建失败，请稍后重试',
      'error',
    )
  }
}

watch(
  () => props.refreshKey,
  () => {
    if (!cancelled) loadOrders()
  },
)

onMounted(loadOrders)

onUnmounted(() => {
  // 关键：离开页面先标记已销毁，再清理唯一的活动定时器。
  // 这样已经在途的 GET /payments 返回后也不会再 setState 或继续轮询。
  cancelled = true
  loadRequestId += 1
  stopPolling()
})
</script>

<template>
  <main class="order-list-view">
    <header class="order-list-view__header">
      <div>
        <p class="order-list-view__eyebrow">我的交易</p>
        <h1>订单列表</h1>
        <p class="order-list-view__hint">
          待支付订单可选择支付宝或微信完成支付。
        </p>
      </div>
      <button
        class="order-list-view__refresh"
        type="button"
        :disabled="loading || payingOrderId !== null"
        @click="loadOrders"
      >
        刷新订单
      </button>
    </header>

    <Transition name="notice">
      <div
        v-if="notice"
        class="order-list-view__notice"
        :class="`order-list-view__notice--${notice.type}`"
        :role="notice.type === 'error' ? 'alert' : 'status'"
        aria-live="polite"
      >
        <span>{{ notice.message }}</span>
        <button type="button" aria-label="关闭提示" @click="closeNotice">
          ×
        </button>
      </div>
    </Transition>

    <p
      v-if="viewState === 'loading'"
      class="order-list-view__state"
      role="status"
    >
      加载中…
    </p>

    <section
      v-else-if="viewState === 'error'"
      class="order-list-view__state order-list-view__state--error"
      role="alert"
    >
      <p>{{ errorMessage }}</p>
      <button type="button" @click="loadOrders">重试</button>
    </section>

    <section
      v-else-if="viewState === 'empty'"
      class="order-list-view__state order-list-view__state--empty"
    >
      <p>暂无订单</p>
      <span>购买商品后，新订单会显示在这里。</span>
    </section>

    <section v-else class="order-list-view__grid" aria-label="订单列表">
      <OrderCard
        v-for="order in orders"
        :key="order.id"
        :order="order"
        :paying="String(payingOrderId) === String(order.id)"
        :payment-locked="
          payingOrderId !== null && String(payingOrderId) !== String(order.id)
        "
        @pay="handlePay"
      />
    </section>
  </main>
</template>

<style scoped>
.order-list-view {
  width: min(1040px, 100%);
  margin: 0 auto;
  padding: 34px 20px 56px;
  color: #27272a;
}

.order-list-view__header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
}

.order-list-view__eyebrow {
  margin: 0;
  color: #71717a;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.order-list-view__header h1 {
  margin: 4px 0 8px;
  color: #18181b;
  font-size: clamp(28px, 4vw, 40px);
  line-height: 1.1;
}

.order-list-view__hint {
  margin: 0;
  color: #71717a;
  font-size: 14px;
}

.order-list-view__refresh,
.order-list-view__state button {
  padding: 9px 16px;
  border: 1px solid #d4d4d8;
  border-radius: 9px;
  background: #ffffff;
  color: #3f3f46;
  cursor: pointer;
  font: inherit;
  font-size: 14px;
  font-weight: 700;
}

.order-list-view__refresh:hover:not(:disabled),
.order-list-view__state button:hover {
  border-color: #a1a1aa;
  background: #fafafa;
}

.order-list-view__refresh:disabled {
  cursor: not-allowed;
  color: #a1a1aa;
}

.order-list-view__notice {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
  padding: 12px 15px;
  border: 1px solid;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 700;
}

.order-list-view__notice--success {
  border-color: #bbf7d0;
  background: #f0fdf4;
  color: #166534;
}

.order-list-view__notice--warning {
  border-color: #fde68a;
  background: #fffbeb;
  color: #92400e;
}

.order-list-view__notice--error {
  border-color: #fecaca;
  background: #fef2f2;
  color: #991b1b;
}

.order-list-view__notice button {
  flex: none;
  padding: 0 4px;
  border: 0;
  background: transparent;
  color: currentColor;
  cursor: pointer;
  font: inherit;
  font-size: 20px;
  line-height: 1;
}

.order-list-view__grid {
  display: grid;
  gap: 16px;
}

.order-list-view__state {
  display: grid;
  justify-items: center;
  gap: 12px;
  margin: 0;
  padding: 72px 20px;
  border: 1px dashed #d4d4d8;
  border-radius: 14px;
  background: #fafafa;
  color: #52525b;
  text-align: center;
}

.order-list-view__state p {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
}

.order-list-view__state span {
  color: #71717a;
  font-size: 14px;
}

.order-list-view__state--error {
  border-color: #fecaca;
  background: #fef2f2;
  color: #991b1b;
}

.order-list-view__state--empty {
  color: #71717a;
}

.notice-enter-active,
.notice-leave-active {
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}

.notice-enter-from,
.notice-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

@media (max-width: 560px) {
  .order-list-view {
    padding: 24px 14px 40px;
  }

  .order-list-view__header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
