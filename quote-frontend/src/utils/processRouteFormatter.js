const parseRouteRows = (route) => {
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
  const rows = parseRouteRows(route)
  if (rows.length === 0) return '-'
  return rows
    .map(formatRouteRow)
    .filter(Boolean)
    .join(' / ') || '-'
}
