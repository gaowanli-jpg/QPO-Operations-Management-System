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
})
