import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8').replace(/\r\n/g, '\n')
const viewerPath = resolve(currentDir, 'components', 'common', 'DrawingFolderViewer.vue')

describe('DrawingFolderViewer', () => {
  test('lists drawing folder files with preview and download actions only', () => {
    expect(existsSync(viewerPath)).toBe(true)
    const source = readSource('components', 'common', 'DrawingFolderViewer.vue')

    expect(source).toContain('listDrawingFolderFiles')
    expect(source).toContain('openAuthenticatedFileInNewWindow')
    expect(source).toContain('downloadFile')
    expect(source).toContain('label="文件名"')
    expect(source).toContain('label="大小"')
    expect(source).toContain('label="上传时间"')
    expect(source).toContain('预览')
    expect(source).toContain('下载')
    expect(source).not.toContain('删除')
    expect(source).not.toContain('重命名')
    expect(source).not.toContain('移动')
  })

  test('loads folder files with backend paging and filename fuzzy search', () => {
    const source = readSource('components', 'common', 'DrawingFolderViewer.vue')

    expect(source).toContain('placeholder="按文件名搜索"')
    expect(source).toContain('v-model:current-page="page"')
    expect(source).toContain('v-model:page-size="pageSize"')
    expect(source).toContain(':page-sizes="[20, 50, 100]"')
    expect(source).toContain('layout="total, sizes, prev, pager, next, jumper"')
    expect(source).toContain('keyword: searchKeyword.value.trim()')
    expect(source).toContain('records || response?.files || []')
    expect(source).toContain('requestSeq')
  })

  test('drawing preview pages mount the shared folder viewer and branch by folder url', () => {
    for (const file of [
      'OrderManage.vue',
      'MarketRelease.vue',
      'SalesOrderManage.vue',
      'DeliveredItemManage.vue',
      'PartSearchManage.vue',
      'GlobalSetup.vue',
      'ItemPricing.vue'
    ]) {
      const source = readSource('components', file)
      expect(source).toContain("import DrawingFolderViewer from './common/DrawingFolderViewer.vue'")
      expect(source).toContain('isDrawingFolderUrl')
      expect(source).toContain('<DrawingFolderViewer')
      expect(source).toContain('folderViewerVisible')
      expect(source).toContain('currentDrawingFolderUrl')
    }
  })
})
