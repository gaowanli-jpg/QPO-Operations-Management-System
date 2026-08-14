import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const pageSource = readFileSync(resolve(currentDir, 'components', 'DeliveredItemManage.vue'), 'utf8')

describe('DeliveredItemManage WMS batch result closure', () => {
  test('keeps WMS batch result independent from MES result state', () => {
    expect(pageSource).toContain('const mesBatchResult = ref(null)')
    expect(pageSource).toContain('const wmsBatchResult = ref(null)')
    expect(pageSource).toContain('wmsBatchResult.value = null')
    expect(pageSource).toContain('closeWmsBatchResult')
    expect(pageSource).toContain('v-if="wmsBatchResult"')
    expect(pageSource).toContain('class="erp-table-surface wms-batch-result-surface"')
  })

  test('shows task-submitted copy before displaying business result counts', () => {
    expect(pageSource).toContain('WMS 库存任务已提交')
    expect(pageSource).not.toContain('WMS 锁库完成')
    expect(pageSource).toContain('WMS 批量库存结果')
    expect(pageSource).toContain('批次数')
    expect(pageSource).toContain('成功')
    expect(pageSource).toContain('失败')
    expect(pageSource).toContain('待核对')
    expect(pageSource).toContain('应用失败')
    expect(pageSource).toContain('跳过')
  })

  test('maps reservation states to recovery copy', () => {
    expect(pageSource).toContain('RESULT_UNKNOWN')
    expect(pageSource).toContain('APPLY_FAILED')
    expect(pageSource).toContain('结果待核对')
    expect(pageSource).toContain('本地写入待修复')
  })

  test('polls the existing task endpoint and does not resubmit WMS on polling failure', () => {
    const pollingStart = pageSource.indexOf('const waitWmsBatchTaskResult')
    const pollingSource = pageSource.slice(pollingStart, pageSource.indexOf('const formatMesBatchCount', pollingStart))

    expect(pollingSource).toContain('getDeliveryIntegrationTask(taskId)')
    expect(pollingSource).toContain('console.warn')
    expect(pollingSource).toContain('return null')
    expect(pollingSource).not.toContain('submitWmsAllocationBatch')
  })
})
