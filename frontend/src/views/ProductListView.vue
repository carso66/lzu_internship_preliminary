<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import OrderForm from '../components/OrderForm.vue'
import ProductList from '../components/ProductList.vue'
import { updateProductStatus } from '../api/products.js'
import { useProducts } from '../composables/useProducts.js'
import { useToasts } from '../composables/useToasts.js'
import { rememberBuyerPhone } from '../composables/useOrders.js'

const route = useRoute()
const { items, total, loading, error, filters, reload, clearFilters, patchItemStatus } = useProducts()
const { toast } = useToasts()

// 正在调 PATCH 的商品 id：按钮 disabled，快速连点也不会发出第二个请求
const pendingIds = ref([])
const listEl = ref(null)
// 当前正在购买的商品；null 表示下单面板收起
const buyingItem = ref(null)
/**
 * 从「发布商品」页跳过来时会带 `?highlight=<id>`：列表按 id 升序，
 * 新商品排在最后一行，不滚过去用户会以为没发布成功（L0-3）。
 */
const highlightId = ref(readHighlight(route.query.highlight))

function readHighlight(value) {
  const id = Number(Array.isArray(value) ? value[0] : value)
  return Number.isSafeInteger(id) && id > 0 ? id : 0
}

onMounted(async () => {
  await reload()
  scrollToHighlight()
})

function scrollToHighlight() {
  if (!highlightId.value) return
  // 等 DOM 真正渲染出这一行再滚，否则 querySelector 还是上一帧的空列表
  requestAnimationFrame(() => {
    listEl.value?.querySelector('[data-highlight]')?.scrollIntoView({
      behavior: 'smooth',
      block: 'center',
    })
  })
}

function onUpdateFilters(next) {
  const statusChanged = next.status !== filters.status
  const categoryChanged = next.category !== filters.category
  Object.assign(filters, next)
  // 下拉框是即时筛选；关键词等用户按回车或点「查询」，避免每敲一个字都发请求
  if (statusChanged || categoryChanged) reload()
}

function onClear() {
  clearFilters()
}

async function onToggle(item) {
  if (pendingIds.value.includes(item.id)) return
  const nextStatus = item.status === 'on' ? 'off' : 'on'
  pendingIds.value = [...pendingIds.value, item.id]
  try {
    const result = await updateProductStatus(item.id, nextStatus)
    // 以服务端返回的 status 为准回写这一行，不做整表刷新
    patchItemStatus(item.id, result?.status ?? nextStatus)
    toast.success(`《${item.title}》已${nextStatus === 'on' ? '上架' : '下架'}`)
  } catch (err) {
    toast.error(err?.message || '操作失败，请重试')
  } finally {
    pendingIds.value = pendingIds.value.filter((id) => id !== item.id)
  }
}

function onBuy(item) {
  buyingItem.value = item
}

async function onPlaced(order) {
  buyingItem.value = null
  highlightId.value = 0
  toast.success(`下单成功，订单号 ${order?.orderNo ?? ''}`)
  // 用户可能改过收货手机号，记下来给「我的订单」页用，否则刚下的单查不到（L1-7）
  rememberBuyerPhone(order?.buyerPhone)
  // 库存变了，重新拉一次列表；订单在「我的订单」页看
  await reload()
}
</script>

<template>
  <div>
    <!-- 选中某件商品后才出现，避免一进页面就摆一堆待填的收货信息 -->
    <OrderForm
      :item="buyingItem"
      @close="buyingItem = null"
      @placed="onPlaced"
      @notify="toast[$event.type]($event.message)"
    />

    <div ref="listEl">
      <ProductList
        :items="items"
        :total="total"
        :loading="loading"
        :error="error"
        :filters="filters"
        :pending-ids="pendingIds"
        :highlight-id="highlightId"
        @update:filters="onUpdateFilters"
        @search="reload"
        @clear="onClear"
        @retry="reload"
        @toggle="onToggle"
        @buy="onBuy"
      />
    </div>
  </div>
</template>
