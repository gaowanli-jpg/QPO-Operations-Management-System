import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const readSource = path => readFileSync(new URL(path, import.meta.url), 'utf8')

describe('sales change notice page wiring', () => {
  it('registers the protected route and change menu entry', () => {
    const router = readSource('./router/index.js')
    const app = readSource('./App.vue')

    expect(router).toContain("path: '/sales-changes'")
    expect(router).toContain("permission: 'change:sales-change:view'")
    expect(app).toContain("paths: ['/linked-changes', '/sales-changes']")
    expect(app).toContain("'/sales-changes': { title: '销售变更', permission: 'change:sales-change:view'")
  })

  it('shows the notice pool operations and protects write controls', () => {
    const page = readSource('./components/SalesOrderChangeNoticeManage.vue')
    const api = readSource('./services/salesOrderChangeApi.js')

    expect(page).toContain("const SEND_PERMISSION = 'change:sales-change:send'")
    expect(page).toContain("const MODE_PERMISSION = 'change:sales-change:mode-update'")
    expect(page).toContain('批量发送')
    expect(page).toContain('自动发送')
    expect(page).toContain('失败')
    expect(page).toContain('结果未知')
    expect(page).toContain('dispatchSetting.autoEnabledAt')
    expect(page).toContain('sales-change-mode-time')
    expect(page).toContain('formatSnapshot(detail.snapshot)')
    expect(page).toContain('ACTIVE')
    expect(page).toContain('HIDDEN')
    expect(page).toContain('SENT')
    expect(page).toContain('批量隐藏')
    expect(page).toContain('批量恢复')
    expect(page).toContain("const HIDE_PERMISSION = 'change:sales-change:hide'")
    expect(page).toContain("const RESTORE_PERMISSION = 'change:sales-change:restore'")
    expect(page).toContain('恢复只重新显示，不会自动发送')
    expect(api).toContain("${BASE}/batch-hide")
    expect(api).toContain("${BASE}/batch-restore")
  })
})
