<script setup>
// onMounted：页面加载完成后自动执行里面的函数
// ref：定义一个响应式变量，值变了页面会自动刷新
import { onMounted, ref } from 'vue'

// 上下架：App 负责调 API，成功后更新 products 里对应商品的状态
async function onToggle(product) {
  // 判断当前状态，决定要改成什么
  // 在售(on) → 改成下架(off)
  // 已下架(off) → 改成上架(on)
  const next = product.status === 'on' ? 'off' : 'on'
  try {
    // 调 PATCH /api/products/{id}/status，请求体是 {status: "off"} 或 {status: "on"}
    await api.setProductStatus(product.id, next)
    // 服务器确认成功了，才更新本地数据
    // 找到 products 数组里这个商品，改它的 status
    const row = products.value.find(p => p.id === product.id)
    if (row) row.status = next
  } catch (e) {
    // 失败了（比如商品不存在），打印到控制台
    console.error(e.message)
  }
}

// 引入我们封装好的 API 客户端，用来发 HTTP 请求
import { api } from './api/client'
// 引入商品列表组件
import ProductList from './components/ProductList.vue'
// 引入发布商品表单组件（不 import + 不写标签，组件就永远不会渲染）
import PublishForm from './components/PublishForm.vue'
// 引入订单列表组件
import OrderList from './components/OrderList.vue'

// 引入全站唯一的分类数组，下拉框的选项从它生成
import { CATEGORIES } from './constants'

// 商品数组，初始值是空数组
const products = ref([])
// 加载状态，初始值是 true（页面刚打开时数据还没回来，要先显示"加载中"）
const loading = ref(true)

// 搜索关键词，初始值是空字符串（不搜索）
const keyword = ref('')
// 选中的分类，初始值是空字符串（全部）
const category = ref('')

// 全站唯一的"操作成功"提示条。
// 挂在 App 而不是卡片里，是因为 search() 会把列表整块卸载重建
// （v-if 是销毁不是隐藏），卡片里的本地状态活不过那一次刷新。
// App 自己永远不销毁，所以提示放它身上才留得住。
const notice = ref('')

// 订单数组 + 它自己的一套加载状态
// 分开是有意的：订单区转圈不应该把商品区也变空
const orders = ref([])
const ordersLoading = ref(true)

// 搜索：重新请求 API，把 keyword 和 category 传给服务器
async function search() {
  loading.value = true
  try {
    const data = await api.listProducts({
      keyword: keyword.value,
      category: category.value,
      pageSize: 50
    })
    products.value = data.items
  } catch (e) {
    console.error(e.message)
  } finally {
    loading.value = false
  }
}

// 拉订单列表：不带筛选，服务器按 id 倒序返回，最新的一单在最上面
async function loadOrders() {
  ordersLoading.value = true
  try {
    const data = await api.listOrders({ pageSize: 20 })
    orders.value = data.items
  } catch (e) {
    console.error(e.message)
  } finally {
    ordersLoading.value = false
  }
}

// 卡片里的 BuyPanel 下单成功 → ProductList 转告 → 落到这里
// payload 是 { productId, orderNo }，orderNo 是服务器生成的，不是我拼的
async function onOrdered(payload) {
  notice.value = `下单成功，订单号 ${payload.orderNo}`
  // 两边都要重拉：商品列表看库存变化，订单列表看新单出现
  // Promise.all 让它们并发跑，不用排队等两个 600ms
  await Promise.all([search(), loadOrders()])
}

// 清空筛选：把关键词和分类清空，重新加载全部商品
function clearQuery() {
  keyword.value = ''
  category.value = ''
  search()
}

// 页面加载完成后，商品和订单各拉各的
// 不 await：两个请求并发跑，谁先回来谁先渲染，互不阻塞
onMounted(() => {
  search()
  loadOrders()
})
</script>

<template>
  <div>
    <h1>校园好物市集</h1>
        <!-- 搜索区：三个控件全部绑到 App.vue 里的 ref -->
    <div class="filters">
      <!-- v-model：输入框内容和 keyword 双向绑定，打一个字就同步一次 -->
      <!-- @keyup.enter：光标在框里按回车也触发搜索，不用去点按钮 -->
      <input
        v-model="keyword"
        type="text"
        placeholder="搜索商品名称"
        @keyup.enter="search"
      />

      <!-- v-model 绑 select：选中的 option 的 value 写进 category -->
      <!-- 空字符串代表"不限分类"，client.js 会把它过滤掉不发给服务器 -->
      <select v-model="category" @change="search">
        <option value="">全部分类</option>
        <!-- v-for 遍历 CATEGORIES 生成剩下的选项 -->
        <option v-for="c in CATEGORIES" :key="c" :value="c">{{ c }}</option>
      </select>

      <button @click="search">搜索</button>
      <button @click="clearQuery">清空筛选</button>
    </div>

    <!-- 发布表单：不需要传任何 props，写成自闭合标签就行 -->
    <!-- @created="search"：子组件 emit('created') 时触发，正好复用 search 把新商品刷出来 -->
    <PublishForm @created="search" />

    <!-- 操作成功提示条：App 不会被卸载，所以这条消息能活过列表重拉 -->
    <p v-if="notice" class="notice">{{ notice }}</p>

    <!-- 把 products 和 loading 传给 ProductList 组件 -->
    <!-- :products 和 :loading 前面的冒号表示"传的是变量的值"，不是字符串 -->
    <!-- @ordered="onOrdered"：BuyPanel 下单成功 → ProductList 转告 → 这里写提示并重拉列表。
         谁拥有数据谁负责更新，所以刷新只能做在 App -->
    <ProductList
      :products="products"
      :loading="loading"
      @toggle="onToggle"
      @clear-query="clearQuery"
      @ordered="onOrdered"
    />

    <h2 class="orders-title">我的订单</h2>
    <!-- 订单列表：和商品列表各有一套 loading，互不影响 -->
    <OrderList :orders="orders" :loading="ordersLoading" />
  </div>
</template>

<style scoped>
/* 搜索区：控件横排，放不下自动换行 */
.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

/* 输入框和下拉框统一高度，视觉对齐 */
.filters input,
.filters select {
  height: 32px;
  padding: 0 8px;
  border: 1px solid #ccc;
  border-radius: 4px;
  font-size: 14px;
}

/* 关键词框宽一点，方便看长标题 */
.filters input {
  width: 220px;
}

/* 两个按钮 */
.filters button {
  height: 32px;
  padding: 0 14px;
  border: 1px solid #ccc;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  font-size: 14px;
}
.filters button:hover {
  background: #f5f5f5;
}

/* 操作成功提示条：配色和发布表单的成功提示、商品卡片的「在售」徽标同一套绿 */
/* 全站只有一种绿，这是 DESIGN.md 第 5 节能写的一条取舍 */
.notice {
  margin: 0 0 12px;
  padding: 8px 12px;
  border-radius: 4px;
  background: #e6f7e6;
  color: #2d7a2d;
  font-size: 13px;
}

/* 「我的订单」小标题：和上方商品区拉开距离，避免看着像一个整体 */
.orders-title {
  margin: 28px 0 8px;
  font-size: 18px;
  border-top: 1px solid #e0e0e0;
  padding-top: 16px;
}
</style>