<template>
  <div class="warehouse-container">
    <el-card shadow="never" class="warehouse-card">
      <template #header>
        <div class="card-header">
          <div>
            <h2>仓库部：库存管理</h2>
            <p>先选择客户，再维护该客户手工录入的图号、已入库库存和待产库存。</p>
          </div>
          <div class="header-actions">
            <el-select
              v-model="selectedCustomer"
              filterable
              clearable
              placeholder="选择客户名称"
              class="customer-select"
              @change="handleCustomerChange"
              @clear="handleCustomerChange"
            >
              <el-option
                v-for="customer in customerOptions"
                :key="customer"
                :label="customer"
                :value="customer"
              />
            </el-select>
            <el-input
              v-model="keyword"
              placeholder="按图号搜索"
              clearable
              :disabled="!selectedCustomer"
              class="search-input"
              @keyup.enter="loadInventory"
              @clear="loadInventory"
            />
            <el-button type="primary" :disabled="!selectedCustomer" @click="loadInventory">查询</el-button>
            <el-button type="success" :disabled="!selectedCustomer" @click="openCreateDialog">录入图号</el-button>
          </div>
        </div>
      </template>

      <el-empty v-if="!selectedCustomer" description="请先选择客户名称" />
      <el-table
        v-else
        :data="inventoryList"
        border
        stripe
        height="560"
        v-loading="loading"
        row-key="id"
      >
        <el-table-column prop="partNo" label="图号" min-width="160" fixed />
        <el-table-column prop="inStockQty" label="已入库库存" width="120" align="center" />
        <el-table-column prop="pendingQty" label="待产库存" width="120" align="center" />
        <el-table-column label="待产单号" min-width="220">
          <template #default="{ row }">
            <el-space wrap v-if="row.pendingRows && row.pendingRows.length">
              <el-tag
                v-for="pending in row.pendingRows"
                :key="pending.id || pending.pendingRefNo"
                size="small"
                type="info"
              >
                {{ pending.pendingRefNo }}：{{ pending.pendingQty }}
              </el-tag>
            </el-space>
            <span v-else class="muted">无</span>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="操作" width="210" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="openAddDialog(row)">新增库存</el-button>
            <el-button type="warning" size="small" plain @click="openModifyDialog(row)">修改库存</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createDialogVisible" title="录入图号" width="460px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="客户名称">
          <el-input v-model="selectedCustomer" disabled />
        </el-form-item>
        <el-form-item label="图号" required>
          <el-input v-model="createForm.partNo" placeholder="例如 L20100000733" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createInventory">确认录入</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="addDialogVisible" title="新增库存" width="620px">
      <div class="dialog-part-title">{{ activeRow?.partNo }}</div>
      <el-tabs v-model="addMode">
        <el-tab-pane label="直接入库" name="direct">
          <el-form :model="directForm" label-width="120px">
            <el-form-item label="入库数量" required>
              <el-input-number v-model="directForm.qty" :min="1" :precision="0" controls-position="right" class="qty-input" />
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="新增待产" name="pending">
          <el-form :model="pendingForm" label-width="120px">
            <el-form-item label="待产单号" required>
              <el-input v-model="pendingForm.pendingRefNo" placeholder="输入待产单号" />
            </el-form-item>
            <el-form-item label="待产数量" required>
              <el-input-number v-model="pendingForm.qty" :min="1" :precision="0" controls-position="right" class="qty-input" />
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="待产转入库" name="transfer">
          <el-alert
            title="选择待产单号后，转入数量必须小于等于该单号剩余待产库存。"
            type="info"
            :closable="false"
            class="notice-alert"
          />
          <el-form :model="transferForm" label-width="120px">
            <el-form-item label="待产单号" required>
              <el-select v-model="transferForm.pendingRefNo" placeholder="选择待产单号" class="full-width">
                <el-option
                  v-for="pending in pendingRows"
                  :key="pending.id || pending.pendingRefNo"
                  :label="`${pending.pendingRefNo}（剩余 ${pending.pendingQty}）`"
                  :value="pending.pendingRefNo"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="转入数量" required>
              <el-input-number
                v-model="transferForm.qty"
                :min="1"
                :max="selectedPendingQty"
                :precision="0"
                controls-position="right"
                class="qty-input"
              />
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitAddStock">确认新增</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="modifyDialogVisible" title="修改库存" width="620px">
      <div class="dialog-part-title">{{ activeRow?.partNo }}</div>
      <el-form :model="modifyForm" label-width="120px">
        <el-form-item label="已入库库存">
          <div class="inline-control-row">
            <el-input-number v-model="modifyForm.inStockQty" :min="0" :precision="0" controls-position="right" class="qty-input" />
            <el-button type="primary" plain :loading="saving" @click="submitModifyInStock">保存已入库库存</el-button>
          </div>
        </el-form-item>
        <el-divider />
        <el-form-item label="待产单号">
          <el-select
            v-model="modifyForm.pendingRefNo"
            placeholder="选择待产单号"
            class="pending-select"
            @change="syncPendingQty"
          >
            <el-option
              v-for="pending in pendingRows"
              :key="pending.id || pending.pendingRefNo"
              :label="`${pending.pendingRefNo}（当前 ${pending.pendingQty}）`"
              :value="pending.pendingRefNo"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="待产库存">
          <div class="inline-control-row">
            <el-input-number
              v-model="modifyForm.pendingQty"
              :min="0"
              :precision="0"
              controls-position="right"
              :disabled="!modifyForm.pendingRefNo"
              class="qty-input"
            />
            <el-button
              type="warning"
              plain
              :disabled="!modifyForm.pendingRefNo"
              :loading="saving"
              @click="submitModifyPending"
            >
              保存待产库存
            </el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="modifyDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { API_BASE_URL } from '../config'

const customerOptions = ref([])
const selectedCustomer = ref('')
const keyword = ref('')
const inventoryList = ref([])
const loading = ref(false)
const saving = ref(false)

const createDialogVisible = ref(false)
const addDialogVisible = ref(false)
const modifyDialogVisible = ref(false)
const activeRow = ref(null)
const pendingRows = ref([])
const addMode = ref('direct')

const createForm = reactive({ partNo: '' })
const directForm = reactive({ qty: 1 })
const pendingForm = reactive({ pendingRefNo: '', qty: 1 })
const transferForm = reactive({ pendingRefNo: '', qty: 1 })
const modifyForm = reactive({ inStockQty: 0, pendingRefNo: '', pendingQty: 0 })

const selectedPendingQty = computed(() => {
  const selected = pendingRows.value.find(row => row.pendingRefNo === transferForm.pendingRefNo)
  return selected ? selected.pendingQty : 1
})

const loadCustomers = async () => {
  try {
    const response = await axios.get(`${API_BASE_URL}/api/warehouse/customers`)
    customerOptions.value = response.data || []
  } catch (error) {
    console.error(error)
    ElMessage.error('客户列表加载失败，请检查后端服务。')
  }
}

const loadInventory = async () => {
  if (!selectedCustomer.value) {
    inventoryList.value = []
    return
  }
  loading.value = true
  try {
    const response = await axios.get(`${API_BASE_URL}/api/warehouse/inventory`, {
      params: {
        customerName: selectedCustomer.value,
        keyword: keyword.value
      }
    })
    inventoryList.value = response.data || []
  } catch (error) {
    console.error(error)
    ElMessage.error('库存列表加载失败，请检查后端服务。')
  } finally {
    loading.value = false
  }
}

const handleCustomerChange = async () => {
  keyword.value = ''
  inventoryList.value = []
  await loadInventory()
}

const openCreateDialog = () => {
  createForm.partNo = ''
  createDialogVisible.value = true
}

const createInventory = async () => {
  if (!selectedCustomer.value) return ElMessage.warning('请先选择客户名称。')
  if (!createForm.partNo.trim()) return ElMessage.warning('请填写图号。')
  saving.value = true
  try {
    const response = await axios.post(`${API_BASE_URL}/api/warehouse/inventory`, {
      customerName: selectedCustomer.value,
      partNo: createForm.partNo
    })
    if (response.data.success) {
      ElMessage.success(response.data.message)
      createDialogVisible.value = false
      await loadInventory()
      await loadCustomers()
    } else {
      ElMessage.error(response.data.message || '录入失败。')
    }
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || '录入请求失败。')
  } finally {
    saving.value = false
  }
}

const openAddDialog = async (row) => {
  activeRow.value = row
  addMode.value = 'direct'
  Object.assign(directForm, { qty: 1 })
  Object.assign(pendingForm, { pendingRefNo: '', qty: 1 })
  Object.assign(transferForm, { pendingRefNo: '', qty: 1 })
  await loadPendingRows(row.id)
  addDialogVisible.value = true
}

const openModifyDialog = async (row) => {
  activeRow.value = row
  await loadPendingRows(row.id)
  Object.assign(modifyForm, {
    inStockQty: row.inStockQty || 0,
    pendingRefNo: pendingRows.value[0]?.pendingRefNo || '',
    pendingQty: pendingRows.value[0]?.pendingQty || 0
  })
  modifyDialogVisible.value = true
}

const loadPendingRows = async (inventoryId) => {
  if (!inventoryId) {
    pendingRows.value = []
    return
  }
  try {
    const response = await axios.get(`${API_BASE_URL}/api/warehouse/inventory/${inventoryId}/pending`)
    pendingRows.value = response.data || []
  } catch (error) {
    console.error(error)
    pendingRows.value = []
    ElMessage.error('待产单号加载失败。')
  }
}

const submitAddStock = async () => {
  if (!activeRow.value) return
  saving.value = true
  try {
    let response
    if (addMode.value === 'pending') {
      if (!pendingForm.pendingRefNo.trim()) {
        saving.value = false
        return ElMessage.warning('请填写待产单号。')
      }
      response = await axios.post(`${API_BASE_URL}/api/warehouse/inventory/add-stock`, {
        inventoryId: activeRow.value.id,
        stockType: 'PENDING',
        qty: pendingForm.qty,
        pendingRefNo: pendingForm.pendingRefNo
      })
    } else if (addMode.value === 'transfer') {
      if (!transferForm.pendingRefNo) {
        saving.value = false
        return ElMessage.warning('请选择待产单号。')
      }
      if (transferForm.qty > selectedPendingQty.value) {
        saving.value = false
        return ElMessage.warning('转入数量不能超过该待产单号的待产库存。')
      }
      response = await axios.post(`${API_BASE_URL}/api/warehouse/inventory/transfer-pending`, {
        inventoryId: activeRow.value.id,
        pendingRefNo: transferForm.pendingRefNo,
        qty: transferForm.qty
      })
    } else {
      response = await axios.post(`${API_BASE_URL}/api/warehouse/inventory/add-stock`, {
        inventoryId: activeRow.value.id,
        stockType: 'IN_STOCK',
        qty: directForm.qty
      })
    }

    if (response.data.success) {
      ElMessage.success(response.data.message)
      addDialogVisible.value = false
      await loadInventory()
    } else {
      ElMessage.error(response.data.message || '新增库存失败。')
    }
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || '新增库存请求失败。')
  } finally {
    saving.value = false
  }
}

const syncPendingQty = () => {
  const selected = pendingRows.value.find(row => row.pendingRefNo === modifyForm.pendingRefNo)
  modifyForm.pendingQty = selected ? selected.pendingQty : 0
}

const submitModifyInStock = async () => {
  if (!activeRow.value) return
  saving.value = true
  try {
    const response = await axios.post(`${API_BASE_URL}/api/warehouse/inventory/modify`, {
      inventoryId: activeRow.value.id,
      inStockQty: modifyForm.inStockQty
    })
    if (response.data.success) {
      ElMessage.success(response.data.message)
      await refreshActiveAfterModify()
    } else {
      ElMessage.error(response.data.message || '修改失败。')
    }
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || '修改请求失败。')
  } finally {
    saving.value = false
  }
}

const submitModifyPending = async () => {
  if (!activeRow.value) return
  if (!modifyForm.pendingRefNo) return ElMessage.warning('请选择待产单号。')
  saving.value = true
  try {
    const response = await axios.post(`${API_BASE_URL}/api/warehouse/inventory/modify`, {
      inventoryId: activeRow.value.id,
      pendingRefNo: modifyForm.pendingRefNo,
      pendingQty: modifyForm.pendingQty
    })
    if (response.data.success) {
      ElMessage.success(response.data.message)
      await refreshActiveAfterModify()
    } else {
      ElMessage.error(response.data.message || '修改失败。')
    }
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || '修改请求失败。')
  } finally {
    saving.value = false
  }
}

const refreshActiveAfterModify = async () => {
  await loadInventory()
  const updated = inventoryList.value.find(row => row.id === activeRow.value?.id)
  if (updated) {
    activeRow.value = updated
    await loadPendingRows(updated.id)
    modifyForm.inStockQty = updated.inStockQty || 0
    if (modifyForm.pendingRefNo) {
      syncPendingQty()
    }
  }
}

onMounted(async () => {
  await loadCustomers()
})
</script>

<style scoped>
.warehouse-container {
  padding: 0;
}

.warehouse-card {
  border: 1px solid var(--erp-border-soft);
  border-radius: 8px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.card-header h2 {
  margin: 0;
  color: var(--erp-text);
  font-size: 20px;
  line-height: 1.3;
}

.card-header p {
  margin: 6px 0 0;
  color: var(--erp-text-secondary);
  font-size: 13px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.customer-select {
  width: 220px;
}

.search-input {
  width: 200px;
}

.full-width {
  width: 100%;
}

.qty-input {
  width: 180px;
}

.notice-alert {
  margin-bottom: 14px;
}

.muted {
  color: var(--erp-text-secondary);
}

.dialog-part-title {
  margin-bottom: 12px;
  padding-left: 10px;
  border-left: 3px solid var(--erp-primary);
  font-weight: 700;
  color: var(--erp-text);
}

.inline-control-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.pending-select {
  width: 260px;
}

.warehouse-card :deep(.el-table) {
  margin-top: 6px;
}

@media (max-width: 1200px) {
  .card-header {
    flex-direction: column;
    align-items: stretch;
  }

  .header-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 760px) {
  .customer-select,
  .search-input,
  .pending-select,
  .qty-input {
    width: 100%;
  }
}
</style>
