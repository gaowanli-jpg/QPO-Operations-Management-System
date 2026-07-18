import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('quote status stage 1 frontend copy', () => {
  test('updates order picker default statuses and status tag colors', () => {
    const source = readSource('components', 'OrderPickerDialog.vue')

    expect(source).toContain("const DEFAULT_STATUS_OPTIONS = ['未核价', '已核价', '已报价', '部分已提单', '已生成提单']")
    expect(source).not.toContain("['待工程部评估'")
    expect(source).toContain("if (status === '未核价') return 'info'")
    expect(source).toContain("if (status === '已核价') return 'warning'")
  })

  test('updates engineering setup copy to the new unpriced wording', () => {
    const source = readSource('components', 'GlobalSetup.vue')

    expect(source).toContain('先选择未核价订单')
    expect(source).not.toContain('待核价订单')
    expect(source).not.toContain('待工程部评估')
  })

  test('prevents completing engineering quote while refresh task is running', () => {
    const source = readSource('components', 'ItemPricing.vue')

    expect(source).toMatch(/const canCompleteQuote = computed\(\(\) => \{[\s\S]*&& !refreshLoading\.value[\s\S]*\}\)/)
  })
})
