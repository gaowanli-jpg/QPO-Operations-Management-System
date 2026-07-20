import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const sourcePath = (...segments) => resolve(currentDir, ...segments)
const readSource = (...segments) => readFileSync(sourcePath(...segments), 'utf8')

const orgManage = readSource('components', 'SystemOrgManage.vue')
const adminApi = readSource('services', 'adminApi.js')
const routerSource = readSource('router', 'index.js')
const appVue = readSource('App.vue')

describe('QPO organization identity management guardrails', () => {
  test('freezes the organization identity management component target', () => {
    expect(existsSync(sourcePath('components', 'SystemOrgManage.vue'))).toBe(true)
  })

  test('wires organization route and shell menu to its own permission', () => {
    expect(routerSource).toContain("const SystemOrgManage = () => import('../components/SystemOrgManage.vue')")
    expect(routerSource).toContain("{ path: '/organization', component: SystemOrgManage, meta: { permission: 'system:org:view' } }")
    expect(appVue).toContain("'/organization'")
    expect(appVue).toContain("title: '组织档案'")
    expect(appVue).toContain("permission: 'system:org:view'")
  })

  test('adds organization dictionary admin API helpers', () => {
    expect(adminApi).toContain('listDepartmentsTree')
    expect(adminApi).toContain("request('/api/system/departments/tree')")
    expect(adminApi).toContain('listDepartmentOptions')
    expect(adminApi).toContain("request('/api/system/departments/options')")
    expect(adminApi).toContain('saveDepartment')
    expect(adminApi).toContain('updateDepartmentStatus')
    expect(adminApi).toContain('deleteDepartment')
    expect(adminApi).toContain('listPosts')
    expect(adminApi).toContain("request('/api/system/posts')")
    expect(adminApi).toContain('listPostOptions')
    expect(adminApi).toContain("request('/api/system/posts/options')")
    expect(adminApi).toContain('savePost')
    expect(adminApi).toContain('updatePostStatus')
    expect(adminApi).toContain('deletePost')
  })

  test('implements department tree and post list management UI', () => {
    expect(orgManage).toContain('部门管理')
    expect(orgManage).toContain('岗位管理')
    expect(orgManage).toContain('row-key="id"')
    expect(orgManage).toContain(':tree-props')
    expect(orgManage).toContain('departmentTree')
    expect(orgManage).toContain('postRows')
    expect(orgManage).toContain('show-overflow-tooltip')
    expect(orgManage).toContain('v-permission="\'system:dept:manage\'"')
    expect(orgManage).toContain('v-permission="\'system:post:manage\'"')
    expect(orgManage).toContain('ElMessageBox.confirm')
    expect(orgManage).toContain('deleteDepartment')
    expect(orgManage).toContain('deletePost')
    expect(orgManage).toContain('updateDepartmentStatus')
    expect(orgManage).toContain('updatePostStatus')
  })

  test('keeps department and post as user identity fields', () => {
    const userManage = readSource('components', 'UserManage.vue')

    expect(userManage).toContain('deptId')
    expect(userManage).toContain('postId')
  })

  test('user management consumes enabled organization dictionaries as profile attributes', () => {
    const userManage = readSource('components', 'UserManage.vue')

    expect(userManage).toContain('prop="deptName"')
    expect(userManage).toContain('prop="postName"')
    expect(userManage).toContain('prop="phone"')
    expect(userManage).toContain('prop="email"')
    expect(userManage).toContain('show-overflow-tooltip')
    expect(userManage).toContain('listDepartmentOptions')
    expect(userManage).toContain('listPostOptions')
    expect(userManage).toContain('departmentOptions')
    expect(userManage).toContain('postOptions')
    expect(userManage).toContain('v-model="form.realName"')
    expect(userManage).toContain('v-model="form.phone"')
    expect(userManage).toContain('v-model="form.email"')
    expect(userManage).toContain('v-model="form.deptId"')
    expect(userManage).toContain('v-model="form.postId"')
    expect(userManage).toContain('loadOrganizationOptions')
  })

  test('keeps role data scope free of department range configuration', () => {
    const roleManage = readSource('components', 'SystemRoleManage.vue')

    expect(roleManage).not.toContain("value: 'DEPT'")
    expect(roleManage).not.toContain("value: 'DEPT_AND_CHILD'")
    expect(roleManage).not.toContain("value: 'CUSTOM'")
    expect(roleManage).not.toContain('listDataScopeDepartments')
    expect(roleManage).not.toContain('指定部门')
    expect(roleManage).not.toContain('本部门及子部门')
  })
})
