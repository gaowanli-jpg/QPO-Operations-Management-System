import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const pageSource = readFileSync(resolve(currentDir, 'components', 'DeliveredItemManage.vue'), 'utf8')
const apiSource = readFileSync(resolve(currentDir, 'services', 'deliveredItemApi.js'), 'utf8')

describe('DeliveredItemManage MES material sync guardrails', () => {
  test('freezes MES sync API and module-owned permission', () => {
    expect(apiSource).toContain('syncMesMaterial')
    expect(apiSource).toContain('/api/order-delivery/tickets/${ticketId}/mes-material')
    expect(apiSource).toContain("method: 'POST'")
    expect(pageSource).toContain('DELIVERED_ITEM_MES_SYNC_PERMISSION')
    expect(pageSource).toContain('market:delivered-item:mes-sync')
    expect(pageSource).toContain('v-permission="DELIVERED_ITEM_MES_SYNC_PERMISSION"')
  })

  test('freezes MES button display rules by ticket status', () => {
    expect(pageSource).toContain('mesSyncAction(row)')
    expect(pageSource).toContain('获取物料号')
    expect(pageSource).toContain('重试')
    expect(pageSource).toContain('处理中')
    expect(pageSource).toContain("row.mesSyncStatus === 'PROCESSING'")
    expect(pageSource).toContain("row.mesSyncStatus === 'FAILED'")
    expect(pageSource).toContain("row.mesSyncStatus === 'SUCCESS' && row.materialNo")
  })

  test('confirms submission and reports task id after MES sync task is created', () => {
    expect(pageSource).toContain('ElMessageBox.confirm')
    expect(pageSource).toContain('确认向 MES 获取该零件的物料号？')
    expect(pageSource).toContain('syncMesMaterial(row.id)')
    expect(pageSource).toContain('MES 物料号任务已提交，任务ID')
    expect(pageSource).toContain('await loadItems()')
    expect(pageSource).toContain('ElMessage.error')
  })
})
