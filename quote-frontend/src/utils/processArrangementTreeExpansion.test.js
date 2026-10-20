import { describe, expect, test } from 'vitest'
import {
  collectProcessArrangementNodeKeys,
  nextProcessArrangementExpandedKeys
} from './processArrangementTreeExpansion.js'

const arrangementTree = [
  {
    treeKey: 'node:1',
    nodeId: 1,
    nodeType: 'node',
    children: [
      {
        treeKey: 'node:2',
        nodeId: 2,
        nodeType: 'node',
        parentNodeId: 1,
        children: [
          { treeKey: 'item:20', nodeType: 'item', parentNodeId: 2 }
        ]
      }
    ]
  },
  {
    treeKey: 'node:3',
    nodeId: 3,
    nodeType: 'node',
    children: [
      { treeKey: 'node:4', nodeId: 4, nodeType: 'node', parentNodeId: 3, children: [] }
    ]
  }
]

describe('process arrangement tree expansion state', () => {
  test('does not expand unrelated branches when revealing a changed parent node', () => {
    const expandedKeys = nextProcessArrangementExpandedKeys({
      tree: arrangementTree,
      currentExpandedKeys: ['node:3'],
      revealNodeId: 2
    })

    expect(expandedKeys).toEqual(['node:3', 'node:1', 'node:2'])
  })

  test('prunes expanded keys that no longer exist after refresh', () => {
    const expandedKeys = nextProcessArrangementExpandedKeys({
      tree: arrangementTree,
      currentExpandedKeys: ['node:1', 'node:99', 'item:20'],
      revealNodeId: null
    })

    expect(expandedKeys).toEqual(['node:1'])
  })

  test('collects only expandable node keys', () => {
    expect(collectProcessArrangementNodeKeys(arrangementTree)).toEqual(['node:1', 'node:2', 'node:3', 'node:4'])
  })
})
