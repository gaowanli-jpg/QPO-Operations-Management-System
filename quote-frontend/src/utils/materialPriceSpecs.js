export const MATERIAL_PRICE_SPEC_DEFINITIONS = [
  { specCode: 'SPEC_1', specName: '规格一', sortNo: 10, buttonType: 'primary' },
  { specCode: 'SPEC_2', specName: '规格二', sortNo: 20, buttonType: 'success' },
  { specCode: 'SPEC_3', specName: '规格三', sortNo: 30, buttonType: 'warning' }
]

const numberOrNull = (value) => {
  if (value === null || value === undefined || value === '') return null
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue : null
}

const positiveNumberOrNull = (value) => {
  const numericValue = numberOrNull(value)
  return numericValue != null && numericValue > 0 ? numericValue : null
}

export const normalizeSpecCode = (value) => String(value || '').trim().toUpperCase()

export const specDefinition = (specCode) => (
  MATERIAL_PRICE_SPEC_DEFINITIONS.find(item => item.specCode === normalizeSpecCode(specCode))
  || MATERIAL_PRICE_SPEC_DEFINITIONS[0]
)

export const normalizePriceSpec = (spec = {}, definition = specDefinition(spec?.specCode)) => ({
  id: spec.id ?? null,
  materialId: spec.materialId ?? null,
  specCode: definition.specCode,
  specName: definition.specName,
  unifiedPrice: numberOrNull(spec.unifiedPrice),
  platePrice: numberOrNull(spec.platePrice),
  barPrice: numberOrNull(spec.barPrice),
  tubePrice: numberOrNull(spec.tubePrice),
  effectivePlatePrice: effectiveShapePrice(spec.platePrice, spec.unifiedPrice),
  effectiveBarPrice: effectiveShapePrice(spec.barPrice, spec.unifiedPrice),
  effectiveTubePrice: effectiveShapePrice(spec.tubePrice, spec.unifiedPrice),
  sortNo: definition.sortNo
})

export const normalizePriceSpecs = (priceSpecs = []) => {
  const byCode = new Map()
  ;(Array.isArray(priceSpecs) ? priceSpecs : []).forEach((spec) => {
    const code = normalizeSpecCode(spec?.specCode)
    if (code) byCode.set(code, spec)
  })
  return MATERIAL_PRICE_SPEC_DEFINITIONS.map(definition => normalizePriceSpec(byCode.get(definition.specCode), definition))
}

export const findPriceSpec = (priceSpecs = [], specCode = 'SPEC_1') => {
  const normalizedCode = normalizeSpecCode(specCode)
  return normalizePriceSpecs(priceSpecs).find(spec => spec.specCode === normalizedCode) || normalizePriceSpecs(priceSpecs)[0]
}

export const isBarShape = (shape) => String(shape || '').trim() === '棒材'

export const isTubeShape = (shape) => String(shape || '').trim() === '管材'

export const effectiveShapePrice = (shapePrice, unifiedPrice) => positiveNumberOrNull(shapePrice) ?? positiveNumberOrNull(unifiedPrice)

export const effectiveSpecPrice = (spec = {}, shape = '板材') => {
  if (isBarShape(shape)) return effectiveShapePrice(spec.barPrice, spec.unifiedPrice)
  if (isTubeShape(shape)) return effectiveShapePrice(spec.tubePrice, spec.unifiedPrice)
  return effectiveShapePrice(spec.platePrice, spec.unifiedPrice)
}

export const unitPriceSource = (spec = {}, shape = '板材') => {
  if (isBarShape(shape) && positiveNumberOrNull(spec.barPrice) != null) return 'BAR_PRICE'
  if (isTubeShape(shape) && positiveNumberOrNull(spec.tubePrice) != null) return 'TUBE_PRICE'
  if (!isBarShape(shape) && !isTubeShape(shape) && positiveNumberOrNull(spec.platePrice) != null) return 'PLATE_PRICE'
  return positiveNumberOrNull(spec.unifiedPrice) != null ? 'UNIFIED_PRICE' : null
}

export const hasMaterialSpecPrice = (spec = {}) => (
  positiveNumberOrNull(spec.unifiedPrice) != null
  || positiveNumberOrNull(spec.platePrice) != null
  || positiveNumberOrNull(spec.barPrice) != null
  || positiveNumberOrNull(spec.tubePrice) != null
)

export const hasAnyMaterialSpecPrice = (priceSpecs = []) => normalizePriceSpecs(priceSpecs).some(hasMaterialSpecPrice)

export const formatMaterialSpecPrice = (value) => {
  const numericValue = numberOrNull(value)
  return numericValue == null ? '未维护' : `${numericValue.toFixed(2)} 元/kg`
}

export const buildMaterialSpecSelection = (material = {}, specCode = 'SPEC_1') => {
  const priceSpecs = normalizePriceSpecs(
    Array.isArray(material.priceSpecs) && material.priceSpecs.length > 0
      ? material.priceSpecs
      : material.priceSpec
      ? [material.priceSpec]
      : []
  )
  const priceSpec = findPriceSpec(priceSpecs, specCode)
  return {
    ...material,
    materialId: material.materialId ?? material.id ?? null,
    priceSpecs,
    priceSpecCode: priceSpec.specCode,
    priceSpecName: priceSpec.specName,
    priceSpec
  }
}

const comparablePriceSpecs = (priceSpecs = []) => normalizePriceSpecs(priceSpecs).map(spec => ({
  specCode: spec.specCode,
  unifiedPrice: spec.unifiedPrice,
  platePrice: spec.platePrice,
  barPrice: spec.barPrice,
  tubePrice: spec.tubePrice
}))

export const samePriceSpecs = (left = [], right = []) => (
  JSON.stringify(comparablePriceSpecs(left)) === JSON.stringify(comparablePriceSpecs(right))
)
