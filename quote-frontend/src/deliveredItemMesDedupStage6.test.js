import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(currentDir, '..', '..')
const pageSource = readFileSync(resolve(currentDir, 'components', 'DeliveredItemManage.vue'), 'utf8')
const permissionDoc = readFileSync(resolve(repoRoot, 'docs', 'QPO权限用户体系', '02-权限点清单与现有角色映射.md'), 'utf8')
const mesInterfaceDoc = readFileSync(resolve(repoRoot, 'docs', 'QPO-MES-WMS对接', '06-提供给MES的物料号接口对接说明.md'), 'utf8')

describe('DeliveredItemManage MES dedup stage 6 regression', () => {
  test('keeps MES dedup under delivered-item MES sync permission without new frontend permission', () => {
    expect(pageSource).toContain("const DELIVERED_ITEM_MES_SYNC_PERMISSION = 'market:delivered-item:mes-sync'")
    expect(pageSource).toContain('v-permission="DELIVERED_ITEM_MES_SYNC_PERMISSION"')
    expect(pageSource).toContain("item.mesRequestRole !== 'FOLLOWER'")
    expect(pageSource).toContain('已关联同图号版本 MES 主请求，等待主请求结果')
    expect(pageSource).not.toContain('market:delivered-item:mes-dedup')
    expect(pageSource).not.toContain('market:delivered-item:dedup')
  })

  test('documents that MES only calls back the master request and users keep the existing permission', () => {
    expect(permissionDoc).toContain('MES 同图号版本去重不新增用户权限')
    expect(permissionDoc).toContain('`market:delivered-item:mes-sync`')
    expect(mesInterfaceDoc).toContain('MES 仍只回调主 `mesRequestId`')
    expect(mesInterfaceDoc).toContain('QPO fan-out')
    expect(mesInterfaceDoc).toContain('`dedupFollowerCount`')
    expect(mesInterfaceDoc).toContain('`submittedToMesCount`')
  })
})
