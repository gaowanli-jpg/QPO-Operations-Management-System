import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

const dictApi = readSource('services', 'dictApi.js')
const materialProcess = readSource('components', 'MaterialProcessManage.vue')

describe('process arrangement management page', () => {
  test('dictApi exposes process arrangement management endpoints', () => {
    expect(dictApi).toContain('listProcessArrangementTree')
    expect(dictApi).toContain('/api/dict/process-arrangement/tree')
    expect(dictApi).toContain('listUnplacedProcessArrangementOptions')
    expect(dictApi).toContain('/api/dict/process-arrangement/unplaced-options')
    expect(dictApi).toContain('createProcessArrangementNode')
    expect(dictApi).toContain('/api/dict/process-arrangement/nodes')
    expect(dictApi).toContain('updateProcessArrangementNode')
    expect(dictApi).toContain('deleteProcessArrangementNode')
    expect(dictApi).toContain('placeProcessArrangementItems')
    expect(dictApi).toContain('/api/dict/process-arrangement/items/batch')
    expect(dictApi).toContain('moveProcessArrangementItem')
    expect(dictApi).toContain('/api/dict/process-arrangement/items/${id}/move')
    expect(dictApi).toContain('removeProcessArrangementItem')
  })

  test('material process page inserts arrangement tab between detail and percentage fee tabs', () => {
    const detailIndex = materialProcess.indexOf('name="processDetails"')
    const arrangementIndex = materialProcess.indexOf('name="processArrangement"')
    const pricingIndex = materialProcess.indexOf('name="pricingMarkupFees"')

    expect(detailIndex).toBeGreaterThan(-1)
    expect(arrangementIndex).toBeGreaterThan(detailIndex)
    expect(pricingIndex).toBeGreaterThan(arrangementIndex)
    expect(materialProcess).toContain('工艺编排')
  })

  test('arrangement tab uses left tree and right unplaced process library workflow', () => {
    expect(materialProcess).toContain('process-arrangement-workbench')
    expect(materialProcess).toContain('process-arrangement-tree-panel')
    expect(materialProcess).toContain('process-arrangement-library-panel')
    expect(materialProcess).toContain(':data="processArrangementTree"')
    expect(materialProcess).toContain('未编排工艺')
    expect(materialProcess).toContain('unplacedArrangementOptions')
    expect(materialProcess).toContain('arrangementKeyword')
    expect(materialProcess).toContain('arrangementSourceType')
    expect(materialProcess).toContain('工时工艺')
    expect(materialProcess).toContain('非工时工艺')
    expect(materialProcess).toContain('@selection-change="handleArrangementOptionSelection"')
    expect(materialProcess).toContain('@click="placeSelectedArrangementItems"')
  })

  test('arrangement tree refresh reveals the changed branch without expanding every tree', () => {
    expect(materialProcess).not.toContain('default-expand-all')
    expect(materialProcess).toContain('ref="processArrangementTreeRef"')
    expect(materialProcess).toContain(':default-expanded-keys="processArrangementExpandedKeys"')
    expect(materialProcess).toContain('@node-expand="handleArrangementNodeExpand"')
    expect(materialProcess).toContain('@node-collapse="handleArrangementNodeCollapse"')
    expect(materialProcess).toContain('setCurrentKey?.(selectedArrangementNode.value?.treeKey ?? null, false)')
    expect(materialProcess).toContain('loadProcessArrangement({ revealNodeId })')
    expect(materialProcess).toContain('loadProcessArrangement({ revealNodeId: selectedArrangementNode.value?.nodeId ?? null })')
  })

  test('arrangement management actions reuse process manage permission and confirm cascade deletion', () => {
    expect(materialProcess).toContain("activeTab === 'processArrangement'")
    expect(materialProcess).toContain("v-permission=\"'engineer:dict:process-manage'\"")
    expect(materialProcess).toContain('openArrangementNodeDialog')
    expect(materialProcess).toContain('saveArrangementNode')
    expect(materialProcess).toContain('moveArrangementNode')
    expect(materialProcess).toContain('deleteArrangementNode')
    expect(materialProcess).toContain('removeArrangementItem')
    expect(materialProcess).toContain('deleteSummary.removedNodeCount')
    expect(materialProcess).toContain('deleteSummary.removedItemCount')
    expect(materialProcess).toContain('级联移除')
  })
})
