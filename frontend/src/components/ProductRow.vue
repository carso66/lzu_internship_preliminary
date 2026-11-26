<script setup>
import { computed } from 'vue'
import { formatPrice } from '../utils/money.js'

const props = defineProps({
  item: { type: Object, required: true },
  pending: { type: Boolean, default: false },
  // 刚发布 / 刚下单的商品在长列表里高亮一下，让用户知道自己操作的是哪一行
  highlight: { type: Boolean, default: false },
})

const emit = defineEmits(['toggle', 'buy'])

// 下架或缺货都不能下单，按钮直接禁用，省一次注定 409 的往返
const buyable = computed(() => props.item.status === 'on' && props.item.stock > 0)
</script>

<template>
  <tr
    :class="{
      'row-soldout': item.status === 'off',
      'row-empty': item.stock === 0,
      'row-new': highlight,
    }"
    :data-highlight="highlight ? '' : undefined"
  >
    <td class="col-id">{{ item.id }}</td>
    <td class="col-title">
      <!--
        标题就是进入详情的入口（L2-13 B：从列表点进详情，URL 变成 /products/3）。
        用 RouterLink 而不是 onclick router.push，这样鼠标中键 / Ctrl+点击能开新标签页。
      -->
      <RouterLink
        class="title title-link"
        :to="{ name: 'product-detail', params: { id: item.id } }"
        :title="item.title"
        :data-testid="`detail-${item.id}`"
      >
        <!-- 必须用文本插值：种子数据里有一条 <script>alert('xss')</script>，v-html 会真弹窗 -->
        {{ item.title }}
      </RouterLink>
      <div v-if="item.description" class="desc" :title="item.description">{{ item.description }}</div>
    </td>
    <td class="col-category">
      <span class="tag">{{ item.category }}</span>
    </td>
    <!-- 接口给的是「分」，展示一律 ¥xx.xx -->
    <td class="col-price">{{ formatPrice(item.price) }}</td>
    <td class="col-stock">
      <template v-if="item.stock === 0">
        <span class="badge badge-soldout">缺货</span>
      </template>
      <template v-else>{{ item.stock }}</template>
    </td>
    <td class="col-status">
      <span class="badge" :class="item.status === 'on' ? 'badge-on' : 'badge-off'">
        {{ item.status === 'on' ? '在售' : '已下架' }}
      </span>
    </td>
    <td class="col-action col-action-group">
      <button
        type="button"
        class="btn btn-primary btn-sm"
        :disabled="!buyable"
        :title="buyable ? '' : item.status === 'off' ? '商品已下架' : '库存不足'"
        :data-testid="`buy-${item.id}`"
        @click="emit('buy', item)"
      >
        购买
      </button>
      <button
        type="button"
        class="btn btn-ghost btn-sm"
        :disabled="pending"
        :data-testid="`toggle-${item.id}`"
        @click="emit('toggle', item)"
      >
        {{ pending ? '处理中…' : item.status === 'on' ? '下架' : '上架' }}
      </button>
    </td>
  </tr>
</template>
