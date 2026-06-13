import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  build: {
    chunkSizeWarningLimit: 800,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined
          if (id.includes('@element-plus/icons-vue')) return 'vendor-icons'
          if (id.includes('element-plus')) return 'vendor-element'
          if (id.includes('vue') || id.includes('pinia')) return 'vendor-vue'
          if (id.includes('marked') || id.includes('dompurify')) return 'vendor-markdown'
          if (id.includes('axios') || id.includes('nprogress') || id.includes('@microsoft/fetch-event-source')) {
            return 'vendor-network'
          }
          return 'vendor'
        }
      }
    }
  },
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
