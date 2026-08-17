import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')
const readRoot = (...segments) => readFileSync(resolve(currentDir, '..', '..', ...segments), 'utf8')

describe('MES master data sync frontend guard', () => {
  test('dict API exposes MES master data sync endpoints as POST calls', () => {
    const dictApi = readSource('services', 'dictApi.js')

    expect(dictApi).toContain('syncMesProcessTypes')
    expect(dictApi).toContain("/api/dict/process-categories/sync-mes")
    expect(dictApi).toContain('syncMesMaterials')
    expect(dictApi).toContain("/api/dict/materials/sync-mes")
    expect(dictApi).toMatch(/syncMesProcessTypes[\s\S]*method: 'POST'/)
    expect(dictApi).toMatch(/syncMesMaterials[\s\S]*method: 'POST'/)
  })

  test('dict API exposes MES timed sync schedule endpoints', () => {
    const dictApi = readSource('services', 'dictApi.js')

    expect(dictApi).toContain('getMesMaterialSyncSchedule')
    expect(dictApi).toContain('saveMesMaterialSyncSchedule')
    expect(dictApi).toContain('getMesProcessSyncSchedule')
    expect(dictApi).toContain('saveMesProcessSyncSchedule')
    expect(dictApi).toContain("request('/api/dict/materials/sync-schedule')")
    expect(dictApi).toContain("request('/api/dict/materials/sync-schedule', {")
    expect(dictApi).toContain("request('/api/dict/process-categories/sync-schedule')")
    expect(dictApi).toContain("request('/api/dict/process-categories/sync-schedule', {")
    expect(dictApi).toMatch(/saveMesMaterialSyncSchedule[\s\S]*method: 'PUT'/)
    expect(dictApi).toMatch(/saveMesProcessSyncSchedule[\s\S]*method: 'PUT'/)
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
    expect(materialProcess).toContain('MES 会更新基础信息，不覆盖 QPO 材料单价和板/棒/管密度')
    expect(materialProcess).toContain('await dictionaryStore.reloadAfterMutation()')
  })

  test('material process page exposes MES timed sync schedule buttons and dialog', () => {
    const materialProcess = readSource('components', 'MaterialProcessManage.vue')

    expect(materialProcess).toContain('定时同步策略')
    expect(materialProcess).toContain('openMesMaterialScheduleDialog')
    expect(materialProcess).toContain('openMesProcessScheduleDialog')
    expect(materialProcess).toContain('getMesMaterialSyncSchedule')
    expect(materialProcess).toContain('saveMesMaterialSyncSchedule')
    expect(materialProcess).toContain('getMesProcessSyncSchedule')
    expect(materialProcess).toContain('saveMesProcessSyncSchedule')
    expect(materialProcess).toContain("v-permission=\"'engineer:dict:material-manage'\"")
    expect(materialProcess).toContain("v-permission=\"'engineer:dict:process-manage'\"")
    expect(materialProcess).toContain('scheduleDialogVisible')
    expect(materialProcess).toContain('scheduleForm.startupDelaySeconds')
    expect(materialProcess).toContain('scheduleForm.fixedDelayMinutes')
    expect(materialProcess).toContain('lastScheduledSyncRunId')
    expect(materialProcess).toContain('scheduleStatusLabel')
    expect(materialProcess).toContain('saveMesScheduleSetting')
    expect(materialProcess).toContain('startupDelayMs: Number(scheduleForm.startupDelaySeconds || 0) * 1000')
    expect(materialProcess).toContain('fixedDelayMs: Number(scheduleForm.fixedDelayMinutes || 1) * 60 * 1000')
    expect(materialProcess).toContain('MES 会更新基础信息，不覆盖 QPO 材料单价和板/棒/管密度')
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

  test('keeps the four material process dictionary tabs but gates MES-owned maintenance', () => {
    const materialProcess = readSource('components', 'MaterialProcessManage.vue')

    expect(materialProcess).toContain('name="materials"')
    expect(materialProcess).toContain('name="materialTypes"')
    expect(materialProcess).toContain('name="processCategories"')
    expect(materialProcess).toContain('name="processDetails"')
    expect(materialProcess).not.toContain('@click="openMaterialDialog()"')
    expect(materialProcess).not.toContain('@click="openMaterialTypeDialog')
    expect(materialProcess).not.toContain('@click="deleteMaterialType')
    expect(materialProcess).not.toContain('@click="openProcessCategoryDialog')
    expect(materialProcess).not.toContain('@click="openProcessDetailDialog()"')
    expect(materialProcess).not.toContain('@click="deleteMaterial(scope.row)"')
    expect(materialProcess).toContain('@click="openMaterialDialog(scope.row)"')
    expect(materialProcess).toContain('@click="openProcessDetailDialog(scope.row)"')
  })

  test('material list exposes read-only MES density from synchronized materials', () => {
    const materialProcess = readSource('components', 'MaterialProcessManage.vue')

    expect(materialProcess).toContain('mesDensityRaw')
    expect(materialProcess).toContain('scope.row.mesDensityRaw')
    expect(materialProcess).toContain('formatDensity(scope.row.mesDensityRaw)')
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
