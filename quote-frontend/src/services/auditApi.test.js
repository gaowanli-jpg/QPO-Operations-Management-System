import { beforeEach, describe, expect, test, vi } from 'vitest'
import { downloadFile, request } from '../api'
import * as auditApi from './auditApi'

vi.mock('../api', () => ({
  downloadFile: vi.fn(),
  request: vi.fn()
}))

beforeEach(() => {
  downloadFile.mockReset()
  request.mockReset()
})

describe('auditApi', () => {
  test('listAuditLogs queries admin audit logs with filters', async () => {
    const params = { username: 'admin', module: 'DICT', result: 'FAILED' }

    await auditApi.listAuditLogs(params)

    expect(request).toHaveBeenCalledWith('/api/admin/audit-logs', { params })
  })

  test('getAuditLog loads a single audit log detail', async () => {
    await auditApi.getAuditLog(7)

    expect(request).toHaveBeenCalledWith('/api/admin/audit-logs/7')
  })

  test('getAuditLogOptions loads filter options', async () => {
    await auditApi.getAuditLogOptions()

    expect(request).toHaveBeenCalledWith('/api/admin/audit-logs/options')
  })

  test('exportAuditLogs downloads CSV with encoded filters', async () => {
    const params = { username: '张三', module: 'ENGINEER', keyword: 'P-001', empty: '' }

    await auditApi.exportAuditLogs(params)

    expect(downloadFile).toHaveBeenCalledWith(
      '/api/admin/audit-logs/export?username=%E5%BC%A0%E4%B8%89&module=ENGINEER&keyword=P-001',
      'audit-logs.csv'
    )
  })

  test('loads, updates and runs audit log retention policy', async () => {
    const payload = { enabled: true, retentionDays: 180, archiveBeforeCleanup: true, cleanupTime: '02:00' }

    await auditApi.getAuditLogRetentionSetting()
    await auditApi.updateAuditLogRetentionSetting(payload)
    await auditApi.runAuditLogRetentionCleanup()

    expect(request).toHaveBeenNthCalledWith(1, '/api/admin/audit-logs/retention')
    expect(request).toHaveBeenNthCalledWith(2, '/api/admin/audit-logs/retention', {
      method: 'PUT',
      data: payload
    })
    expect(request).toHaveBeenNthCalledWith(3, '/api/admin/audit-logs/retention/run', {
      method: 'POST'
    })
  })
})
