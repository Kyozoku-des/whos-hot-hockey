import { readFileSync } from 'node:fs'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { searchAssetsPlugin } from './scripts/search-assets.js'

const { version } = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf-8'))

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), searchAssetsPlugin()],
  // Preserve Vite 6's browser floor while upgrading the build toolchain.
  build: {
    target: ['es2020', 'edge88', 'firefox78', 'chrome87', 'safari14']
  },
  define: {
    // App version, sent along with feedback
    __APP_VERSION__: JSON.stringify(version)
  },
  server: {
    host: '127.0.0.1',
    port: 3000,
    fs: {
      // Cover query-bearing module IDs (?raw/?url) and
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

