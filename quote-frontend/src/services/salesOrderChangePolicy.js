const MANUAL_SEND_STATUSES = new Set(['PENDING', 'FAILED', 'RESULT_UNKNOWN'])
const AUTOMATIC_RETRY_STATUSES = new Set(['FAILED', 'RESULT_UNKNOWN'])

export const canSelectSalesChangeNotice = (row, dispatchMode) => {
  const status = String(row?.status || '').trim().toUpperCase()
  const mode = String(dispatchMode || '').trim().toUpperCase()
  return (mode === 'AUTO' ? AUTOMATIC_RETRY_STATUSES : MANUAL_SEND_STATUSES).has(status)
}
