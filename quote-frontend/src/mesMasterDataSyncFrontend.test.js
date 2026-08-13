import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')
const readRoot = (...segments) => readFileSync(resolve(currentDir, '..', '..', ...segments), 'utf8')

describe('MES master data sync stage 0 frontend guard', () => {
  test('dict API exposes MES master data sync endpoints as POST calls', () => {
    const dictApi = readSource('services', 'dictApi.js')

    expect(dictApi).toContain('syncMesProcessTypes')
    expect(dictApi).toContain("/api/dict/process-categories/sync-mes")
    expect(dictApi).toContain('syncMesMaterials')
    expect(dictApi).toContain("/api/dict/materials/sync-mes")
    expect(dictApi).toMatch(/syncMesProcessTypes[\s\S]*method: 'POST'/)
    expect(dictApi).toMatch(/syncMesMaterials[\s\S]*method: 'POST'/)
  })

  test('material process page exposes permission-gated MES sync buttons with pending guards', () => {
    const materialProcess = readSource('components', 'MaterialProcessManage.vue')

    expect(materialProcess).toContain('从 MES 同步材料')
    expect(materialProcess).toContain('从 MES 同步工艺')
    expect(materialProcess).toContain("v-permission=\"'engineer:dict:material-manage'\" v-if=\"activeTab === 'materials'\"")
    expect(materialProcess).toContain("v-permission=\"'engineer:dict:process-manage'\" v-if=\"activeTab === 'processCategories'\"")
    expect(materialProcess).toContain(':loading="isPending(dictActionKeys.syncMesMaterials)"')
    expect(materialProcess).toContain(':disabled="isPending(dictActionKeys.syncMesMaterials)"')
    expect(materialProcess).toContain(':loading="isPending(dictActionKeys.syncMesProcessTypes)"')
    expect(materialProcess).toContain(':disabled="isPending(dictActionKeys.syncMesProcessTypes)"')
    expect(materialProcess).toContain('return runAction(dictActionKeys.syncMesMaterials, async () =>')
    expect(materialProcess).toContain('return runAction(dictActionKeys.syncMesProcessTypes, async () =>')
    expect(materialProcess).toContain('MES 会更新基础信息，不覆盖 QPO 材料单价')
    expect(materialProcess).toContain('await dictionaryStore.reloadAfterMutation()')
  })

  test('material and process lists display MES ownership and local price maintenance state', () => {
    const materialProcess = readSource('components', 'MaterialProcessManage.vue')

    expect(materialProcess).toContain('label="来源"')
    expect(materialProcess).toContain('label="MES 类型"')
    expect(materialProcess).toContain('label="同步时间"')
    expect(materialProcess).toContain('label="单价状态"')
    expect(materialProcess).toContain('scope.row.mesMaterialType')
    expect(materialProcess).toContain('scope.row.mesSyncedAt')
    expect(materialProcess).toContain('formatSyncTime')
    expect(materialProcess).toContain('materialPriceStatusText')
    expect(materialProcess).toContain('materialPriceStatusType')
    expect(materialProcess).toContain('单价未维护')
  })

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
