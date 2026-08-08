export const parseProcessRouteRows = (route) => {
  if (!route) return []
  if (Array.isArray(route)) return route
  if (typeof route === 'string') {
    try {
      const parsed = JSON.parse(route)
      return Array.isArray(parsed) ? parsed : []
    } catch {
      return []
    }
  }
  return []
}

const hasValue = (value) => value !== null && value !== undefined && value !== ''

const formatCompactNumber = (value) => {
  const number = Number(value)
  if (!Number.isFinite(number)) return String(value || '')
  return number.toFixed(3).replace(/\.?0+$/, '')
}

const formatCompactMoney = (value) => `¥${formatCompactNumber(value)}`

const routeValue = (row = {}, field) => {
  const isFee = String(row.routeType || '').toUpperCase() === 'FEE' || row.sourceType === 'extra'
  if (field === 'processCode') return row.processCode || ''
  if (field === 'categoryName') return row.categoryName || ''
  if (field === 'detailName') return row.detailName || row.processName || row.name || ''
  if (field === 'workHours') return isFee ? '' : (row.unitWorkHours ?? row.inputVal ?? '')
  if (field === 'hourlyRate') return isFee ? '' : (row.hourlyRate ?? row.unitRate ?? '')
  if (field === 'lineCost') return row.lineCost ?? row.cost ?? ''
  return row[field] ?? ''
}

export const formatProcessRouteFieldList = (route, field) => {
  const rows = parseProcessRouteRows(route)
  if (rows.length === 0) return '-'
  const values = rows
    .map(row => routeValue(row, field))
    .filter(hasValue)
    .map(formatCompactNumber)
  return values.length > 0 ? values.join(' / ') : '-'
}

const formatRouteRow = (row = {}) => {
  const isFee = String(row.routeType || '').toUpperCase() === 'FEE' || row.sourceType === 'extra'
  if (isFee) {
    const feeName = row.processName || row.detailName || row.name || '费用'
    return hasValue(row.lineCost) ? `${feeName}（${formatCompactMoney(row.lineCost)}）` : feeName
  }

  const identity = [row.processCode, row.categoryName, row.detailName || row.processName || row.name]
    .filter(Boolean)
    .join(' / ')
  const metrics = []
  const workHours = row.unitWorkHours ?? row.inputVal
  const hourlyRate = row.hourlyRate ?? row.unitRate
  if (hasValue(workHours)) metrics.push(`${formatCompactNumber(workHours)}h`)
  if (hasValue(hourlyRate)) metrics.push(formatCompactMoney(hourlyRate))
  if (hasValue(row.lineCost)) metrics.push(formatCompactMoney(row.lineCost))
  if (!identity) return metrics.join(' × ')
  return metrics.length > 0 ? `${identity}（${metrics.join(' × ')}）` : identity
}

export const formatProcessRouteSnapshot = (route) => {
  const rows = parseProcessRouteRows(route)
  if (rows.length === 0) return '-'
  return rows
    .map(formatRouteRow)
    .filter(Boolean)
    .join(' / ') || '-'
}
