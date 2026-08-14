import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const pageSource = readFileSync(resolve(currentDir, 'components', 'DeliveredItemManage.vue'), 'utf8')
const apiSource = readFileSync(resolve(currentDir, 'services', 'deliveredItemApi.js'), 'utf8')

describe('DeliveredItemManage MES material sync guardrails', () => {
  test('freezes MES sync API and module-owned permission', () => {
    expect(apiSource).toContain('syncMesMaterialBatch')
    expect(apiSource).toContain('/api/order-delivery/tickets/mes-material/batch')
    expect(apiSource).not.toContain('syncMesMaterial =')
    expect(apiSource).not.toContain('/api/order-delivery/tickets/${ticketId}/mes-material')
    expect(apiSource).toContain("method: 'POST'")
    expect(pageSource).toContain('DELIVERED_ITEM_MES_SYNC_PERMISSION')
    expect(pageSource).toContain('market:delivered-item:mes-sync')
    expect(pageSource).toContain('v-permission="DELIVERED_ITEM_MES_SYNC_PERMISSION"')
  })

  test('keeps MES sync as a batch toolbar action instead of row-level fetch buttons', () => {
    expect(pageSource).toContain('handleBatchMesSync')
    expect(pageSource).toContain('syncMesMaterialBatch')
    expect(pageSource).toContain('batchMesSync')
    expect(pageSource).toContain("CALLBACK_PENDING: 'warning'")
    expect(pageSource).not.toContain('mesSyncAction(row)')
    expect(pageSource).not.toContain('@click="handleMesSync(row)"')
    expect(pageSource).not.toContain('syncMesMaterial(row.id)')
  })

  test('confirms batch submission and reports task id after MES sync task is created', () => {
    expect(pageSource).toContain('ElMessageBox.confirm')
    expect(pageSource).toContain('确认批量向 MES 获取')
    expect(pageSource).toContain('syncMesMaterialBatch(selectedTicketIds.value)')
    expect(pageSource).toContain('批量 MES 物料号任务已提交，任务ID')
    expect(pageSource).toContain('await loadItems()')
    expect(pageSource).toContain('ElMessage.error')
  })
})
