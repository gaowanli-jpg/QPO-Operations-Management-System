import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const pageSource = readFileSync(resolve(currentDir, 'components', 'SalesOrderManage.vue'), 'utf8')

describe('Sales order WMS task result feedback', () => {
  test('keeps the submitted async task and per-ticket result rows in the order detail', () => {
    expect(pageSource).toContain('const wmsAllocationTask = ref(null)')
    expect(pageSource).toContain('v-if="wmsAllocationTask"')
    expect(pageSource).toContain('wmsAllocationRows')
    expect(pageSource).toContain('prop="ticketId" label="提单行"')
    expect(pageSource).toContain('prop="message" label="说明"')
  })

  test('reports submission and shows running, unknown, and local-apply recovery states', () => {
    expect(pageSource).toContain('库存信息获取任务已提交')
    expect(pageSource).toContain('RESULT_UNKNOWN: \'结果待核对\'')
    expect(pageSource).toContain('APPLY_FAILED: \'本地应用待修复\'')
    expect(pageSource).toContain('PENDING: \'排队中\'')
    expect(pageSource).toContain('RUNNING: \'处理中\'')
  })

  test('refreshes the existing task without resubmitting the allocation', () => {
    const refreshStart = pageSource.indexOf('const refreshWmsAllocationTask = async')
    const refreshSource = pageSource.slice(refreshStart, pageSource.indexOf('const fetchDetailProcessSheets', refreshStart))
    expect(refreshSource).toContain('getTask(taskId)')
    expect(refreshSource).toContain('console.warn')
    expect(refreshSource).not.toContain('submitSalesOrderWmsAllocationBatch')
    expect(pageSource).toContain('refreshWmsAllocationTask(true)')
  })
})
