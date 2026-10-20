import { describe, expect, test } from 'vitest'
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(currentDir, '..', '..')
const readSource = (...segments) => readFileSync(resolve(repoRoot, ...segments), 'utf8')

const runtimeExtensions = new Set(['.vue', '.js'])

function collectRuntimeSources(root) {
  const files = []
  for (const entry of readdirSync(root)) {
    const fullPath = resolve(root, entry)
    const stat = statSync(fullPath)
    if (stat.isDirectory()) {
      files.push(...collectRuntimeSources(fullPath))
      continue
    }
    if (entry.endsWith('.test.js')) continue
    if ([...runtimeExtensions].some((extension) => entry.endsWith(extension))) {
      files.push(fullPath)
    }
  }
  return files
}

describe('Stage 38 phase 15 documentation and frontend closure', () => {
  test('phase 15 docs close permissions, logs, and MES/WMS process route scope', () => {
    const plan = readSource('docs', 'QPO报价状态流转', '38-数据库基线重建与两级工艺主数据全链路重构开发文档.md')
    const permissionDoc = readSource('docs', 'QPO权限用户体系', '02-权限点清单与现有角色映射.md')
    const logDoc = readSource('docs', 'QPO日志模块', '03-日志运维说明.md')
    const mesProductionDoc = readSource('docs', 'QPO-MES-WMS对接', '09-提供给MES的待生产下发接口对接说明.md')

    expect(plan).toContain('文档状态：阶段 15 已完成')
    expect(permissionDoc).toContain('两级工艺主数据阶段 15 收口')
    expect(permissionDoc).toContain('工艺大类和具体工艺查询继续复用 `engineer:dict:view`')
    expect(permissionDoc).toContain('维护动作继续复用 `engineer:dict:process-manage`')
    expect(permissionDoc).toContain('具体工艺小时价格维护继续复用 `engineer:dict:process-manage`，不新增权限点。')
    expect(logDoc).toContain('普通查询、工艺选择器加载、核价页读取和页面刷新不写操作日志')
    expect(logDoc).toContain('具体工艺价格保存走 `DICT_PROCESS_DETAIL_UPDATE`，读取和实时预估不记高频日志。')
    expect(logDoc).toContain('不记录完整 `processRoute` JSON')
    expect(mesProductionDoc).toContain('processCode / categoryName / detailName / unitWorkHours / sortNo')
    expect(mesProductionDoc).toContain('不传 QPO 内部工艺成本字段')
  })

  test('frontend runtime no longer calls legacy process dictionary endpoints or tiered price fields', () => {
    const sources = collectRuntimeSources(currentDir)
      .map((file) => readFileSync(file, 'utf8'))
      .join('\n')

    expect(sources).not.toContain('/api/dict/processes')
    expect(sources).not.toContain('price_1_5')
    expect(sources).not.toContain('price_3000_plus')
    expect(sources).toContain('/api/dict/process-categories')
    expect(sources).toContain('/api/dict/process-details')
  })

  test('process route formatter keeps structured process identity visible', () => {
    const formatter = readSource('quote-frontend', 'src', 'utils', 'processRouteFormatter.js')

    expect(formatter).toContain('row.processCode')
    expect(formatter).toContain('row.categoryName')
    expect(formatter).toContain('detailName')
    expect(formatter).toContain('unitWorkHours')
    expect(existsSync(resolve(currentDir, 'components', 'MaterialProcessManage.vue'))).toBe(true)
  })

  test('pricing markup fee docs and preview close the categorized multiplicative formula', () => {
    const permissionDoc = readSource('docs', 'QPO权限用户体系', '02-权限点清单与现有角色映射.md')
    const logDoc = readSource('docs', 'QPO日志模块', '03-日志运维说明.md')
    const realtimeDoc = readSource('docs', 'QPO报价状态流转', '45-零件核价实时价格预估开发文档.md')
    const itemPricing = readSource('quote-frontend', 'src', 'components', 'ItemPricing.vue')

    expect(permissionDoc).toContain('统一百分比费用阶段 6 收口')
    expect(permissionDoc).toContain('统一百分比费用维护继续复用 `engineer:dict:view` 和 `engineer:dict:fee-manage`，不新增权限点。')
    expect(logDoc).toContain('统一百分比费用 CRUD 使用 `DICT_PRICING_MARKUP_FEE_CREATE`、`DICT_PRICING_MARKUP_FEE_UPDATE`、`DICT_PRICING_MARKUP_FEE_DELETE`')
    expect(logDoc).toContain('读取和实时预估不记高频日志')
    expect(realtimeDoc).toContain('未税单价 = 基础成本 * 客户权重因子 * 基础百分比费用因子')
    expect(realtimeDoc).toContain('含税单价 = 基础成本 * 客户权重因子 * 基础百分比费用因子 * 税率因子')
    expect(realtimeDoc).toContain('不再通过费用名称包含 `税 / tax / vat` 判断税率')
    expect(itemPricing).toContain('TAX_INCLUDED_PRICING_FEE_SEQUENCE = [CUSTOMER_WEIGHT, BASIC_PERCENT, TAX]')
    expect(itemPricing).not.toContain('isTaxFeeName')
  })
})
