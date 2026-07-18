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
    expect(roleManage).not.toContain('<el-checkbox-group')
  })

  test('freezes history orders as an independently authorized menu module', () => {
    expect(routerSource).toContain("{ path: '/history-orders', component: HistoryOrderManage, meta: { permission: 'market:history-order:view' } }")
    expect(appVue).toContain("'/history-orders': { title: '历史订单', permission: 'market:history-order:view'")
    expect(appVue).not.toContain("'/history-orders': { title: '历史订单', permission: 'market:order:view'")
  })

  test('does not expose legacy common permissions in the role authorization UI', () => {
    expect(roleManage).not.toContain('common:customer:select')
    expect(roleManage).not.toContain('common:order:select')
    expect(roleManage).not.toContain('common:file:preview')
    expect(roleManage).not.toContain('common:task:view')
  })
})
