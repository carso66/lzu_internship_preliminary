import { defineConfig } from 'vite';

// 只把 Vite 当 dev server / 测试运行器用，业务代码是原生 JS，没有框架。
export default defineConfig({
  // appType 默认就是 'spa'：直接访问 /products/1 也会回退到 index.html，
  // L2-13B「路由：新标签页直接打开详情页」靠的就是这个。
  server: {
    // 显式绑 IPv4：默认的 'localhost' 在 Windows 上会只监听 ::1，
    // 导致 http://127.0.0.1:5173 连不上（dev server 与 mock 都用 IPv4，口径统一）
    host: '127.0.0.1',
    port: 5173,
    strictPort: false,
  },
  test: {
    environment: 'node',
    include: ['tests/**/*.test.js'],
  },
});
