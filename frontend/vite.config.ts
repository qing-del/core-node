import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/api/, '')
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
        changeOrigin: true
      },
      // The backend owns /s/{code}: it returns the short-lived 302 to the SPA.
      // Do not rewrite this path, otherwise the controller cannot receive code.
      '/s/': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
