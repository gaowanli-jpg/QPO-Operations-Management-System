import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))
const appVue = readFileSync(resolve(currentDir, 'App.vue'), 'utf8')
const profilePlan = readFileSync(
  resolve(currentDir, '..', '..', 'docs', 'QPO权限用户体系', '07-个人入口与我的资料优化开发文档.md'),
  'utf8'
)

describe('personal profile entry target contract', () => {
  test('freezes the personal popover component boundary before implementation', () => {
    expect(profilePlan).toContain('quote-frontend/src/components/UserProfilePopover.vue')
    expect(profilePlan).toContain('部门、岗位、角色只读')
    expect(profilePlan).toContain('编辑联系方式')
    expect(profilePlan).toContain('USER_PROFILE_UPDATE')
  })

  test('keeps password-change blocking owned by App shell for now', () => {
    expect(appVue).toContain('watch(mustChangePassword')
    expect(appVue).toContain('openPasswordDialog(true)')
    expect(appVue).toContain('changePassword')
  })
})
