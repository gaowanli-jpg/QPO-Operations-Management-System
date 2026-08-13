import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')
const readRoot = (...segments) => readFileSync(resolve(currentDir, '..', '..', ...segments), 'utf8')

describe('MES master data sync stage 0 frontend guard', () => {
  test('keeps the four material process dictionary tabs before MES sync implementation', () => {
    const materialProcess = readSource('components', 'MaterialProcessManage.vue')

    expect(materialProcess).toContain('name="materials"')
    expect(materialProcess).toContain('name="materialTypes"')
    expect(materialProcess).toContain('name="processCategories"')
    expect(materialProcess).toContain('name="processDetails"')
    expect(materialProcess).toContain('openMaterialDialog')
    expect(materialProcess).toContain('openMaterialTypeDialog')
    expect(materialProcess).toContain('openProcessCategoryDialog')
    expect(materialProcess).toContain('openProcessDetailDialog')
  })

  test('documents future MES sync entry points and QPO price ownership', () => {
    const designDoc = readRoot('docs', 'superpowers', 'specs', '2026-08-13-mes-master-data-sync-design.md')

    expect(designDoc).toContain('syncMesProcessTypes()')
    expect(designDoc).toContain('syncMesMaterials()')
    expect(designDoc).toContain('POST /api/dict/process-categories/sync-mes')
    expect(designDoc).toContain('POST /api/dict/materials/sync-mes')
    expect(designDoc).toContain('QPO 继续维护材料核价单价')
    expect(designDoc).toContain('材料单价缺失或为 0 时，后端禁止完成核价')
  })
})
