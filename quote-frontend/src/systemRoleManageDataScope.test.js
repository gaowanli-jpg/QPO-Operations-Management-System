import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

const roleManage = readSource('components', 'SystemRoleManage.vue')

describe('QPO role management data scope guardrails', () => {
  test('presents the recommended QPO data scope vocabulary', () => {
    expect(roleManage).toContain('全部数据')
    expect(roleManage).toContain('指定客户')
    expect(roleManage).toContain('仅本人')
    expect(roleManage).toContain('无数据')
    expect(roleManage).toContain('高级/兼容配置')
  })

  test('explains self scope as imported or created orders without exposing owner assignment', () => {
    expect(roleManage).toContain('导入/创建')
    expect(roleManage).not.toContain('订单负责人分派')
  })

  test('shows customer configuration only for customer data scope', () => {
    expect(roleManage).toContain('isCustomerDataScope')
    expect(roleManage).toContain('v-if="isCustomerDataScope"')
    expect(roleManage).toContain('指定客户数据范围只按订单客户名称匹配')
  })

  test('marks department data scopes as advanced compatibility settings', () => {
    expect(roleManage).toContain('isDepartmentCompatibilityScope')
    expect(roleManage).toContain('isCustomDepartmentScope')
    expect(roleManage).toContain('v-if="isCustomDepartmentScope"')
    expect(roleManage).toContain('不推荐用于 QPO 订单主流程')
  })
})
