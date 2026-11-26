import OrderListView from '../views/OrderListView.vue'
import ProductCreateView from '../views/ProductCreateView.vue'
import ProductDetailView from '../views/ProductDetailView.vue'
import ProductListView from '../views/ProductListView.vue'
import NotFoundView from '../views/NotFoundView.vue'

export const routes = [
  {
    path: '/',
    name: 'products',
    component: ProductListView,
    meta: { title: '商品列表' },
  },
  {
    path: '/orders',
    name: 'orders',
    component: OrderListView,
    meta: { title: '我的订单' },
  },
  {
    path: '/products/new',
    name: 'product-create',
    component: ProductCreateView,
    meta: { title: '上架商品' },
  },
  {
    path: '/products/:id',
    name: 'product-detail',
    component: ProductDetailView,
    meta: { title: '商品详情' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: NotFoundView,
    meta: { title: '页面不存在' },
  },
]
