import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const elementPlusComponentChunk = (moduleId) => {
  const normalizedId = moduleId.replace(/\\/g, '/')
  const match = normalizedId.match(/\/node_modules\/element-plus\/(?:es|lib)\/components\/([^/]+)/)
  return match ? `vendor-ep-${match[1]}` : null
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  build: {
    rolldownOptions: {
      output: {
        codeSplitting: {
          groups: [
            {
              name: elementPlusComponentChunk,
              test: /node_modules[\\/]element-plus[\\/](es|lib)[\\/]components[\\/]/,
              priority: 70
            },
            {
              name: 'vendor-icons',
              test: /node_modules[\\/]@element-plus[\\/]icons-vue[\\/]/,
              priority: 60
            },
            {
              name: 'vendor-element-plus-core',
              test: /node_modules[\\/]element-plus[\\/]/,
              priority: 50
            },
            {
              name: 'vendor-element-plus',
              test: /node_modules[\\/](@ctrl|@floating-ui|async-validator|dayjs|lodash-es|lodash-unified|memoize-one|normalize-wheel)[\\/]/,
              priority: 40
            },
            {
              name: 'vendor-vue',
              test: /node_modules[\\/](vue|@vue|vue-router)[\\/]/,
              priority: 30
            },
            {
              name: 'vendor-utils',
              test: /node_modules[\\/](axios|pinia)[\\/]/,
              priority: 20
            }
          ]
        }
      }
    }
  }
})
