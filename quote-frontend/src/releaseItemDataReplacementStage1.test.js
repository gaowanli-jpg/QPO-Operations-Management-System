import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const marketReleaseSource = readFileSync(resolve(currentDir, 'components', 'MarketRelease.vue'), 'utf8')

describe('release item data replacement stage 1 characterization', () => {
  test('keeps accepted quantity as page draft until formal release submission', () => {
    const submitBlock = marketReleaseSource.slice(
      marketReleaseSource.indexOf('const submitTicket = async () =>'),
      marketReleaseSource.indexOf('const resolveDeliveryLeadTime')
    )

    expect(marketReleaseSource).toContain('const acceptedQtyMap = reactive({})')
    expect(marketReleaseSource).toContain('v-model="acceptedQtyMap[rowKey(scope.row)]"')
    expect(submitBlock).toContain('acceptedQty: acceptedQtyMap[rowKey(row)] || defaultAcceptedQtyFor(row)')
  })

  test('does not send accepted quantity through any single-row master save API', () => {
    const fieldSaveBlock = marketReleaseSource.slice(
      marketReleaseSource.indexOf('const saveReleaseItemFields = async (row) =>'),
      marketReleaseSource.indexOf('const saveReleaseItemOrderField = async (row, field) =>')
    )
    const orderFieldSaveBlock = marketReleaseSource.slice(
      marketReleaseSource.indexOf('const saveReleaseItemOrderField = async (row, field) =>'),
      marketReleaseSource.indexOf('const handleSelectionChange')
    )
    const leadTimeSaveBlock = marketReleaseSource.slice(
      marketReleaseSource.indexOf('const saveInternalTargetLeadTime = async (row) =>'),
      marketReleaseSource.indexOf('const openAppendSalesOrderPicker = () =>')
    )

    expect(fieldSaveBlock).not.toContain('acceptedQty')
    expect(orderFieldSaveBlock).not.toContain('acceptedQty')
    expect(leadTimeSaveBlock).not.toContain('acceptedQty')
  })
})
