import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

const roleManage = readSource('components', 'SystemRoleManage.vue')
const routerSource = readSource('router', 'index.js')
const appVue = readSource('App.vue')

describe('QPO role permission tree guardrails', () => {
  test('uses a tree authorization drawer instead of a flat checkbox list', () => {
    expect(roleManage).toContain('<el-tree')
    expect(roleManage).toContain('show-checkbox')
    expect(roleManage).toContain('permissionTreeRef')
    expect(roleManage).toContain('permissionTree')
    expect(roleManage).toContain('collectPermissionCodes')
    expect(roleManage).toContain('listPermissionTree')
    expect(roleManage).toContain('size="720px"')
    expect(roleManage).toContain('permissionTreeSearch')
    expect(roleManage).toContain('showSelectedOnly')
    expect(roleManage).toContain('expandPermissionTree')
    expect(roleManage).toContain('collapsePermissionTree')
    expect(roleManage).toContain('<el-tooltip')
    expect(roleManage).not.toContain('<el-checkbox-group')
  })

  test('uses sales orders as the independently authorized replacement for history orders', () => {
    expect(routerSource).toContain("{ path: '/sales-orders', component: SalesOrderManage, meta: { permission: 'market:sales-order:view' } }")
    expect(routerSource).toContain("{ path: '/history-orders', redirect: '/sales-orders' }")
    expect(appVue).toContain("'/sales-orders': { title: '销售订单', permission: 'market:sales-order:view'")
    expect(appVue).not.toContain("'/history-orders': { title: '历史订单'")
    expect(appVue).not.toContain("'/sales-orders': { title: '销售订单', permission: 'market:history-order:view'")
  })

  test('does not expose legacy common permissions in the role authorization UI', () => {
    expect(roleManage).not.toContain('common:customer:select')
    expect(roleManage).not.toContain('common:order:select')
    expect(roleManage).not.toContain('common:file:preview')
    expect(roleManage).not.toContain('common:task:view')
  })
})
