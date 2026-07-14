<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useInventoryStore } from '../stores/inventoryStore'
import { field, number } from '../helpers'
import ApsPager from '../components/ApsPager.vue'
import { confirmAction } from '../utils/confirm'

const loading = ref(false)
const error = ref('')
const notice = ref('')
const inventoryStore = useInventoryStore()
const {
  partCustomers: customers,
  partInventory,
  partPage: page,
  partPageSize: pageSize,
  partTotal: total,
  activePartRow: activeRow,
  partPendingRows: pendingRows
} = storeToRefs(inventoryStore)
const selectedCustomer = ref('')
const keyword = ref('')
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
  await inventoryStore.loadPartCustomers()
}

const loadInventory = async () => {
  if (!selectedCustomer.value) {
    inventoryStore.partInventory = []
    inventoryStore.partTotal = 0
    return
  }
  await inventoryStore.loadPartInventoryPage({
    customerName: selectedCustomer.value,
    keyword: keyword.value
  })
}

const loadData = async () => {
  await Promise.all([loadCustomers(), loadInventory()])
}

const selectCustomer = async () => {
  page.value = 1
  inventoryStore.clearActivePartRow()
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
  if (!createForm.partNo.trim()) {
    error.value = '请输入图号'
    return
  }
  await run(async () => {
    try {
      await inventoryStore.createPartInventory({
        customerName: selectedCustomer.value,
        partNo: createForm.partNo.trim(),
        version: createForm.version.trim(),
        inStockQty: 0,
        inProductionQty: 0,
        remark: createForm.remark.trim()
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
  inventoryStore.setActivePartRow(row)
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
    inventoryStore.partPendingRows = []
    return
  }
  await inventoryStore.loadPartPending(activeRow.value.id)
}

const refreshActiveRow = async () => {
  await inventoryStore.refreshActivePartRow()
}

const submitAddStock = async () => {
  if (!activeRow.value) return
  if (stockMode.value === 'PENDING' && !stockForm.pendingRefNo.trim()) {
    error.value = '请输入在产单号'
    return
  }
  if (Number(stockForm.qty || 0) <= 0) {
    error.value = '请输入大于 0 的数量'
    return
  }
  await run(async () => {
    await inventoryStore.addPartStock({
      inventoryId: activeRow.value.id,
      stockType: stockMode.value,
      qty: Number(stockForm.qty || 0),
      pendingRefNo: stockForm.pendingRefNo.trim()
    })
    Object.assign(stockForm, { qty: 1, pendingRefNo: '' })
    await refreshActiveRow()
  }, stockMode.value === 'PENDING' ? '在产库存已新增' : '已入库库存已新增')
}

const submitTransfer = async () => {
  if (!activeRow.value || !transferForm.pendingRefNo) return
  if (Number(transferForm.qty || 0) <= 0) {
    error.value = '请输入大于 0 的转入数量'
    return
  }
  await run(async () => {
    await inventoryStore.transferPartPending({
      inventoryId: activeRow.value.id,
      pendingRefNo: transferForm.pendingRefNo,
      qty: Number(transferForm.qty || 0)
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
  if (Number(modifyForm.inStockQty || 0) < 0) {
    error.value = '已入库库存不能小于 0'
    return
  }
  await run(async () => {
    await inventoryStore.modifyPartInventory({
      inventoryId: activeRow.value.id,
      inStockQty: Number(modifyForm.inStockQty || 0)
    })
    await refreshActiveRow()
  }, '已入库库存已修改')
}

const submitModifyPending = async () => {
  if (!activeRow.value || !modifyForm.pendingRefNo) return
  if (Number(modifyForm.pendingQty || 0) < 0) {
    error.value = '在产库存不能小于 0'
    return
  }
  await run(async () => {
    await inventoryStore.modifyPartInventory({
      inventoryId: activeRow.value.id,
      pendingRefNo: modifyForm.pendingRefNo,
      pendingQty: Number(modifyForm.pendingQty || 0)
    })
    await refreshActiveRow()
  }, '在产库存已修改')
}

const remove = async (id) => {
  if (!await confirmAction('\u786e\u8ba4\u5220\u9664\u8fd9\u6761\u96f6\u4ef6\u5e93\u5b58\uff1f\u5df2\u9501\u5b9a\u5e93\u5b58\u4e0d\u80fd\u5220\u9664\u3002')) return
  await run(async () => {
    await inventoryStore.deletePartInventory(id)
    await Promise.all([loadInventory(), loadCustomers()])
  }, '零件库存已删除')
}

const formatPart = (row) => row ? `${field(row, 'part_no') || '-'} / ${field(row, 'version') || '-'}` : '未选择'

const goPage = (nextPage) => {
  page.value = nextPage
}

const changePageSize = (nextSize) => {
  pageSize.value = nextSize
  page.value = 1
}

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
          <el-select v-model="selectedCustomer" class="control-short" placeholder="先选择客户" clearable filterable @change="selectCustomer">
            <el-option v-for="customer in customers" :key="customer" :label="customer" :value="customer" />
          </el-select>
          <el-input v-model="keyword" class="control-short" placeholder="图号或版本" :disabled="!selectedCustomer" clearable @keyup.enter="searchInventory" />
          <el-button type="primary" :disabled="loading || !selectedCustomer" @click="searchInventory">查询</el-button>
          <el-button :disabled="loading" @click="run(loadData)">刷新</el-button>
        </div>
      </div>

      <p v-if="!selectedCustomer" class="empty-state">请先选择客户，再维护该客户的图号版本、已入库库存和在产库存。</p>
      <template v-else>
        <form class="form-grid" @submit.prevent="createInventory">
          <label>图号<el-input v-model="createForm.partNo" placeholder="必填" /></label>
          <label>版本<el-input v-model="createForm.version" /></label>
          <label>备注<el-input v-model="createForm.remark" /></label>
          <el-button type="primary" native-type="submit" :disabled="loading">录入图号版本</el-button>
        </form>

        <el-table :data="partInventory" border stripe class="aps-data-table inventory-table" empty-text="该客户暂无零件库存" row-key="id">
          <el-table-column label="图号" min-width="150">
            <template #default="{ row }">
              <strong>{{ field(row, 'part_no') }}</strong>
            </template>
          </el-table-column>
          <el-table-column label="版本" min-width="90">
            <template #default="{ row }">{{ field(row, 'version') || '-' }}</template>
          </el-table-column>
          <el-table-column label="已入库" min-width="90">
            <template #default="{ row }">{{ number(field(row, 'in_stock_qty'), 0) }}</template>
          </el-table-column>
          <el-table-column label="在产" min-width="90">
            <template #default="{ row }">{{ number(field(row, 'in_production_qty'), 0) }}</template>
          </el-table-column>
          <el-table-column label="入库锁定" min-width="100">
            <template #default="{ row }">{{ number(field(row, 'locked_in_stock_qty'), 0) }}</template>
          </el-table-column>
          <el-table-column label="在产锁定" min-width="100">
            <template #default="{ row }">{{ number(field(row, 'locked_in_production_qty'), 0) }}</template>
          </el-table-column>
          <el-table-column label="可用入库" min-width="100">
            <template #default="{ row }">{{ number(field(row, 'available_in_stock_qty'), 0) }}</template>
          </el-table-column>
          <el-table-column label="可用在产" min-width="100">
            <template #default="{ row }">{{ number(field(row, 'available_in_production_qty'), 0) }}</template>
          </el-table-column>
          <el-table-column label="在产单号" min-width="210">
            <template #default="{ row }">
              <span v-if="!row.pendingRows?.length" class="muted">无</span>
              <el-tag v-for="pending in row.pendingRows" v-else :key="pending.id" class="mini-tag" type="info" effect="plain">
                {{ pending.pending_ref_no }}：{{ number(pending.pending_qty, 0) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="118" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button type="primary" link @click="setActiveRow(row)">维护</el-button>
                <el-button type="danger" link @click="remove(row.id)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <ApsPager
          :disabled="loading"
          :page="page"
          :page-size="pageSize"
          :total="total"
          @current-change="goPage"
          @size-change="changePageSize"
        />
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
            <el-select v-model="stockMode">
              <el-option label="直接入库" value="IN_STOCK" />
              <el-option label="新增在产" value="PENDING" />
            </el-select>
          </label>
          <label v-if="stockMode === 'PENDING'">在产单号<el-input v-model="stockForm.pendingRefNo" /></label>
          <label>数量<el-input-number v-model="stockForm.qty" :min="1" :precision="0" :step="1" controls-position="right" /></label>
          <el-button type="primary" native-type="submit" :disabled="loading">确认新增</el-button>
        </form>

        <form class="action-box" @submit.prevent="submitTransfer">
          <h3>在产转入库</h3>
          <label>在产单号
            <el-select v-model="transferForm.pendingRefNo" placeholder="选择在产单号" clearable>
              <el-option
                v-for="pending in pendingRows"
                :key="pending.id"
                :label="`${pending.pending_ref_no} / 可用 ${number(pending.available_qty, 0)}`"
                :value="pending.pending_ref_no"
              />
            </el-select>
          </label>
          <label>转入数量<el-input-number v-model="transferForm.qty" :min="1" :max="selectedPendingQty" :precision="0" :step="1" controls-position="right" /></label>
          <el-button type="primary" native-type="submit" :disabled="loading || !transferForm.pendingRefNo">确认转入</el-button>
        </form>

        <form class="action-box" @submit.prevent="submitModifyInStock">
          <h3>修改库存</h3>
          <label>已入库库存<el-input-number v-model="modifyForm.inStockQty" :min="0" :precision="0" :step="1" controls-position="right" /></label>
          <el-button native-type="submit" :disabled="loading">保存已入库</el-button>
          <label>在产单号
            <el-select v-model="modifyForm.pendingRefNo" placeholder="选择在产单号" clearable @change="syncPendingQty">
              <el-option
                v-for="pending in pendingRows"
                :key="pending.id"
                :label="`${pending.pending_ref_no} / 当前 ${number(pending.pending_qty, 0)}`"
                :value="pending.pending_ref_no"
              />
            </el-select>
          </label>
          <label>在产库存<el-input-number v-model="modifyForm.pendingQty" :min="0" :precision="0" :step="1" controls-position="right" :disabled="!modifyForm.pendingRefNo" /></label>
          <el-button type="primary" plain :disabled="loading || !modifyForm.pendingRefNo" @click="submitModifyPending">保存在产</el-button>
        </form>
      </div>
    </section>
  </section>
</template>
