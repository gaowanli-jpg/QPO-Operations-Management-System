import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))
const viteConfigSource = readFileSync(resolve(currentDir, '..', 'vite.config.js'), 'utf8')

describe('ERP Vite code splitting config', () => {
  test('splits major shared dependencies out of the app entry chunk', () => {
    expect(viteConfigSource).toContain('rolldownOptions')
    expect(viteConfigSource).toContain('codeSplitting')

    for (const chunkName of ['vendor-vue', 'vendor-element-plus', 'vendor-icons', 'vendor-utils']) {
      expect(viteConfigSource).toContain(`name: '${chunkName}'`)
    }

    expect(viteConfigSource).toContain('elementPlusComponentChunk')
    expect(viteConfigSource).toContain('vendor-element-plus-core')
  })
})
