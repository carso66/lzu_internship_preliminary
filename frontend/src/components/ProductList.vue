<script setup>
// 引入金额换算函数：把"分"转成"¥xx.xx"格式
import { fenToYuan } from '../utils/money'

// 定义 props：父组件（App.vue）传下来的数据
// products：商品数组
// loading：是否正在加载（true 显示"加载中"，false 显示列表）
defineProps({
  products: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  }
})

// 这个组件会向父组件发两种事件：
// 'toggle'：点上/下架按钮时触发
// 'clear-query'：点"清空筛选"按钮时触发
const emit = defineEmits(['toggle', 'clear-query'])

</script>

<template>
  <!-- v-if="loading"：如果正在加载，显示"加载中…"，不显示列表 -->
  <p v-if="loading" class="state">加载中…</p>

  <!-- v-else-if：不在加载中，且 products 长度为 0 时走这个分支 -->
  <!-- !products.length 就是"没有数据"的意思（0 是假值，取反变真） -->
  <!-- 三个分支互斥，所以"加载中"和"没找到"绝不可能同时出现在页面上 -->
  <div v-else-if="!products.length" class="state">
    <p>没找到匹配的商品。</p>
    <!-- 组件自己不知道 keyword 是什么（那是 App 的数据） -->
    <!-- 所以只能发事件"通知"父亲去清空，这就是"数据往下流、事件往上冒" -->
    <button @click="emit('clear-query')">清空筛选</button>
  </div>

  <!-- v-else：如果不在加载，才显示商品列表 -->
  <!-- v-for：遍历 products 数组，每个商品渲染一个卡片 -->
  <!-- :key="product.id"：Vue 用来追踪每个卡片，数据变了能准确更新 -->
  <div v-else class="product-list">
    <div v-for="product in products" :key="product.id" class="product-card">

      <!-- 卡片头部：标题 + 状态徽标，左右排列 -->
      <div class="card-header">
        <!-- {{ product.title }}：双大括号是文本插值，会自动转义 HTML -->
        <!-- 所以 mock 数据里的 <script>alert('xss')</script> 会安全地显示成文字 -->
        <h3 class="title">{{ product.title }}</h3>

        <!-- :class="product.status"：动态 CSS 类名 -->
        <!-- status 是 "on" 时，class 变成 "badge on"，CSS 给绿色背景 -->
        <!-- status 是 "off" 时，class 变成 "badge off"，CSS 给灰色背景 -->
        <span class="badge" :class="product.status">
          {{ product.status === 'on' ? '在售' : '已下架' }}
        </span>
      </div>

      <!-- 价格：调用 fenToYuan 把分转成元 -->
      <!-- 比如 product.price 是 1990（分），显示成 ¥19.90 -->
      <p class="price">{{ fenToYuan(product.price) }}</p>

      <!-- 库存、分类、缺货提示 -->
      <p class="meta">
        <span>库存 {{ product.stock }}</span>
        <span>{{ product.category }}</span>
        <!-- v-if：库存为 0 时才显示"缺货"，否则这行不渲染 -->
        <span v-if="product.stock === 0" class="out-of-stock">缺货</span>
      </p>

      <!-- 卖家名称 -->
      <p class="seller">{{ product.sellerName }}</p>

      <!-- 上下架按钮 -->
      <!-- 在售时按钮文案是"下架"，已下架时是"上架" -->
      <div class="actions">
        <button @click="emit('toggle', product)">
          {{ product.status === 'on' ? '下架' : '上架' }}
        </button>
      </div>

    </div>
  </div>
</template>

<style scoped>
/* scoped 表示这些样式只作用于当前组件，不影响其他组件 */

/* "加载中"和"没找到"这类状态文字 */
.state {
  color: #999;
  padding: 20px 0;
}

/* 商品列表的整体布局：自动换行的网格 */
/* repeat(auto-fill, minmax(280px, 1fr))：每个卡片至少 280px 宽，放得下几个放几个 */
.product-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  padding: 16px 0;
}

/* 单个商品卡片：白底 + 灰色边框 + 圆角 */
.product-card {
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 16px;
  background: #fff;
}

/* 卡片头部：标题和徽标在同一行，左右两端对齐 */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}

/* 标题：允许任意位置换行，超长也不撑破卡片 */
.title {
  margin: 0;
  font-size: 16px;
  overflow-wrap: anywhere;
}

/* 状态徽标：小圆角标签 */
.badge {
  flex-shrink: 0;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  white-space: nowrap;
}
/* 在售：浅绿底 + 深绿字 */
.badge.on {
  background: #e6f7e6;
  color: #2d7a2d;
}
/* 已下架：浅灰底 + 深灰字 */
.badge.off {
  background: #f0f0f0;
  color: #999;
}

/* 价格：大号红色加粗 */
.price {
  font-size: 20px;
  font-weight: bold;
  color: #e4393c;
  margin: 8px 0;
}

/* 库存、分类、缺货提示：横排，间距 12px */
.meta {
  display: flex;
  gap: 12px;
  font-size: 14px;
  color: #666;
  margin: 4px 0;
}
/* 缺货：红色加粗 */
.out-of-stock {
  color: #e4393c;
  font-weight: bold;
}

/* 卖家：小号灰色 */
.seller {
  font-size: 12px;
  color: #999;
  margin: 4px 0 0;
}

/* 按钮区域 */
.actions {
  margin-top: 12px;
}
/* 按钮样式 */
.actions button {
  padding: 6px 16px;
  border: 1px solid #ccc;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
}
.actions button:hover {
  background: #f5f5f5;
}
</style>