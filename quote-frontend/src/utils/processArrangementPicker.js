const PROCESS_SOURCE_TYPE = 'process'
const EXTRA_SOURCE_TYPE = 'extra'

const isArrangementItem = (row) => row?.nodeType === 'item'

const normalizeSourceType = (sourceType) => {
  const value = String(sourceType || '').trim().toLowerCase()
  return value === PROCESS_SOURCE_TYPE || value === EXTRA_SOURCE_TYPE ? value : ''
}

const pushUnique = (rows, value) => {
  if (value == null || value === '') return
  if (!rows.includes(value)) rows.push(value)
}

const findTreeNode = (rows = [], treeKey) => {
  if (!treeKey) return null
  for (const row of rows || []) {
    if (row?.treeKey === treeKey) return row
    const child = findTreeNode(row?.children || [], treeKey)
    if (child) return child
  }
  return null
}

const collectLeafIds = (row, sourceType, result) => {
  if (!row) return
  if (isArrangementItem(row)) {
    if ((!sourceType || sourceType === PROCESS_SOURCE_TYPE) && row.sourceType === PROCESS_SOURCE_TYPE) {
      pushUnique(result.processDetailIds, row.processDetailId)
    }
    if ((!sourceType || sourceType === EXTRA_SOURCE_TYPE) && row.sourceType === EXTRA_SOURCE_TYPE) {
      pushUnique(result.feeItemIds, row.feeItemId)
    }
    return
  }
  for (const child of row.children || []) {
    collectLeafIds(child, sourceType, result)
  }
}

const arrangementItemLookupKey = (row) => {
  if (!isArrangementItem(row)) return null
  if (row.sourceType === PROCESS_SOURCE_TYPE && row.processDetailId != null) {
    return `process:${row.processDetailId}`
  }
  if (row.sourceType === EXTRA_SOURCE_TYPE && row.feeItemId != null) {
    return `extra:${row.feeItemId}`
  }
  return null
}

export const collectProcessArrangementPickerFilter = ({
  tree = [],
  selectedTreeKey = '',
  sourceType = ''
} = {}) => {
  if (!selectedTreeKey) {
    return {
      hasSelection: false,
      hasLeaves: false,
      processDetailIds: [],
      feeItemIds: []
    }
  }

  const result = {
    hasSelection: true,
    hasLeaves: false,
    processDetailIds: [],
    feeItemIds: []
  }
  const selectedNode = findTreeNode(tree, selectedTreeKey)
  collectLeafIds(selectedNode, normalizeSourceType(sourceType), result)
  result.hasLeaves = result.processDetailIds.length > 0 || result.feeItemIds.length > 0
  return result
}

export const buildProcessArrangementPathIndex = (tree = []) => {
  const index = new Map()

  const visit = (rows = [], parentLabels = []) => {
    for (const row of rows || []) {
      if (!row) continue
      if (isArrangementItem(row)) {
        const key = arrangementItemLookupKey(row)
        if (key) index.set(key, parentLabels.join(' / '))
        continue
      }
      const nextLabels = row.label ? [...parentLabels, row.label] : parentLabels
      visit(row.children || [], nextLabels)
    }
  }

  visit(tree)
  return index
}

export const findProcessArrangementPathLabels = (tree = [], treeKey = '') => {
  const visit = (rows = [], path = []) => {
    for (const row of rows || []) {
      if (!row) continue
      const nextPath = row.label ? [...path, row.label] : path
      if (row.treeKey === treeKey) return nextPath
      const childPath = visit(row.children || [], nextPath)
      if (childPath.length) return childPath
    }
    return []
  }

  return visit(tree)
}
