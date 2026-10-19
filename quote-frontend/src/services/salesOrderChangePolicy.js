const MANUAL_SEND_STATUSES = new Set(['PENDING', 'FAILED', 'RESULT_UNKNOWN'])
const AUTOMATIC_RETRY_STATUSES = new Set(['FAILED', 'RESULT_UNKNOWN'])

export const canSelectSalesChangeNotice = (row, dispatchMode, currentAutoEpoch = 0) => {
  const status = String(row?.status || '').trim().toUpperCase()
  const mode = String(dispatchMode || '').trim().toUpperCase()
  if (mode !== 'AUTO') return MANUAL_SEND_STATUSES.has(status)
  if (AUTOMATIC_RETRY_STATUSES.has(status)) return true
  return status === 'PENDING' && Number(row?.autoDispatchEpoch || 0) < Number(currentAutoEpoch || 0)
}
