import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('quote status stage 4 quote process frontend flow', () => {
  test('allows selecting priced quoted and released orders in quote process', () => {
    const source = readSource('components', 'QuoteProcess.vue')

    expect(source).toContain("const QUOTE_PROCESS_ALLOWED_STATUSES = ['已核价', '已报价', '部分已提单', '已生成提单']")
    expect(source).toContain(':allowed-statuses="QUOTE_PROCESS_ALLOWED_STATUSES"')
    expect(source).toContain('选择已核价 / 已报价 / 已提单 RFQ 订单')
    expect(source).toContain('请选择已核价、已报价或已提单订单')
  })

  test('locks edit save and export actions by quote status', () => {
    const source = readSource('components', 'QuoteProcess.vue')

    expect(source).toContain('const canEditQuoteLeadTime = computed(() => orderInfo.value.status === \'已核价\')')
    expect(source).toContain('const canSaveQuoteSheet = computed(() => canEditQuoteLeadTime.value)')
    expect(source).toContain("const QUOTE_PROCESS_EXPORTABLE_STATUSES = ['已报价', '部分已提单', '已生成提单']")
    expect(source).toContain('const canExportQuoteSheet = computed(() => QUOTE_PROCESS_EXPORTABLE_STATUSES.includes(orderInfo.value.status))')
    expect(source).toContain(':disabled="!canEditQuoteLeadTime || selectedQuoteProcessItemIds.size === 0 || quoteLeadTimeBatchPending || savingQuoteSheet || importSelecting || savingQuoteLeadTimeDrafts || leadTimeReplacementBusy"')
    expect(source).toContain(':disabled="!canEditQuoteLeadTime || selectedQuoteProcessItemIds.size === 0 || !manualQuoteDate || quoteLeadTimeBatchPending || savingQuoteSheet || importSelecting || savingQuoteLeadTimeDrafts || leadTimeReplacementBusy"')
    expect(source).toContain(':disabled="!canEditQuoteLeadTime"')
    expect(source).toContain(':disabled="!canExportQuoteSheet || isQuoteExporting(\'exportDetail\')"')
  })

  test('calls save quote API and refreshes status after market confirmation', () => {
    const source = readSource('components', 'QuoteProcess.vue')

    expect(source).toContain("saveQuoteSheet as saveQuoteSheetApi")
    expect(source).toContain('v-permission="QUOTE_PROCESS_SAVE_QUOTE_PERMISSION"')
    expect(source).toContain('@click="saveMarketQuoteSheet"')
    expect(source).toContain('const saveMarketQuoteSheet = async () =>')
    expect(source).toContain('const response = await saveQuoteSheetApi(selectedRfqId.value)')
    expect(source).toContain('orderInfo.value = response.orderInfo || orderInfo.value')
    expect(source).toContain("ElMessage.success('报价单已保存，可导出。')")
  })

  test('locks engineering pricing sheet export to priced orders', () => {
    const source = readSource('components', 'ItemPricing.vue')

    expect(source).toContain('生成核价单')
    expect(source).not.toContain('导出正式报价单')
    expect(source).toContain('v-permission="ENGINEER_PRICING_EXPORT_SHEET_PERMISSION"')
    expect(source).toContain('const ENGINEER_PRICING_EXPORT_SHEET_PERMISSION = \'engineer:pricing:export-sheet\'')
    expect(source).toContain('downloadPricingSheet')
    expect(source).toContain('const canExportPricingSheet = computed(() => !!rfqId.value')
    expect(source).toContain("quoteData.value?.orderInfo?.status === '已核价'")
    expect(source).toContain("ElMessage.warning('只有已核价订单可以生成核价单。')")
  })
})
