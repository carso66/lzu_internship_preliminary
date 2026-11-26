<script setup>
import { formatPrice } from '../utils/money.js'
import { formatDateTime, orderStatusClass, orderStatusText } from '../utils/order.js'

defineProps({
  items: { type: Array, required: true },
  total: { type: Number, default: 0 },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  buyerPhone: { type: String, default: '' },
  /** 正在提交的下单商品 id，用于高亮刚下的那一单 */
  highlightOrderNo: { type: String, default: '' },
})

const emit = defineEmits(['retry'])
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <h2>我的订单</h2>
      <span class="panel-meta">
        手机号 <code>{{ buyerPhone }}</code> · 共 <strong>{{ total }}</strong> 单
      </span>
    </header>

    <!-- 三态互斥，和商品列表保持一致：加载中 / 出错 / 空 / 有数据 -->
    <div v-if="loading" class="state state-loading" data-testid="orders-loading">
      <span class="spinner" aria-hidden="true"></span>
      <p>加载中…</p>
    </div>

    <div v-else-if="error" class="state state-error" data-testid="orders-error">
      <p class="state-title">订单加载失败</p>
      <p class="state-desc">{{ error }}</p>
      <button type="button" class="btn btn-primary" @click="emit('retry')">重试</button>
    </div>

    <div v-else-if="items.length === 0" class="state state-empty" data-testid="orders-empty">
      <p class="state-title">还没有订单</p>
      <p class="state-desc">在左边的商品列表里点「购买」下第一单。</p>
    </div>

    <div v-else class="table-wrap">
      <table class="table table-orders">
        <thead>
          <tr>
            <th class="col-order-no">订单号</th>
            <th class="col-title">商品</th>
            <th class="col-qty">数量</th>
            <th class="col-price">金额</th>
            <th class="col-order-status">状态</th>
            <th class="col-time">下单时间</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="order in items"
            :key="order.orderNo"
            :class="{ 'row-new': order.orderNo === highlightOrderNo }"
            :data-testid="`order-${order.orderNo}`"
          >
            <td class="col-order-no"><code>{{ order.orderNo }}</code></td>
            <!-- 商品名用快照：商品改名后历史订单仍显示下单时的名字 -->
            <td class="col-title">
              <div class="title" :title="order.productTitle">{{ order.productTitle }}</div>
            </td>
            <td class="col-qty">{{ order.quantity }}</td>
            <!-- 金额永远是 ¥xx.xx 两位小数 -->
            <td class="col-price">{{ formatPrice(order.totalAmount) }}</td>
            <!-- 状态必须是中文，不露 created / paid 原文 -->
            <td class="col-order-status">
              <span class="badge" :class="orderStatusClass(order.status)">
                {{ orderStatusText(order.status) }}
              </span>
            </td>
            <td class="col-time">{{ formatDateTime(order.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
