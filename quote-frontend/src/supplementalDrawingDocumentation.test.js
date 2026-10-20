import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(currentDir, '..', '..')
const readSource = (...segments) => readFileSync(resolve(repoRoot, ...segments), 'utf8')

describe('supplemental drawing governance documentation', () => {
  test('documents permissions and safe logging for supplemental pricing quote and append release', () => {
    const permissionDoc = readSource('docs', 'QPO权限用户体系', '02-权限点清单与现有角色映射.md')
    const logDoc = readSource('docs', 'QPO日志模块', '03-日志运维说明.md')

    expect(permissionDoc).toContain('补图待核价入口复用 `engineer:pricing:view`、`engineer:pricing:save-item` 和 `engineer:pricing:complete-order`，不新增权限点。')
    expect(permissionDoc).toContain('补充报价待确认入口复用 `market:quote-process:view`、`market:quote-process:order-select`、`market:quote-process:update-lead-time` 和 `market:quote-process:save-quote`，不新增权限点。')
    expect(permissionDoc).toContain('补图零件追加议价提单复用 `market:release:create`，不新增权限点。')
    expect(logDoc).toContain('补图补核价追加提单阶段 7 收口')
    expect(logDoc).toContain('`QUOTE_PROCESS_SAVE_QUOTE`')
    expect(logDoc).toContain('摘要只允许记录 `statusBefore/statusAfter`、`restoredUnpricedCount`、`supplemental=true`、`supplementalItemCount`、`pricedSupplementalItemCount`、`orderStatusBefore/orderStatusAfter`、`ticketCount` 等治理字段')
    expect(logDoc).toContain('不得记录完整图纸、完整报价快照、完整工艺路线 JSON、token、Authorization、Cookie 或外部系统密钥')
  })
})
