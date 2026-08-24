import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))
const appVue = readFileSync(resolve(currentDir, 'App.vue'), 'utf8')
const userProfilePopoverPath = resolve(currentDir, 'components', 'UserProfilePopover.vue')
const userProfilePopover = existsSync(userProfilePopoverPath)
  ? readFileSync(userProfilePopoverPath, 'utf8')
  : ''
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

  test('keeps manual password change without forced password blocking', () => {
    expect(appVue).not.toContain('watch(mustChangePassword')
    expect(appVue).not.toContain('openPasswordDialog(true)')
    expect(appVue).not.toContain('forcePasswordMode')
    expect(appVue).toContain('changePassword')
  })

  test('App shell delegates the compact account entry to UserProfilePopover', () => {
    expect(appVue).toContain("import UserProfilePopover from './components/UserProfilePopover.vue'")
    expect(appVue).toContain('<UserProfilePopover')
    expect(appVue).toContain('@change-password="openPasswordDialog"')
    expect(appVue).toContain('@logout="logout"')
    expect(appVue).not.toContain('<div class="user-card">')
    expect(appVue).not.toContain('<div class="header-actions">')
  })

  test('UserProfilePopover displays complete read-only identity fields', () => {
    expect(existsSync(userProfilePopoverPath)).toBe(true)
    expect(userProfilePopover).toContain('profile-trigger')
    expect(userProfilePopover).toContain('profile-panel')
    expect(userProfilePopover).toContain('roleNames')
    expect(userProfilePopover).toContain('deptName')
    expect(userProfilePopover).toContain('postName')
    expect(userProfilePopover).toContain('phone')
    expect(userProfilePopover).toContain('email')
    expect(userProfilePopover).toContain("emit('change-password')")
    expect(userProfilePopover).toContain("emit('logout')")
  })

  test('UserProfilePopover lets the current user edit contact fields and refresh cached state', () => {
    expect(userProfilePopover).toContain('profileDialogVisible')
    expect(userProfilePopover).toContain('编辑联系方式')
    expect(userProfilePopover).toContain('profileForm.phone')
    expect(userProfilePopover).toContain('profileForm.email')
    expect(userProfilePopover).toContain('authStore.updateMyProfile')
    expect(userProfilePopover).toContain('ElMessage.success')
    expect(userProfilePopover).toContain('未填写')
  })

  test('UserProfilePopover blocks duplicate contact saves while pending', () => {
    const saveBlock = userProfilePopover.slice(userProfilePopover.indexOf('const saveProfile = async () =>'))

    expect(userProfilePopover).toContain(':loading="profileSaving"')
    expect(userProfilePopover).toContain(':disabled="profileSaving"')
    expect(saveBlock).toContain('if (profileSaving.value) return')
  })

  test('UserProfilePopover exposes logout pending state to the logout button', () => {
    expect(userProfilePopover).toContain('defineProps')
    expect(userProfilePopover).toContain('logoutPending')
    expect(userProfilePopover).toContain(':loading="logoutPending"')
    expect(userProfilePopover).toContain(':disabled="logoutPending"')
  })
})
