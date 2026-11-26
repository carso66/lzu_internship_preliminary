<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import OrderForm from '../components/OrderForm.vue'
import { getProduct } from '../api/products.js'
import { useToasts } from '../composables/useToasts.js'
import { rememberBuyerPhone } from '../composables/useOrders.js'
import { formatPrice } from '../utils/money.js'
import { formatDateTime } from '../utils/order.js'

const props = defineProps({
  /** 路由参数永远是字符串，由页面自己校验 */
  id: { type: String, required: true },
})

const { toast } = useToasts()

const item = ref(null)
const loading = ref(false)
const error = ref('')
const buying = ref(false)

/**
 * `/products/abc`、`/products/0` 这类地址根本不存在商品，
 * 直接给「商品不存在」，不发请求 —— 不然要等一次 400 往返才看到提示。
 */
const productId = computed(() => {
  const text = String(props.id ?? '').trim()
  if (!/^\d+$/.test(text)) return 0
  const id = Number(text)
  return Number.isSafeInteger(id) && id > 0 ? id : 0
})

async function load() {
  if (!productId.value) {
    item.value = null
    error.value = '商品不存在'
    return
  }
  loading.value = true
  error.value = ''
  try {
    item.value = await getProduct(productId.value)
  } catch (err) {
    item.value = null
    // 后端 404 / 40401 的语义就是「商品不存在」，用固定文案；
    // 其它错误（连不上后端等）原样显示，别一律说成不存在
    error.value = err?.code === 40401 ? '商品不存在' : err?.message || '加载商品失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
// 从详情页再点另一个商品（同组件复用）时也要重新拉
watch(() => props.id, load)

async function onPlaced(order) {
  buying.value = false
  toast.success(`下单成功，订单号 ${order?.orderNo ?? ''}`)
  rememberBuyerPhone(order?.buyerPhone)
  // 库存少了，重新拉一次详情，页面上的库存和服务端保持一致
  await load()
}
</script>

<template>
  <div>
    <RouterLink class="back-link" :to="{ name: 'products' }" data-testid="back-to-list">
      ← 返回商品列表
    </RouterLink>

    <!-- 三态互斥，和列表页保持一致的视觉语言 -->
    <section v-if="loading" class="panel" data-testid="detail-loading">
      <div class="state state-loading">
        <span class="spinner" aria-hidden="true"></span>
        <p>加载中…</p>
      </div>
    </section>

    <section v-else-if="error" class="panel" data-testid="detail-error">
      <div class="state state-error">
        <p class="state-title">{{ error === '商品不存在' ? '商品不存在' : '加载失败' }}</p>
        <p class="state-desc">
          {{
            error === '商品不存在'
              ? `没有找到 id 为 ${id} 的商品，可能已被删除或链接写错了。`
              : error
          }}
        </p>
        <RouterLink class="btn btn-primary" :to="{ name: 'products' }">回到商品列表</RouterLink>
      </div>
    </section>

    <template v-else-if="item">
      <section class="panel" data-testid="detail-panel">
        <header class="panel-head">
          <h2>{{ item.title }}</h2>
          <span class="panel-meta">商品 ID <code>{{ item.id }}</code></span>
        </header>

        <dl class="detail-grid">
          <div>
            <dt>价格</dt>
            <dd>{{ formatPrice(item.price) }}</dd>
          </div>
          <div>
            <dt>库存</dt>
            <dd>
              <span v-if="item.stock === 0" class="badge badge-soldout">缺货</span>
              <template v-else>{{ item.stock }} 件</template>
            </dd>
          </div>
          <div>
            <dt>分类</dt>
            <dd><span class="tag">{{ item.category }}</span></dd>
          </div>
          <div>
            <dt>状态</dt>
            <dd>
              <span class="badge" :class="item.status === 'on' ? 'badge-on' : 'badge-off'">
                {{ item.status === 'on' ? '在售' : '已下架' }}
              </span>
            </dd>
          </div>
          <div>
            <dt>卖家</dt>
            <dd>{{ item.sellerName || '—' }}</dd>
          </div>
          <div>
            <dt>上架时间</dt>
            <dd>{{ formatDateTime(item.createdAt) }}</dd>
          </div>
        </dl>

        <!-- v-text 语义的插值：种子数据里有 <script>alert('xss')</script>，绝不能用 v-html -->
        <p v-if="item.description" class="detail-desc">{{ item.description }}</p>
        <p v-else class="detail-desc detail-desc-empty">卖家没有填写描述。</p>

        <div class="detail-actions">
          <button
            type="button"
            class="btn btn-primary"
            :disabled="item.status !== 'on' || item.stock === 0"
            :title="
              item.status !== 'on' ? '商品已下架' : item.stock === 0 ? '库存不足' : ''
            "
            data-testid="detail-buy"
            @click="buying = true"
          >
            购买
          </button>
          <RouterLink class="btn btn-ghost" :to="{ name: 'products' }">返回列表</RouterLink>
        </div>
      </section>

      <OrderForm
        :item="buying ? item : null"
        @close="buying = false"
        @placed="onPlaced"
        @notify="toast[$event.type]($event.message)"
      />
    </template>
  </div>
</template>
