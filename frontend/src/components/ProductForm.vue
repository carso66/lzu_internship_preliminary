<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue'
import { createProduct } from '../api/index.js'
import { yuanToFen } from '../utils/money.js'

const CATEGORIES = ['数码', '图书', '服饰', '食品', '文具']

const emit = defineEmits(['created'])

const form = reactive({
  title: '',
  price: '',
  stock: '',
  category: '数码',
  description: '',
})

const errors = reactive({
  title: '',
  price: '',
  stock: '',
  category: '',
})

const submitting = ref(false)
const submitError = ref('')
const toastMessage = ref('')
let toastTimer = null

function clearFieldError(field) {
  errors[field] = ''
  submitError.value = ''
}

function countCharacters(value) {
  return Array.from(value).length
}

function validateForm() {
  errors.title = ''
  errors.price = ''
  errors.stock = ''
  errors.category = ''
  submitError.value = ''

  const title = form.title.trim()
  const titleLength = countCharacters(title)
  if (titleLength === 0) {
    errors.title = '标题不能为空'
  } else if (titleLength > 50) {
    errors.title = '标题不能超过 50 个字'
  }

  const price = form.price.trim()
  if (!price) {
    errors.price = '价格不能为空'
  } else if (!/^\d+(?:\.\d{1,2})?$/.test(price)) {
    errors.price = '价格必须是大于 0 的数字，最多两位小数'
  } else if (!(Number(price) > 0)) {
    errors.price = '价格必须大于 0'
  }

  const stock = form.stock.trim()
  if (!stock) {
    errors.stock = '库存不能为空'
  } else if (!/^\d+$/.test(stock) || Number(stock) > 999) {
    errors.stock = '库存必须是 0~999 的整数'
  }

  if (!CATEGORIES.includes(form.category)) {
    errors.category = '请选择有效的分类'
  }

  return !Object.values(errors).some(Boolean)
}

function resetForm() {
  form.title = ''
  form.price = ''
  form.stock = ''
  form.category = '数码'
  form.description = ''

  errors.title = ''
  errors.price = ''
  errors.stock = ''
  errors.category = ''
}

function showToast(message) {
  toastMessage.value = message
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => {
    toastMessage.value = ''
  }, 3000)
}

async function handleSubmit() {
  if (submitting.value || !validateForm()) return

  submitting.value = true
  submitError.value = ''

  try {
    const priceInFen = yuanToFen(form.price.trim())
    const createdProduct = await createProduct({
      title: form.title.trim(),
      price: priceInFen,
      stock: Number(form.stock),
      category: form.category,
      description: form.description.trim(),
    })

    resetForm()
    showToast('商品发布成功')
    emit('created', createdProduct)
  } catch (error) {
    submitError.value =
      error instanceof Error && error.message
        ? error.message
        : '发布失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

onBeforeUnmount(() => {
  window.clearTimeout(toastTimer)
})
</script>

<template>
  <form class="product-form" novalidate @submit.prevent="handleSubmit">
    <header class="product-form__header">
      <div>
        <p class="product-form__eyebrow">发布新商品</p>
        <h2>填写商品信息</h2>
      </div>
      <p class="product-form__hint">价格以元填写，提交时会自动换算为整数分。</p>
    </header>

    <div class="product-form__grid">
      <div class="product-form__field product-form__field--full">
        <label for="product-title">标题</label>
        <input
          id="product-title"
          v-model="form.title"
          type="text"
          placeholder="例如：校园纪念卫衣"
          :aria-invalid="Boolean(errors.title)"
          aria-describedby="product-title-error"
          @input="clearFieldError('title')"
        />
        <p
          v-if="errors.title"
          id="product-title-error"
          class="product-form__field-error"
        >
          {{ errors.title }}
        </p>
      </div>

      <div class="product-form__field">
        <label for="product-price">价格（元）</label>
        <input
          id="product-price"
          v-model="form.price"
          type="text"
          inputmode="decimal"
          placeholder="例如：19.90"
          :aria-invalid="Boolean(errors.price)"
          aria-describedby="product-price-error"
          @input="clearFieldError('price')"
        />
        <p
          v-if="errors.price"
          id="product-price-error"
          class="product-form__field-error"
        >
          {{ errors.price }}
        </p>
      </div>

      <div class="product-form__field">
        <label for="product-stock">库存</label>
        <input
          id="product-stock"
          v-model="form.stock"
          type="text"
          inputmode="numeric"
          placeholder="0~999"
          :aria-invalid="Boolean(errors.stock)"
          aria-describedby="product-stock-error"
          @input="clearFieldError('stock')"
        />
        <p
          v-if="errors.stock"
          id="product-stock-error"
          class="product-form__field-error"
        >
          {{ errors.stock }}
        </p>
      </div>

      <div class="product-form__field">
        <label for="product-category">分类</label>
        <select
          id="product-category"
          v-model="form.category"
          :aria-invalid="Boolean(errors.category)"
          aria-describedby="product-category-error"
          @change="clearFieldError('category')"
        >
          <option v-for="category in CATEGORIES" :key="category" :value="category">
            {{ category }}
          </option>
        </select>
        <p
          v-if="errors.category"
          id="product-category-error"
          class="product-form__field-error"
        >
          {{ errors.category }}
        </p>
      </div>

      <div class="product-form__field product-form__field--full">
        <label for="product-description">描述</label>
        <textarea
          id="product-description"
          v-model="form.description"
          rows="3"
          placeholder="补充商品成色、规格或使用说明（选填）"
        ></textarea>
      </div>
    </div>

    <div v-if="submitError" class="product-form__submit-error" role="alert">
      {{ submitError }}
    </div>

    <footer class="product-form__actions">
      <button type="submit" :disabled="submitting">
        {{ submitting ? '提交中…' : '发布商品' }}
      </button>
    </footer>

    <Transition name="toast">
      <div
        v-if="toastMessage"
        class="product-form__toast"
        role="status"
        aria-live="polite"
      >
        {{ toastMessage }}
      </div>
    </Transition>
  </form>
</template>

<style scoped>
.product-form {
  width: 100%;
  margin-bottom: 26px;
  padding: 24px;
  border: 1px solid #e4e4e7;
  border-radius: 16px;
  background: #ffffff;
  box-shadow: 0 10px 30px rgb(24 24 27 / 6%);
}

.product-form__header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 22px;
}

.product-form__eyebrow {
  margin: 0 0 5px;
  color: #c2410c;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.product-form__header h2 {
  margin: 0;
  color: #18181b;
  font-size: 22px;
}

.product-form__hint {
  max-width: 360px;
  margin: 0;
  color: #71717a;
  font-size: 13px;
  line-height: 1.5;
  text-align: right;
}

.product-form__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px 20px;
}

.product-form__field {
  display: grid;
  min-width: 0;
  align-content: start;
  gap: 7px;
}

.product-form__field--full {
  grid-column: 1 / -1;
}

.product-form__field label {
  color: #3f3f46;
  font-size: 13px;
  font-weight: 700;
}

.product-form__field input,
.product-form__field select,
.product-form__field textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 11px 13px;
  border: 1px solid #d4d4d8;
  border-radius: 9px;
  background: #ffffff;
  color: #18181b;
  font: inherit;
  outline: none;
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease;
}

.product-form__field textarea {
  min-height: 86px;
  resize: vertical;
}

.product-form__field input:focus,
.product-form__field select:focus,
.product-form__field textarea:focus {
  border-color: #52525b;
  box-shadow: 0 0 0 3px rgb(82 82 91 / 12%);
}

.product-form__field input[aria-invalid='true'],
.product-form__field select[aria-invalid='true'] {
  border-color: #ef4444;
  box-shadow: 0 0 0 3px rgb(239 68 68 / 10%);
}

.product-form__field-error {
  margin: 0;
  color: #dc2626;
  font-size: 12px;
  line-height: 1.4;
}

.product-form__submit-error {
  margin-top: 18px;
  padding: 11px 14px;
  border: 1px solid #fecaca;
  border-radius: 9px;
  background: #fef2f2;
  color: #991b1b;
  font-size: 13px;
  line-height: 1.5;
}

.product-form__actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

.product-form__actions button {
  min-width: 116px;
  padding: 11px 20px;
  border: 0;
  border-radius: 9px;
  background: #18181b;
  color: #ffffff;
  cursor: pointer;
  font: inherit;
  font-size: 14px;
  font-weight: 700;
}

.product-form__actions button:hover:not(:disabled) {
  background: #3f3f46;
}

.product-form__actions button:disabled {
  cursor: wait;
  background: #a1a1aa;
}

.product-form__toast {
  position: fixed;
  z-index: 20;
  top: 22px;
  right: 22px;
  padding: 12px 18px;
  border-radius: 10px;
  background: #166534;
  box-shadow: 0 12px 30px rgb(22 101 52 / 25%);
  color: #ffffff;
  font-size: 14px;
  font-weight: 700;
}

.toast-enter-active,
.toast-leave-active {
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

@media (max-width: 700px) {
  .product-form {
    padding: 18px;
  }

  .product-form__header {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }

  .product-form__hint {
    max-width: none;
    text-align: left;
  }

  .product-form__grid {
    grid-template-columns: 1fr;
  }
}
</style>
