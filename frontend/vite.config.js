import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * 代理目标按优先级取：
 *   1. API_TARGET —— 完整地址，最灵活（如 http://127.0.0.1:3001 回退到 mock）
 *   2. MOCK_PORT  —— 兼容题目给的 mock 用法
 *   3. 默认 http://127.0.0.1:8080 —— Spring Boot 后端
 *
 * 导出只为单测能直接断言优先级（test/proxy.test.js），运行时由 Vite 调用。
 */
export function resolveApiTarget(env) {
  if (env.API_TARGET) return env.API_TARGET
  if (env.MOCK_PORT) return `http://127.0.0.1:${env.MOCK_PORT}`
  return `http://127.0.0.1:${env.BACKEND_PORT || '8080'}`
}

export default defineConfig(({ mode }) => {
  // 第三个参数传 '' 才会把不带 VITE_ 前缀的变量也读进来
  const env = { ...loadEnv(mode, process.cwd(), ''), ...process.env }
  const apiTarget = resolveApiTarget(env)

  return {
    plugins: [vue()],
    server: {
      port: 5173,
      // 端口被占时直接报错，而不是悄悄换成 5174 —— 免得评审按 5173 打不开
      strictPort: true,
      proxy: {
        // 前端一律请求同源 /api，由 Vite 转发，避免拼错 host / 跨域问题
        '/api': {
          target: apiTarget,
          changeOrigin: true,
        },
      },
    },
    test: {
      environment: 'node',
      include: ['test/**/*.test.js'],
    },
  }
})
