import { describe, expect, test } from 'vitest'
import fs from 'node:fs'
import path from 'node:path'

const readComponent = name => fs.readFileSync(
  path.resolve(process.cwd(), 'src/components', name),
  'utf8'
).replace(/\r\n/g, '\n')

describe('sales order customer filter binding', () => {
  test('customer autocomplete binds to state that the sales order page actually declares', () => {
    const source = readComponent('SalesOrderManage.vue')
    const bindings = [...source.matchAll(/<CustomerAutocomplete\b[^>]*>/g)].map(match => match[0])

    expect(bindings.length).toBeGreaterThan(0)
    for (const binding of bindings) {
      const stateExpressions = [
        ...binding.matchAll(/v-model="([^"]+)"/g),
        ...binding.matchAll(/:customer-code="([^"]+)"/g)
      ].map(match => match[1])

      for (const expression of stateExpressions) {
        const rootName = expression.split(/[.[]/)[0]
        expect(source, `unbound template state "${expression}"`).toContain(`const ${rootName} =`)
      }
    }
  })
})
