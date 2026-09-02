<template>
  <div class="erp-page order-manage-page">
    <header class="erp-page-header">
      <div>
        <h1 class="erp-page-title">询价订单</h1>
        <p class="erp-page-description">先选择客户，再管理该客户下的询价订单、零件、图纸文件、提单和工艺表数据。</p>
      </div>
    </header>

    <section class="erp-toolbar">
      <div class="erp-toolbar-group order-filter-group">
        <el-button v-permission="'market:order:customer-select'" class="customer-picker-trigger" @click="customerPickerVisible = true">
          <span class="customer-picker-content">
            <span
              :class="selectedCustomer ? 'customer-picker-text' : 'customer-picker-placeholder'"
              :title="selectedCustomer || '先选择客户'"
            >
              {{ selectedCustomer || '先选择客户' }}
            </span>
            <span class="customer-picker-action">选择客户</span>
          </span>
        </el-button>
        <el-button v-if="selectedCustomer" plain @click="handleCustomerClear">清除客户</el-button>
        <el-select
          v-model="statusFilter"
          clearable
          :disabled="!selectedCustomer"
          placeholder="全部状态"
          class="status-select"
          @change="handleSearch"
          @clear="handleSearch"
        >
          <el-option label="全部状态" value="" />
          <el-option
            v-for="status in ORDER_MANAGEMENT_STATUSES"
            :key="status"
            :label="status"
            :value="status"
          />
        </el-select>
        <el-input
          v-model="keyword"
          clearable
          :disabled="!selectedCustomer"
          placeholder="搜索 RFQ 号"
          class="search-input"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button :disabled="!selectedCustomer" @click="handleSearch">搜索</el-button>
        <el-button type="primary" :loading="loading" :disabled="!selectedCustomer" @click="refreshOrders">刷新</el-button>
      </div>
    </section>

    <section class="erp-table-surface order-table-surface">
      <div v-if="!selectedCustomer" class="erp-empty-state">
        <el-empty description="请先选择客户查看询价订单" />
      </div>

      <template v-else>
        <div class="erp-table-body">
          <el-table :data="orders" border stripe height="600" v-loading="loading">
          <el-table-column prop="rfqNo" label="RFQ号" min-width="170" fixed show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="120" align="center">
            <template #default="scope">
              <el-tag type="success" size="small">
                {{ scope.row.status || '-' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="零件" width="90" align="center">
            <template #default="scope">{{ scope.row.itemCount || 0 }}</template>
          </el-table-column>
          <el-table-column label="已报价" width="90" align="center">
            <template #default="scope">{{ scope.row.quotedCount || 0 }}</template>
          </el-table-column>
          <el-table-column label="缺图" width="90" align="center">
            <template #default="scope">
              <el-tag :type="scope.row.missingDrawingCount > 0 ? 'warning' : 'success'" size="small">
                {{ scope.row.missingDrawingCount || 0 }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="提单行" width="90" align="center">
            <template #default="scope">{{ scope.row.ticketCount || 0 }}</template>
          </el-table-column>
          <el-table-column label="工艺表行" width="100" align="center">
            <template #default="scope">{{ scope.row.processSheetCount || 0 }}</template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="180" show-overflow-tooltip />
          <el-table-column label="操作" width="270" align="center" fixed="right">
            <template #default="scope">
              <div class="erp-table-actions order-row-actions">
                <el-button size="small" @click="openDetail(scope.row)">详情</el-button>
                <el-button
                  v-for="action in rowStatusActions(scope.row)"
                  :key="`${scope.row.id}-${action.label}`"
                  v-permission="action.permission"
                  size="small"
                  :type="action.type"
                  plain
                  class="status-action-button"
                  :loading="isOrderStatusPending(scope.row)"
                  :disabled="isOrderStatusPending(scope.row)"
                  @click="handleStatusAction(scope.row, action)"
                >
                  {{ action.label }}
                </el-button>
                <el-tooltip
                  v-if="statusActionLocked(scope.row)"
                  :content="RELEASED_ORDER_STATUS_ACTION_DISABLED_REASON"
                  placement="top"
                >
                  <span class="status-locked-label">状态锁定</span>
                </el-tooltip>
              </div>
            </template>
          </el-table-column>
          </el-table>
        </div>
        <div class="erp-pagination">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :page-sizes="[20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            :pager-count="7"
            :total="total"
            background
            @size-change="page = 1"
          />
        </div>
      </template>
    </section>

    <el-drawer v-model="detailVisible" title="询价订单详情" size="72%">
      <div v-if="orderDetail" v-loading="detailLoading" class="detail-wrap">
        <el-descriptions :column="2" border class="detail-descriptions">
          <el-descriptions-item label="RFQ号">{{ orderDetail.orderInfo?.rfqNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ orderDetail.orderInfo?.status || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ orderDetail.orderInfo?.customerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="图纸目录" :span="2">{{ orderDetail.fileRoot || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-actions">
          <el-alert
            :title="`当前缺图零件：${drawingSummary?.missingDrawingCount || 0} 个`"
            type="warning"
            :closable="false"
            show-icon
          />
          <el-button
            v-permission="ORDER_ITEM_UPDATE_PERMISSION"
            plain
            class="workflow-soft-button"
            :disabled="orderItemDataReplacementDisabled"
            :loading="orderItemDataReplacementBusy"
            @click="openOrderItemDataReplacementDialog"
          >
            表格数据替换
          </el-button>
          <el-button
            v-permission="ORDER_ITEM_UPDATE_PERMISSION"
            type="primary"
            plain
            class="workflow-soft-button"
            :disabled="!hasOrderItemDrafts || orderItemSavePending"
            :loading="orderItemSavePending"
            @click="saveOrderItemChanges"
          >
            保存修改
          </el-button>
          <el-button
            v-permission="ORDER_ITEM_UPDATE_PERMISSION"
            plain
            class="workflow-soft-button"
            :disabled="replaceDrawingUploadPending"
            :loading="replaceDrawingUploadPending"
            @click="openReplaceDrawingDialog"
          >
            导入替换图纸
          </el-button>
          <el-button
            v-permission="ORDER_ITEM_UPDATE_PERMISSION"
            plain
            class="workflow-soft-button"
            :disabled="orderItemAppendPending"
            :loading="orderItemAppendPending"
            @click="openOrderItemAppendDialog"
          >
            新增零件
          </el-button>
          <el-button v-permission="'market:order:supplement-drawing'" type="primary" plain :disabled="Number(drawingSummary?.missingDrawingCount || 0) === 0" @click="openSupplementDialog">
            补传图纸
          </el-button>
        </div>

        <el-tabs v-model="activeDetailTab" class="detail-tabs" @tab-change="handleDetailTabChange">
          <el-tab-pane label="零件明细" name="items">
            <div v-if="hasOrderItemDrafts" class="order-item-draft-tip">
              <el-alert
                :title="`已有 ${orderItemDraftCount} 行未保存修改，请确认后点击“保存修改”。`"
                type="info"
                :closable="false"
                show-icon
              />
            </div>
            <el-table :data="detailItemList" border stripe height="460" scrollbar-always-on v-loading="detailItemsLoading">
              <el-table-column label="图号" width="160" fixed show-overflow-tooltip>
                <template #default="{ row }">
                  <el-input
                    :model-value="itemFieldValue(row, 'partNo')"
                    size="small"
                    maxlength="100"
                    clearable
                    :class="{ 'order-item-editor-changed': itemFieldChanged(row, 'partNo') }"
                    @update:model-value="value => updateItemDraft(row, 'partNo', value)"
                  />
                </template>
              </el-table-column>
              <el-table-column label="版本" width="100" align="center">
                <template #default="{ row }">
                  <el-input
                    :model-value="itemFieldValue(row, 'version')"
                    size="small"
                    maxlength="20"
                    clearable
                    :class="{ 'order-item-editor-changed': itemFieldChanged(row, 'version') }"
                    @update:model-value="value => updateItemDraft(row, 'version', value)"
                  />
                </template>
              </el-table-column>
              <el-table-column label="类型" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="isAssemblyItem(row) ? 'warning' : 'info'" size="small">
                    {{ isAssemblyItem(row) ? '组合件' : '普通件' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="图纸" width="86" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.hasDrawing || row.drawingUrl ? 'success' : 'warning'" size="small">
                    {{ row.hasDrawing || row.drawingUrl ? '已绑定' : '缺图' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="询价数量" width="144" align="center" class-name="order-item-qty-column">
                <template #default="{ row }">
                  <el-input-number
                    :model-value="itemFieldValue(row, 'qty')"
                    :min="detailItemMinQty(row)"
                    :precision="0"
                    controls-position="right"
                    size="small"
                    class="order-item-qty-input"
                    :class="{ 'order-item-editor-changed': itemFieldChanged(row, 'qty') }"
                    @update:model-value="value => updateItemDraft(row, 'qty', value)"
                  />
                </template>
              </el-table-column>
              <el-table-column prop="ticketedQty" label="已提单数量" width="105" align="center" />
              <el-table-column label="剩余可提数量" width="110" align="center">
                <template #default="{ row }">{{ itemRemainingQty(row) }}</template>
              </el-table-column>
              <el-table-column label="客户询价交期" width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-input
                    :model-value="itemFieldValue(row, 'leadTime')"
                    size="small"
                    maxlength="100"
                    clearable
                    placeholder="日期或天数"
                    :class="{ 'order-item-editor-changed': itemFieldChanged(row, 'leadTime') }"
                    @update:model-value="value => updateItemDraft(row, 'leadTime', value)"
                  />
                </template>
              </el-table-column>
              <el-table-column label="报价承诺交期" width="170" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-input
                    :model-value="itemFieldValue(row, 'quoteLeadTime')"
                    size="small"
                    maxlength="100"
                    clearable
                    placeholder="默认沿用客户交期"
                    :class="{ 'order-item-editor-changed': itemFieldChanged(row, 'quoteLeadTime') }"
                    @update:model-value="value => updateItemDraft(row, 'quoteLeadTime', value)"
                  />
                </template>
              </el-table-column>
              <el-table-column label="内部目标交期" width="170">
                <template #default="{ row }">
                  <el-date-picker
                    :model-value="itemFieldValue(row, 'internalTargetLeadTime')"
                    type="date"
                    value-format="YYYY-MM-DD"
                    format="YYYY-MM-DD"
                    placeholder="默认沿用报价交期"
                    clearable
                    size="small"
                    class="order-item-date-picker"
                    :class="{ 'order-item-editor-changed': itemFieldChanged(row, 'internalTargetLeadTime') }"
                    @update:model-value="value => updateItemDraft(row, 'internalTargetLeadTime', value)"
                  />
                </template>
              </el-table-column>
              <el-table-column prop="engineeringMaterialName" label="材料" width="120" show-overflow-tooltip>
                <template #default="{ row }">{{ row.engineeringMaterialName || row.materialName || '-' }}</template>
              </el-table-column>
              <el-table-column prop="materialNo" label="物料号" width="130" show-overflow-tooltip>
                <template #default="{ row }">{{ row.materialNo || '-' }}</template>
              </el-table-column>
              <el-table-column prop="materialNoStatus" label="物料号状态" width="116" align="center">
                <template #default="{ row }">
                  <el-tag :type="materialStatusTagType(row.materialNoStatus)" size="small">
                    {{ row.materialNoStatus || '未确认' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="物料描述" width="220" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-input
                    :model-value="itemFieldValue(row, 'description')"
                    size="small"
                    maxlength="500"
                    clearable
                    :class="{ 'order-item-editor-changed': itemFieldChanged(row, 'description') }"
                    @update:model-value="value => updateItemDraft(row, 'description', value)"
                  />
                </template>
              </el-table-column>
              <el-table-column label="表面处理" width="190" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-input
                    :model-value="itemFieldValue(row, 'surfaceTreatment')"
                    size="small"
                    maxlength="200"
                    clearable
                    :class="{ 'order-item-editor-changed': itemFieldChanged(row, 'surfaceTreatment') }"
                    @update:model-value="value => updateItemDraft(row, 'surfaceTreatment', value)"
                  />
                </template>
              </el-table-column>
              <el-table-column label="备注" width="190" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-input
                    :model-value="itemFieldValue(row, 'remark')"
                    size="small"
                    maxlength="500"
                    clearable
                    :class="{ 'order-item-editor-changed': itemFieldChanged(row, 'remark') }"
                    @update:model-value="value => updateItemDraft(row, 'remark', value)"
                  />
                </template>
              </el-table-column>
              <el-table-column label="下单日期" width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-date-picker
                    :model-value="itemFieldValue(row, 'salesOrderDate')"
                    type="date"
                    value-format="YYYY-MM-DD"
                    format="YYYY-MM-DD"
                    placeholder="选择日期"
                    clearable
                    size="small"
                    class="order-item-date-picker"
                    :class="{ 'order-item-editor-changed': itemFieldChanged(row, 'salesOrderDate') }"
                    @update:model-value="value => updateItemDraft(row, 'salesOrderDate', value)"
                  />
                </template>
              </el-table-column>
              <el-table-column label="客户订单号" width="190" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-input
                    :model-value="itemFieldValue(row, 'customerOrderNo')"
                    size="small"
                    maxlength="120"
                    clearable
                    :class="{ 'order-item-editor-changed': itemFieldChanged(row, 'customerOrderNo') }"
                    @update:model-value="value => updateItemDraft(row, 'customerOrderNo', value)"
                  />
                </template>
              </el-table-column>
              <el-table-column label="客户项目号" width="190" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-input
                    :model-value="itemFieldValue(row, 'customerProjectNo')"
                    size="small"
                    maxlength="120"
                    clearable
                    :class="{ 'order-item-editor-changed': itemFieldChanged(row, 'customerProjectNo') }"
                    @update:model-value="value => updateItemDraft(row, 'customerProjectNo', value)"
                  />
                </template>
              </el-table-column>
              <el-table-column label="客户下单抬头" width="210" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-input
                    :model-value="itemFieldValue(row, 'customerOrderTitle')"
                    size="small"
                    maxlength="255"
                    clearable
                    :class="{ 'order-item-editor-changed': itemFieldChanged(row, 'customerOrderTitle') }"
                    @update:model-value="value => updateItemDraft(row, 'customerOrderTitle', value)"
                  />
                </template>
              </el-table-column>
              <el-table-column label="订单类型" width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-select
                    :model-value="itemFieldValue(row, 'orderType')"
                    size="small"
                    clearable
                    placeholder="请选择"
                    class="order-item-order-type-select"
                    :class="{ 'order-item-editor-changed': itemFieldChanged(row, 'orderType') }"
                    @update:model-value="value => updateItemDraft(row, 'orderType', value)"
                  >
                    <el-option label="预测订单" value="预测订单" />
                    <el-option label="备货订单" value="备货订单" />
                    <el-option label="正式订单" value="正式订单" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="内部项目号" width="190" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-input
                    :model-value="itemFieldValue(row, 'internalProjectNo')"
                    size="small"
                    maxlength="120"
                    clearable
                    :class="{ 'order-item-editor-changed': itemFieldChanged(row, 'internalProjectNo') }"
                    @update:model-value="value => updateItemDraft(row, 'internalProjectNo', value)"
                  />
                </template>
              </el-table-column>
              <el-table-column label="状态" width="140" align="center" fixed="right">
                <template #default="{ row }">
                  <div class="order-item-status-cell">
                    <el-tag :type="orderItemStatusTagType(row.status)" size="small">
                      {{ row.status || '-' }}
                    </el-tag>
                    <el-button
                      v-permission="ORDER_ITEM_UPDATE_PERMISSION"
                      link
                      :type="row.status === '废弃' ? 'success' : 'danger'"
                      size="small"
                      :loading="orderItemStatusPending(row)"
                      :disabled="orderItemStatusPending(row)"
                      @click="changeOrderItemDiscardState(row)"
                    >
                      {{ row.status === '废弃' ? '恢复' : '废弃' }}
                    </el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
            <div class="erp-pagination detail-pagination">
              <el-pagination
                v-model:current-page="detailItemPagination.page"
                v-model:page-size="detailItemPagination.pageSize"
                :page-sizes="[20, 50, 100]"
                layout="total, sizes, prev, pager, next, jumper"
                :total="detailItemTotal"
                background
                :pager-count="7"
                @size-change="detailItemPagination.page = 1"
              />
            </div>
          </el-tab-pane>

          <el-tab-pane label="提单行" name="tickets">
            <el-table :data="detailTicketList" border stripe height="320" v-loading="detailTicketsLoading">
              <el-table-column prop="internalProjectNo" label="内部项目号" min-width="130" show-overflow-tooltip />
              <el-table-column prop="partNo" label="图号" min-width="140" show-overflow-tooltip />
              <el-table-column prop="version" label="版本" width="80" align="center" />
              <el-table-column prop="qty" label="数量" width="70" align="center" />
              <el-table-column prop="arrangeType" label="安排类型" width="110" />
              <el-table-column prop="startDate" label="开工日期" width="120" />
              <el-table-column prop="deliveryDate" label="交货日期" width="120" />
              <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
            </el-table>
            <div class="erp-pagination detail-pagination">
              <el-pagination
                v-model:current-page="detailTicketPagination.page"
                v-model:page-size="detailTicketPagination.pageSize"
                :page-sizes="[20, 50, 100]"
                layout="total, sizes, prev, pager, next, jumper"
                :total="detailTicketTotal"
                background
                :pager-count="7"
                @size-change="detailTicketPagination.page = 1"
              />
            </div>
          </el-tab-pane>

          <el-tab-pane label="工艺表行" name="processSheets">
            <el-table :data="detailProcessSheetList" border stripe height="320" v-loading="detailProcessSheetsLoading">
              <el-table-column prop="internalProjectNo" label="内部项目号" min-width="130" show-overflow-tooltip />
              <el-table-column prop="partNo" label="图号" min-width="140" show-overflow-tooltip />
              <el-table-column prop="version" label="版本" width="80" align="center" />
              <el-table-column prop="description" label="物料描述" min-width="180" show-overflow-tooltip />
              <el-table-column prop="qty" label="数量" width="70" align="center" />
              <el-table-column prop="deliveryDate" label="交期" width="120" />
              <el-table-column label="工艺路线" min-width="260" show-overflow-tooltip>
                <template #default="scope">{{ formatRoute(scope.row.processRoute) }}</template>
              </el-table-column>
            </el-table>
            <div class="erp-pagination detail-pagination">
              <el-pagination
                v-model:current-page="detailProcessSheetPagination.page"
                v-model:page-size="detailProcessSheetPagination.pageSize"
                :page-sizes="[20, 50, 100]"
                layout="total, sizes, prev, pager, next, jumper"
                :total="detailProcessSheetTotal"
                background
                :pager-count="7"
                @size-change="detailProcessSheetPagination.page = 1"
              />
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <SupplementDrawingDialog
      v-model="supplementVisible"
      v-model:files="supplementFiles"
      v-model:version-files="supplementVersionFiles"
      title="补传图纸"
      :order-label="orderDetail?.orderInfo?.rfqNo || '-'"
      :customer-name="orderDetail?.orderInfo?.customerName || '-'"
      :missing-items="missingDrawingItems"
      :result="supplementResult"
      :pending="supplementUploadPending"
      :permission="'market:order:supplement-drawing'"
      @submit="submitSupplementDrawings"
    />

    <SupplementDrawingDialog
      v-model="replaceDrawingVisible"
      v-model:files="replaceDrawingFiles"
      v-model:version-files="replaceVersionDrawingFiles"
      title="导入替换图纸"
      submit-text="上传并替换"
      list-title="当前订单零件"
      part-upload-title="按图号替换"
      version-upload-title="按图号+版本替换"
      part-pending-title="按图号待替换图纸"
      version-pending-title="按图号+版本待替换图纸"
      bound-result-label="已替换"
      already-bound-result-label="已跳过"
      :order-label="orderDetail?.orderInfo?.rfqNo || '-'"
      :customer-name="orderDetail?.orderInfo?.customerName || '-'"
      :missing-items="detailItemList"
      :result="replaceDrawingResult"
      :pending="replaceDrawingUploadPending"
      :permission="ORDER_ITEM_UPDATE_PERMISSION"
      @submit="submitReplaceDrawings"
    />

    <OrderItemDataReplacementDialog
      v-model="orderItemDataReplacementVisible"
      :rfq-id="orderDetail?.orderInfo?.id"
      :disabled="orderItemDataReplacementDisabled"
      :draft-rows-by-key="orderItemDraftRowsByKey"
      @busy-change="orderItemDataReplacementBusy = $event"
      @confirmed="handleOrderItemDataReplacementConfirmed"
    />

    <OrderItemAppendDialog
      v-model="orderItemAppendVisible"
      v-model:excel-file="orderItemAppendExcelFile"
      v-model:files="orderItemAppendDrawingFiles"
      v-model:version-files="orderItemAppendVersionDrawingFiles"
      :order-label="orderDetail?.orderInfo?.rfqNo || '-'"
      :customer-name="orderDetail?.orderInfo?.customerName || '-'"
      :result="orderItemAppendResult"
      :pending="orderItemAppendPending"
      :permission="ORDER_ITEM_UPDATE_PERMISSION"
      @submit="submitOrderItemAppend"
    />

    <CustomerPickerDialog
      v-model="customerPickerVisible"
      :current-customer="selectedCustomer"
      :current-customer-code="selectedCustomerCode"
      customer-scope="market-order"
      @select="handleCustomerSelected"
    />
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import CustomerPickerDialog from './CustomerPickerDialog.vue'
import OrderItemAppendDialog from './order/OrderItemAppendDialog.vue'
import OrderItemDataReplacementDialog from './order/OrderItemDataReplacementDialog.vue'
import SupplementDrawingDialog from './order/SupplementDrawingDialog.vue'
import { usePendingAction } from '../composables/usePendingAction'
import {
  appendOrderItems,
  discardOrder,
  discardOrderItem,
  getOrderDrawingSummary,
  getOrder,
  listOrderItems,
  listOrderProcessSheets,
  listOrderTickets,
  listOrders,
  replaceOrderDrawings,
  restoreOrder,
  restoreOrderItem,
  rollbackPricedOrder,
  rollbackQuotedOrder,
  saveOrderItems,
  supplementDrawings
} from '../services/orderApi'

const { isPending, runAction } = usePendingAction()

const orders = ref([])
const selectedCustomer = ref('')
const selectedCustomerCode = ref('')
const keyword = ref('')
const statusFilter = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const detailLoading = ref(false)
const detailVisible = ref(false)
const orderDetail = ref(null)
const activeDetailTab = ref('items')
const detailItemList = ref([])
const orderItemRowsByKey = reactive({})
const orderItemDrafts = reactive({})
const detailTicketList = ref([])
const detailProcessSheetList = ref([])
const drawingSummary = ref(null)
const detailItemsLoading = ref(false)
const detailTicketsLoading = ref(false)
const detailProcessSheetsLoading = ref(false)
const detailItemPagination = reactive({ page: 1, pageSize: 20 })
const detailTicketPagination = reactive({ page: 1, pageSize: 20 })
const detailProcessSheetPagination = reactive({ page: 1, pageSize: 20 })
const detailItemTotal = ref(0)
const detailTicketTotal = ref(0)
const detailProcessSheetTotal = ref(0)
const supplementVisible = ref(false)
const supplementFiles = ref([])
const supplementVersionFiles = ref([])
const supplementResult = ref(null)
const replaceDrawingVisible = ref(false)
const replaceDrawingFiles = ref([])
const replaceVersionDrawingFiles = ref([])
const replaceDrawingResult = ref(null)
const orderItemDataReplacementVisible = ref(false)
const orderItemDataReplacementBusy = ref(false)
const orderItemAppendVisible = ref(false)
const orderItemAppendExcelFile = ref(null)
const orderItemAppendDrawingFiles = ref([])
const orderItemAppendVersionDrawingFiles = ref([])
const orderItemAppendResult = ref(null)
const customerPickerVisible = ref(false)

const ORDER_ITEM_UPDATE_PERMISSION = 'market:order:update-item'
const ORDER_MANAGEMENT_STATUSES = ['未核价', '已核价', '已报价', '部分已提单', '已生成提单', '废弃']
const RELEASED_ORDER_STATUSES = ['部分已提单', '已生成提单']
const ORDER_ITEM_EDITABLE_FIELDS = [
  'partNo',
  'version',
  'qty',
  'leadTime',
  'quoteLeadTime',
  'internalTargetLeadTime',
  'description',
  'surfaceTreatment',
  'remark',
  'salesOrderDate',
  'customerOrderNo',
  'customerProjectNo',
  'customerOrderTitle',
  'orderType',
  'internalProjectNo'
]
const ORDER_ITEM_DATE_FIELDS = new Set(['salesOrderDate'])
const ORDER_ITEM_LEAD_TIME_FIELDS = new Set(['leadTime', 'quoteLeadTime', 'internalTargetLeadTime'])
const ORDER_ITEM_TEXT_FIELDS = new Set(ORDER_ITEM_EDITABLE_FIELDS.filter(field => !['qty', 'salesOrderDate'].includes(field)))
const ORDER_ITEM_ORDER_TYPES = ['预测订单', '备货订单', '正式订单']
const orderActionKeys = {
  statusChange: (orderId) => `order:status-change:${orderId || 'none'}`,
  supplementDrawings: (orderId) => `order:supplement-drawings:${orderId || 'none'}`,
  replaceDrawings: (orderId) => `order:replace-drawings:${orderId || 'none'}`,
  saveItems: (orderId) => `order:save-items:${orderId || 'none'}`,
  appendItems: (orderId) => `order:append-items:${orderId || 'none'}`,
  itemStatus: (orderId, itemId) => `order:item-status:${orderId || 'none'}:${itemId || 'none'}`
}
const ORDER_STATUS_ACTIONS = [
  {
    status: '已报价',
    label: '退回已核价',
    permission: 'market:order:rollback-quoted',
    handler: rollbackQuotedOrder,
    type: 'warning',
    reason: '询价订单：已报价退回已核价',
    successMessage: '询价订单已退回已核价。',
    errorMessage: '退回已核价失败。',
    confirmTitle: '退回已核价',
    confirmButtonText: '确认退回',
    confirmMessage: (row) => `确认将询价订单「${row.rfqNo || row.id}」从“已报价”退回“已核价”吗？系统只调整询价订单和零件状态，不会清空报价交期、核价结果或图纸。`
  },
  {
    status: '已核价',
    label: '退回未核价',
    permission: 'market:order:rollback-priced',
    handler: rollbackPricedOrder,
    type: 'warning',
    reason: '询价订单：已核价退回未核价',
    successMessage: '询价订单已退回未核价。',
    errorMessage: '退回未核价失败。',
    confirmTitle: '退回未核价',
    confirmButtonText: '确认退回',
    confirmMessage: (row) => `确认将询价订单「${row.rfqNo || row.id}」从“已核价”退回“未核价”吗？系统只调整状态，已有核价数据会保留为草稿。`
  },
  {
    status: '未核价',
    label: '废弃',
    permission: 'market:order:discard',
    handler: discardOrder,
    type: 'danger',
    reason: '询价订单：废弃未核价订单',
    successMessage: '询价订单已废弃。',
    errorMessage: '废弃询价订单失败。',
    confirmTitle: '废弃询价订单',
    confirmButtonText: '确认废弃',
    confirmMessage: (row) => `确认废弃询价订单「${row.rfqNo || row.id}」吗？废弃后询价订单不会进入核价、报价处理和议价提单候选，但数据仍保留。`
  },
  {
    status: '已核价',
    label: '废弃',
    permission: 'market:order:discard',
    handler: discardOrder,
    type: 'danger',
    reason: '询价订单：废弃已核价订单',
    successMessage: '询价订单已废弃。',
    errorMessage: '废弃询价订单失败。',
    confirmTitle: '废弃询价订单',
    confirmButtonText: '确认废弃',
    confirmMessage: (row) => `确认废弃询价订单「${row.rfqNo || row.id}」吗？系统只调整状态，不会清空核价结果、报价交期或图纸。`
  },
  {
    status: '已报价',
    label: '废弃',
    permission: 'market:order:discard',
    handler: discardOrder,
    type: 'danger',
    reason: '询价订单：废弃已报价订单',
    successMessage: '询价订单已废弃。',
    errorMessage: '废弃询价订单失败。',
    confirmTitle: '废弃询价订单',
    confirmButtonText: '确认废弃',
    confirmMessage: (row) => `确认废弃询价订单「${row.rfqNo || row.id}」吗？系统只调整状态，不会清空报价单数据。`
  },
  {
    status: '废弃',
    label: '恢复为已核价',
    permission: 'market:order:restore',
    handler: restoreOrder,
    type: 'success',
    reason: '询价订单：恢复废弃订单',
    successMessage: '询价订单已恢复为已核价。',
    errorMessage: '恢复询价订单失败。',
    confirmTitle: '恢复询价订单',
    confirmButtonText: '确认恢复',
    confirmMessage: (row) => `确认将废弃询价订单「${row.rfqNo || row.id}」恢复为“已核价”吗？恢复后仍需按后续业务校验保存报价单。`
  }
]
const RELEASED_ORDER_STATUS_ACTION_DISABLED_REASON = '询价订单已生成提单，不能废弃或回退'

const missingDrawingItems = computed(() => detailItemList.value.filter(item => !item.drawingUrl))
const supplementUploadPending = computed(() => isPending(orderActionKeys.supplementDrawings(orderDetail.value?.orderInfo?.id)))
const replaceDrawingUploadPending = computed(() => isPending(orderActionKeys.replaceDrawings(orderDetail.value?.orderInfo?.id)))
const orderItemSavePending = computed(() => isPending(orderActionKeys.saveItems(orderDetail.value?.orderInfo?.id)))
const orderItemAppendPending = computed(() => isPending(orderActionKeys.appendItems(orderDetail.value?.orderInfo?.id)))
const orderItemDraftEntries = computed(() => Object.entries(orderItemDrafts).filter(([, draft]) => draft && Object.keys(draft).length > 0))
const orderItemDraftCount = computed(() => orderItemDraftEntries.value.length)
const hasOrderItemDrafts = computed(() => orderItemDraftCount.value > 0)
const orderItemDraftRowsByKey = computed(() => Object.fromEntries(orderItemDraftEntries.value.map(([key, draft]) => [key, { ...draft }])))
const orderItemDataReplacementDisabled = computed(() => !orderDetail.value?.orderInfo?.id || orderItemDataReplacementBusy.value || orderItemSavePending.value)

const materialStatusTagType = (status) => {
  if (status === '已确认') return 'success'
  if (status === '获取失败') return 'danger'
  if (status === '未确认') return 'warning'
  return 'info'
}

const orderItemStatusTagType = (status) => {
  if (status === '已核价' || status === '已报价' || status === '已生成提单') return 'success'
  if (status === '未核价' || status === '部分已提单') return 'warning'
  if (status === '废弃') return 'danger'
  return 'info'
}

const isAssemblyItem = (row) => String(row?.quoteMode || '').toUpperCase() === 'ASSEMBLY'

const isOrderStatusPending = (row) => isPending(orderActionKeys.statusChange(row?.id))

const orderItemStatusPending = (row) => isPending(orderActionKeys.itemStatus(orderDetail.value?.orderInfo?.id, row?.itemId ?? row?.id))

watch([page, pageSize], () => {
  if (selectedCustomer.value) {
    loadOrders()
  }
})

watch(() => [detailItemPagination.page, detailItemPagination.pageSize], () => {
  if (detailVisible.value && activeDetailTab.value === 'items') {
    fetchDetailItems()
  }
})

watch(() => [detailTicketPagination.page, detailTicketPagination.pageSize], () => {
  if (detailVisible.value && activeDetailTab.value === 'tickets') {
    fetchDetailTickets()
  }
})

watch(() => [detailProcessSheetPagination.page, detailProcessSheetPagination.pageSize], () => {
  if (detailVisible.value && activeDetailTab.value === 'processSheets') {
    fetchDetailProcessSheets()
  }
})

const loadOrders = async () => {
  if (!selectedCustomer.value) {
    orders.value = []
    return
  }
  loading.value = true
  try {
    const response = await listOrders({
      customerCode: selectedCustomerCode.value || undefined,
      customerName: selectedCustomer.value,
      keyword: keyword.value.trim() || undefined,
      status: statusFilter.value || undefined,
      page: page.value,
      pageSize: pageSize.value
    })
    orders.value = response.records || []
    total.value = response.total || 0
  } catch (error) {
    console.error(error)
    ElMessage.error('询价订单列表加载失败。')
  } finally {
    loading.value = false
  }
}

const refreshOrders = async () => {
  await loadOrders()
}

const handleSearch = async () => {
  page.value = 1
  await loadOrders()
}

const handleCustomerChange = async () => {
  keyword.value = ''
  statusFilter.value = ''
  page.value = 1
  orderDetail.value = null
  detailVisible.value = false
  await loadOrders()
}

const resolveCustomerName = (customer) => typeof customer === 'string' ? customer : (customer?.customerName || '')
const resolveCustomerCode = (customer) => typeof customer === 'string' ? customer : (customer?.customerCode || '')

const handleCustomerSelected = async (customer) => {
  selectedCustomer.value = resolveCustomerName(customer)
  selectedCustomerCode.value = resolveCustomerCode(customer)
  await handleCustomerChange()
}

const handleCustomerClear = () => {
  selectedCustomer.value = ''
  selectedCustomerCode.value = ''
  keyword.value = ''
  statusFilter.value = ''
  orders.value = []
  total.value = 0
  page.value = 1
  orderDetail.value = null
  detailVisible.value = false
}

const draftKey = (row) => String(row?.itemId ?? row?.id ?? '')

const clearOrderItemDrafts = () => {
  Object.keys(orderItemDrafts).forEach(key => delete orderItemDrafts[key])
}

const clearOrderItemRowsByKey = () => {
  Object.keys(orderItemRowsByKey).forEach(key => delete orderItemRowsByKey[key])
}

const normalizeOrderItemDraftValue = (field, value) => {
  if (field === 'qty') {
    const number = Number(value)
    return Number.isFinite(number) ? Math.trunc(number) : null
  }
  if (ORDER_ITEM_DATE_FIELDS.has(field)) return value || null
  if (ORDER_ITEM_TEXT_FIELDS.has(field) || ORDER_ITEM_LEAD_TIME_FIELDS.has(field)) {
    return value === null || value === undefined ? '' : String(value).trim()
  }
  return value ?? ''
}

const normalizeOrderItemCompareValue = (field, value) => {
  if (field === 'qty') {
    const number = Number(value)
    return Number.isFinite(number) ? String(Math.trunc(number)) : ''
  }
  if (value === null || value === undefined) return ''
  return String(value).trim()
}

const baseItemFieldValue = (row, field) => {
  if (field === 'qty') return row?.qty ?? null
  if (ORDER_ITEM_DATE_FIELDS.has(field)) return row?.[field] || null
  return row?.[field] ?? ''
}

const itemFieldValue = (row, field) => {
  const key = draftKey(row)
  if (key && orderItemDrafts[key] && Object.prototype.hasOwnProperty.call(orderItemDrafts[key], field)) {
    return orderItemDrafts[key][field]
  }
  return baseItemFieldValue(row, field)
}

const itemFieldChanged = (row, field) => (
  normalizeOrderItemCompareValue(field, itemFieldValue(row, field)) !== normalizeOrderItemCompareValue(field, baseItemFieldValue(row, field))
)

const updateItemDraft = (row, field, value) => {
  const key = draftKey(row)
  if (!key || !ORDER_ITEM_EDITABLE_FIELDS.includes(field)) return

  const nextValue = normalizeOrderItemDraftValue(field, value)
  if (!orderItemDrafts[key]) orderItemDrafts[key] = {}
  orderItemRowsByKey[key] = { ...(orderItemRowsByKey[key] || row || {}) }

  if (normalizeOrderItemCompareValue(field, nextValue) === normalizeOrderItemCompareValue(field, baseItemFieldValue(orderItemRowsByKey[key], field))) {
    delete orderItemDrafts[key][field]
  } else {
    orderItemDrafts[key][field] = nextValue
  }

  if (Object.keys(orderItemDrafts[key]).length === 0) {
    delete orderItemDrafts[key]
  }
}

const detailItemMinQty = (row) => Math.max(1, Number(row?.ticketedQty || 0))

const itemRemainingQty = (row) => Math.max(0, Number(itemFieldValue(row, 'qty') || 0) - Number(row?.ticketedQty || 0))

const buildOrderItemPayloadRow = (row) => {
  const payload = {
    itemId: Number(row?.itemId ?? row?.id)
  }
  ORDER_ITEM_EDITABLE_FIELDS.forEach(field => {
    payload[field] = normalizeOrderItemDraftValue(field, itemFieldValue(row, field))
  })
  return payload
}

const changedOrderItemRows = () => orderItemDraftEntries.value
  .map(([key]) => orderItemRowsByKey[key])
  .filter(Boolean)

const validateOrderItemPayloadRows = (rows) => {
  for (const row of rows) {
    const label = row.partNo || row.itemId || '未知零件'
    if (!String(row.partNo || '').trim()) {
      ElMessage.warning(`零件 ${label} 的图号不能为空。`)
      return false
    }
    if (!Number.isInteger(Number(row.qty)) || Number(row.qty) < 1) {
      ElMessage.warning(`零件 ${label} 的询价数量必须为正整数。`)
      return false
    }
    const sourceRow = orderItemRowsByKey[String(row.itemId)] || {}
    if (Number(row.qty) < Number(sourceRow.ticketedQty || 0)) {
      ElMessage.warning(`零件 ${label} 的询价数量不能小于已提单数量。`)
      return false
    }
    if (row.orderType && !ORDER_ITEM_ORDER_TYPES.includes(row.orderType)) {
      ElMessage.warning(`零件 ${label} 的订单类型只能是预测订单、备货订单或正式订单。`)
      return false
    }
  }
  return true
}

const saveOrderItemChanges = async () => {
  const rfqId = orderDetail.value?.orderInfo?.id
  if (!rfqId) return ElMessage.warning('请先打开询价订单详情。')
  const rows = changedOrderItemRows().map(buildOrderItemPayloadRow)
  if (rows.length === 0) return ElMessage.warning('当前没有需要保存的修改。')
  if (!validateOrderItemPayloadRows(rows)) return

  return runAction(orderActionKeys.saveItems(rfqId), async () => {
    try {
      const response = await saveOrderItems(rfqId, { items: rows })
      clearOrderItemDrafts()
      ElMessage.success(response?.message || `已保存 ${rows.length} 行询价订单零件信息。`)
      await refreshCurrentDetail()
      await loadOrders()
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || '保存询价订单零件信息失败。')
    }
  })
}

const resetDetailPagination = () => {
  activeDetailTab.value = 'items'
  detailItemPagination.page = 1
  detailTicketPagination.page = 1
  detailProcessSheetPagination.page = 1
  detailItemList.value = []
  clearOrderItemDrafts()
  clearOrderItemRowsByKey()
  detailTicketList.value = []
  detailProcessSheetList.value = []
  detailItemTotal.value = 0
  detailTicketTotal.value = 0
  detailProcessSheetTotal.value = 0
  drawingSummary.value = null
}

const openDetail = async (row) => {
  detailVisible.value = true
  detailLoading.value = true
  resetDetailPagination()
  try {
    orderDetail.value = await getOrder(row.id)
    await fetchDrawingSummary(row.id)
    await fetchCurrentDetailTab()
  } catch (error) {
    console.error(error)
    ElMessage.error('询价订单详情加载失败。')
  } finally {
    detailLoading.value = false
  }
}

const fetchDrawingSummary = async (rfqId = orderDetail.value?.orderInfo?.id) => {
  if (!rfqId) return
  drawingSummary.value = await getOrderDrawingSummary(rfqId)
}

const fetchDetailItems = async () => {
  const rfqId = orderDetail.value?.orderInfo?.id
  if (!rfqId) return
  detailItemsLoading.value = true
  try {
    const response = await listOrderItems(rfqId, {
      page: detailItemPagination.page,
      pageSize: detailItemPagination.pageSize
    })
    const records = response.records || []
    records.forEach(row => {
      const key = draftKey(row)
      if (key) orderItemRowsByKey[key] = { ...row }
    })
    detailItemList.value = records
    detailItemTotal.value = Number(response.total ?? 0)
  } catch (error) {
    console.error(error)
    ElMessage.error('询价订单零件明细加载失败。')
  } finally {
    detailItemsLoading.value = false
  }
}

const fetchDetailTickets = async () => {
  const rfqId = orderDetail.value?.orderInfo?.id
  if (!rfqId) return
  detailTicketsLoading.value = true
  try {
    const response = await listOrderTickets(rfqId, {
      page: detailTicketPagination.page,
      pageSize: detailTicketPagination.pageSize
    })
    detailTicketList.value = response.records || []
    detailTicketTotal.value = Number(response.total ?? 0)
  } catch (error) {
    console.error(error)
    ElMessage.error('询价订单提单行加载失败。')
  } finally {
    detailTicketsLoading.value = false
  }
}

const fetchDetailProcessSheets = async () => {
  const rfqId = orderDetail.value?.orderInfo?.id
  if (!rfqId) return
  detailProcessSheetsLoading.value = true
  try {
    const response = await listOrderProcessSheets(rfqId, {
      page: detailProcessSheetPagination.page,
      pageSize: detailProcessSheetPagination.pageSize
    })
    detailProcessSheetList.value = response.records || []
    detailProcessSheetTotal.value = Number(response.total ?? 0)
  } catch (error) {
    console.error(error)
    ElMessage.error('询价订单工艺表行加载失败。')
  } finally {
    detailProcessSheetsLoading.value = false
  }
}

const fetchCurrentDetailTab = async () => {
  if (activeDetailTab.value === 'tickets') {
    await fetchDetailTickets()
  } else if (activeDetailTab.value === 'processSheets') {
    await fetchDetailProcessSheets()
  } else {
    await fetchDetailItems()
  }
}

const handleDetailTabChange = async () => {
  await fetchCurrentDetailTab()
}

const openSupplementDialog = () => {
  supplementResult.value = null
  supplementFiles.value = []
  supplementVersionFiles.value = []
  supplementVisible.value = true
}

const openReplaceDrawingDialog = () => {
  replaceDrawingResult.value = null
  replaceDrawingFiles.value = []
  replaceVersionDrawingFiles.value = []
  replaceDrawingVisible.value = true
}

const openOrderItemDataReplacementDialog = () => {
  if (orderItemDataReplacementDisabled.value) {
    ElMessage.warning('请先打开询价订单详情，或等待当前保存完成。')
    return
  }
  orderItemDataReplacementVisible.value = true
}

const openOrderItemAppendDialog = () => {
  if (!orderDetail.value?.orderInfo?.id) {
    ElMessage.warning('请先打开询价订单详情。')
    return
  }
  orderItemAppendExcelFile.value = null
  orderItemAppendDrawingFiles.value = []
  orderItemAppendVersionDrawingFiles.value = []
  orderItemAppendResult.value = null
  orderItemAppendVisible.value = true
}

const submitOrderItemAppend = async () => {
  const rfqId = orderDetail.value?.orderInfo?.id
  if (!rfqId) return ElMessage.warning('请先打开询价订单详情。')
  if (!orderItemAppendExcelFile.value) return ElMessage.warning('请先选择新增零件询价表。')

  return runAction(orderActionKeys.appendItems(rfqId), async () => {
    const formData = new FormData()
    formData.append('excelFile', orderItemAppendExcelFile.value)
    orderItemAppendDrawingFiles.value.forEach(file => formData.append('drawings', file))
    orderItemAppendVersionDrawingFiles.value.forEach(file => formData.append('versionDrawings', file))
    try {
      const response = await appendOrderItems(rfqId, formData)
      orderItemAppendResult.value = response
      orderItemAppendExcelFile.value = null
      orderItemAppendDrawingFiles.value = []
      orderItemAppendVersionDrawingFiles.value = []
      clearOrderItemDrafts()
      await refreshCurrentDetail()
      await loadOrders()
      ElMessage.success(response?.message || '新增零件导入完成。')
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || '新增零件导入失败。')
    }
  })
}

const handleOrderItemDataReplacementConfirmed = async () => {
  clearOrderItemDrafts()
  await refreshCurrentDetail()
  await loadOrders()
}

const changeOrderItemDiscardState = async (row) => {
  const rfqId = orderDetail.value?.orderInfo?.id
  const itemId = row?.itemId ?? row?.id
  if (!rfqId || !itemId) return
  const discarding = row.status !== '废弃'
  try {
    await ElMessageBox.confirm(
      discarding
        ? `确认废弃零件“${row.partNo || itemId}”吗？废弃后该零件不再参与后续核价、报价和提单选择，历史提单记录不受影响。`
        : `确认恢复零件“${row.partNo || itemId}”到废弃前状态吗？`,
      discarding ? '废弃零件' : '恢复零件',
      {
        type: discarding ? 'error' : 'warning',
        confirmButtonText: discarding ? '确认废弃' : '确认恢复',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }

  return runAction(orderActionKeys.itemStatus(rfqId, itemId), async () => {
    try {
      const response = discarding
        ? await discardOrderItem(rfqId, itemId)
        : await restoreOrderItem(rfqId, itemId)
      clearOrderItemDrafts()
      await refreshCurrentDetail()
      await loadOrders()
      ElMessage.success(response?.message || (discarding ? '零件已废弃。' : '零件已恢复。'))
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || (discarding ? '废弃零件失败。' : '恢复零件失败。'))
    }
  })
}

const refreshCurrentDetail = async () => {
  if (!orderDetail.value?.orderInfo?.id) return
  const currentId = orderDetail.value.orderInfo.id
  const latestDetail = await getOrder(currentId)
  orderDetail.value = { ...orderDetail.value, ...latestDetail }
  await fetchDrawingSummary(currentId)
  await fetchCurrentDetailTab()
}

const statusActionLocked = (row) => {
  if (!row) return false
  return RELEASED_ORDER_STATUSES.includes(row.status) || Number(row.ticketCount || 0) > 0
}

const rowStatusActions = (row) => {
  if (!row || statusActionLocked(row)) {
    return []
  }
  return ORDER_STATUS_ACTIONS.filter(action => action.status === row.status)
}

const handleStatusAction = async (row, action) => {
  if (!row?.id || !action?.handler) return
  try {
    await ElMessageBox.confirm(
      action.confirmMessage(row),
      action.confirmTitle,
      {
        type: action.type === 'danger' ? 'error' : 'warning',
        confirmButtonText: action.confirmButtonText,
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }
  await performStatusAction(row, action)
}

const performStatusAction = async (row, action) => {
  return runAction(orderActionKeys.statusChange(row.id), async () => {
    const response = await action.handler(row.id, { reason: action.reason })
    ElMessage.success(response.message || action.successMessage)
    await loadOrders()
    await refreshCurrentDetail()
  }).catch((error) => {
    if (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || action.errorMessage)
    }
  })
}

const submitSupplementDrawings = async () => {
  if (!orderDetail.value?.orderInfo?.id) return ElMessage.warning('请先打开询价订单详情。')
  if (supplementFiles.value.length === 0 && supplementVersionFiles.value.length === 0) return ElMessage.warning('请先选择需要补传的 PDF 图纸。')

  const rfqId = orderDetail.value.orderInfo.id
  return runAction(orderActionKeys.supplementDrawings(rfqId), async () => {
    const formData = new FormData()
    supplementFiles.value.forEach(file => formData.append('drawings', file))
    supplementVersionFiles.value.forEach(file => formData.append('versionDrawings', file))
    const response = await supplementDrawings(rfqId, formData)
    supplementResult.value = response
    ElMessage.success(response.message || '补传图纸处理完成。')
    supplementFiles.value = []
    supplementVersionFiles.value = []
    await refreshCurrentDetail()
    await loadOrders()
  }).catch((error) => {
    if (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || '补传图纸失败。')
    }
  })
}

const submitReplaceDrawings = async () => {
  if (!orderDetail.value?.orderInfo?.id) return ElMessage.warning('请先打开询价订单详情。')
  if (replaceDrawingFiles.value.length === 0 && replaceVersionDrawingFiles.value.length === 0) return ElMessage.warning('请先选择需要替换的 PDF 图纸。')

  const rfqId = orderDetail.value.orderInfo.id
  return runAction(orderActionKeys.replaceDrawings(rfqId), async () => {
    const formData = new FormData()
    replaceDrawingFiles.value.forEach(file => formData.append('drawings', file))
    replaceVersionDrawingFiles.value.forEach(file => formData.append('versionDrawings', file))
    try {
      const response = await replaceOrderDrawings(rfqId, formData)
      replaceDrawingResult.value = {
        ...(response || {}),
        boundCount: response?.replacedCount ?? response?.boundCount ?? 0,
        boundItems: response?.replacedItems || response?.boundItems || [],
        alreadyBoundCount: response?.alreadyBoundCount || 0,
        alreadyBoundItems: response?.alreadyBoundItems || []
      }
      replaceDrawingFiles.value = []
      replaceVersionDrawingFiles.value = []
      await refreshCurrentDetail()
      await loadOrders()
      if (Number(replaceDrawingResult.value.unmatchedCount || 0) > 0 || Number(replaceDrawingResult.value.skippedCount || 0) > 0) {
        ElMessage.warning(response?.message || '图纸替换处理完成，存在未匹配或跳过文件。')
      } else {
        ElMessage.success(response?.message || '图纸替换处理完成。')
      }
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || '图纸替换失败。')
    }
  })
}

const formatRoute = (route) => {
  if (!route) return '-'
  let list = Array.isArray(route) ? route : []
  if (typeof route === 'string') {
    try {
      list = JSON.parse(route)
    } catch {
      list = []
    }
  }
  if (list.length === 0) return '-'
  return list
    .map(item => `${item.processName || ''}${item.inputVal ? `(${item.inputVal})` : ''}`)
    .filter(Boolean)
    .join(' / ')
}
</script>

<style scoped>
.order-filter-group {
  width: 100%;
  justify-content: flex-end;
}

.customer-picker-trigger {
  justify-content: space-between;
  width: 220px;
  flex: 0 0 220px;
  min-height: 32px;
  padding: 0 12px;
  text-align: left;
}

.customer-picker-trigger :deep(> span) {
  display: block;
  width: 100%;
}

.customer-picker-content {
  align-items: center;
  display: flex;
  gap: 12px;
  min-width: 0;
  width: 100%;
}

.customer-picker-text,
.customer-picker-placeholder {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.customer-picker-placeholder {
  color: var(--erp-text-secondary);
}

.customer-picker-action {
  align-items: center;
  border-left: 1px solid var(--erp-border-soft);
  display: inline-flex;
  flex: none;
  color: var(--erp-primary);
  font-size: 12px;
  font-weight: 700;
  min-height: 24px;
  padding-left: 12px;
}

.search-input {
  width: 260px;
  flex: 0 0 260px;
}

.status-select {
  width: 150px;
  flex: 0 0 150px;
}

.order-table-surface {
  overflow: hidden;
}

.order-table-surface .erp-table-body {
  padding: var(--erp-space-3);
}

.order-table-surface .erp-empty-state {
  margin: var(--erp-space-4);
}

.order-row-actions {
  align-items: center;
  justify-content: flex-start;
  gap: 6px;
}

.status-action-button {
  margin-left: 0;
}

.status-locked-label {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 8px;
  border: 1px solid var(--erp-border-soft);
  border-radius: 6px;
  color: var(--erp-text-secondary);
  font-size: 12px;
  line-height: 1;
  background: var(--erp-surface-soft);
  cursor: default;
}

.detail-wrap {
  padding-right: 4px;
}

.detail-descriptions :deep(.el-descriptions__label),
.supplement-descriptions :deep(.el-descriptions__label) {
  color: var(--erp-text-secondary);
  background: #f5f7fb;
}

.detail-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
  flex-wrap: wrap;
}

.detail-actions :deep(.el-alert) {
  flex: 1;
  min-width: 240px;
}

.detail-tabs {
  margin-top: 16px;
}

.order-item-draft-tip {
  margin-bottom: 10px;
}

.order-item-status-cell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-width: 0;
}

.order-item-qty-input {
  display: block;
  width: 108px;
  margin: 0 auto;
}

:deep(.order-item-qty-column .cell) {
  overflow: visible;
  text-overflow: clip;
}

.order-item-date-picker,
.order-item-order-type-select {
  width: 100%;
}

.order-item-editor-changed :deep(.el-input__wrapper),
.order-item-editor-changed :deep(.el-select__wrapper) {
  box-shadow: 0 0 0 1px var(--erp-primary) inset;
}

.supplement-dialog {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.section-subtitle {
  margin-bottom: 8px;
  padding-left: 10px;
  border-left: 3px solid var(--erp-primary);
  color: var(--erp-text);
  font-size: 14px;
  font-weight: 700;
}

.custom-dropzone {
  width: 100%;
  padding: 28px 16px;
  border: 1px dashed #aab6c8;
  border-radius: 8px;
  background: var(--erp-surface-soft);
  text-align: center;
  transition: border-color 0.2s ease, background-color 0.2s ease;
}

.custom-dropzone:hover {
  border-color: var(--erp-primary);
  background: #f3f8fc;
}

.supplement-dropzone :deep(.el-icon),
.supplement-dropzone .el-icon {
  margin-bottom: 12px;
  color: #8c98aa;
  font-size: 42px;
}

.folder-select-row {
  margin-top: 12px;
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
}

.supplement-upload-actions {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}

.folder-select-button {
  min-height: 32px;
  min-width: 150px;
  padding-inline: 14px;
}

.hidden-input {
  display: none;
}

.supplement-file-stats {
  padding: 12px 14px;
  border: 1px solid #cfe8d2;
  border-radius: 8px;
  background: #f4fbf5;
  color: var(--erp-success);
}

.supplement-file-stats h4 {
  margin: 0 0 8px;
  font-size: 14px;
}

.supplement-pdf-list {
  list-style: none;
  padding: 0;
  margin: 0;
  max-height: 150px;
  overflow-y: auto;
}

.supplement-pdf-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 0;
  border-bottom: 1px solid #dcefe0;
  color: #256b2b;
}

.supplement-pdf-list li:last-child {
  border-bottom: none;
}

.supplement-result {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.result-list {
  list-style: none;
  padding: 0;
  margin: 0;
  max-height: 150px;
  overflow: auto;
  border: 1px solid var(--erp-border-soft);
  border-radius: 6px;
  background: var(--erp-surface-soft);
}

.result-list li {
  padding: 7px 10px;
  border-bottom: 1px solid var(--erp-border-soft);
  color: var(--erp-text);
}

.result-list li:last-child {
  border-bottom: none;
}

.result-main {
  font-weight: 700;
}

.result-sub {
  color: var(--erp-text-secondary);
}

.result-reason {
  color: var(--erp-warning);
}

@media (max-width: 1200px) {
  .order-filter-group {
    justify-content: flex-start;
  }
}

@media (max-width: 760px) {
  .customer-picker-trigger,
  .search-input,
  .status-select {
    width: 100%;
    flex-basis: 100%;
  }
}
</style>
