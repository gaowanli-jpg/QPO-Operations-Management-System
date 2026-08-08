import { describe, expect, test } from 'vitest'
import fs from 'fs'
import path from 'path'

const root = process.cwd()
const readComponent = (name) => fs.readFileSync(path.join(root, 'src', 'components', name), 'utf8')

describe('process route snapshot stage 8 frontend display', () => {
  test('SalesOrderManage formats process snapshots with process identity fields', () => {
    const source = readComponent('SalesOrderManage.vue')

    expect(source).toContain('const formatProcessRouteRow = (row = {}) =>')
    expect(source).toContain('row.processCode')
    expect(source).toContain('row.categoryName')
    expect(source).toContain('row.detailName')
    expect(source).toContain('row.hourlyRate')
    expect(source).toContain('row.lineCost')
    expect(source).not.toContain('`${item.processName || \'\'}${item.inputVal ? `(${item.inputVal})` : \'\'}`')
  })

  test('SalesMatch preview includes route snapshot summary instead of only availability labels', () => {
    const source = readComponent('SalesMatch.vue')

    expect(source).toContain('label="工艺路线"')
    expect(source).toContain('formatRouteSummary(row.currentProcessRouteSummary || row.sourceProcessRouteSummary)')
    expect(source).toContain('sourceProcessRouteSummary')
    expect(source).toContain('currentProcessRouteSummary')
  })
})
