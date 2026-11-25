<script setup>
import { computed, onUnmounted, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { createOrder } from './api/index.js'
import {
  createDemoSession,
  ROLE_LABELS,
  USER_ROLES,
} from './utils/permissions.js'
import { getQuantityError } from './utils/quantity.js'

const route = useRoute()
const router = useRouter()
const creatingOrder = ref(false)
const orderingProductId = ref(null)
const ordersRefreshKey = ref(0)
const appNotice = ref('')
const demoRole = ref(USER_ROLES.admin)
const demoSellerId = ref('')
const demoSession = computed(() =>
  createDemoSession(demoRole.value, demoSellerId.value),
)
const demoSessionLabel = computed(() => {
  const roleLabel = ROLE_LABELS[demoSession.value.role] ?? '未知角色'

  if (demoSession.value.role !== USER_ROLES.seller) return roleLabel

  return demoSession.value.id
    ? `${roleLabel} · 编号 ${demoSession.value.id}`
    : `${roleLabel} / 未填写编号`
})
const demoHint = computed(() => {
  if (demoSession.value.role !== USER_ROLES.seller) return ''

  if (!demoSession.value.id) {
    return '填写卖家编号后，才能匹配到该卖家名下的商品。'
  }

  return '当前商品暂未关联卖家编号，因此这个卖家身份只能浏览。'
})
let noticeTimer = null

const isProductsActive = computed(
  () => route.name === 'products' || route.name === 'product-detail',
)
const isCreateActive = computed(() => route.name === 'product-create')
const isOrdersActive = computed(() => route.name === 'orders')
const productSession = computed(() =>
  route.name === 'products' || route.name === 'product-detail'
    ? demoSession.value
    : null,
)

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

  const quantityError = getQuantityError(quantity, product.stock)
  if (quantityError) {
    showNotice(quantityError)
    return
  }

  if (product.status !== 'on') {
    showNotice('商品已下架，暂不可购买')
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
      <div class="app-nav__identity">
        <span class="app-nav__brand">校园好物市集</span>
        <div class="app-nav__demo" aria-label="身份预览控制">
          <label class="app-nav__demo-label" for="demo-role"> 当前身份 </label>
          <select id="demo-role" v-model="demoRole">
            <option :value="USER_ROLES.visitor">游客</option>
            <option :value="USER_ROLES.buyer">买家</option>
            <option :value="USER_ROLES.seller">卖家</option>
            <option :value="USER_ROLES.admin">管理员</option>
          </select>
          <template v-if="demoRole === USER_ROLES.seller">
            <label class="app-nav__demo-label" for="demo-seller-id">
              卖家编号
            </label>
            <input
              id="demo-seller-id"
              v-model="demoSellerId"
              type="text"
              inputmode="numeric"
              autocomplete="off"
              spellcheck="false"
              placeholder="例如 42"
              aria-describedby="demo-seller-hint"
            />
          </template>
          <span
            class="app-nav__session"
            title="仅用于页面预览，不会改变真实账号"
          >
            当前身份：{{ demoSessionLabel }}
          </span>
        </div>
        <p
          v-if="demoHint"
          id="demo-seller-hint"
          class="app-nav__demo-note"
          role="note"
        >
          {{ demoHint }}
        </p>
      </div>
      <div class="app-nav__links">
        <RouterLink
          :to="{ name: 'products' }"
          :class="{ 'app-nav__link--active': isProductsActive }"
          :aria-current="isProductsActive ? 'page' : undefined"
        >
          商品列表
        </RouterLink>
        <RouterLink
          :to="{ name: 'product-create' }"
          :class="{ 'app-nav__link--active': isCreateActive }"
          :aria-current="isCreateActive ? 'page' : undefined"
        >
          上架商品
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
        :session="productSession"
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

.app-nav__identity {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 10px;
}

.app-nav__brand {
  color: #18181b;
  font-size: 16px;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.app-nav__demo {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 8px;
}

.app-nav__demo-label {
  color: #71717a;
  font-size: 11px;
  font-weight: 700;
}

.app-nav__demo select,
.app-nav__demo input {
  box-sizing: border-box;
  padding: 5px 7px;
  border: 1px solid #d4d4d8;
  border-radius: 7px;
  background: #ffffff;
  color: #18181b;
  font: inherit;
  font-size: 12px;
  font-weight: 650;
}

.app-nav__demo select {
  max-width: 96px;
}

.app-nav__demo input {
  width: 82px;
}

.app-nav__demo select:focus-visible,
.app-nav__demo input:focus-visible {
  border-color: #52525b;
  box-shadow: 0 0 0 3px rgb(82 82 91 / 12%);
  outline: none;
}

.app-nav__demo-note {
  flex-basis: 100%;
  margin: 0;
  color: #92400e;
  font-size: 11px;
  font-weight: 650;
  line-height: 1.5;
}

.app-nav__session {
  padding: 3px 8px;
  border: 1px solid #fed7aa;
  border-radius: 999px;
  background: #fff7ed;
  color: #9a3412;
  font-size: 11px;
  font-weight: 800;
  white-space: nowrap;
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

@media (max-width: 900px) {
  .app-nav {
    flex-wrap: wrap;
  }

  .app-nav__links {
    width: 100%;
  }
}

@media (max-width: 560px) {
  .app-nav {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
    padding: 12px 14px;
  }

  .app-nav__identity,
  .app-nav__links {
    width: 100%;
    flex-wrap: wrap;
  }

  .app-nav__demo {
    width: 100%;
  }
}
</style>
