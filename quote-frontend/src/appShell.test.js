import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))
const appVue = readFileSync(resolve(currentDir, 'App.vue'), 'utf8')
const loginView = readFileSync(resolve(currentDir, 'components', 'LoginView.vue'), 'utf8')
const indexHtml = readFileSync(resolve(currentDir, '..', 'index.html'), 'utf8')

describe('QPO app shell polish', () => {
  test('uses QPO as the visible product brand', () => {
    expect(appVue).toContain('<span class="logo-icon">QPO</span>')
    expect(loginView).toContain('<div class="brand-mark">QPO</div>')
    expect(indexHtml).toContain('<title>QPO</title>')
    expect(appVue).not.toContain('<span class="logo-icon">ERP</span>')
    expect(loginView).not.toContain('<div class="brand-mark">ERP</div>')
  })

  test('uses short grouped sidebar labels for the Stage 2 shell', () => {
    expect(appVue).toContain('menu-section')
    expect(appVue).toContain('市场')
    expect(appVue).toContain('工程')
    expect(appVue).toContain('订单')
    expect(appVue).toContain('系统')
    expect(appVue).toContain('新单导入')
    expect(appVue).toContain('报价处理')
    expect(appVue).toContain('工程设置')
    expect(appVue).toContain('材料工艺')
    expect(appVue).not.toContain('市场部订单处理')
    expect(appVue).not.toContain('系统管理：')
  })

  test('maps related workbench routes to an obvious active navigation item', () => {
    expect(appVue).toContain("route.path === '/pricing'")
    expect(appVue).toContain("return '/engineering'")
  })

  test('uses tokenized shell classes instead of hard-coded menu colors', () => {
    expect(appVue).toContain('shell-title')
    expect(appVue).toContain('shell-subtitle')
    expect(appVue).toContain('user-card')
    expect(appVue).toContain('role-pill')
    expect(appVue).not.toContain('background-color="#172331"')
    expect(appVue).not.toContain('active-text-color="#ffffff"')
  })

  test('keeps the header shell icon decorative rather than an inert action', () => {
    expect(appVue).toContain('<span class="shell-icon-button" aria-hidden="true">')
    expect(appVue).not.toContain('<el-button class="shell-icon-button"')
  })
})
