import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const deliveredPageSource = readFileSync(resolve(currentDir, 'components', 'DeliveredItemManage.vue'), 'utf8')
const productionPageSource = readFileSync(resolve(currentDir, 'components', 'ProductionItemManage.vue'), 'utf8')
const apiSource = readFileSync(resolve(currentDir, 'services', 'deliveredItemApi.js'), 'utf8')

describe('MES real batch frontend workflow', () => {
  test('delivered item API exposes task-result polling through module API', () => {
    expect(apiSource).toContain('getDeliveryIntegrationTask')
    expect(apiSource).toContain('/api/tasks/${taskId}')
  })

  test('delivered item page renders complete MES material batch result states', () => {
    expect(deliveredPageSource).toContain('mesMaterialBatchStats')
    expect(deliveredPageSource).toContain('MES 批次数')
    expect(deliveredPageSource).toContain('实际发送')
    expect(deliveredPageSource).toContain('已有物料成功')
    expect(deliveredPageSource).toContain('待 MES 创建')
    expect(deliveredPageSource).toContain('Follower')
    expect(deliveredPageSource).toContain('结果待核对')
    expect(deliveredPageSource).toContain('MES 已受理，等待物料创建结果')
    expect(deliveredPageSource).toContain('RESULT_UNKNOWN')
    expect(deliveredPageSource).toContain('closeMesBatchResult')
    expect(deliveredPageSource).toContain('mes-batch-result-details')
    expect(deliveredPageSource).toContain('taskNo')
    expect(deliveredPageSource).toContain('bizId')
  })

  test('production page summarizes selected rows before dispatch and supports unknown reconciliation wording', () => {
    expect(productionPageSource).toContain('productionDispatchPreview')
    expect(productionPageSource).toContain('将实际发送')
    expect(productionPageSource).toContain('预计 MES 批次')
    expect(productionPageSource).toContain('待生产总数量')
    expect(productionPageSource).toContain('被阻断或跳过')
    expect(productionPageSource).toContain('RESULT_UNKNOWN')
    expect(productionPageSource).toContain('核对结果')
    expect(productionPageSource).toContain('handleMesProductionReconcile')
    expect(productionPageSource).toContain('closeMesProductionResult')
    expect(productionPageSource).toContain('mes-production-result-details')
    expect(productionPageSource).toContain('externalId')
    expect(productionPageSource).toContain('requestId')
  })

  test('batch result details stay locally scrollable on small screens', () => {
    expect(deliveredPageSource).toMatch(/\.mes-batch-result-details[\s\S]*overflow-x:\s*auto/)
    expect(productionPageSource).toMatch(/\.mes-production-result-details[\s\S]*overflow-x:\s*auto/)
    expect(deliveredPageSource).toMatch(/word-break:\s*break-word/)
    expect(productionPageSource).toMatch(/word-break:\s*break-word/)
  })
})
