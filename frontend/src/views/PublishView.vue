<script setup>
import { useRouter } from 'vue-router'
import ProductForm from '../components/ProductForm.vue'
import { useToasts } from '../composables/useToasts.js'

const router = useRouter()
const { toast } = useToasts()

/**
 * 发布成功后直接跳回列表，并带上 `?highlight=<id>`。
 *
 * 页面拆开以后「表单」和「列表」不在同一屏，光靠行内成功提示用户看不到新商品，
 * L0-3 的「列表里能找到刚发布的商品」就不成立了。
 * toast 是模块级单例，换页之后提示仍然看得见。
 */
async function onCreated(created) {
  await router.push({ name: 'products', query: { highlight: String(created?.id ?? '') } })
}
</script>

<template>
  <div>
    <ProductForm @created="onCreated" @notify="toast[$event.type]($event.message)" />
  </div>
</template>
