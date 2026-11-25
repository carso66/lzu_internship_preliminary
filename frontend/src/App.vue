<script setup>
import { computed, onUnmounted, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { createOrder } from './api/index.js'

const route = useRoute()
const router = useRouter()
const creatingOrder = ref(false)
const orderingProductId = ref(null)
const ordersRefreshKey = ref(0)
const appNotice = ref('')
let noticeTimer = null

const isProductsActive = computed(
  () => route.name === 'products' || route.name === 'product-detail',
)
const isOrdersActive = computed(() => route.name === 'orders')

function showNotice(message) {
  appNotice.value = message
  window.clearTimeout(noticeTimer)
  noticeTimer = window.setTimeout(() => {
    appNotice.value = ''
  }, 3000)
}

async function handleBuy(payload) {
  if (creatingOrder.value) return

  const product = payload?.product ?? payload
  const quantity = Number(payload?.quantity ?? 1)

  if (!product?.id) {
    showNotice('商品信息不完整，请刷新后重试')
    return
  }

  if (!Number.isInteger(quantity) || quantity <= 0) {
    showNotice('购买数量必须是正整数')
    return
  }

  creatingOrder.value = true
  orderingProductId.value = product.id

  try {
    const order = await createOrder({
      productId: product.id,
      quantity,
    })
    ordersRefreshKey.value += 1
    await router.push({ name: 'orders' })
    showNotice(`订单 ${order.orderNo} 已创建`)
  } catch (error) {
    showNotice(
      error instanceof Error && error.message
        ? error.message
        : '下单失败，请稍后重试',
    )
  } finally {
    creatingOrder.value = false
    orderingProductId.value = null
  }
}

onUnmounted(() => {
  window.clearTimeout(noticeTimer)
})
</script>

<template>
  <div class="app-shell">
    <nav class="app-nav" aria-label="主导航">
      <span class="app-nav__brand">校园好物市集</span>
      <div class="app-nav__links">
        <RouterLink
          :to="{ name: 'products' }"
          :class="{ 'app-nav__link--active': isProductsActive }"
          :aria-current="isProductsActive ? 'page' : undefined"
        >
          商品列表
        </RouterLink>
        <RouterLink
          :to="{ name: 'orders' }"
          :class="{ 'app-nav__link--active': isOrdersActive }"
          :aria-current="isOrdersActive ? 'page' : undefined"
        >
          我的订单
        </RouterLink>
      </div>
    </nav>

    <RouterView v-slot="{ Component }">
      <component
        :is="Component"
        :ordering-product-id="orderingProductId"
        :refresh-key="ordersRefreshKey"
        @buy="handleBuy"
      />
    </RouterView>

    <Transition name="app-notice">
      <p v-if="appNotice" class="app-notice" role="status">
        {{ appNotice }}
      </p>
    </Transition>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
}

.app-nav {
  position: sticky;
  z-index: 10;
  top: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 14px max(20px, calc((100vw - 1200px) / 2));
  border-bottom: 1px solid #e4e4e7;
  background: rgb(255 255 255 / 92%);
  backdrop-filter: blur(14px);
}

.app-nav__brand {
  color: #18181b;
  font-size: 16px;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.app-nav__links {
  display: flex;
  gap: 6px;
}

.app-nav__links a {
  padding: 8px 13px;
  border-radius: 8px;
  color: #71717a;
  font-size: 14px;
  font-weight: 700;
  text-decoration: none;
}

.app-nav__links a:hover {
  background: #f4f4f5;
  color: #27272a;
}

.app-nav__links .app-nav__link--active {
  background: #18181b;
  color: #ffffff;
}

.app-notice {
  position: fixed;
  z-index: 30;
  top: 72px;
  right: 22px;
  max-width: min(360px, calc(100vw - 28px));
  margin: 0;
  padding: 12px 16px;
  border-radius: 10px;
  background: #18181b;
  box-shadow: 0 12px 30px rgb(24 24 27 / 22%);
  color: #ffffff;
  font-size: 14px;
  font-weight: 700;
}

.app-notice-enter-active,
.app-notice-leave-active {
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}

.app-notice-enter-from,
.app-notice-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

@media (max-width: 560px) {
  .app-nav {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
    padding: 12px 14px;
  }
}
</style>
