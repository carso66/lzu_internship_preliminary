<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { DEFAULT_BUYER, createOrder } from '../api/orders.js'
import { formatPrice, multiplyFen } from '../utils/money.js'

const props = defineProps({
  /** 要购买的商品；null 表示不显示 */
  item: { type: Object, default: null },
})
const emit = defineEmits(['close', 'placed', 'notify'])

// 收货信息：后端三个字段都是必填，mock 会忽略它们，所以两种后端都能跑
const buyer = reactive({ ...DEFAULT_BUYER })
const form = reactive({ quantity: '1' })
const errors = reactive({ quantity: '', buyerName: '', buyerPhone: '', address: '' })
const submitting = ref(false)

const open = computed(() => Boolean(props.item))
const stock = computed(() => Number(props.item?.stock ?? 0))
// 下架或缺货时不允许下单，按钮直接禁用；后端也会拦（40902 / 40901），这里只是少一次往返
const buyable = computed(() => props.item?.status === 'on' && stock.value > 0)

const quantityNumber = computed(() => Number(form.quantity))
/** 前端预估总价：必须和服务端 totalAmount 完全一致（L1-8） */
const estimatedFen = computed(() => multiplyFen(props.item?.price, form.quantity))

// 换商品时把数量重置，避免上一件的 5 件带到库存只有 2 件的商品上
watch(
  () => props.item?.id,
  () => {
    form.quantity = '1'
    clearErrors()
  },
)

// 用户一改数量就把旧错误清掉：错误提示应该跟着当前输入走，
// 不然改对了还挂着「库存只有 10 件」，会让人以为没改成功。
watch(
  () => form.quantity,
  () => {
    errors.quantity = ''
  },
)

watch(
  () => [buyer.name, buyer.phone, buyer.address],
  () => {
    errors.buyerName = ''
    errors.buyerPhone = ''
    errors.address = ''
  },
)

function clearErrors() {
  errors.quantity = ''
  errors.buyerName = ''
  errors.buyerPhone = ''
  errors.address = ''
}

function validate() {
  clearErrors()
  const quantityText = String(form.quantity).trim()
  if (quantityText === '') errors.quantity = '请填写购买数量'
  else if (!/^\d+$/.test(quantityText)) errors.quantity = '数量必须是整数'
  else if (Number(quantityText) < 1) errors.quantity = '数量至少为 1'
  else if (Number(quantityText) > stock.value) errors.quantity = `库存只有 ${stock.value} 件`

  const name = buyer.name.trim()
  if (!name) errors.buyerName = '请填写收货人姓名'
  else if (name.length > 50) errors.buyerName = '姓名最多 50 个字'

  // 后端只认 11 位纯数字，前端先拦一道，省得用户等一次往返才看到错误
  if (!/^\d{11}$/.test(buyer.phone.trim())) errors.buyerPhone = '手机号需为 11 位数字'

  const address = buyer.address.trim()
  if (!address) errors.address = '请填写收货地址'
  else if (address.length > 255) errors.address = '地址最多 255 个字'

  return !Object.values(errors).some(Boolean)
}

async function submit() {
  if (!open.value || submitting.value) return
  // 校验不过就一个请求都不发（L1-9 的要求）
  if (!validate()) return

  submitting.value = true
  try {
    // quantity 传 number，不是字符串（L1-6 验收标准里点名了这一条）
    const order = await createOrder({
      productId: props.item.id,
      quantity: quantityNumber.value,
      buyer: { name: buyer.name.trim(), phone: buyer.phone.trim(), address: buyer.address.trim() },
    })
    emit('placed', order)
  } catch (err) {
    // 后端的「库存不足」「商品已下架」原样显示，不自己编文案
    const message = err?.message || '下单失败，请重试'
    emit('notify', { type: 'error', message })
  } finally {
    // 无论成功失败都要解禁，否则失败一次按钮就永久灰掉
    submitting.value = false
  }
}
</script>

<template>
  <section v-if="open" class="panel panel-buy" data-testid="buy-panel">
    <header class="panel-head">
      <h2>购买：{{ item.title }}</h2>
      <button type="button" class="btn btn-ghost" :disabled="submitting" @click="emit('close')">
        取消
      </button>
    </header>

    <p class="buy-summary">
      单价 {{ formatPrice(item.price) }} · 库存 {{ stock }} 件
      <span v-if="item.status !== 'on'" class="badge badge-off">已下架</span>
      <span v-else-if="stock === 0" class="badge badge-soldout">缺货</span>
    </p>

    <form class="form" novalidate @submit.prevent="submit">
      <div class="field">
        <label for="b-quantity">购买数量</label>
        <input
          id="b-quantity"
          v-model="form.quantity"
          class="input"
          type="text"
          inputmode="numeric"
          :disabled="!buyable"
          placeholder="1"
        />
        <p v-if="errors.quantity" class="field-error">{{ errors.quantity }}</p>
        <!-- 单价和数量就在同一行里看得见，这里只报一个总价，不再重复算式 -->
        <p v-else class="field-hint" data-testid="estimate">
          预估总价 {{ estimatedFen === null ? '—' : formatPrice(estimatedFen) }}
        </p>
      </div>

      <div class="field">
        <label for="b-name">收货人</label>
        <input id="b-name" v-model="buyer.name" class="input" type="text" maxlength="60" />
        <p v-if="errors.buyerName" class="field-error">{{ errors.buyerName }}</p>
      </div>

      <div class="field">
        <label for="b-phone">手机号</label>
        <input id="b-phone" v-model="buyer.phone" class="input" type="text" inputmode="numeric" maxlength="11" />
        <p v-if="errors.buyerPhone" class="field-error">{{ errors.buyerPhone }}</p>
      </div>

      <div class="field field-wide">
        <label for="b-address">收货地址</label>
        <input id="b-address" v-model="buyer.address" class="input" type="text" maxlength="255" />
        <p v-if="errors.address" class="field-error">{{ errors.address }}</p>
      </div>

      <div class="form-actions">
        <button
          type="submit"
          class="btn btn-primary"
          :disabled="submitting || !buyable"
          data-testid="submit-order"
        >
          {{ submitting ? '提交中…' : '提交订单' }}
        </button>
        <span class="panel-meta">金额由服务端计算，前端预估只用于核对</span>
      </div>
    </form>
  </section>
</template>
