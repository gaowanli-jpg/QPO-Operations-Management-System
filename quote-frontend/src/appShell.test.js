import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))
const appVue = readFileSync(resolve(currentDir, 'App.vue'), 'utf8')
const loginView = readFileSync(resolve(currentDir, 'components', 'LoginView.vue'), 'utf8')
const quoteTemplateManage = readFileSync(resolve(currentDir, 'components', 'QuoteTemplateManage.vue'), 'utf8')
const indexHtml = readFileSync(resolve(currentDir, '..', 'index.html'), 'utf8')

describe('QPO app shell polish', () => {
  test('uses QPO as the visible product brand', () => {
    expect(appVue).toContain('<span class="logo-icon">QPO</span>')
    expect(loginView).toContain('<div class="brand-mark">QPO</div>')
    expect(indexHtml).toContain('<title>QPO</title>')
    expect(appVue).not.toContain('<span class="logo-icon">ERP</span>')
    expect(loginView).not.toContain('<div class="brand-mark">ERP</div>')
  })

  test('login submit cannot be triggered repeatedly while pending', () => {
    const loginBlock = loginView.slice(loginView.indexOf('const handleLogin = async () =>'))

    expect(loginView).toContain(':loading="loading"')
    expect(loginView).toContain(':disabled="loading"')
    expect(loginBlock).toContain('if (loading.value) return')
  })

  test('shell password and logout writes cannot be triggered repeatedly while pending', () => {
    const changePasswordBlock = appVue.slice(appVue.indexOf('const changePassword = async () =>'), appVue.indexOf('const logout = async () =>'))
    const logoutBlock = appVue.slice(appVue.indexOf('const logout = async () =>'))

    expect(appVue).toContain(':loading="passwordSaving"')
    expect(appVue).toContain(':disabled="passwordSaving"')
    expect(changePasswordBlock).toContain('if (passwordSaving.value) return')
    expect(appVue).toContain(':logout-pending="logoutPending"')
    expect(appVue).toContain('const logoutPending = ref(false)')
    expect(logoutBlock).toContain('if (logoutPending.value) return')
  })

  test('uses a compact sidebar width for dense workflow pages', () => {
    expect(appVue).toContain('const sidebarWidth = computed(() => isSidebarCollapsed.value ? \'72px\' : \'200px\')')
    expect(appVue).toContain(':width="sidebarWidth"')
    expect(appVue).toContain(':collapse="isSidebarCollapsed"')
    expect(appVue).not.toContain('width="208px"')
    expect(appVue).not.toContain('width="216px"')
    expect(appVue).not.toContain('width="240px"')
  })

  test('keeps the shell usable on narrow dashboard review viewports', () => {
    expect(appVue).toContain('@media (max-width: 760px)')
    expect(appVue).toContain('width: 72px !important;')
    expect(appVue).toContain('.sidebar .el-menu-item span')
    expect(appVue).toContain('display: none;')
    expect(appVue).toContain('padding: var(--erp-space-2)')
  })

  test('uses short grouped sidebar labels for the Stage 2 shell', () => {
    expect(appVue).toContain('menu-section')
    expect(appVue).toContain('市场')
    expect(appVue).toContain('工程')
    expect(appVue).toContain('订单')
    expect(appVue).toContain('系统')
    expect(appVue).toContain('新单导入')
    expect(appVue).toContain('报价处理')
    expect(appVue).toContain('零件核价')
    expect(appVue).toContain('材料工艺')
    expect(appVue).not.toContain('市场部订单处理')
    expect(appVue).not.toContain('系统管理：')
  })

  test('keeps sidebar item labels local and four characters long', () => {
    expect(appVue).not.toContain('title: backendMenu?.title || meta.title')
    expect(appVue).toContain('title: meta.title')

    const menuMetaBlock = appVue.slice(appVue.indexOf('const menuMetaByPath'), appVue.indexOf('const flattenMenus'))
    const titles = [...menuMetaBlock.matchAll(/title: '([^']+)'/g)].map((match) => match[1])

    expect(titles.length).toBeGreaterThan(0)
    for (const title of titles) {
      expect([...title].length).toBe(4)
    }
  })

  test('requires each sidebar entry to be authorized by its own backend menu path', () => {
    expect(appVue).not.toContain('const backendVisiblePermissions')
    expect(appVue).not.toContain('backendVisiblePermissions.value.has(permission)')
    expect(appVue).toContain('return backendPaths.has(path)')
    expect(appVue).toContain("'/orders': { title: '询价订单', permission: 'market:order:view'")
    expect(appVue).not.toContain("'/orders': { title: '订单管理', permission: 'market:order:view'")
    expect(appVue).toContain("'/sales-orders': { title: '销售订单', permission: 'market:sales-order:view'")
    expect(appVue).not.toContain("'/history-orders': { title: '历史订单'")
    expect(appVue).toContain("'/delivered-items': { title: '已提零件', permission: 'market:delivered-item:view'")
    expect(appVue).toContain("'/locked-items': { title: '锁库零件', permission: 'market:locked-item:view'")
    expect(appVue).not.toContain("'/delivered-items': { title: '已提零件', permission: 'market:order:view'")
    expect(appVue).not.toContain("'/locked-items': { title: '锁库零件', permission: 'market:order:view'")
    expect(appVue).not.toContain("'/locked-items': { title: '锁库零件', permission: 'market:delivered-item:view'")
    expect(appVue).not.toContain("permission: 'market:order:view', icon: Files")
  })

  test('places delivered and locked items in the order sidebar group with four-character titles', () => {
    expect(appVue).toContain("{ key: 'orders', title: '订单', paths: ['/orders', '/sales-orders', '/delivered-items', '/locked-items'] }")
    expect(appVue).toContain("'/orders': { title: '询价订单', permission: 'market:order:view'")
    expect(appVue).toContain("'/sales-orders': { title: '销售订单', permission: 'market:sales-order:view'")
    expect(appVue).toContain("'/delivered-items': { title: '已提零件', permission: 'market:delivered-item:view'")
    expect(appVue).toContain("'/locked-items': { title: '锁库零件', permission: 'market:locked-item:view'")
  })

  test('places quote template under market release instead of the system group', () => {
    const releaseMenuIndex = appVue.indexOf("'/release'")
    const templateMenuIndex = appVue.indexOf("'/quote-template'")

    expect(releaseMenuIndex).toBeGreaterThan(-1)
    expect(templateMenuIndex).toBeGreaterThan(releaseMenuIndex)
    expect(templateMenuIndex).toBeLessThan(appVue.indexOf("{ key: 'engineer'"))
    expect(templateMenuIndex).toBeLessThan(appVue.indexOf("{ key: 'system'"))
    expect(quoteTemplateManage).toContain('<h1 class="erp-page-title">报价模板</h1>')
    expect(quoteTemplateManage).not.toContain('系统管理：报价单模板')
  })

  test('maps related workbench routes to an obvious active navigation item', () => {
    expect(appVue).toContain("route.path === '/pricing'")
    expect(appVue).toContain("return '/engineering'")
  })

  test('uses tokenized shell classes instead of hard-coded menu colors', () => {
    expect(appVue).toContain('shell-title')
    expect(appVue).toContain('shell-subtitle')
    expect(appVue).toContain('UserProfilePopover')
    expect(appVue).not.toContain('<div class="user-card">')
    expect(appVue).not.toContain('<div class="header-actions">')
    expect(appVue).not.toContain('background-color="#172331"')
    expect(appVue).not.toContain('active-text-color="#ffffff"')
  })

  test('turns the header shell icon into an accessible sidebar toggle', () => {
    expect(appVue).toContain('<button')
    expect(appVue).toContain('@click="toggleSidebar"')
    expect(appVue).toContain(':aria-label="isSidebarCollapsed ? \'展开侧边栏\' : \'收起侧边栏\'"')
    expect(appVue).toContain('<component :is="isSidebarCollapsed ? Expand : Fold" />')
    expect(appVue).not.toContain('<span class="shell-icon-button" aria-hidden="true">')
  })

  test('keeps forced password dialog global while personal entry is redesigned', () => {
    expect(appVue).toContain('const passwordVisible = ref(false)')
    expect(appVue).toContain('const forcePasswordMode = ref(false)')
    expect(appVue).toContain('openPasswordDialog(true)')
    expect(appVue).toContain(':show-close="!forcePasswordMode"')
    expect(appVue).toContain('当前账号使用初始密码或管理员重置密码')
  })
})
