import { describe, expect, test } from 'vitest'
import { auditDataRows, FIELD_LABELS, formatAuditJson } from './auditLogFormat'

describe('auditLogFormat', () => {
  test('formats valid JSON and keeps invalid JSON readable', () => {
    expect(formatAuditJson('{"partNo":"P-001","qty":2}')).toContain('\n  "partNo": "P-001"')
    expect(formatAuditJson('{bad json')).toBe('{bad json')
    expect(formatAuditJson('')).toBe('-')
    expect(formatAuditJson(null)).toBe('-')
  })

  test('localizes common audit fields into table rows', () => {
    const rows = auditDataRows('{"partNo":"P-001","qty":2,"materialName":"6061","leadTime":"7天"}')

    expect(FIELD_LABELS.partNo).toBe('图号')
    expect(FIELD_LABELS.qty).toBe('数量')
    expect(rows).toEqual([
      { key: 'partNo', label: '图号', value: 'P-001' },
      { key: 'qty', label: '数量', value: '2' },
      { key: 'materialName', label: '材料名称', value: '6061' },
      { key: 'leadTime', label: '交货周期', value: '7天' }
    ])
  })

  test('renders empty and array values without throwing', () => {
    expect(auditDataRows(null)).toEqual([])
    expect(auditDataRows('[{"partNo":"P-001"}]')[0]).toMatchObject({
      key: '0',
      label: '第 1 项'
    })
  })
})
