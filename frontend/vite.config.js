import { readFileSync } from 'node:fs'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const { version } = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf-8'))

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  define: {
    // App version, sent along with feedback
    __APP_VERSION__: JSON.stringify(version)
  },
  server: {
    host: '127.0.0.1',
    port: 3000,
    fs: {
      // Vite 6 can match the query-bearing module ID. Cover ?raw/?url and
      // Windows alternate paths as well as the plain sensitive filenames.
      deny: ['.env*', '*.{crt,pem}*', '**/.git/**']
    },
    // Mirrors the nginx /api proxy used in the container image so the relative
    // API base URL in useApi.js works identically in dev.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
