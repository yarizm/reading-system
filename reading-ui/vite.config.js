import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173, // 前端端口
    proxy: {
      '/api': {
        target: 'http://localhost:8090', // 后端地址
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '') // 去掉路径中的 /api
      },
      '/ws': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        ws: true // 启用 WebSocket 代理
      },
      '/files': {
        target: 'http://localhost:8090',
        changeOrigin: true
      }
    }
  }
})