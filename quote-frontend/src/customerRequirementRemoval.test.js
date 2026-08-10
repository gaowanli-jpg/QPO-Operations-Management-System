import { describe, expect, test } from 'vitest'
import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve(__dirname)
const removedTokens = [
  'customer' + 'Requirement',
  'customer' + '_requirement',
  'customer' + 'Requirements',
  '客' + '户' + '需' + '求'
]

const readRuntimeSource = (dir) => fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
  const fullPath = path.join(dir, entry.name)
  if (entry.isDirectory()) return readRuntimeSource(fullPath)
  if (!/\.(vue|js)$/.test(entry.name)) return []
  return fs.readFileSync(fullPath, 'utf8')
})

describe('customer requirement field removal', () => {
  test('frontend runtime no longer exposes customer requirement fields', () => {
    const source = [
      ...readRuntimeSource(path.join(root, 'components')),
      ...readRuntimeSource(path.join(root, 'services'))
    ].join('\n')

    for (const token of removedTokens) {
      expect(source).not.toContain(token)
    }
  })
})
