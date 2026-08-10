import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'fs'
import { dirname, resolve } from 'path'
import { fileURLToPath } from 'url'

const currentDir = dirname(fileURLToPath(import.meta.url))

const readComponent = (name) => readFileSync(resolve(currentDir, 'components', name), 'utf-8')

describe('material master stage 7 frontend', () => {
  test('retired manual SalesMatch preview is not a material identity surface', () => {
    expect(existsSync(resolve(currentDir, 'components', 'SalesMatch.vue'))).toBe(false)

    const salesOrderU9 = readComponent('SalesOrderU9Sync.vue')
    expect(salesOrderU9).toContain('prop="materialNo" label="物料号"')
    expect(salesOrderU9).toContain('prop="materialNoStatus"')
  })
})
