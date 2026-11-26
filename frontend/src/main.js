import { createApp } from 'vue'
import App from './App.vue'
import { createAppRouter } from './router/index.js'
import './styles.css'

const app = createApp(App)
// history 模式：`/products/3` 这类地址直接在新标签页打开也能落到对应页面
app.use(createAppRouter())
app.mount('#app')
