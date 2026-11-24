<script setup>
import { onMounted, ref } from 'vue'
import { api } from './api/client'
import ProductList from './components/ProductList.vue'

const products = ref([])

onMounted(async () => {
  const data = await api.listProducts({ pageSize: 50 })
  products.value = data.items
})
</script>

<template>
  <div>
    <h1>校园好物市集</h1>
    <ProductList :products="products" />
  </div>
</template>