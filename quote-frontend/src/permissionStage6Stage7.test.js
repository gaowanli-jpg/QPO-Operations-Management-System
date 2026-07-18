import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('QPO permission stage 6 and 7 data scope cleanup', () => {
  test('role data-scope drawer uses department tree and customer selector', () => {
    const roleManage = readSource('components', 'SystemRoleManage.vue')

    expect(roleManage).toContain('listDataScopeDepartments')
    expect(roleManage).toContain('listDataScopeCustomers')
    expect(roleManage).toContain('<el-tree')
    expect(roleManage).toContain('show-checkbox')
    expect(roleManage).toContain('customerOptions')
    expect(roleManage).not.toContain('deptIdsText')
    expect(roleManage).not.toContain('customerNamesText')
  })

  test('admin api exposes data-scope option endpoints', () => {
    const adminApi = readSource('services', 'adminApi.js')

    expect(adminApi).toContain("listDataScopeDepartments")
    expect(adminApi).toContain("request('/api/system/data-scope/departments')")
    expect(adminApi).toContain("listDataScopeCustomers")
    expect(adminApi).toContain("request('/api/system/data-scope/customers')")
  })

  test('frontend business routes do not depend on legacy role metadata', () => {
    const routerSource = readSource('router', 'index.js')
    const appSource = readSource('App.vue')

    expect(routerSource).not.toContain('meta.roles')
    expect(routerSource).not.toContain('meta: { roles:')
    expect(appSource).not.toContain('authStore.hasRole')
  })
})
