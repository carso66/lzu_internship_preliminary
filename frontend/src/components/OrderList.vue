<script setup>
// 订单是"扫读型"数据，用表格；商品是"操作型"数据，用卡片。这是两者的分工。
import { fenToYuan } from '../utils/money'

// orders 和 loading 都是 App 传下来的，这个组件只负责画，不碰数据
defineProps({
  orders: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  }
})

// 状态中文映射，全站只维护这一份
// 以后服务器加 refunded，只改这里一行，不用去翻模板
const STATUS_TEXT = {
  created: '待支付',
  paid: '已支付'
}

// 兜底：遇到没见过的状态就原样显示，绝不留空白
// 留空白会让人以为"这单没状态"，比显示英文更糟
function statusText(status) {
  return STATUS_TEXT[status] || status
}
</script>

<template>
  <!-- 三态互斥，和 ProductList 用的是同一套手法 -->
  <p v-if="loading" class="state">加载中…</p>

  <p v-else-if="!orders.length" class="state">还没有订单。</p>

  <table v-else class="order-table">
    <thead>
      <tr>
        <th>订单号</th>
        <th>商品</th>
        <th>数量</th>
        <th>金额</th>
        <th>状态</th>
      </tr>
    </thead>
    <tbody>
      <!-- 服务器按 id 倒序返回，最新的订单自动在最上面 -->
      <tr v-for="order in orders" :key="order.id">
        <td class="mono">{{ order.orderNo }}</td>

        <!-- 商品名：服务器冗余存了一份 productTitle，
             所以这里不用再回查商品表，也不怕商品后来被改名或下架 -->
        <td class="title">{{ order.productTitle }}</td>

        <td class="num">{{ order.quantity }}</td>

        <!-- 金额直接用服务器的 totalAmount，不在前端重算。
             前端那个"预估合计"只在下单前有用，下单后以这里为准 -->
        <td class="num amount">{{ fenToYuan(order.totalAmount) }}</td>

        <td>
          <!-- 动态 class：paid 绿、created 橙，和商品徽标同一套配色逻辑 -->
          <span class="badge" :class="order.status">{{ statusText(order.status) }}</span>
        </td>
      </tr>
    </tbody>
  </table>
</template>

<style scoped>
/* 状态文字：和 ProductList 的 .state 保持一样 */
.state {
  color: #999;
  padding: 12px 0;
}

.order-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

/* 表头：浅灰底 + 细下划线，把表头和数据分开 */
.order-table th {
  text-align: left;
  padding: 8px 10px;
  background: #fafafa;
  border-bottom: 1px solid #e0e0e0;
  color: #666;
  font-weight: normal;
}

.order-table td {
  padding: 8px 10px;
  border-bottom: 1px solid #f0f0f0;
}

/* 订单号：等宽字体，字母数字对齐好认 */
.mono {
  font-family: ui-monospace, Consolas, monospace;
  font-size: 13px;
}

/* 商品名：超长任意位置换行，不撑破表格 */
.title {
  overflow-wrap: anywhere;
}

/* 数量和金额右对齐，方便比大小 */
.num {
  text-align: right;
}

.amount {
  color: #e4393c;
  font-weight: bold;
}

/* 状态徽标：和商品卡片的在售/已下架同款 */
.badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  white-space: nowrap;
}
/* 待支付：橙色，提示"还有事没做完" */
.badge.created {
  background: #fff4e5;
  color: #a05a00;
}
/* 已支付：绿色，和「在售」同一个绿 */
.badge.paid {
  background: #e6f7e6;
  color: #2d7a2d;
}
</style>
