import { describe, expect, test } from 'vitest'
import {
  effectiveShapePrice,
  effectiveSpecPrice,
  hasMaterialSpecPrice,
  normalizePriceSpec,
  unitPriceSource
} from './materialPriceSpecs'

describe('materialPriceSpecs', () => {
  test('treats zero shape price as a maintained effective price', () => {
    const spec = normalizePriceSpec({ specCode: 'SPEC_1', unifiedPrice: 99, platePrice: 0 })

    expect(spec.effectivePlatePrice).toBe(0)
    expect(effectiveShapePrice(0, 99)).toBe(0)
    expect(effectiveSpecPrice(spec, 'PLATE')).toBe(0)
    expect(unitPriceSource(spec, 'PLATE')).toBe('PLATE_PRICE')
    expect(hasMaterialSpecPrice({ platePrice: 0 })).toBe(true)
  })
})
