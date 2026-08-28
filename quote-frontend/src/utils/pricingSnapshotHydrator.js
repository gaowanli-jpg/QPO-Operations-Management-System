const normalizeCodeKey = (value) => String(value || '').trim().toUpperCase()
const normalizeIdKey = (value) => String(value ?? '').trim()

const numberOrNull = (value) => {
  if (value === null || value === undefined || value === '') return null
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue : null
}

const firstNumber = (...values) => {
  for (const value of values) {
    const numericValue = numberOrNull(value)
    if (numericValue !== null) return numericValue
  }
  return null
}

const positiveNumberOrNull = (value) => {
  const numericValue = numberOrNull(value)
  return numericValue != null && numericValue > 0 ? numericValue : null
}

export const materialCodeKey = normalizeCodeKey
export const processDetailIdKey = normalizeIdKey

export const effectiveMaterialDensity = (material = {}) => (
  positiveNumberOrNull(material.density) ?? positiveNumberOrNull(material.mesDensityRaw)
)

export const buildPricingOptionIndex = ({ materialOptions = [], processOptions = [] } = {}) => {
  const materialByCode = new Map()
  ;(materialOptions || []).forEach((material) => {
    const key = materialCodeKey(material?.materialCode)
    if (key) materialByCode.set(key, material)
  })

  const processByDetailId = new Map()
  ;(processOptions || []).forEach((process) => {
    const id = process?.processDetailId ?? process?.id
    const key = processDetailIdKey(id)
    if (key) processByDetailId.set(key, process)
  })

  return { materialByCode, processByDetailId }
}

export const hydrateMaterialPricingSnapshot = (snapshot = {}, optionIndex = {}) => {
  const key = materialCodeKey(snapshot?.materialCode)
  const latest = key ? optionIndex.materialByCode?.get(key) : null
  if (!latest) return snapshot

  const density = effectiveMaterialDensity(latest)
  const unitPrice = firstNumber(latest.unitPrice)
  return {
    ...snapshot,
    materialId: latest.materialId ?? latest.id ?? snapshot.materialId ?? snapshot.id ?? null,
    materialCode: latest.materialCode || snapshot.materialCode || '',
    materialName: latest.materialName || latest.name || snapshot.materialName || snapshot.name || '',
    name: latest.materialName || latest.name || snapshot.name || snapshot.materialName || '',
    materialTypeId: latest.materialTypeId ?? snapshot.materialTypeId ?? null,
    materialTypeCode: latest.materialTypeCode || snapshot.materialTypeCode || '',
    materialTypeName: latest.materialTypeName || snapshot.materialTypeName || '',
    materialCategory: latest.materialCategory || snapshot.materialCategory || '',
    density: density ?? effectiveMaterialDensity(snapshot),
    mesDensityRaw: latest.mesDensityRaw ?? snapshot.mesDensityRaw,
    platePrice: firstNumber(latest.platePrice, unitPrice) ?? firstNumber(snapshot.platePrice, snapshot.unitPrice),
    barPrice: firstNumber(latest.barPrice, unitPrice) ?? firstNumber(snapshot.barPrice, snapshot.unitPrice),
    tubePrice: firstNumber(latest.tubePrice, unitPrice) ?? firstNumber(snapshot.tubePrice, snapshot.unitPrice),
    remarks: latest.remarks ?? snapshot.remarks ?? '',
    shape: snapshot.shape || latest.shape || '板材'
  }
}

export const hydrateProcessPricingSnapshot = (snapshot = {}, optionIndex = {}) => {
  const sourceType = snapshot.sourceType || (snapshot.calcType === '非工时费用' ? 'extra' : 'process')
  if (sourceType === 'extra') return snapshot

  const key = processDetailIdKey(snapshot.processDetailId ?? snapshot.id)
  const latest = key ? optionIndex.processByDetailId?.get(key) : null
  if (!latest) return snapshot

  const processDetailId = latest.processDetailId ?? latest.id ?? snapshot.processDetailId ?? null
  const processCategoryId = latest.processCategoryId ?? latest.categoryId ?? snapshot.processCategoryId ?? snapshot.categoryId ?? null
  const effectiveHourlyRate = firstNumber(latest.effectiveHourlyRate, latest.hourlyRate, latest.unitRate)
  const hourlyRate = firstNumber(latest.hourlyRate, latest.effectiveHourlyRate, latest.unitRate)
  const processName = latest.detailName || latest.processName || snapshot.detailName || snapshot.processName || ''

  return {
    ...snapshot,
    optionKey: latest.optionKey || (processDetailId ? `process:${processDetailId}` : snapshot.optionKey || ''),
    processDetailId,
    processCategoryId,
    categoryId: processCategoryId,
    processCode: latest.processCode || snapshot.processCode || '',
    categoryName: latest.categoryName || snapshot.categoryName || '',
    detailName: processName,
    processName,
    detailHourlyRate: latest.detailHourlyRate ?? snapshot.detailHourlyRate ?? null,
    categoryHourlyRate: latest.categoryHourlyRate ?? snapshot.categoryHourlyRate ?? null,
    effectiveHourlyRate: effectiveHourlyRate ?? firstNumber(snapshot.effectiveHourlyRate, snapshot.hourlyRate, snapshot.unitRate),
    hourlyRate: hourlyRate ?? firstNumber(snapshot.hourlyRate, snapshot.effectiveHourlyRate, snapshot.unitRate),
    unitRate: hourlyRate ?? effectiveHourlyRate ?? firstNumber(snapshot.unitRate, snapshot.hourlyRate, snapshot.effectiveHourlyRate),
    label: latest.label || snapshot.label || '',
    calcType: '工时',
    sourceType: 'process'
  }
}

export const hydrateProcessRoutePricingSnapshots = (routes = [], optionIndex = {}) => (
  (routes || []).map(route => hydrateProcessPricingSnapshot(route, optionIndex))
)
