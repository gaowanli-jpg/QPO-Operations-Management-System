export const dashboardMockData = {
  updatedAt: '2026-08-03 14:30:00',
  summaryCards: [
    {
      key: 'rfqTodo',
      title: '询价待推进',
      value: 36,
      unit: '单',
      helper: '未核价、已核价、已报价',
      trendText: '优先关注工程核价积压',
      tone: 'primary',
      target: { path: '/orders', query: { status: '未核价' } },
      permission: 'market:order:view'
    },
    {
      key: 'salesQuoted',
      title: '销售已报价',
      value: 19,
      unit: '单',
      helper: '按客户查看销售订单',
      trendText: '查看导入、物料号和 U9 状态',
      tone: 'success',
      target: { path: '/sales-orders', query: { status: '已报价' } },
      permission: 'market:sales-order:view'
    },
    {
      key: 'deliveredActive',
      title: '已提未完成',
      value: 14,
      unit: '行',
      helper: '等待物料号或库存信息',
      trendText: '处理 MES / WMS 待办',
      tone: 'warning',
      target: { path: '/delivered-items', query: { flowScope: 'ACTIVE' } },
      permission: 'market:delivered-item:view'
    },
    {
      key: 'lockedProduction',
      title: '待生产下发',
      value: 7,
      unit: '行',
      helper: 'WMS 锁库后仍需生产',
      trendText: '进入待产零件下发生产',
      tone: 'info',
      target: { path: '/production-items', query: { mesProductionStatus: 'PENDING' } },
      permission: 'market:production-item:view'
    }
  ],
  rfqStatusDistribution: [
    { name: '未核价', value: 12, targetPath: '/engineering', query: { status: '未核价' } },
    { name: '已核价', value: 8, targetPath: '/quote-process', query: { status: '已核价' } },
    { name: '已报价', value: 16, targetPath: '/release', query: { status: '已报价' } }
  ],
  salesQuotedCustomerDistribution: [
    {
      name: '示例客户A',
      customerName: '示例客户A',
      customerCode: 'CUS-DEMO-001',
      customerId: 1001,
      value: 9,
      targetPath: '/sales-orders',
      query: { status: '已报价', customerId: 1001 }
    },
    {
      name: '示例客户B',
      customerName: '示例客户B',
      customerCode: 'CUS-DEMO-002',
      customerId: 1002,
      value: 6,
      targetPath: '/sales-orders',
      query: { status: '已报价', customerId: 1002 }
    },
    {
      name: '示例客户C',
      customerName: '示例客户C',
      customerCode: 'CUS-DEMO-003',
      customerId: 1003,
      value: 4,
      targetPath: '/sales-orders',
      query: { status: '已报价', customerId: 1003 }
    }
  ],
  customerMatrix: [
    {
      customerId: 1001,
      customerCode: 'CUS-DEMO-001',
      customerName: '示例客户A',
      rfqUnpriced: 3,
      rfqPriced: 2,
      rfqQuoted: 4,
      salesQuoted: 9,
      totalFocus: 18
    },
    {
      customerId: 1002,
      customerCode: 'CUS-DEMO-002',
      customerName: '示例客户B',
      rfqUnpriced: 5,
      rfqPriced: 3,
      rfqQuoted: 2,
      salesQuoted: 6,
      totalFocus: 16
    },
    {
      customerId: 1003,
      customerCode: 'CUS-DEMO-003',
      customerName: '示例客户C',
      rfqUnpriced: 4,
      rfqPriced: 3,
      rfqQuoted: 10,
      salesQuoted: 4,
      totalFocus: 21
    }
  ],
  todoCards: [
    {
      key: 'rfqPricing',
      title: '待核价询价订单',
      value: 12,
      unit: '单',
      description: '进入零件核价处理未核价订单',
      path: '/engineering',
      query: { status: '未核价' },
      permission: 'engineer:setup:view',
      tone: 'info'
    },
    {
      key: 'salesMatch',
      title: '销售订单异常治理',
      value: 19,
      unit: '单',
      description: '排查历史订单或自动补全异常',
      path: '/sales-match',
      query: { status: '已报价' },
      permission: 'market:sales-match:view',
      tone: 'primary'
    },
    {
      key: 'mesProductionTodo',
      title: '待下发 MES 生产',
      value: 7,
      unit: '行',
      description: '进入待产零件下发 MES 生产',
      path: '/production-items',
      query: { mesProductionStatus: 'PENDING' },
      permission: 'market:production-item:view',
      tone: 'info'
    },
    {
      key: 'u9SyncReady',
      title: '可同步 U9 订单',
      value: 6,
      unit: '单',
      description: '整单物料号就绪后进入 U9 同步',
      path: '/u9-sync',
      query: { status: '已提单' },
      permission: 'market:u9-sync:view',
      tone: 'success'
    }
  ],
  attentionItems: [
    {
      key: 'mesCallbackPending',
      title: '待 MES 回调',
      value: 5,
      unit: '行',
      description: '物料号请求已提交，等待数据中心转回结果',
      path: '/delivered-items',
      query: { flowStage: 'CALLBACK_PENDING' },
      permission: 'market:delivered-item:view',
      severity: 'warning'
    },
    {
      key: 'mesFailed',
      title: 'MES 物料号失败',
      value: 3,
      unit: '行',
      description: '进入已提零件排查或重新获取物料号',
      path: '/delivered-items',
      query: { flowStage: 'MES_FAILED' },
      permission: 'market:delivered-item:view',
      severity: 'danger'
    },
    {
      key: 'wmsFailed',
      title: 'WMS 库存失败',
      value: 2,
      unit: '行',
      description: '进入已提零件重试库存信息获取',
      path: '/delivered-items',
      query: { flowStage: 'WMS_FAILED' },
      permission: 'market:delivered-item:view',
      severity: 'danger'
    },
    {
      key: 'mesProductionFailed',
      title: 'MES 生产下发失败',
      value: 1,
      unit: '行',
      description: '进入待产零件重试 MES 生产下发',
      path: '/production-items',
      query: { mesProductionStatus: 'FAILED' },
      permission: 'market:production-item:view',
      severity: 'danger'
    }
  ]
}
