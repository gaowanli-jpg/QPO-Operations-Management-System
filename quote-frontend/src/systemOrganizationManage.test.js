import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const sourcePath = (...segments) => resolve(currentDir, ...segments)
const readSource = (...segments) => readFileSync(sourcePath(...segments), 'utf8')

describe('QPO organization identity management guardrails', () => {
  test('freezes the organization identity management component target', () => {
    expect(existsSync(sourcePath('components', 'SystemOrgManage.vue'))).toBe(true)
  })

  test('keeps department and post as user identity fields', () => {
    const userManage = readSource('components', 'UserManage.vue')

    expect(userManage).toContain('deptId')
    expect(userManage).toContain('postId')
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
