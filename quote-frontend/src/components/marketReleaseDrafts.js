export const RELEASE_ITEM_DRAFT_SOURCES = {
  internalTargetLeadTimeDraft: 'internalTargetLeadTime',
  versionDraft: 'version',
  descriptionDraft: 'description',
  surfaceTreatmentDraft: 'surfaceTreatment',
  remarkDraft: 'remark',
  salesOrderDateDraft: 'salesOrderDate',
  customerOrderNoDraft: 'customerOrderNo',
  customerProjectNoDraft: 'customerProjectNo',
  customerOrderTitleDraft: 'customerOrderTitle',
  orderTypeDraft: 'orderType',
  internalProjectNoDraft: 'internalProjectNo'
}

const RELEASE_DRAFT_CHANGE_GROUPS = {
  version: [['versionDraft', 'version']],
  internalTargetLeadTime: [['internalTargetLeadTimeDraft', 'internalTargetLeadTime']],
  fields: [
    ['descriptionDraft', 'description'],
    ['surfaceTreatmentDraft', 'surfaceTreatment'],
    ['remarkDraft', 'remark']
  ],
  orderFields: [
    ['salesOrderDateDraft', 'salesOrderDate'],
    ['customerOrderNoDraft', 'customerOrderNo'],
    ['customerProjectNoDraft', 'customerProjectNo'],
    ['customerOrderTitleDraft', 'customerOrderTitle'],
    ['orderTypeDraft', 'orderType'],
    ['internalProjectNoDraft', 'internalProjectNo']
  ]
}

const defaultReleaseDraftRowKey = (row) => row?.releaseItemKey || `${row?.sourceType || 'RFQ'}:${row?.sourceItemId ?? row?.id ?? ''}`

export const normalizeReleaseDraftValue = (value) => {
  if (value === null || value === undefined) return ''
  return String(value).trim()
}

export const hasReleaseDraftChange = (row = {}, draftField, sourceField) => (
  normalizeReleaseDraftValue(row[draftField]) !== normalizeReleaseDraftValue(row[sourceField])
)

export const releaseDraftChangeSummary = (row = {}) => {
  const summary = Object.fromEntries(
    Object.entries(RELEASE_DRAFT_CHANGE_GROUPS).map(([group, pairs]) => [
      group,
      pairs.some(([draftField, sourceField]) => hasReleaseDraftChange(row, draftField, sourceField))
    ])
  )
  summary.any = Object.values(summary).some(Boolean)
  return summary
}

export const collectReleaseRowsWithDraftChanges = (rows = [], rowKey = defaultReleaseDraftRowKey) => {
  const rowsByKey = new Map()
  ;(rows || []).forEach(row => {
    if (!row) return
    rowsByKey.set(rowKey(row), row)
  })
  return Array.from(rowsByKey.values()).filter(row => releaseDraftChangeSummary(row).any)
}

export const hydrateReleaseItemDrafts = (row = {}, selectedRow) => {
  const hydrated = { ...row }

  Object.entries(RELEASE_ITEM_DRAFT_SOURCES).forEach(([draftField, sourceField]) => {
    hydrated[draftField] = row[sourceField] || ''
  })

  if (!selectedRow) return hydrated

  Object.keys(RELEASE_ITEM_DRAFT_SOURCES).forEach((draftField) => {
    if (Object.prototype.hasOwnProperty.call(selectedRow, draftField)) {
      hydrated[draftField] = selectedRow[draftField]
    }
  })

  return hydrated
}
