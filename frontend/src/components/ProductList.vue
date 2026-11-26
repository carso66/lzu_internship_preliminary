<script setup>
import { computed } from 'vue'
import ProductRow from './ProductRow.vue'
import { CATEGORIES } from '../api/products.js'

const props = defineProps({
  items: { type: Array, required: true },
  total: { type: Number, default: 0 },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  filters: { type: Object, required: true },
  pendingIds: { type: Array, default: () => [] },
  // 从「发布成功」跳回列表时会带上 highlight=新 id，让新行一眼可见
  highlightId: { type: Number, default: 0 },
})

const emit = defineEmits(['update:filters', 'search', 'clear', 'toggle', 'buy', 'retry'])

const hasFilters = computed(
  () => Boolean(props.filters.keyword.trim() || props.filters.status || props.filters.category),
)

function patch(key, value) {
  emit('update:filters', { ...props.filters, [key]: value })
}
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <h2>商品列表</h2>
      <span class="panel-meta">
        共 <strong>{{ total }}</strong> 件<template v-if="hasFilters">（已筛选）</template>
      </span>
    </header>

    <div class="filters">
      <input
        :value="filters.keyword"
        type="search"
        class="input"
        placeholder="搜索标题，如 耳机 / 图书"
        @input="patch('keyword', $event.target.value)"
        @keyup.enter="emit('search')"
      />
      <select :value="filters.status" class="input" @change="patch('status', $event.target.value)">
        <option value="">全部状态</option>
        <option value="on">在售</option>
        <option value="off">已下架</option>
      </select>
      <select :value="filters.category" class="input" @change="patch('category', $event.target.value)">
        <option value="">全部分类</option>
        <option v-for="c in CATEGORIES" :key="c" :value="c">{{ c }}</option>
      </select>
      <button type="button" class="btn btn-primary" @click="emit('search')">查询</button>
      <button v-if="hasFilters" type="button" class="btn btn-ghost" @click="emit('clear')">
        清空筛选
      </button>
    </div>

    <!-- 三态互斥：加载中 / 出错 / 空态 / 有数据，同一时刻只渲染一个 -->
    <div v-if="loading" class="state state-loading" data-testid="state-loading">
      <span class="spinner" aria-hidden="true"></span>
      <p>加载中…</p>
    </div>

    <div v-else-if="error" class="state state-error" data-testid="state-error">
      <p class="state-title">加载失败</p>
      <p class="state-desc">{{ error }}</p>
      <button type="button" class="btn btn-primary" @click="emit('retry')">重试</button>
    </div>

    <div v-else-if="items.length === 0" class="state state-empty" data-testid="state-empty">
      <p class="state-title">{{ hasFilters ? '没有找到符合条件的商品' : '暂时还没有商品' }}</p>
      <p class="state-desc">
        {{ hasFilters ? '试试换个关键词，或清空筛选看看全部商品。' : '用左上角的发布表单添加第一件商品。' }}
      </p>
      <button v-if="hasFilters" type="button" class="btn btn-primary" @click="emit('clear')">
        清空筛选
      </button>
    </div>

    <div v-else class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th class="col-id">ID</th>
            <th class="col-title">商品</th>
            <th class="col-category">分类</th>
            <th class="col-price">价格</th>
            <th class="col-stock">库存</th>
            <th class="col-status">状态</th>
            <th class="col-action">操作</th>
          </tr>
        </thead>
        <tbody>
          <ProductRow
            v-for="item in items"
            :key="item.id"
            :item="item"
            :pending="pendingIds.includes(item.id)"
            :highlight="item.id === highlightId"
            @toggle="emit('toggle', $event)"
            @buy="emit('buy', $event)"
          />
        </tbody>
      </table>
    </div>
  </section>
</template>
