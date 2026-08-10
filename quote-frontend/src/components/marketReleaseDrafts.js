const RELEASE_ITEM_DRAFT_SOURCES = {
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
