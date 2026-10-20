import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

const appVue = readSource('App.vue')
const routerSource = readSource('router', 'index.js')
const auditLogManage = readSource('components', 'AuditLogManage.vue')

describe('QPO audit log module shell', () => {
  test('adds admin-only operation logs under the system menu', () => {
    expect(appVue).toContain("'/audit-logs':")
    expect(appVue).toContain("permission: 'system:audit:view'")
    expect(appVue).toContain('visibleMenuGroups')

    const usersMenuIndex = appVue.indexOf("'/users':")
    const auditMenuIndex = appVue.indexOf("'/audit-logs':")
    expect(auditMenuIndex).toBeGreaterThan(usersMenuIndex)
  })

  test('lazy-loads audit logs route and protects it with audit permission', () => {
    expect(routerSource).toContain("const AuditLogManage = () => import('../components/AuditLogManage.vue')")
    expect(routerSource).toContain("{ path: '/audit-logs', component: AuditLogManage, meta: { permission: 'system:audit:view' } }")
    expect(routerSource).not.toContain("import AuditLogManage from '../components/AuditLogManage.vue'")
  })

  test('uses QPO shared surfaces, overflow tooltips and readable JSON detail areas', () => {
    expect(auditLogManage).toContain('erp-page audit-log-page')
    expect(auditLogManage).toContain('erp-page-header')
    expect(auditLogManage).toContain('erp-toolbar')
    expect(auditLogManage).toContain('erp-table-surface')
    expect(auditLogManage).toContain('erp-table-actions')
    expect(auditLogManage).toContain('show-overflow-tooltip')
    expect(auditLogManage).toContain('beforeData')
    expect(auditLogManage).toContain('afterData')
    expect(auditLogManage).toContain('audit-json-table')
    expect(auditLogManage).toContain('localizedAuditRows')
    expect(auditLogManage).toContain('copyJson')
    expect(auditLogManage).toContain('导出 CSV')
    expect(auditLogManage).toContain('日志保留策略')
    expect(auditLogManage).toContain('getAuditLogRetentionSetting')
    expect(auditLogManage).toContain('updateAuditLogRetentionSetting')
    expect(auditLogManage).toContain('runAuditLogRetentionCleanup')
    expect(auditLogManage).toContain('retention-form')
    expect(auditLogManage).toContain('exportAuditLogs')
    expect(auditLogManage).toContain('formatAuditJson')
    expect(auditLogManage).toContain('el-drawer')
  })

  test('exposes request id as a dedicated filter for log correlation', () => {
    expect(auditLogManage).toContain('placeholder="Request ID"')
    expect(auditLogManage).toContain("requestId: ''")
    expect(auditLogManage).toContain("'requestId'")
  })

  test('uses compact toolbar-style filters aligned with other QPO modules', () => {
    const headerBlock = auditLogManage.slice(
      auditLogManage.indexOf('<header class="erp-page-header audit-log-page-header">'),
      auditLogManage.indexOf('</header>')
    )
    const toolbarBlock = auditLogManage.slice(
      auditLogManage.indexOf('<section class="erp-toolbar audit-toolbar">'),
      auditLogManage.indexOf('</section>', auditLogManage.indexOf('<section class="erp-toolbar audit-toolbar">'))
    )

    expect(auditLogManage).toContain('class="erp-toolbar audit-toolbar"')
    expect(headerBlock).toContain('class="erp-page-actions"')
    expect(headerBlock).toContain('日志保留策略')
    expect(headerBlock).toContain('导出 CSV')
    expect(headerBlock).toContain('刷新')
    expect(auditLogManage).toContain('class="audit-toolbar-main"')
    expect(auditLogManage).toContain('class="erp-toolbar-group audit-primary-filters"')
    expect(auditLogManage).toContain('class="erp-toolbar-group audit-advanced-filters"')
    expect(auditLogManage).toContain('advancedFiltersVisible')
    expect(auditLogManage).toContain("{{ advancedFiltersVisible ? '收起筛选' : '高级筛选' }}")
    expect(toolbarBlock).not.toContain('日志保留策略')
    expect(toolbarBlock).not.toContain('导出 CSV')
    expect(toolbarBlock).not.toContain('刷新')
    expect(auditLogManage).not.toContain('class="erp-toolbar-group audit-page-actions"')
    expect(auditLogManage).not.toContain('class="audit-toolbar-divider"')
    expect(auditLogManage).not.toContain('audit-filter-grid')
    expect(auditLogManage).not.toContain('class="erp-toolbar-group audit-actions"')
  })
})
