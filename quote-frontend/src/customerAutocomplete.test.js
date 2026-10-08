import { describe, expect, test } from 'vitest'
import fs from 'node:fs'
import path from 'node:path'

const source = fs.readFileSync(path.resolve(process.cwd(), 'src/components/CustomerAutocomplete.vue'), 'utf8')

describe('customer autocomplete', () => {
  test('queries the scoped customer source with fuzzy keyword and ten-result page', () => {
    expect(source).toContain('listCustomers(props.customerScope')
    expect(source).toContain("field: 'all'")
    expect(source).toContain('keyword: query')
    expect(source).toContain('pageSize: 10')
    expect(source).toContain('.slice(0, 10)')
  })

  test('keeps the result list scrollable and emits selected or cleared customers', () => {
    expect(source).toContain('customer-autocomplete-option')
    expect(source).toContain("emit('select', normalized)")
    expect(source).toContain("emit('clear')")
  })
})
