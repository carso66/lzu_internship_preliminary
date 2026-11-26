<script setup>
// computed：根据别的值算出来的值，依赖变了它自动重算
import { ref, computed } from 'vue'
import { api } from '../api/client'
import { fenToYuan } from '../utils/money'

// product 由父组件传下来：单价、库存、状态、id 都从它读
const props = defineProps({
  product: {
    type: Object,
    required: true
  }
})

// 下单成功后通知父组件，父组件再往上抛给 App 去刷新列表
const emit = defineEmits(['ordered'])

// 数量存文本，不存数字。理由和发布表单的库存一样：
// "1.5"、"-1"、"abc" 这些非法输入得先看到原始字符串才能判非法
const quantity = ref('1')

// 下单失败的提示，直接放后端返回的 message
// 失败不会触发列表重拉，组件不会被销毁，所以这条红字放在本地是安全的
const error = ref('')

// 提交中禁用按钮，防止连点下两单（这就是 L2-11 防重复提交）
const submitting = ref(false)

// 本地只校验格式：必须是正整数。
// 库存够不够、有没有下架，故意不在这里判 —— 那两个是状态问题，交给服务器
const valid = computed(() => {
  const s = quantity.value.trim()
  // /^\d+$/ 只放过纯数字，"-1" "1.5" "1e3" 全被拒；再单独排除 0
  return /^\d+$/.test(s) && Number(s) > 0
})

// 预估合计，单位是"分"。
// 分是整数，整数 × 整数 = 精确整数，绝不可能出现浮点尾巴
const totalFen = computed(() =>
  valid.value ? props.product.price * Number(quantity.value.trim()) : null
)

async function submit() {
  // 上一次请求还没回来就再点，直接忽略
  if (submitting.value) return

  // 每次先清掉上一条红字，避免旧提示残留
  error.value = ''

  if (!valid.value) {
    error.value = '购买数量必须是正整数'
    return
  }

  submitting.value = true
  try {
    // quantity 必须传"数字"，不能传 "2" 这种字符串（验收项明确要看请求体）
    // await 拿到的就是服务器建好的那条订单，orderNo / totalAmount / status 都在里面
    const order = await api.createOrder({
      productId: props.product.id,
      quantity: Number(quantity.value.trim())
    })

    // 数量重置回 1：不重置的话，用户看到提示以为没点上，再点一次就重复下单了
    quantity.value = '1'

    // 成功提示不在这里显示。
    // 下面这句 emit 会引来 App 的 search()，而 search() 会把整个列表卸载重建
    // （v-if 是销毁，不是隐藏），本地那个 success 活不过那一瞬。
    // 所以把订单号交给上层，由永远不会被卸载的 App 去显示。
    emit('ordered', { productId: props.product.id, orderNo: order.orderNo })
  } catch (e) {
    // client.js 抛出来的就是后端的 message，
    // "库存不足" / "商品已下架" 都是这样自动显示出来的
    error.value = e.message
  } finally {
    // 失败也要解禁，否则按钮永久点不动（验收项最后一条）
    submitting.value = false
  }
}
</script>

<template>
  <div class="buy">
    <!-- label 包住 input，就不用写 for/id 了，也不会有 id 撞车的问题 -->
    <label class="qty-label">
      数量
      <input
        v-model="quantity"
        class="qty"
        type="text"
        inputmode="numeric"
      />
    </label>

    <!-- 合计：分算完再交给 fenToYuan 统一格式化成 ¥xx.xx -->
    <!-- 数量非法时 totalFen 是 null，这里显示破折号而不是 NaN -->
    <span class="total">{{ totalFen === null ? '—' : fenToYuan(totalFen) }}</span>

    <button type="button" class="buy-btn" :disabled="submitting" @click="submit">
      {{ submitting ? '提交中…' : '购买' }}
    </button>

    <!-- 只在这里显示失败提示；成功提示由 App 统一显示，理由见 submit() 里的注释 -->
    <p v-if="error" class="buy-error">{{ error }}</p>
  </div>
</template>

<style scoped>
/* 一横行：数量、合计、按钮；放不下就换行 */
.buy {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #e0e0e0;   /* 和上面的商品信息隔开，但不抢视觉 */
}

.qty-label {
  font-size: 13px;
  color: #666;
}

/* 数量框很窄就够，别让它把卡片撑开 */
.qty {
  width: 56px;
  height: 28px;
  padding: 0 6px;
  border: 1px solid #ccc;
  border-radius: 4px;
  font-size: 13px;
}

/* 合计：金额一律红色加粗，和卡片主价格同色系 */
.total {
  font-size: 14px;
  font-weight: bold;
  color: #e4393c;
}

.buy-btn {
  height: 28px;
  padding: 0 12px;
  border: 1px solid #ccc;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  font-size: 13px;
}
.buy-btn:hover:not(:disabled) {
  background: #f5f5f5;
}
/* 提交中要看起来点不动 */
.buy-btn:disabled {
  color: #999;
  background: #f0f0f0;
  cursor: not-allowed;
}

/* 错误提示独占一行，红字小号 */
.buy-error {
  flex-basis: 100%;      /* 强制换行，不被挤在按钮右边 */
  margin: 0;
  font-size: 12px;
  color: #e4393c;
}
</style>