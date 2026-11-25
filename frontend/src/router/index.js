import { createRouter, createWebHistory } from 'vue-router'
import { routes } from './routes.js'

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : '页面不存在'
  document.title = `${title} · 校园好物市集`
})

export default router
