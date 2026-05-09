import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',       // 允许局域网访问
    allowedHosts: true,
    port: 5174,
    proxy: {
      '/api': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      '/ws': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        ws: true
      },
      '/files': {
        target: 'http://localhost:8090',
        changeOrigin: true
      }
    }
  },
  // 预览模式也需要代理请求
  preview: {
    host: '0.0.0.0',
    allowedHosts: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      '/ws': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        ws: true
      },
      '/files': {
        target: 'http://localhost:8090',
        changeOrigin: true
      }
    }
  }
})
