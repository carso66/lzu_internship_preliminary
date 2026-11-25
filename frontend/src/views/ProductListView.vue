<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { getProducts, updateProductStatus } from '../api/index.js'
import ProductCard from '../components/ProductCard.vue'
import { canManageProduct } from '../utils/permissions.js'
import { normalizeProductTitle } from '../utils/product.js'

const props = defineProps({
  orderingProductId: {
    type: [Number, String],
    default: null,
  },
  session: {
    type: Object,
    default: null,
  },
})

const emit = defineEmits(['buy'])

const products = ref([])
const keyword = ref('')
const loading = ref(true)
const errorMessage = ref('')
const statusUpdatingIds = ref(new Set())
const actionNotice = ref('')
const actionNoticeType = ref('success')

let actionNoticeTimer = null
let cancelled = false
let loadRequestId = 0

const filteredProducts = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  if (!query) return products.value

  return products.value.filter((product) =>
    normalizeProductTitle(product?.title, '').toLowerCase().includes(query),
  )
})

const viewState = computed(() => {
  if (loading.value) return 'loading'
  if (errorMessage.value) return 'error'
  if (filteredProducts.value.length === 0) return 'empty'
  return 'ready'
})

function canManage(product) {
  return canManageProduct(product, props.session)
}

function clearFilter() {
  keyword.value = ''
}

function handleBuy(payload) {
  emit('buy', payload)
}

function isStatusUpdating(id) {
  return statusUpdatingIds.value.has(String(id))
}

function markStatusUpdating(id, updating) {
  const next = new Set(statusUpdatingIds.value)
  const key = String(id)

  if (updating) {
    next.add(key)
  } else {
    next.delete(key)
  }

  statusUpdatingIds.value = next
}

function showActionNotice(message, type = 'success') {
  actionNotice.value = message
  actionNoticeType.value = type
  window.clearTimeout(actionNoticeTimer)
  actionNoticeTimer = window.setTimeout(() => {
    actionNotice.value = ''
  }, 3000)
}

async function handleToggleStatus(product) {
  const id = product?.id
  if (id === undefined || id === null || isStatusUpdating(id)) return
  if (!canManage(product)) {
    showActionNotice('仅商品所有者或管理员可以变更商品状态', 'error')
    return
  }

  const nextStatus = product.status === 'on' ? 'off' : 'on'
  markStatusUpdating(id, true)

  try {
    const result = await updateProductStatus(id, nextStatus)
    if (cancelled) return

    const confirmedStatus =
      result?.status === 'on' || result?.status === 'off'
        ? result.status
        : nextStatus

    products.value = products.value.map((item) =>
      String(item.id) === String(id)
        ? { ...item, status: confirmedStatus }
        : item,
    )
    showActionNotice(
      confirmedStatus === 'on' ? '商品已上架' : '商品已下架',
      'success',
    )
  } catch (error) {
    if (cancelled) return
    showActionNotice(
      error instanceof Error && error.message
        ? error.message
        : '状态更新失败，请稍后重试',
      'error',
    )
  } finally {
    if (!cancelled) markStatusUpdating(id, false)
  }
}

async function loadProducts() {
  const currentRequestId = ++loadRequestId
  loading.value = true
  errorMessage.value = ''

  try {
    const data = await getProducts({ pageSize: 50 })
    if (cancelled || currentRequestId !== loadRequestId) return
    products.value = Array.isArray(data?.items) ? data.items : []
  } catch (error) {
    if (cancelled || currentRequestId !== loadRequestId) return
    products.value = []
    errorMessage.value =
      error instanceof Error && error.message
        ? error.message
        : '商品加载失败，请稍后重试'
  } finally {
    if (!cancelled && currentRequestId === loadRequestId) {
      loading.value = false
    }
  }
}

onMounted(loadProducts)

onUnmounted(() => {
  cancelled = true
  loadRequestId += 1
  window.clearTimeout(actionNoticeTimer)
})
</script>

<template>
  <main class="product-list-view">
    <header class="product-list-view__header">
      <div class="product-list-view__heading">
        <div>
          <p class="product-list-view__eyebrow">校园好物市集</p>
          <h1>商品列表</h1>
        </div>
        <div class="product-list-view__heading-actions">
          <RouterLink
            class="product-list-view__create"
            :to="{ name: 'product-create' }"
          >
            上架商品
          </RouterLink>
          <button
            class="product-list-view__clear"
            type="button"
            @click="clearFilter"
          >
            清空筛选
          </button>
        </div>
      </div>

      <div class="product-list-view__search">
        <label for="product-search">搜索商品</label>
        <input
          id="product-search"
          v-model="keyword"
          type="search"
          placeholder="输入商品标题进行筛选"
          autocomplete="off"
        />
      </div>
    </header>

    <p class="product-list-view__rules">
      同名商品以商品编号区分；来源名称为展示信息，卖家身份未核验时仅供参考。
    </p>

    <Transition name="action-notice">
      <p
        v-if="actionNotice"
        class="product-list-view__action-notice"
        :class="`product-list-view__action-notice--${actionNoticeType}`"
        :role="actionNoticeType === 'error' ? 'alert' : 'status'"
        aria-live="polite"
      >
        {{ actionNotice }}
      </p>
    </Transition>

    <p
      v-if="viewState === 'loading'"
      class="product-list-view__state"
      role="status"
    >
      加载中…
    </p>

    <section
      v-else-if="viewState === 'error'"
      class="product-list-view__state product-list-view__state--error"
      role="alert"
    >
      <p>{{ errorMessage }}</p>
      <button type="button" @click="loadProducts">重试</button>
    </section>

    <section
      v-else-if="viewState === 'empty'"
      class="product-list-view__state product-list-view__state--empty"
    >
      <p>没找到相关商品</p>
      <button type="button" @click="clearFilter">清空筛选</button>
    </section>

    <section v-else class="product-list-view__grid" aria-label="商品列表">
      <ProductCard
        v-for="product in filteredProducts"
        :key="product.id"
        :product="product"
        :ordering="String(orderingProductId) === String(product.id)"
        :order-locked="orderingProductId !== null"
        :status-updating="isStatusUpdating(product.id)"
        :can-manage="canManage(product)"
        @buy="handleBuy"
        @toggle-status="handleToggleStatus"
      />
    </section>
  </main>
</template>

<style scoped>
.product-list-view {
  width: min(1200px, 100%);
  margin: 0 auto;
  padding: 32px 20px 56px;
  color: #27272a;
}

.product-list-view__header {
  display: grid;
  gap: 22px;
  margin-bottom: 28px;
}

.product-list-view__heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.product-list-view__heading h1 {
  margin: 2px 0 0;
  color: #18181b;
  font-size: clamp(28px, 4vw, 40px);
  line-height: 1.1;
}

.product-list-view__eyebrow {
  margin: 0;
  color: #71717a;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.product-list-view__heading-actions {
  display: flex;
  flex: none;
  align-items: center;
  gap: 10px;
}

.product-list-view__create {
  padding: 9px 16px;
  border-radius: 9px;
  background: #18181b;
  color: #ffffff;
  font-size: 14px;
  font-weight: 700;
  text-decoration: none;
}

.product-list-view__create:hover {
  background: #3f3f46;
}

.product-list-view__clear,
.product-list-view__state button {
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

.product-list-view__clear:hover,
.product-list-view__state button:hover {
  border-color: #a1a1aa;
  background: #fafafa;
}

.product-list-view__search {
  display: grid;
  max-width: 520px;
  gap: 8px;
}

.product-list-view__search label {
  color: #52525b;
  font-size: 13px;
  font-weight: 700;
}

.product-list-view__search input {
  width: 100%;
  box-sizing: border-box;
  padding: 12px 14px;
  border: 1px solid #d4d4d8;
  border-radius: 10px;
  background: #ffffff;
  color: #18181b;
  font: inherit;
  outline: none;
}

.product-list-view__search input:focus {
  border-color: #52525b;
  box-shadow: 0 0 0 3px rgb(82 82 91 / 12%);
}

.product-list-view__rules {
  margin: -10px 0 20px;
  color: #71717a;
  font-size: 13px;
  line-height: 1.6;
}

.product-list-view__action-notice {
  margin: 0 0 18px;
  padding: 11px 14px;
  border: 1px solid #bbf7d0;
  border-radius: 10px;
  background: #f0fdf4;
  color: #166534;
  font-size: 13px;
  font-weight: 700;
}

.product-list-view__action-notice--error {
  border-color: #fecaca;
  background: #fef2f2;
  color: #991b1b;
}

.action-notice-enter-active,
.action-notice-leave-active {
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
}

.action-notice-enter-from,
.action-notice-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.product-list-view__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(235px, 1fr));
  gap: 16px;
}

.product-list-view__state {
  display: grid;
  justify-items: center;
  gap: 16px;
  margin: 0;
  padding: 72px 20px;
  border: 1px dashed #d4d4d8;
  border-radius: 14px;
  background: #fafafa;
  color: #52525b;
  text-align: center;
}

.product-list-view__state p {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
}

.product-list-view__state--error {
  border-color: #fecaca;
  background: #fef2f2;
  color: #991b1b;
}

.product-list-view__state--empty {
  color: #71717a;
}

@media (max-width: 560px) {
  .product-list-view {
    padding: 24px 14px 40px;
  }

  .product-list-view__heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .product-list-view__grid {
    grid-template-columns: 1fr;
  }
}
</style>
