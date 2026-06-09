<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { request } from '../api'
import { field, number } from '../helpers'

const loading = ref(false)
const error = ref('')
const notice = ref('')
const customers = ref([])
const selectedCustomer = ref('')
const keyword = ref('')
const partInventory = ref([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const activeRow = ref(null)
const pendingRows = ref([])
const stockMode = ref('IN_STOCK')

const createForm = reactive({ partNo: '', version: '', remark: '' })
const stockForm = reactive({ qty: 1, pendingRefNo: '' })
const transferForm = reactive({ pendingRefNo: '', qty: 1 })
const modifyForm = reactive({ inStockQty: 0, pendingRefNo: '', pendingQty: 0 })

const selectedPendingQty = computed(() => {
  const selected = pendingRows.value.find((row) => row.pending_ref_no === transferForm.pendingRefNo)
  return selected ? Number(selected.available_qty || 0) : 1
})

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

const loadCustomers = async () => {
  customers.value = await request('/api/aps/inventory/parts/customers')
}

const loadInventory = async () => {
  if (!selectedCustomer.value) {
    partInventory.value = []
    total.value = 0
    return
  }
  const query = new URLSearchParams({
    customerName: selectedCustomer.value,
    page: String(page.value),
    pageSize: String(pageSize.value)
  })
  if (keyword.value) query.set('keyword', keyword.value)
  const result = await request(`/api/aps/inventory/parts/page?${query}`)
  partInventory.value = result.records || []
  total.value = result.total || 0
}

const loadData = async () => {
  await Promise.all([loadCustomers(), loadInventory()])
}

const selectCustomer = async () => {
  page.value = 1
  activeRow.value = null
  pendingRows.value = []
  await run(loadInventory)
}

const searchInventory = async () => {
  page.value = 1
  await run(loadInventory)
}

const createInventory = async () => {
  if (!selectedCustomer.value) {
    error.value = '请先选择客户'
    return
  }
  await run(async () => {
    try {
      await request('/api/aps/inventory/parts', {
        method: 'POST',
        body: JSON.stringify({
          customerName: selectedCustomer.value,
          partNo: createForm.partNo,
          version: createForm.version,
          inStockQty: 0,
          inProductionQty: 0,
          remark: createForm.remark
        })
      })
    } catch (err) {
      if (err?.status === 409) {
        throw new Error('该客户下的零件图号/版本已存在，请直接修改库存数量。')
      }
      throw err
    }
    Object.assign(createForm, { partNo: '', version: '', remark: '' })
    await Promise.all([loadInventory(), loadCustomers()])
  }, '零件图号版本已录入')
}

const setActiveRow = async (row) => {
  activeRow.value = row
  stockMode.value = 'IN_STOCK'
  Object.assign(stockForm, { qty: 1, pendingRefNo: '' })
  Object.assign(transferForm, { pendingRefNo: '', qty: 1 })
  await refreshPendingRows()
  Object.assign(modifyForm, {
    inStockQty: Number(field(row, 'in_stock_qty') || 0),
    pendingRefNo: pendingRows.value[0]?.pending_ref_no || '',
    pendingQty: Number(pendingRows.value[0]?.pending_qty || 0)
  })
}

const refreshPendingRows = async () => {
  if (!activeRow.value?.id) {
    pendingRows.value = []
    return
  }
  pendingRows.value = await request(`/api/aps/inventory/parts/${activeRow.value.id}/pending`)
}

const refreshActiveRow = async () => {
  await loadInventory()
  const updated = partInventory.value.find((row) => row.id === activeRow.value?.id)
  if (updated) {
    activeRow.value = updated
    await refreshPendingRows()
  }
}

const submitAddStock = async () => {
  if (!activeRow.value) return
  await run(async () => {
    await request('/api/aps/inventory/parts/add-stock', {
      method: 'POST',
      body: JSON.stringify({
        inventoryId: activeRow.value.id,
        stockType: stockMode.value,
        qty: Number(stockForm.qty || 0),
        pendingRefNo: stockForm.pendingRefNo
      })
    })
    Object.assign(stockForm, { qty: 1, pendingRefNo: '' })
    await refreshActiveRow()
  }, stockMode.value === 'PENDING' ? '在产库存已新增' : '已入库库存已新增')
}

const submitTransfer = async () => {
  if (!activeRow.value || !transferForm.pendingRefNo) return
  await run(async () => {
    await request('/api/aps/inventory/parts/transfer-pending', {
      method: 'POST',
      body: JSON.stringify({
        inventoryId: activeRow.value.id,
        pendingRefNo: transferForm.pendingRefNo,
        qty: Number(transferForm.qty || 0)
      })
    })
    Object.assign(transferForm, { pendingRefNo: '', qty: 1 })
    await refreshActiveRow()
  }, '在产库存已转入已入库')
}

const syncPendingQty = () => {
  const selected = pendingRows.value.find((row) => row.pending_ref_no === modifyForm.pendingRefNo)
  modifyForm.pendingQty = selected ? Number(selected.pending_qty || 0) : 0
}

const submitModifyInStock = async () => {
  if (!activeRow.value) return
  await run(async () => {
    await request('/api/aps/inventory/parts/modify', {
      method: 'POST',
      body: JSON.stringify({
        inventoryId: activeRow.value.id,
        inStockQty: Number(modifyForm.inStockQty || 0)
      })
    })
    await refreshActiveRow()
  }, '已入库库存已修改')
}

const submitModifyPending = async () => {
  if (!activeRow.value || !modifyForm.pendingRefNo) return
  await run(async () => {
    await request('/api/aps/inventory/parts/modify', {
      method: 'POST',
      body: JSON.stringify({
        inventoryId: activeRow.value.id,
        pendingRefNo: modifyForm.pendingRefNo,
        pendingQty: Number(modifyForm.pendingQty || 0)
      })
    })
    await refreshActiveRow()
  }, '在产库存已修改')
}

const remove = async (id) => {
  if (!window.confirm('确认删除这条零件库存？已锁定库存不能删除。')) return
  await run(async () => {
    await request(`/api/aps/inventory/parts/${id}`, { method: 'DELETE' })
    if (activeRow.value?.id === id) {
      activeRow.value = null
      pendingRows.value = []
    }
    await Promise.all([loadInventory(), loadCustomers()])
  }, '零件库存已删除')
}

const formatPart = (row) => row ? `${field(row, 'part_no') || '-'} / ${field(row, 'version') || '-'}` : '未选择'

onMounted(() => run(loadData))

watch([page, pageSize], () => {
  if (selectedCustomer.value) {
    run(loadInventory)
  }
})
</script>

<template>
  <section class="content-grid">
    <p v-if="error" class="message error">{{ error }}</p>
    <p v-else-if="notice" class="message notice">{{ notice }}</p>

    <section class="panel">
      <div class="section-head">
        <h2>零件库存维护</h2>
        <div class="section-actions">
          <select v-model="selectedCustomer" class="control-short" @change="selectCustomer">
            <option value="">先选择客户</option>
            <option v-for="customer in customers" :key="customer" :value="customer">{{ customer }}</option>
          </select>
          <input v-model="keyword" class="control-short" placeholder="图号或版本" :disabled="!selectedCustomer" @keyup.enter="searchInventory" />
          <button class="secondary" type="button" :disabled="loading || !selectedCustomer" @click="searchInventory">查询</button>
          <button class="secondary" type="button" :disabled="loading" @click="run(loadData)">刷新</button>
        </div>
      </div>

      <p v-if="!selectedCustomer" class="empty-state">请先选择客户，再维护该客户的图号版本、已入库库存和在产库存。</p>
      <template v-else>
        <form class="form-grid" @submit.prevent="createInventory">
          <label>图号<input v-model="createForm.partNo" required /></label>
          <label>版本<input v-model="createForm.version" /></label>
          <label>备注<input v-model="createForm.remark" /></label>
          <button class="primary" type="submit" :disabled="loading">录入图号版本</button>
        </form>

        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>图号</th>
                <th>版本</th>
                <th>已入库</th>
                <th>在产</th>
                <th>入库锁定</th>
                <th>在产锁定</th>
                <th>可用入库</th>
                <th>可用在产</th>
                <th>在产单号</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in partInventory" :key="row.id">
                <td><strong>{{ field(row, 'part_no') }}</strong></td>
                <td>{{ field(row, 'version') || '-' }}</td>
                <td>{{ number(field(row, 'in_stock_qty'), 0) }}</td>
                <td>{{ number(field(row, 'in_production_qty'), 0) }}</td>
                <td>{{ number(field(row, 'locked_in_stock_qty'), 0) }}</td>
                <td>{{ number(field(row, 'locked_in_production_qty'), 0) }}</td>
                <td>{{ number(field(row, 'available_in_stock_qty'), 0) }}</td>
                <td>{{ number(field(row, 'available_in_production_qty'), 0) }}</td>
                <td>
                  <span v-if="!row.pendingRows?.length" class="muted">无</span>
                  <span v-for="pending in row.pendingRows" v-else :key="pending.id" class="mini-tag">
                    {{ pending.pending_ref_no }}：{{ number(pending.pending_qty, 0) }}
                  </span>
                </td>
                <td>
                  <button class="text-button" type="button" @click="setActiveRow(row)">维护</button>
                  <button class="text-button danger" type="button" @click="remove(row.id)">删除</button>
                </td>
              </tr>
              <tr v-if="partInventory.length === 0">
                <td class="empty" colspan="10">该客户暂无零件库存</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="pager">
          <span>共 {{ total }} 条</span>
          <button class="secondary" type="button" :disabled="page <= 1 || loading" @click="page--">上一页</button>
          <span>第 {{ page }} 页</span>
          <button class="secondary" type="button" :disabled="page * pageSize >= total || loading" @click="page++">下一页</button>
          <select v-model.number="pageSize" class="page-size">
            <option :value="20">20 条</option>
            <option :value="50">50 条</option>
            <option :value="100">100 条</option>
          </select>
        </div>
      </template>
    </section>

    <section v-if="activeRow" class="panel">
      <div class="section-head">
        <h2>库存操作</h2>
        <span>{{ formatPart(activeRow) }}</span>
      </div>
      <div class="inventory-actions">
        <form class="action-box" @submit.prevent="submitAddStock">
          <h3>新增库存</h3>
          <label>类型
            <select v-model="stockMode">
              <option value="IN_STOCK">直接入库</option>
              <option value="PENDING">新增在产</option>
            </select>
          </label>
          <label v-if="stockMode === 'PENDING'">在产单号<input v-model="stockForm.pendingRefNo" required /></label>
          <label>数量<input v-model.number="stockForm.qty" min="1" type="number" /></label>
          <button class="primary" type="submit" :disabled="loading">确认新增</button>
        </form>

        <form class="action-box" @submit.prevent="submitTransfer">
          <h3>在产转入库</h3>
          <label>在产单号
            <select v-model="transferForm.pendingRefNo" required>
              <option value="">选择在产单号</option>
              <option v-for="pending in pendingRows" :key="pending.id" :value="pending.pending_ref_no">
                {{ pending.pending_ref_no }} / 可用 {{ number(pending.available_qty, 0) }}
              </option>
            </select>
          </label>
          <label>转入数量<input v-model.number="transferForm.qty" min="1" :max="selectedPendingQty" type="number" /></label>
          <button class="primary" type="submit" :disabled="loading || !transferForm.pendingRefNo">确认转入</button>
        </form>

        <form class="action-box" @submit.prevent="submitModifyInStock">
          <h3>修改库存</h3>
          <label>已入库库存<input v-model.number="modifyForm.inStockQty" min="0" type="number" /></label>
          <button class="secondary" type="submit" :disabled="loading">保存已入库</button>
          <label>在产单号
            <select v-model="modifyForm.pendingRefNo" @change="syncPendingQty">
              <option value="">选择在产单号</option>
              <option v-for="pending in pendingRows" :key="pending.id" :value="pending.pending_ref_no">
                {{ pending.pending_ref_no }} / 当前 {{ number(pending.pending_qty, 0) }}
              </option>
            </select>
          </label>
          <label>在产库存<input v-model.number="modifyForm.pendingQty" min="0" type="number" :disabled="!modifyForm.pendingRefNo" /></label>
          <button class="secondary" type="button" :disabled="loading || !modifyForm.pendingRefNo" @click="submitModifyPending">保存在产</button>
        </form>
      </div>
    </section>
  </section>
</template>
