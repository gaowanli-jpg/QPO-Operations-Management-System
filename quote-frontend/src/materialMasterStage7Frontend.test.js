import { describe, expect, test } from 'vitest'
import { readFileSync } from 'fs'
import { dirname, resolve } from 'path'
import { fileURLToPath } from 'url'

const currentDir = dirname(fileURLToPath(import.meta.url))

const readComponent = (name) => readFileSync(resolve(currentDir, 'components', name), 'utf-8')

describe('material master stage 7 frontend', () => {
  test('SalesMatch preview shows material code and material type differences', () => {
    const source = readComponent('SalesMatch.vue')

    expect(source).toContain('prop="engineeringMaterialCode" label="材料编码"')
    expect(source).toContain('prop="engineeringMaterialTypeName" label="材料类型"')
    expect(source).toContain("engineeringMaterialCode: '材料编码'")
    expect(source).toContain("engineeringMaterialTypeName: '材料类型'")
  })
})
