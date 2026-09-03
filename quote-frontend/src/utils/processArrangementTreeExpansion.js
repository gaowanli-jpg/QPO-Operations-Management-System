const isNode = (row) => row?.nodeType === 'node' && row.treeKey

const pushUnique = (rows, key) => {
  if (key && !rows.includes(key)) rows.push(key)
}

export const collectProcessArrangementNodeKeys = (rows = []) => {
  const keys = []
  const visit = (items = []) => {
    for (const item of items) {
      if (isNode(item)) pushUnique(keys, item.treeKey)
      visit(item?.children || [])
    }
  }
  visit(rows)
  return keys
}

export const findProcessArrangementNodePathKeys = (nodeId, rows = []) => {
  if (nodeId == null) return []
  const expectedNodeId = String(nodeId)

  const visit = (items = [], path = []) => {
    for (const item of items) {
      const nextPath = isNode(item) ? [...path, item.treeKey] : path
      if (isNode(item) && String(item.nodeId) === expectedNodeId) return nextPath
      const childPath = visit(item?.children || [], nextPath)
      if (childPath.length) return childPath
    }
    return []
  }

  return visit(rows)
}

export const nextProcessArrangementExpandedKeys = ({
  tree = [],
  currentExpandedKeys = [],
  revealNodeId = null
} = {}) => {
  const validKeys = new Set(collectProcessArrangementNodeKeys(tree))
  const nextKeys = []

  for (const key of currentExpandedKeys || []) {
    if (validKeys.has(key)) pushUnique(nextKeys, key)
  }

  for (const key of findProcessArrangementNodePathKeys(revealNodeId, tree)) {
    if (validKeys.has(key)) pushUnique(nextKeys, key)
  }

  return nextKeys
}
