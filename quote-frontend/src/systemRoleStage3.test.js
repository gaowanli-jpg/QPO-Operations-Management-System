import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

const appVue = readSource('App.vue')
const routerSource = readSource('router', 'index.js')
const userManage = readSource('components', 'UserManage.vue')

describe('QPO permission stage 3 role management shell', () => {
  test('adds permission-protected role management route and sidebar entry', () => {
    expect(routerSource).toContain("const SystemRoleManage = () => import('../components/SystemRoleManage.vue')")
    expect(routerSource).toContain("{ path: '/roles', component: SystemRoleManage, meta: { permission: 'system:role:view' } }")
    expect(appVue).toContain("'/roles':")
    expect(appVue).toContain("permission: 'system:role:view'")
  })

  test('user management supports assigning multiple enterprise roles', () => {
    expect(userManage).toContain('v-model="form.roleCodes"')
    expect(userManage).toContain('multiple')
    expect(userManage).toContain('roleCodes')
    expect(userManage).toContain('roleTags')
    expect(userManage).not.toContain('v-model="form.role"')
  })

  test('role management page exposes permission and data-scope configuration entries', () => {
    const roleManage = readSource('components', 'SystemRoleManage.vue')

    expect(roleManage).toContain('erp-page role-manage-page')
    expect(roleManage).toContain('listRoles')
    expect(roleManage).toContain('assignRolePermissions')
    expect(roleManage).toContain('assignRoleDataScope')
    expect(roleManage).toContain('dataScope')
    expect(roleManage).toContain('permissionCodes')
  })
})
