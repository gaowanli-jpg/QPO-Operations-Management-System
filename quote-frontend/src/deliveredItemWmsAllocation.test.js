import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const pageSource = readFileSync(resolve(currentDir, 'components', 'DeliveredItemManage.vue'), 'utf8')
const planSource = readFileSync(resolve(currentDir, '..', '..', 'docs', 'QPO-MES-WMS对接', '04-WMS锁库分配与锁库零件模块开发文档.md'), 'utf8')

describe('DeliveredItemManage WMS allocation guardrails', () => {
  test('freezes module-owned WMS allocation permission and action labels', () => {
    expect(planSource).toContain('market:delivered-item:wms-allocate')
    expect(planSource).toContain('v-permission="\'market:delivered-item:wms-allocate\'"')
    expect(planSource).toContain('submitWmsAllocation')
    expect(planSource).toContain('提交WMS锁库')
    expect(planSource).toContain('重试WMS')
    expect(planSource).toContain('处理中')
    expect(planSource).toContain('查看锁库')
  })

  test('freezes WMS button display rules by material number and allocation status', () => {
    expect(pageSource).toContain('wmsAllocateStatus')
    expect(pageSource).toContain('statusText(row.wmsAllocateStatus)')
    expect(planSource).toContain('materialNo')
    expect(planSource).toContain('wmsAllocateStatus=PENDING')
    expect(planSource).toContain('wmsAllocateStatus=FAILED')
    expect(planSource).toContain('wmsAllocateStatus=PROCESSING')
    expect(planSource).toContain('wmsAllocateStatus=SUCCESS')
  })

  test('freezes confirmation, refresh, and Element Plus error-handling expectations', () => {
    expect(planSource).toContain('确认向 WMS 提交该零件锁库分配')
    expect(planSource).toContain('WMS 锁库任务已提交')
    expect(planSource).toContain('任务ID')
    expect(planSource).toContain('失败摘要')
  })
})
