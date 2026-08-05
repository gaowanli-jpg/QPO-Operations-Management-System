import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(currentDir, '..', '..')
const lockedPageSource = readFileSync(resolve(currentDir, 'components', 'LockedItemManage.vue'), 'utf8')
const productionPageSource = readFileSync(resolve(currentDir, 'components', 'ProductionItemManage.vue'), 'utf8')
const apiSource = readFileSync(resolve(currentDir, 'services', 'deliveredItemApi.js'), 'utf8')
const interfaceDocPath = resolve(repoRoot, 'docs', 'QPO-MES-WMS对接', '09-提供给MES的待生产下发接口对接说明.md')
const permissionDoc = readFileSync(resolve(repoRoot, 'docs', 'QPO权限用户体系', '02-权限点清单与现有角色映射.md'), 'utf8')
const logDoc = readFileSync(resolve(repoRoot, 'docs', 'QPO日志模块', '03-日志运维说明.md'), 'utf8')

describe('Locked item MES production dispatch stage 6 closure', () => {
  test('keeps frontend dispatch interaction aligned with the documented batch endpoint', () => {
    expect(apiSource).toContain('/api/order-delivery/production-items/mes-production')
    expect(apiSource).toContain('data: { allocationIds }')
    expect(productionPageSource).toContain('下发MES生产')
    expect(lockedPageSource).not.toContain('下发MES生产')
    expect(productionPageSource).toContain('确认将选中的待产零件下发给 MES 生产流程')
    expect(productionPageSource).toContain('非待产行会自动跳过')
    expect(productionPageSource).toContain('MES生产下发结果')
    expect(productionPageSource).toContain('successCount')
    expect(productionPageSource).toContain('failedCount')
    expect(productionPageSource).toContain('skippedCount')
    expect(productionPageSource).toContain('v-permission="PRODUCTION_ITEM_MES_PRODUCTION_SUBMIT_PERMISSION"')
  })

  test('documents MES production dispatch interface and user permission boundary', () => {
    expect(existsSync(interfaceDocPath)).toBe(true)
    const interfaceDoc = readFileSync(interfaceDocPath, 'utf8')

    expect(interfaceDoc).toContain('POST {ERP_MES_BASE_URL}{ERP_MES_PRODUCTION_PATH}')
    expect(interfaceDoc).toContain('`allocationId`')
    expect(interfaceDoc).toContain('`productionQty`')
    expect(interfaceDoc).toContain('`ticketQty`')
    expect(interfaceDoc).toContain('`assemblyRelations`')
    expect(interfaceDoc).toContain('普通件或无父子关系时传空数组')
    expect(interfaceDoc).toContain('只下发 `PRODUCTION_REQUIRED`')
    expect(permissionDoc).toContain('market:production-item:mes-production-submit')
    expect(permissionDoc).not.toContain('common:mes-production')
    expect(logDoc).toContain('PRODUCTION_ITEM_MES_PRODUCTION_SUBMIT')
  })
})
