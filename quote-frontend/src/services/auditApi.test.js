import { beforeEach, describe, expect, test, vi } from 'vitest'
import { request } from '../api'
import * as auditApi from './auditApi'

vi.mock('../api', () => ({
  request: vi.fn()
}))

beforeEach(() => {
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
})
