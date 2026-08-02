import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const deliveredPageSource = readFileSync(resolve(currentDir, 'components', 'DeliveredItemManage.vue'), 'utf8')
const lockedPageSource = readFileSync(resolve(currentDir, 'components', 'LockedItemManage.vue'), 'utf8')
const deliveredDoc = readFileSync(resolve(currentDir, '..', '..', 'docs', 'QPO-MES-WMS对接', '02-基础数据模型与已提零件模块开发文档.md'), 'utf8')
const wmsDoc = readFileSync(resolve(currentDir, '..', '..', 'docs', 'QPO-MES-WMS对接', '04-WMS锁库分配与锁库零件模块开发文档.md'), 'utf8')
const permissionDoc = readFileSync(resolve(currentDir, '..', '..', 'docs', 'QPO权限用户体系', '02-权限点清单与现有角色映射.md'), 'utf8')

describe('Delivered item active pool stage 4 closure', () => {
  test('keeps delivered item page as the unfinished MES WMS work pool', () => {
    expect(deliveredPageSource).toContain('仅展示尚未完成物料号获取和锁库流程的提单零件')
    expect(deliveredPageSource).toContain('完成锁库后的结果请在锁库零件模块查看')
    expect(deliveredPageSource).toContain('暂无未完成流程的已提零件')
    expect(deliveredPageSource).not.toContain('delivered-active-pool-tip')
    expect(deliveredPageSource).not.toContain("flowScope: 'ALL'")
    expect(deliveredPageSource).not.toContain('items.value = (response.records || []).filter')
  })

  test('keeps locked item page able to trace original delivered ticket rows', () => {
    expect(lockedPageSource).toContain('查看提单行')
    expect(lockedPageSource).toContain('原始提单行详情')
    expect(lockedPageSource).toContain('getDeliveredItem')
    expect(lockedPageSource).toContain('market:delivered-item:view')
    expect(lockedPageSource).toContain('selectedTicketDetail.mesSyncStatus')
    expect(lockedPageSource).toContain('selectedTicketDetail.wmsAllocateStatus')
    expect(lockedPageSource).toContain('selectedTicketDetail.processRoute')
  })

  test('documents active pool, result pool, and no-new-permission boundaries', () => {
    expect(deliveredDoc).toContain('已提零件默认是未完成池')
    expect(deliveredDoc).toContain('完成流程行不删除、不搬表、不复制到历史表')
    expect(wmsDoc).toContain('WMS 成功后结果进入锁库零件模块')
    expect(wmsDoc).toContain('`order_delivery_ticket_allocation` 是锁库结果池')
    expect(permissionDoc).toContain('已提零件未完成池与锁库结果池重构不新增权限')
  })
})
