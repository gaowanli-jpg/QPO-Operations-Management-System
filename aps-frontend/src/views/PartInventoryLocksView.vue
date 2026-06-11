<script setup>
import { onMounted, ref } from 'vue'
import { apiUrl, request } from '../api'
import DataTable from '../components/DataTable.vue'

const loading = ref(false)
const error = ref('')
const notice = ref('')
const rows = ref([])
const customerOptions = ref([])
const customerName = ref('')

const tableColumns = [
  ['work_item_id', '任务'],
  ['internal_project_no', '内部项目号'],
  ['customer_order_no', '客户订单号'],
  ['customer_project_no', '客户项目号'],
  ['customer_name', '客户'],
  ['part_no', '图号'],
  ['version', '版本'],
  ['lock_type_text', '锁定库存'],
  ['pending_ref_no', '在产单号'],
  ['locked_qty', '锁定数量']
]

const run = async (fn, successMessage = '') => {
  loading.value = true
  error.value = ''
  notice.value = ''
  try {
    await fn()
    if (successMessage) notice.value = successMessage
  } catch (err) {
    error.value = err.message || '操作失败'
  } finally {
    loading.value = false
  }
}

const loadData = async () => {
  const params = new URLSearchParams({ limit: '200' })
  if (customerName.value) params.set('customerName', customerName.value)
  rows.value = await request(`/api/aps/inventory/parts/locks?${params.toString()}`)
}

const loadCustomers = async () => {
  customerOptions.value = await request('/api/aps/inventory/parts/customers')
}

const confirmLock = async (id) => {
  if (!id) return
  await run(async () => {
    await request(`/api/aps/inventory/parts/locks/${id}/confirm`, { method: 'POST' })
    await loadData()
  }, '锁定已确认')
}

const releaseLock = async (id) => {
  if (!id) return
  if (!window.confirm('确认释放这一条零件库存锁定？释放后库存会回滚，需生产数量会增加。')) return
  await run(async () => {
    await request(`/api/aps/inventory/parts/locks/${id}/release`, { method: 'POST' })
    await loadData()
  }, '锁定已释放')
}

const handleLockAction = async (action, row) => {
  if (!row?.id) return
  if (action === 'confirm') {
    await confirmLock(row.id)
    return
  }
  if (action === 'release') {
    await releaseLock(row.id)
  }
}

const clearCustomer = async () => {
  customerName.value = ''
  await run(loadData)
}

const exportData = async () => {
  loading.value = true
  error.value = ''
  notice.value = ''
  try {
    const params = new URLSearchParams({ limit: '200' })
    if (customerName.value) params.set('customerName', customerName.value)
    const response = await fetch(apiUrl(`/api/aps/inventory/parts/locks/export?${params.toString()}`))
    if (!response.ok) {
      const text = await response.text()
      throw new Error(text || `导出失败：${response.status}`)
    }
    const blob = await response.blob()
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = exportFileName(response.headers.get('Content-Disposition'))
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(link.href)
    notice.value = '导出已生成'
  } catch (err) {
    error.value = err.message || '导出失败'
  } finally {
    loading.value = false
  }
}

const exportFileName = (contentDisposition) => {
  const header = contentDisposition || ''
  const encodedMatch = header.match(/filename\*=UTF-8''([^;]+)/)
  if (encodedMatch?.[1]) return decodeURIComponent(encodedMatch[1])
  const plainMatch = header.match(/filename="?([^";]+)"?/)
  if (plainMatch?.[1]) return plainMatch[1]
  return '零件锁定记录.xlsx'
}

const printData = () => {
  error.value = ''
  notice.value = ''
  const printWindow = window.open('', '_blank')
  if (!printWindow) {
    error.value = '浏览器阻止了打印窗口，请允许弹窗后重试'
    return
  }

  printWindow.document.write(printDocumentHtml())
  printWindow.document.close()
  printWindow.focus()
  printWindow.onafterprint = () => printWindow.close()
  window.setTimeout(() => printWindow.print(), 100)
  notice.value = '打印窗口已打开'
}

const printDocumentHtml = () => {
  const printedAt = new Date().toLocaleString('zh-CN', { hour12: false })
  const customer = customerName.value || '全部客户'
  const headHtml = tableColumns
    .map(([, title]) => `<th>${escapeHtml(title)}</th>`)
    .join('')
  const bodyHtml = rows.value.length
    ? rows.value.map(printRowHtml).join('')
    : `<tr><td class="empty" colspan="${tableColumns.length}">暂无数据</td></tr>`

  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <title>零件锁定记录</title>
  <style>
    @page { size: A4 landscape; margin: 10mm; }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      color: #111827;
      font-family: "Microsoft YaHei", "Microsoft JhengHei", Arial, sans-serif;
      font-size: 12px;
    }
    .header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 18px;
      margin-bottom: 12px;
    }
    h1 {
      margin: 0 0 7px;
      font-size: 20px;
      line-height: 1.2;
    }
    .meta {
      display: flex;
      flex-wrap: wrap;
      gap: 8px 18px;
      color: #4b5563;
    }
    .count {
      color: #111827;
      font-weight: 700;
      white-space: nowrap;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      table-layout: fixed;
    }
    th,
    td {
      border: 1px solid #9ca3af;
      padding: 6px 7px;
      text-align: left;
      vertical-align: top;
      word-break: break-word;
    }
    th {
      background: #f3f4f6;
      font-weight: 700;
    }
    th:nth-child(1), td:nth-child(1) { width: 48px; }
    th:nth-child(2), td:nth-child(2) { width: 104px; }
    th:nth-child(3), td:nth-child(3) { width: 104px; }
    th:nth-child(4), td:nth-child(4) { width: 104px; }
    th:nth-child(5), td:nth-child(5) { width: 96px; }
    th:nth-child(7), td:nth-child(7) { width: 56px; }
    th:nth-child(8), td:nth-child(8) { width: 108px; }
    th:nth-child(10), td:nth-child(10) { width: 72px; }
    .empty {
      height: 42px;
      text-align: center;
      color: #6b7280;
    }
    @media print {
      body {
        -webkit-print-color-adjust: exact;
        print-color-adjust: exact;
      }
    }
  </style>
</head>
<body>
  <div class="header">
    <div>
      <h1>零件锁定记录</h1>
      <div class="meta">
        <span>客户：${escapeHtml(customer)}</span>
        <span>打印时间：${escapeHtml(printedAt)}</span>
      </div>
    </div>
    <div class="count">${rows.value.length} 条</div>
  </div>
  <table>
    <thead><tr>${headHtml}</tr></thead>
    <tbody>${bodyHtml}</tbody>
  </table>
</body>
</html>`
}

const printRowHtml = (row) => {
  const cells = tableColumns
    .map(([key]) => `<td>${escapeHtml(printCellValue(row, key))}</td>`)
    .join('')
  return `<tr>${cells}</tr>`
}

const printCellValue = (row, key) => {
  const value = row?.[key]
  if (key === 'customer_project_no' && (value === undefined || value === null || value === '')) return '空'
  if (value === undefined || value === null || value === '') return ''
  return String(value)
}

const escapeHtml = (value) => String(value)
  .replaceAll('&', '&amp;')
  .replaceAll('<', '&lt;')
  .replaceAll('>', '&gt;')
  .replaceAll('"', '&quot;')
  .replaceAll("'", '&#39;')

onMounted(() => run(() => Promise.all([loadCustomers(), loadData()])))
</script>

<template>
  <section class="content-grid">
    <section class="panel lock-filter-panel">
      <div class="lock-filter">
        <label class="lock-filter-field">
          客户
          <el-select v-model="customerName" placeholder="全部客户" clearable filterable @change="run(loadData)">
            <el-option v-for="customer in customerOptions" :key="customer" :label="customer" :value="customer" />
          </el-select>
        </label>
        <div class="lock-filter-actions">
          <el-button type="primary" :disabled="loading" @click="run(loadData)">查询</el-button>
          <el-button :disabled="loading || !customerName" @click="clearCustomer">清空</el-button>
          <el-button :disabled="loading" @click="run(loadData)">刷新</el-button>
          <el-button :disabled="loading" @click="exportData">导出当前表格</el-button>
          <el-button :disabled="loading" @click="printData">打印当前表格</el-button>
        </div>
      </div>
    </section>
    <p v-if="error" class="message error">{{ error }}</p>
    <p v-else-if="notice" class="message notice">{{ notice }}</p>
    <DataTable title="零件锁定记录" :rows="rows" :actions="[
      ['confirm', '已锁定', 'confirm'],
      ['release', '释放', 'danger']
    ]" :columns="tableColumns" @row-action="handleLockAction" />
  </section>
</template>
