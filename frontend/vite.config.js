import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

const mockProxy = {
  '/api': {
    target: 'http://127.0.0.1:3001',
    changeOrigin: true,
  },
}

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: mockProxy,
  },
  preview: {
    proxy: mockProxy,
  },
})
