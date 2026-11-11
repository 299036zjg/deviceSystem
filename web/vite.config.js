import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

/**
 * 构建产物直接输出到后端静态资源目录，演示时只需启动 Spring Boot 一个进程。
 * 开发时可用 pnpm dev，通过代理访问 8080 端口的后端接口。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true,
    chunkSizeWarningLimit: 1500
  }
})
