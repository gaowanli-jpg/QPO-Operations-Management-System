export const FIELD_LABELS = {
  id: 'ID',
  partNo: '图号',
  normalizedPartNo: '标准图号',
  version: '版本',
  qty: '数量',
  unit: '单位',
  description: '物料描述',
  materialName: '材料名称',
  engineeringMaterialName: '工程材料',
  surfaceTreatment: '表面处理',
  leadTime: '交货周期',
  quoteLeadTime: '报价交期',
  internalTargetLeadTime: '内部目标交期',
  remark: '备注',
  rfqId: '订单ID',
  rfqNo: '订单号',
  customerName: '客户名称',
  itemCount: '零件数量',
  uploadedDrawingCount: '上传图纸数',
  matchedItemCount: '匹配零件数',
  missingDrawingCount: '缺少图纸数',
  unmatchedDrawingCount: '未匹配图纸数',
  taskId: '任务ID',
  taskType: '任务类型',
  status: '状态',
  request: '请求参数',
  fileName: '文件名',
  updatedAt: '更新时间',
  size: '文件大小',
  username: '账号',
  displayName: '显示名称',
  role: '角色',
  reason: '原因',
  failedLoginAttempts: '登录失败次数',
  lockedUntil: '锁定至',
  forcePasswordChange: '强制改密',
  beforeData: '操作前数据',
  afterData: '操作后数据',
  processRoute: '工艺路线',
  feeData: '费用数据',
  materialData: '材料数据',
  baseCost: '基础成本',
  finalUnitPrice: '最终单价',
  finalTotalPrice: '最终总价'
}

export const parseAuditJson = (value) => {
  if (value === null || value === undefined || value === '') return null
  if (typeof value !== 'string') return value
  try {
    return JSON.parse(value)
  } catch {
    return value
  }
}

export const formatAuditJson = (value) => {
  const parsed = parseAuditJson(value)
  if (parsed === null || parsed === undefined || parsed === '') return '-'
  if (typeof parsed === 'string') return parsed
  try {
    return JSON.stringify(parsed, null, 2)
  } catch {
    return String(parsed)
  }
}

export const auditDataRows = (value) => {
  const parsed = parseAuditJson(value)
  if (parsed === null || parsed === undefined || parsed === '') return []
  if (Array.isArray(parsed)) {
    return parsed.map((item, index) => ({
      key: String(index),
      label: `第 ${index + 1} 项`,
      value: formatRowValue(item)
    }))
  }
  if (typeof parsed !== 'object') {
    return [{ key: 'value', label: '内容', value: String(parsed) }]
  }
  return Object.entries(parsed).map(([key, item]) => ({
    key,
    label: FIELD_LABELS[key] || key,
    value: formatRowValue(item)
  }))
}

const formatRowValue = (value) => {
  if (value === null || value === undefined || value === '') return '-'
  if (typeof value === 'object') return JSON.stringify(value, null, 2)
  return String(value)
}