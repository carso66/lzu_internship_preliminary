<script setup>
// reactive：定义一个"对象型"响应式变量，里面每个属性变了都会刷新页面
// ref：定义单个响应式变量（这里用来存布尔值和字符串）
import { reactive, ref } from 'vue'

// 只走 client 里的统一封装：它已经剥掉了 { code, message, data } 的壳
// 失败时它会 throw new Error(message)，所以我们下面用 try/catch 接
import { api } from '../api/client'

// 分类全站唯一来源，绝不在这里另写一份数组，否则以后加分类要改两处
import { CATEGORIES } from '../constants'

// 元 → 分。合法返回整数分，不合法（空 / 负数 / 三位小数 / 超两位）返回 null
import { yuanToFen } from '../utils/money'

// 只向父组件发一个事件：'created'，意思是"发布成功了，你去刷新列表吧"
const emit = defineEmits(['created'])

// 表单数据。注意 price 和 stock 存的是用户敲进去的"文本"，
// 因为校验要看原始字符串（比如 "1.999"、"1.5" 这种必须能识别出来当作非法）
const form = reactive({
  title: '',
  price: '',
  stock: '',
  category: '',
  description: ''
})

// 每个字段各自的错误信息，空字符串 = 这个字段没有错误
// 键名和字段一一对应，渲染时就能"哪里错显示在哪里"
const errors = reactive({
  title: '',
  price: '',
  stock: '',
  category: ''
})

// 表单顶部的整体错误（来自服务器，比如商品重复、503 等），对应需求第 7 条
const topError = ref('')

// 发布成功的行内提示文案，空字符串 = 不显示
const success = ref('')

// 是否正在提交：true 时按钮禁用并显示"提交中…"，防止连点重复发布
const submitting = ref(false)

// 校验函数：返回 true 表示全部通过，false 表示有错
// 每次进来先把旧错误清空，避免上次的红字残留
function validate() {
  errors.title = ''
  errors.price = ''
  errors.stock = ''
  errors.category = ''

  // 标题：先去掉首尾空格再算长度，全是空格等于没填
  const title = form.title.trim()
  if (title.length === 0) {
    errors.title = '请填写商品标题'
  } else if (title.length > 50) {
    // 需求是 1~50 字，超过 50 就拦下来（不会偷偷截断用户的输入）
    errors.title = `标题最多 50 个字，当前 ${title.length} 个`
  }

  // 价格：交给 yuanToFen 判断，它返回 null 就说明不是合法的"元"
  // 覆盖了：空、0、负数、"abc"、超过两位小数
  if (yuanToFen(form.price) === null) {
    errors.price = '价格必须大于 0，且最多两位小数'
  }

  // 库存：先转成字符串再 trim，兼容用户输入的空格
  // /^\d+$/ 只允许纯数字，所以 "-1"、"1.5"、"1e3" 都会被拒绝
  const stockText = String(form.stock).trim()
  if (!/^\d+$/.test(stockText)) {
    errors.stock = '库存必须是 0~999 的整数'
  } else if (Number(stockText) > 999) {
    errors.stock = '库存不能超过 999'
  }

  // 分类：必须真的是 CATEGORIES 里的一项，防止有人手改 DOM 传个非法值进来
  if (!CATEGORIES.includes(form.category)) {
    errors.category = '请选择分类'
  }

  // 四个字段都没错才算通过。一个都不能漏，否则会把非法数据发给服务器
  return !errors.title && !errors.price && !errors.stock && !errors.category
}

// 提交成功或用户手动清空时调用：把所有输入和错误都恢复成初始状态
function resetForm() {
  form.title = ''
  form.price = ''
  form.stock = ''
  form.category = ''
  form.description = ''
  errors.title = ''
  errors.price = ''
  errors.stock = ''
  errors.category = ''
  topError.value = ''
}

// 表单提交：注意按钮在 form 里，所以用 @submit 而不是 @click
// @submit.prevent 会阻止浏览器默认的"整页刷新"行为
async function onSubmit() {
  // 双保险：上一次还没回来时，直接忽略新的提交
  if (submitting.value) return

  // 每次提交先清掉上一次的成功/失败提示，避免旧消息残留
  topError.value = ''
  success.value = ''

  // 校验不过就直接 return，一个请求都不发（需求第 4 条：校验通过才调 API）
  if (!validate()) return

  // 能走到这里说明价格一定合法，null 的情况已经被 validate 拦掉了
  const priceFen = yuanToFen(form.price)

  submitting.value = true
  try {
    // 请求体字段严格按约定：title / price / stock / category / description
    // price 传的是"分"的整数，绝对不传用户输入的 "19.90" 字符串
    // await 拿到的就是服务器新建好的那条商品，里面的 title 是服务器 trim 过的
    const created = await api.createProduct({
      title: form.title.trim(),
      price: priceFen,
      stock: Number(String(form.stock).trim()), // 转成数字，避免发 "3" 这种字符串
      category: form.category,
      description: form.description.trim()
    })

    // 标题必须在 resetForm() 之前取！
    // resetForm() 会把 form.title 清空，晚一步就只能显示《》空书名号
    // 优先用服务器返回的 title，本地 form 只作兜底
    const createdTitle = (created && created.title) || form.title.trim()

    // 服务器确认成功后才清空表单，失败了要保留用户填的内容让他改
    resetForm()

    // 赋值放在 resetForm() 之后，才不会被它顺手清掉
    success.value = `发布成功！《${createdTitle}》已上架`

    // 通知父组件"发布成功了"。父组件收到后去调 search() 刷新列表
    emit('created')
  } catch (e) {
    // client.js 抛出来的就是后端 message，直接显示在表单顶部
    topError.value = e.message
  } finally {
    // 不管成功失败都要解除禁用，否则失败后按钮永远点不动
    submitting.value = false
  }
}
</script>

<template>
  <form class="publish-form" @submit.prevent="onSubmit">
    <h2 class="form-title">发布商品</h2>

    <!-- 顶部提示三态互斥：v-if / v-else-if 在语法上就不可能同时渲染 -->
    <!-- 成功用 role="status"（温和播报），失败用 role="alert"（立刻打断朗读） -->
    <p v-if="success" class="form-success" role="status">{{ success }}</p>
    <p v-else-if="topError" class="form-error" role="alert">{{ topError }}</p>

    <!-- 标题 -->
    <div class="field">
      <label for="publish-title">标题</label>
      <input
        id="publish-title"
        v-model="form.title"
        type="text"
        placeholder="例如：九成新自行车"
      />
      <!-- 错误紧贴在输入框正下方，不用 alert（需求第 3 条） -->
      <p v-if="errors.title" class="field-error">{{ errors.title }}</p>
    </div>

    <!-- 价格：用户填"元"，提交时用 yuanToFen 转成"分" -->
    <!-- 用 inputmode 而不是 type="number"：number 输入框会吞掉非法字符，
         导致我们看不到用户到底敲了什么，也不好给出准确的错误提示 -->
    <div class="field">
      <label for="publish-price">价格（元）</label>
      <input
        id="publish-price"
        v-model="form.price"
        type="text"
        inputmode="decimal"
        placeholder="例如：19.90"
      />
      <p v-if="errors.price" class="field-error">{{ errors.price }}</p>
    </div>

    <!-- 库存 -->
    <div class="field">
      <label for="publish-stock">库存</label>
      <input
        id="publish-stock"
        v-model="form.stock"
        type="text"
        inputmode="numeric"
        placeholder="0~999 的整数"
      />
      <p v-if="errors.stock" class="field-error">{{ errors.stock }}</p>
    </div>

    <!-- 分类：选项从 CATEGORIES 生成，不硬编码 -->
    <div class="field">
      <label for="publish-category">分类</label>
      <select id="publish-category" v-model="form.category">
        <!-- 空 value 代表"还没选"，校验时会因为不在 CATEGORIES 里而被拦下 -->
        <option value="">请选择分类</option>
        <option v-for="c in CATEGORIES" :key="c" :value="c">{{ c }}</option>
      </select>
      <p v-if="errors.category" class="field-error">{{ errors.category }}</p>
    </div>

    <!-- 描述：选填。maxlength=200 拦住输入，服务器也会校验，超了返回 400 显示在顶部 -->
    <div class="field">
      <label for="publish-description">描述（选填，最多 200 字）</label>
      <textarea
        id="publish-description"
        v-model="form.description"
        maxlength="200"
        rows="3"
        placeholder="新旧程度、取货地点等"
      ></textarea>
    </div>

    <!-- :disabled="submitting"：提交中变成禁用，防止连点重复下单 -->
    <!-- 文案也跟着变，让用户知道"点了，正在等服务器"（需求第 6 条） -->
    <button type="submit" class="submit-btn" :disabled="submitting">
      {{ submitting ? '提交中…' : '发布商品' }}
    </button>
  </form>
</template>

<style scoped>
/* scoped：样式只作用于这个组件，不会污染别处 */

/* 表单外框，和 ProductList 的卡片风格保持一致 */
.publish-form {
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 16px;
  background: #fff;
  margin-bottom: 16px;
}

.form-title {
  margin: 0 0 12px;
  font-size: 16px;
}

/* 每个字段一整块，纵向排列，错误信息自然落在输入框下面 */
.field {
  margin-bottom: 12px;
}

/* label 独占一行，光标也能通过 for/id 关联点到输入框 */
.field label {
  display: block;
  font-size: 14px;
  color: #333;
  margin-bottom: 4px;
}

/* 输入框、下拉框、多行文本框统一外观，和 App.vue 筛选区一致 */
.field input,
.field select,
.field textarea {
  width: 100%;
  padding: 0 8px;
  border: 1px solid #ccc;
  border-radius: 4px;
  font-size: 14px;
  box-sizing: border-box;
}

/* 单行控件固定 32px 高；textarea 用 padding 撑高度 */
.field input,
.field select {
  height: 32px;
}

.field textarea {
  padding: 6px 8px;
  font-family: inherit; /* 不继承的话 textarea 默认是等宽字体 */
  resize: vertical;     /* 只允许纵向拉伸，防止横向拖拽撑破布局 */
}

/* 字段级错误：小号红字，紧贴输入框下方 */
.field-error {
  margin: 4px 0 0;
  font-size: 12px;
  color: #e4393c;
}

/* 表单顶部整体错误：加个浅红底，比字段错误更显眼 */
.form-error {
  margin: 0 0 12px;
  padding: 8px 12px;
  border-radius: 4px;
  background: #fdecec;
  color: #e4393c;
  font-size: 13px;
}

/* 发布成功的行内提示：尺寸和 .form-error 完全一样，只换配色 */
/* 成功和失败两条消息占同一块版面，切换时不会跳来跳去 */
.form-success {
  margin: 0 0 12px;
  padding: 8px 12px;
  border-radius: 4px;
  background: #e6f7e6;   /* 淡绿底，和商品卡片的「在售」徽标同色 */
  color: #2d7a2d;        /* 深绿字，同上 */
  font-size: 13px;
}

.submit-btn {
  height: 32px;
  padding: 0 16px;
  border: 1px solid #ccc;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  font-size: 14px;
}

.submit-btn:hover:not(:disabled) {
  background: #f5f5f5;
}

/* 禁用状态要看起来"点不动"，并去掉手型光标 */
.submit-btn:disabled {
  color: #999;
  background: #f0f0f0;
  cursor: not-allowed;
}
</style>