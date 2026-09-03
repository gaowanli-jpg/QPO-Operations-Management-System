import { describe, expect, test } from 'vitest'
import {
  buildProcessArrangementPathIndex,
  collectProcessArrangementPickerFilter,
  findProcessArrangementPathLabels
} from './processArrangementPicker.js'

const tree = [
  {
    treeKey: 'node:1',
    nodeId: 1,
    nodeType: 'node',
    label: 'Milling',
    children: [
      {
        treeKey: 'node:2',
        nodeId: 2,
        nodeType: 'node',
        label: 'Five Axis',
        children: [
          { treeKey: 'item:10', nodeType: 'item', label: 'CNC 5X', sourceType: 'process', processDetailId: 100 },
          { treeKey: 'item:11', nodeType: 'item', label: 'Outsource', sourceType: 'extra', feeItemId: 5 }
        ]
      }
    ]
  },
  {
    treeKey: 'node:3',
    nodeId: 3,
    nodeType: 'node',
    label: 'Empty Group',
    children: []
  }
]

describe('process arrangement picker helpers', () => {
  test('collects descendant process and extra leaves for a selected branch', () => {
    expect(collectProcessArrangementPickerFilter({
      tree,
      selectedTreeKey: 'node:2',
      sourceType: ''
    })).toEqual({
      hasSelection: true,
      hasLeaves: true,
      processDetailIds: [100],
      feeItemIds: [5]
    })
  })

  test('applies source type filtering inside the selected branch', () => {
    expect(collectProcessArrangementPickerFilter({
      tree,
      selectedTreeKey: 'node:2',
      sourceType: 'extra'
    })).toEqual({
      hasSelection: true,
      hasLeaves: true,
      processDetailIds: [],
      feeItemIds: [5]
    })
  })

  test('distinguishes empty selected branches from the all-processes state', () => {
    expect(collectProcessArrangementPickerFilter({
      tree,
      selectedTreeKey: 'node:3',
      sourceType: ''
    })).toEqual({
      hasSelection: true,
      hasLeaves: false,
      processDetailIds: [],
      feeItemIds: []
    })
  })

  test('builds readable paths for search result rows', () => {
    const index = buildProcessArrangementPathIndex(tree)

    expect(index.get('process:100')).toBe('Milling / Five Axis')
    expect(index.get('extra:5')).toBe('Milling / Five Axis')
    expect(findProcessArrangementPathLabels(tree, 'item:10')).toEqual(['Milling', 'Five Axis', 'CNC 5X'])
  })
})
