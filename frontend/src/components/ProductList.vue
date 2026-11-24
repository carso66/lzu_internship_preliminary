<script setup>
// 引入金额换算函数，把分转成元
import { fenToYuan } from '../utils/money'

// 定义 props：父组件（App.vue）传下来的商品数组
defineProps({
  products: {
    type: Array,
    default: () => []
  }
})
</script>

<template>
  <div class="product-list">
    <!-- 遍历每个商品，渲染成卡片 -->
    <div v-for="product in products" :key="product.id" class="product-card">
      <!-- 标题和状态徽标 -->
      <div class="card-header">
        <h3 class="title">{{ product.title }}</h3>
        <span class="badge" :class="product.status">
          {{ product.status === 'on' ? '在售' : '已下架' }}
        </span>
      </div>
      
      <!-- 价格 -->
      <p class="price">{{ fenToYuan(product.price) }}</p>
      
      <!-- 库存、分类、缺货提示 -->
      <p class="meta">
      <span>库存 {{ product.stock }}</span>
      <span>{{ product.category }}</span>
      <span v-if="product.stock === 0" class="out-of-stock">缺货</span>
      </p>
      
      <!-- 卖家 -->
      <p class="seller">{{ product.sellerName }}</p>
    </div>
  </div>
</template>

<style scoped>
/* 商品列表的整体布局：自动换行的网格 */
.product-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  padding: 16px 0;
}

/* 单个商品卡片 */
.product-card {
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 16px;
  background: #fff;
}

/* 卡片头部：标题和徽标在同一行 */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}

/* 标题：允许换行，不撑破卡片 */
.title {
  margin: 0;
  font-size: 16px;
  overflow-wrap: anywhere;
}

/* 状态徽标 */
.badge {
  flex-shrink: 0;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  white-space: nowrap;
}
.badge.on {
  background: #e6f7e6;
  color: #2d7a2d;
}
.badge.off {
  background: #f0f0f0;
  color: #999;
}

/* 价格 */
.price {
  font-size: 20px;
  font-weight: bold;
  color: #e4393c;
  margin: 8px 0;
}

/* 库存、分类、缺货提示 */
.meta {
  display: flex;
  gap: 12px;
  font-size: 14px;
  color: #666;
  margin: 4px 0;
}
.out-of-stock {
  color: #e4393c;
  font-weight: bold;
}

/* 卖家 */
.seller {
  font-size: 12px;
  color: #999;
  margin: 4px 0 0;
}
</style>