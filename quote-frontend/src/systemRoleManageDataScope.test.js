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
    expect(roleManage).not.toContain('高级/兼容配置')
    expect(roleManage).not.toContain("value: 'DEPT'")
    expect(roleManage).not.toContain("value: 'DEPT_AND_CHILD'")
    expect(roleManage).not.toContain("value: 'CUSTOM'")
  })

  test('explains self scope as imported or created orders without owner wording', () => {
    expect(roleManage).toContain('仅查看当前用户导入/创建的订单。')
    expect(roleManage).not.toContain('负责人')
  })

  test('shows customer configuration only for customer data scope', () => {
    expect(roleManage).toContain('isCustomerDataScope')
    expect(roleManage).toContain('v-if="isCustomerDataScope"')
    expect(roleManage).toContain('指定客户数据范围只按订单客户名称匹配')
  })

  test('removes department data scope configuration from role management', () => {
    expect(roleManage).not.toContain('isDepartmentCompatibilityScope')
    expect(roleManage).not.toContain('isCustomDepartmentScope')
    expect(roleManage).not.toContain('dataScopeDeptTreeRef')
    expect(roleManage).not.toContain('listDataScopeDepartments')
    expect(roleManage).not.toContain('指定部门')
    expect(roleManage).not.toContain('本部门及子部门')
  })

  test('normalizes hidden data scope fields before saving', () => {
    expect(roleManage).toContain('buildDataScopePayload(dataScopeForm)')
    expect(roleManage).toContain("case 'CUSTOMER'")
    expect(roleManage).toContain('deptIds: []')
    expect(roleManage).toContain('customerNames: []')
    expect(roleManage).toContain('default:')
  })
})
