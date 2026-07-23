<template>
  <div class="erp-page delivery-document-page">
    <header class="erp-page-header delivery-document-page-header">
      <div>
        <h1 class="erp-page-title">提单表据</h1>
        <p class="erp-page-description">选择已提单订单，查看并导出已生成提单行和工艺表行。</p>
      </div>
      <div class="erp-page-actions">
        <el-button
          v-permission="'market:delivery-document:order-select'"
          type="primary"
          @click="openOrderPicker"
        >
          选择订单
        </el-button>
      </div>
    </header>

    <section class="erp-table-surface delivery-document-summary">
      <div class="delivery-document-summary-title">订单摘要</div>
      <el-descriptions v-if="selectedOrder.id" :column="3" border>
        <el-descriptions-item label="客户">{{ selectedOrder.customerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="RFQ号">{{ selectedOrder.rfqNo || selectedOrder.id }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ selectedOrder.status || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div v-else class="erp-empty-state delivery-document-empty">
        <el-empty description="请先选择部分已提单或已生成提单订单" />
      </div>
    </section>

    <section class="erp-table-surface delivery-document-table-placeholder">
      <div class="delivery-document-summary-title">已生成提单行</div>
      <el-empty :description="selectedOrderLabel" />
    </section>

    <section class="erp-table-surface delivery-document-table-placeholder">
      <div class="delivery-document-summary-title">已生成工艺表行</div>
      <el-empty :description="selectedOrderLabel" />
    </section>

    <OrderPickerDialog
      v-model="orderPickerVisible"
      title="选择提单表据订单"
      customer-scope="delivery-document"
      order-scope="delivery-document"
      :allowed-statuses="DELIVERY_DOCUMENT_ALLOWED_STATUSES"
      @select="handleOrderSelected"
    />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import OrderPickerDialog from './OrderPickerDialog.vue'

const DELIVERY_DOCUMENT_ALLOWED_STATUSES = ['部分已提单', '已生成提单']

const orderPickerVisible = ref(false)
const selectedOrder = ref({})

const selectedOrderLabel = computed(() => (
  selectedOrder.value.id ? '阶段 3/4 将在这里加载对应表据。' : '请先选择部分已提单或已生成提单订单'
))

const openOrderPicker = () => {
  orderPickerVisible.value = true
}

const handleOrderSelected = (order) => {
  selectedOrder.value = order || {}
}
</script>

<style scoped>
.delivery-document-page {
  gap: 16px;
}

.delivery-document-summary,
.delivery-document-table-placeholder {
  padding: 20px;
}

.delivery-document-summary-title {
  margin-bottom: 14px;
  font-size: 15px;
  font-weight: 600;
  color: var(--erp-text-primary);
}

.delivery-document-empty {
  padding: 8px 0;
}
</style>
