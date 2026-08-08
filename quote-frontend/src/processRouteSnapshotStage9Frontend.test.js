import { describe, expect, test } from 'vitest'
import fs from 'fs'
import path from 'path'
import { formatProcessRouteSnapshot } from './utils/processRouteFormatter.js'

const root = process.cwd()
const readComponent = (name) => fs.readFileSync(path.join(root, 'src', 'components', name), 'utf8')

describe('process route snapshot stage 9 frontend display', () => {
  test('delivered, production, and locked item pages share the standard process route formatter', () => {
    for (const name of ['DeliveredItemManage.vue', 'ProductionItemManage.vue', 'LockedItemManage.vue']) {
      const source = readComponent(name)
      expect(source).toContain("import { formatProcessRouteSnapshot } from '../utils/processRouteFormatter'")
      expect(source).toContain('const formatProcessRoute = (route) => formatProcessRouteSnapshot(route)')
      expect(source).not.toContain("row.processName || row.name || ''")
    }
  })

  test('formatter renders standard process identity and stored snapshot price without old field gaps', () => {
    const text = formatProcessRouteSnapshot([
      {
        routeType: 'PROCESS',
        processCode: 'PACKAGING',
        categoryName: '包装',
        detailName: '精细包装',
        hourlyRate: 10,
        unitWorkHours: 1.5,
        lineCost: 15
      },
      {
        routeType: 'FEE',
        processName: '组装费',
        lineCost: 30
      }
    ])

    expect(text).toContain('PACKAGING / 包装 / 精细包装')
    expect(text).toContain('1.5h')
    expect(text).toContain('¥10')
    expect(text).toContain('¥15')
    expect(text).toContain('组装费')
    expect(text).toContain('¥30')
  })

  test('formatter remains safe for malformed or missing routes', () => {
    expect(formatProcessRouteSnapshot(null)).toBe('-')
    expect(formatProcessRouteSnapshot('not json')).toBe('-')
    expect(formatProcessRouteSnapshot([])).toBe('-')
  })
})
