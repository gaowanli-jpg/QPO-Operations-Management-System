import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))
const apiJs = readFileSync(resolve(currentDir, 'api.js'), 'utf8')
const mainJs = readFileSync(resolve(currentDir, 'main.js'), 'utf8')
const appVue = readFileSync(resolve(currentDir, 'App.vue'), 'utf8')
const authStore = readFileSync(resolve(currentDir, 'stores', 'authStore.js'), 'utf8')
const userManage = readFileSync(resolve(currentDir, 'components', 'UserManage.vue'), 'utf8')
const ssoDoc = readFileSync(
  resolve(currentDir, '..', '..', 'docs', 'QPO权限用户体系', '08-数据中心统一入口SSO登录开发文档.md'),
  'utf8'
)

describe('QPO password change policy', () => {
  test('frontend has no forced password change runtime state', () => {
    for (const source of [apiJs, mainJs, appVue, authStore, userManage]) {
      expect(source).not.toContain('forcePasswordChange')
      expect(source).not.toContain('mustChangePassword')
      expect(source).not.toContain('onPasswordChangeRequired')
      expect(source).not.toContain('强制改密')
      expect(source).not.toContain('需改密')
    }
    expect(apiJs).not.toContain('status === 428')
  })

  test('user management describes reset password as immediately usable', () => {
    expect(userManage).toContain("ElMessage.success('密码已重置')")
    expect(userManage).not.toContain('用户下次登录必须修改密码')
  })

  test('data center sso document no longer needs forced password bypass', () => {
    expect(ssoDoc).not.toContain('forcePasswordChange')
    expect(ssoDoc).not.toContain('强制改密')
    expect(ssoDoc).toContain('QPO 不再设置首次登录或重置密码后的改密阻断')
  })
})
