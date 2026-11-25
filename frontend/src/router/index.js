import { createRouter, createWebHistory } from 'vue-router'
import OrderListView from '../views/OrderListView.vue'
import ProductDetailView from '../views/ProductDetailView.vue'
import ProductListView from '../views/ProductListView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'products',
      component: ProductListView,
    },
    {
      path: '/orders',
      name: 'orders',
      component: OrderListView,
    },
    {
      path: '/products/:id',
      name: 'product-detail',
      component: ProductDetailView,
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

export default router