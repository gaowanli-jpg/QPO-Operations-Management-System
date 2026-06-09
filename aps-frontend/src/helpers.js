export const statusLabels = {
  LOCKED: '已锁定',
  PARTIAL_LOCKED: '部分锁定',
  NEED_PRODUCTION: '需生产',
  SHORTAGE: '有缺口',
  NOT_REQUIRED: '不需要',
  UNLOCKED: '未锁定',
  PENDING: '待分配',
  PENDING_PMC_ASSIGNMENT: '待 PMC 分配',
  ASSIGNED: '已分配',
  RELEASED: '已释放',
  PENDING_PRODUCTION: '待生产',
  PENDING_PURCHASE: '待采购',
  ENABLED: '启用',
  DISABLED: '停用',
  PRODUCTION: '生产部门',
  OUTSOURCE: '外协部门',
  IDLE: '空闲',
  RUNNING: '生产中',
  MAINTENANCE: '维修',
  STOPPED: '停机'
}

export const label = (value) => statusLabels[value] || value || '-'

export const statusClass = (value) => {
  if (['LOCKED', 'NOT_REQUIRED', 'ASSIGNED', 'ENABLED', 'IDLE'].includes(value)) return 'ok'
  if (['PARTIAL_LOCKED', 'NEED_PRODUCTION', 'SHORTAGE', 'PENDING', 'PENDING_PMC_ASSIGNMENT', 'PENDING_PRODUCTION', 'PENDING_PURCHASE'].includes(value)) return 'warn'
  if (['DISABLED', 'STOPPED'].includes(value)) return 'bad'
  return 'muted'
}

export const number = (value, digits = 0) => {
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) return '-'
  return parsed.toLocaleString('zh-CN', { maximumFractionDigits: digits })
}

export const field = (row, ...keys) => {
  if (!row) return ''
  for (const key of keys) {
    if (row[key] !== undefined && row[key] !== null) return row[key]
  }
  return ''
}

export const customerProjectText = (row) => field(row, 'customer_project_no', 'customerProjectNo') || '空'

export const taskOrderText = (row) => [
  field(row, 'internal_project_no', 'internalProjectNo') || '-',
  field(row, 'customer_order_no', 'customerOrderNo') || '-',
  customerProjectText(row)
].join(' / ')
