<script setup>
import { reactive, ref } from 'vue'
import { CATEGORIES, createProduct } from '../api/products.js'
import { formatPrice, yuanToFen } from '../utils/money.js'

const emit = defineEmits(['created', 'notify'])

// 价格用「元」输入（字符串），提交前转成「分」整数
const form = reactive({ title: '', price: '', stock: '', category: '', description: '' })
const errors = reactive({ title: '', price: '', stock: '', category: '', description: '' })
const submitting = ref(false)
const feedback = ref(null) // { type: 'success' | 'error', message }

function resetForm() {
  form.title = ''
  form.price = ''
  form.stock = ''
  form.category = ''
  form.description = ''
  clearErrors()
}

function clearErrors() {
  errors.title = ''
  errors.price = ''
  errors.stock = ''
  errors.category = ''
  errors.description = ''
}

function validate() {
  clearErrors()
  const title = form.title.trim()
  if (title.length === 0) errors.title = '请填写标题'
  else if (title.length > 50) errors.title = `标题最多 50 个字，当前 ${title.length} 个`

  const priceFen = yuanToFen(form.price)
  if (form.price === '') errors.price = '请填写价格'
  else if (priceFen === null) errors.price = '价格最多两位小数，例如 19.9'
  else if (priceFen <= 0) errors.price = '价格必须大于 0'

  const stockText = String(form.stock).trim()
  if (stockText === '') errors.stock = '请填写库存'
  else if (!/^\d+$/.test(stockText)) errors.stock = '库存必须是整数'
  else if (Number(stockText) > 999) errors.stock = '库存需在 0~999 之间'

  if (!CATEGORIES.includes(form.category)) errors.category = '请选择分类'
  if (form.description.length > 200) errors.description = `描述最多 200 字，当前 ${form.description.length} 个`

  return !Object.values(errors).some(Boolean)
}

async function submit() {
  feedback.value = null
  // 校验不通过就不发请求（L1-9 的要求，顺手也省掉一次无谓的往返）
  if (!validate()) return

  const priceFen = yuanToFen(form.price)
  submitting.value = true
  try {
    const created = await createProduct({
      title: form.title.trim(),
      price: priceFen,
      stock: Number(form.stock),
      category: form.category,
      description: form.description.trim(),
    })
    resetForm()
    feedback.value = { type: 'success', message: `发布成功：${created.title}（${formatPrice(created.price)}）` }
    emit('notify', { type: 'success', message: `发布成功：${created.title}（${formatPrice(created.price)}）` })
    emit('created', created)
  } catch (error) {
    // 后端的 message 原样显示，别自己编
    const message = error?.message || '发布失败，请重试'
    feedback.value = { type: 'error', message }
    emit('notify', { type: 'error', message })
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <h2>发布商品</h2>
      <span class="panel-meta">价格按<strong>元</strong>填写，提交时自动换算成「分」</span>
    </header>

    <form class="form" novalidate @submit.prevent="submit">
      <div class="field">
        <label for="f-title">标题</label>
        <input
          id="f-title"
          v-model="form.title"
          class="input"
          type="text"
          maxlength="60"
          placeholder="1~50 字，例如 降噪蓝牙耳机 Pro"
        />
        <p v-if="errors.title" class="field-error">{{ errors.title }}</p>
      </div>

      <div class="field">
        <label for="f-price">价格（元）</label>
        <input
          id="f-price"
          v-model="form.price"
          class="input"
          type="text"
          inputmode="decimal"
          placeholder="例如 19.9"
        />
        <p v-if="errors.price" class="field-error">{{ errors.price }}</p>
        <p v-else-if="yuanToFen(form.price) !== null" class="field-hint">
          将提交 price = {{ yuanToFen(form.price) }}（分），显示为 {{ formatPrice(yuanToFen(form.price)) }}
        </p>
      </div>

      <div class="field">
        <label for="f-stock">库存</label>
        <input
          id="f-stock"
          v-model="form.stock"
          class="input"
          type="text"
          inputmode="numeric"
          placeholder="0~999"
        />
        <p v-if="errors.stock" class="field-error">{{ errors.stock }}</p>
      </div>

      <div class="field">
        <label for="f-category">分类</label>
        <select id="f-category" v-model="form.category" class="input">
          <option value="" disabled>请选择分类</option>
          <option v-for="c in CATEGORIES" :key="c" :value="c">{{ c }}</option>
        </select>
        <p v-if="errors.category" class="field-error">{{ errors.category }}</p>
      </div>

      <div class="field field-wide">
        <label for="f-desc">描述</label>
        <textarea
          id="f-desc"
          v-model="form.description"
          class="input"
          rows="2"
          placeholder="选填，最多 200 字"
        ></textarea>
        <p v-if="errors.description" class="field-error">{{ errors.description }}</p>
      </div>

      <div class="form-actions">
        <button type="submit" class="btn btn-primary" :disabled="submitting">
          {{ submitting ? '发布中…' : '发布商品' }}
        </button>
        <button type="button" class="btn btn-ghost" :disabled="submitting" @click="resetForm">
          重置
        </button>
        <p v-if="feedback" class="form-feedback" :class="`is-${feedback.type}`" data-testid="form-feedback">
          {{ feedback.message }}
        </p>
      </div>
    </form>
  </section>
</template>
