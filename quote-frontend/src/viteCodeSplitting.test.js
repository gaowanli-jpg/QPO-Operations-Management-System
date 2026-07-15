import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))
const viteConfigSource = readFileSync(resolve(currentDir, '..', 'vite.config.js'), 'utf8')

describe('ERP Vite production chunking config', () => {
  test('avoids fragile third-party manual vendor splitting', () => {
    expect(viteConfigSource).not.toContain('rolldownOptions')
    expect(viteConfigSource).not.toContain('codeSplitting')

    for (const chunkName of ['vendor-vue', 'vendor-element-plus', 'vendor-element-plus-core', 'vendor-icons', 'vendor-utils']) {
      expect(viteConfigSource).not.toContain(chunkName)
    }

    expect(viteConfigSource).not.toContain('elementPlusComponentChunk')
    expect(viteConfigSource).not.toContain('element-plus[\\/](es|lib)[\\/]components')
    expect(viteConfigSource).toContain('chunkSizeWarningLimit: 1000')
  })
})
