<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import OrderList from '../components/OrderList.vue'
import { useOrders } from '../composables/useOrders.js'

const route = useRoute()
const { items, total, loading, error, buyerPhone, reload } = useOrders()

// 刚下的那一单高亮一下（下单页跳过来会带 ?highlight=<orderNo>）
const highlightOrderNo = ref(String(route.query.highlight ?? ''))

onMounted(() => {
  // 手机号用的是模块级共享值：下单时改过收货手机号，这里也能查到那一单
  const phone = String(route.query.phone ?? '').trim()
  reload(phone || undefined)
})
</script>

<template>
  <div>
    <OrderList
      :items="items"
      :total="total"
      :loading="loading"
      :error="error"
      :buyer-phone="buyerPhone"
      :highlight-order-no="highlightOrderNo"
      @retry="reload"
    />
  </div>
</template>
