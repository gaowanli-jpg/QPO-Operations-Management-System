import {
  hydrateReleaseItemDrafts,
  RELEASE_ITEM_DRAFT_SOURCES
} from './marketReleaseDrafts'

const SOURCE_FIELD_TO_DRAFT_FIELD = Object.entries(RELEASE_ITEM_DRAFT_SOURCES)
  .reduce((result, [draftField, sourceField]) => ({
    ...result,
    [sourceField]: draftField
  }), {})

const defaultReleaseItemKey = (row = {}) => {
  if (row.releaseItemKey) return row.releaseItemKey
  const sourceType = row.sourceType || 'RFQ'
  const sourceItemId = row.sourceItemId ?? row.itemId ?? row.id
  return sourceItemId == null || sourceItemId === '' ? '' : `${sourceType}:${sourceItemId}`
}

const cleanReleaseItemKey = (key) => {
  const text = String(key || '').trim()
  return text && !text.endsWith(':') ? text : ''
}

const resolveReleaseItemKey = (row = {}, rowKey = defaultReleaseItemKey) => (
  cleanReleaseItemKey(defaultReleaseItemKey(row))
  || cleanReleaseItemKey(rowKey(row))
)

const valueForDraft = (value) => value ?? ''

const normalizeDraftValue = (value) => {
  if (value === null || value === undefined) return ''
  return String(value).trim()
}

const fieldSetByPreviewRowKey = (previewRows = [], rowKey = defaultReleaseItemKey) => {
  const result = new Map()
  previewRows.forEach(row => {
    const key = resolveReleaseItemKey(row, rowKey)
    if (!key) return
    ;(row.changes || []).forEach(change => {
      if (!change?.field || !SOURCE_FIELD_TO_DRAFT_FIELD[change.field]) return
      if (!result.has(key)) result.set(key, new Set())
      result.get(key).add(change.field)
    })
  })
  return result
}

const replacementRowWithDrafts = (savedRow, selectedRow, changedFields = new Set()) => {
  const row = hydrateReleaseItemDrafts(savedRow, selectedRow)
  changedFields.forEach(field => {
    const draftField = SOURCE_FIELD_TO_DRAFT_FIELD[field]
    if (draftField) {
      row[draftField] = valueForDraft(savedRow?.[field])
    }
  })
  return row
}

const updateKey = (update = {}) => resolveReleaseItemKey(update)

export const applyReleaseItemDataReplacementResult = ({
  itemList = [],
  selectedItemIdMap = {},
  acceptedQtyMap = {},
  response = {},
  draftRowsByKey = {},
  rowKey = defaultReleaseItemKey
} = {}) => {
  const affectedRows = Array.isArray(response?.affectedRows) ? response.affectedRows : []
  const acceptedQtyUpdates = Array.isArray(response?.acceptedQtyUpdates) ? response.acceptedQtyUpdates : []
  const changedFieldsByKey = fieldSetByPreviewRowKey(response?.previewRows || [], rowKey)
  const affectedRowsByKey = new Map()

  affectedRows.forEach(row => {
    const key = resolveReleaseItemKey(row, rowKey)
    if (key) affectedRowsByKey.set(key, row)
  })

  const nextItemList = (itemList || []).map(row => {
    const key = resolveReleaseItemKey(row, rowKey)
    const affectedRow = affectedRowsByKey.get(key)
    if (!affectedRow) return row
    return replacementRowWithDrafts(affectedRow, selectedItemIdMap[key] || draftRowsByKey[key] || row, changedFieldsByKey.get(key))
  })

  affectedRowsByKey.forEach((affectedRow, key) => {
    if (!selectedItemIdMap[key]) return
    selectedItemIdMap[key] = replacementRowWithDrafts(
      affectedRow,
      selectedItemIdMap[key] || draftRowsByKey[key],
      changedFieldsByKey.get(key)
    )
  })

  const itemListByKey = new Map(nextItemList.map(row => [resolveReleaseItemKey(row, rowKey), row]))
  acceptedQtyUpdates.forEach(update => {
    const key = updateKey(update)
    if (!key || update.acceptedQty === null || update.acceptedQty === undefined) return

    acceptedQtyMap[key] = update.acceptedQty
    const row = affectedRowsByKey.get(key)
      || itemListByKey.get(key)
      || selectedItemIdMap[key]
      || {
        releaseItemKey: key,
        id: update.itemId,
        sourceType: 'RFQ',
        sourceItemId: update.itemId,
        itemId: update.itemId
      }
    selectedItemIdMap[key] = replacementRowWithDrafts(
      row,
      selectedItemIdMap[key] || draftRowsByKey[key],
      changedFieldsByKey.get(key)
    )
  })

  return {
    itemList: nextItemList,
    affectedKeys: [...affectedRowsByKey.keys()],
    quantityKeys: acceptedQtyUpdates.map(updateKey).filter(Boolean)
  }
}

export const collectReleaseItemDraftReplacementConflicts = ({
  previewRows = [],
  selectedRowsByKey = {},
  rowKey = defaultReleaseItemKey
} = {}) => {
  const conflicts = []
  previewRows.forEach(row => {
    const key = resolveReleaseItemKey(row, rowKey)
    const selectedRow = selectedRowsByKey[key]
    if (!key || !selectedRow) return

    ;(row.changes || []).forEach(change => {
      const draftField = SOURCE_FIELD_TO_DRAFT_FIELD[change?.field]
      if (!draftField) return
      if (normalizeDraftValue(selectedRow[draftField]) === normalizeDraftValue(selectedRow[change.field])) return

      conflicts.push({
        releaseItemKey: key,
        field: change.field,
        label: change.label || change.field
      })
    })
  })
  return conflicts
}
