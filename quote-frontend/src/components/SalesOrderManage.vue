<template>
  <div class="erp-page sales-order-manage-page">
    <header class="erp-page-header">
      <div>
        <h1 class="erp-page-title">销售订单</h1>
        <p class="erp-page-description">默认显示全部客户销售订单，可选择客户进一步筛选订单、零件、提单行、工艺表行和图纸绑定状态。</p>
      </div>
    </header>

    <section class="erp-toolbar">
      <div class="erp-toolbar-group sales-order-filter-group">
        <CustomerAutocomplete
          v-model="selectedCustomer"
          :customer-code="selectedCustomerCode"
          customer-scope="sales-order"
          permission="market:sales-order:customer-select"
          @select="handleCustomerSelected"
          @clear="handleCustomerClear"
          @open-picker="customerPickerVisible = true"
        />
        <el-button v-if="selectedCustomer" plain @click="handleCustomerClear">清除客户</el-button>
        <el-select
          v-model="statusFilter"
          clearable
          placeholder="全部状态"
          class="status-filter"
          @change="handleSearch"
          @clear="handleSearch"
        >
          <el-option label="全部状态" value="" />
          <el-option v-for="status in SALES_ORDER_STATUSES" :key="status" :label="status" :value="status" />
        </el-select>
        <el-input
          v-model="keyword"
          clearable
          placeholder="搜索销售订单号 / 客户订单号 / 内部项目号"
          class="search-input"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button @click="handleSearch">搜索</el-button>
        <el-button type="primary" :loading="loading" @click="refreshOrders">刷新</el-button>
      </div>
    </section>

    <section class="erp-table-surface sales-order-table-surface">
      <div class="erp-table-body">
        <el-table :data="orders" border stripe height="600" v-loading="loading">
            <el-table-column prop="salesOrderNo" label="销售订单号" min-width="170" fixed show-overflow-tooltip />
            <el-table-column prop="customerOrderNo" label="客户订单号" min-width="150" show-overflow-tooltip />
            <el-table-column prop="customerProjectNo" label="客户项目号" min-width="140" show-overflow-tooltip />
            <el-table-column prop="internalProjectNo" label="内部项目号" min-width="140" show-overflow-tooltip />
            <el-table-column prop="documentTypeCode" label="单据类型编码" width="130" />
            <el-table-column prop="status" label="状态" width="120" align="center">
              <template #default="scope">
                <el-tag :type="statusTagType(scope.row.status)" size="small">{{ scope.row.status || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="零件" width="90" align="center">
              <template #default="scope">{{ scope.row.itemCount || 0 }}</template>
            </el-table-column>
            <el-table-column label="已提 / 总数" width="110" align="center">
              <template #default="scope">{{ scope.row.releasedQuantity || 0 }} / {{ scope.row.totalQuantity || 0 }}</template>
            </el-table-column>
            <el-table-column label="缺图" width="90" align="center">
              <template #default="scope">
                <el-tag :type="scope.row.drawingMissingCount > 0 ? 'warning' : 'success'" size="small">
                  {{ scope.row.drawingMissingCount || 0 }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="u9SyncStatus" label="U9状态" width="120" show-overflow-tooltip />
            <el-table-column prop="updatedAt" label="更新时间" width="180" show-overflow-tooltip />
            <el-table-column label="操作" width="330" align="center" fixed="right">
              <template #default="scope">
                <div class="erp-table-actions sales-order-row-actions">
                  <el-button size="small" @click="openDetail(scope.row)">查看销售订单</el-button>
                  <el-button
                    v-if="canResetU9Sync(scope.row)"
                    v-permission="'market:u9-sync:submit'"
                    size="small"
                    type="warning"
                    plain
                    :loading="isU9ResyncResetPending(scope.row)"
                    :disabled="isU9ResyncResetPending(scope.row)"
                    @click="handleResetU9Sync(scope.row)"
                  >
                    重新同步U9
                  </el-button>
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
                  <el-button
                    v-permission="SALES_ORDER_UPDATE_PERMISSION"
                    size="small"
                    :type="scope.row.linkedDiscarded ? 'success' : 'warning'"
                    plain
                    class="status-action-button"
                    :loading="isOrderLinkedStatusPending(scope.row)"
                    :disabled="isOrderLinkedStatusPending(scope.row)"
                    @click="handleLinkedStatusAction(scope.row)"
                  >
                    {{ scope.row.linkedDiscarded ? '关联恢复' : '关联废弃' }}
                  </el-button>
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
    </section>

    <el-drawer v-model="detailVisible" title="销售订单详情" size="76%">
      <div v-if="orderDetail" v-loading="detailLoading" class="detail-wrap">
        <el-descriptions :column="2" border class="detail-descriptions">
          <el-descriptions-item label="客户">{{ orderDetail.orderInfo?.customerName || selectedCustomer || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户编码">{{ orderDetail.orderInfo?.customerCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户简称">{{ orderDetail.orderInfo?.customerShortName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="单据类型编码">{{ orderDetail.orderInfo?.documentTypeCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="销售订单号">{{ orderDetail.orderInfo?.salesOrderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户订单号">{{ orderDetail.orderInfo?.customerOrderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户项目号">{{ orderDetail.orderInfo?.customerProjectNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="内部项目号">{{ orderDetail.orderInfo?.internalProjectNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ orderDetail.orderInfo?.status || '-' }}</el-descriptions-item>
          <el-descriptions-item label="U9状态">{{ orderDetail.orderInfo?.u9SyncStatus || '-' }}</el-descriptions-item>
        </el-descriptions>

        <section class="order-maintenance-panel">
          <div class="erp-section-title-row order-maintenance-title-row">
            <div>
              <h2 class="erp-section-title">订单信息维护</h2>
              <p class="erp-section-description">仅维护本地销售订单字段，不改写历史提单快照。</p>
            </div>
            <el-tooltip :disabled="!salesOrderEditLocked" :content="salesOrderEditLockedReason" placement="top">
              <span class="sales-order-action-wrap">
                <el-button
                  v-permission="SALES_ORDER_UPDATE_PERMISSION"
                  type="primary"
                  plain
                  :loading="orderEditSaving"
                  :disabled="salesOrderEditLocked"
                  @click="saveSalesOrderHeaderChanges"
                >
                  保存订单信息
                </el-button>
              </span>
            </el-tooltip>
          </div>
          <el-alert
            v-if="salesOrderEditLocked"
            class="order-maintenance-lock-alert"
            :title="salesOrderEditLockedReason"
            type="warning"
            :closable="false"
            show-icon
          />
          <el-form class="order-maintenance-form" :model="orderEditDraft" label-width="108px">
            <el-form-item label="单据类型">
              <el-input v-model="orderEditDraft.documentTypeCode" clearable :disabled="salesOrderEditLocked" placeholder="单据类型" />
            </el-form-item>
            <el-form-item label="下单日期">
              <el-date-picker
                v-model="orderEditDraft.salesOrderDate"
                type="date"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                clearable
                :disabled="salesOrderEditLocked"
                placeholder="下单日期"
              />
            </el-form-item>
            <el-form-item label="客户订单号">
              <el-input v-model="orderEditDraft.customerOrderNo" clearable :disabled="salesOrderEditLocked" placeholder="客户订单号" />
            </el-form-item>
            <el-form-item label="客户项目号">
              <el-input v-model="orderEditDraft.customerProjectNo" clearable :disabled="salesOrderEditLocked" placeholder="客户项目号" />
            </el-form-item>
            <el-form-item label="客户下单抬头">
              <el-input v-model="orderEditDraft.customerOrderTitle" clearable :disabled="salesOrderEditLocked" placeholder="客户下单抬头" />
            </el-form-item>
            <el-form-item label="订单类型">
              <el-select v-model="orderEditDraft.orderType" clearable :disabled="salesOrderEditLocked" placeholder="订单类型">
                <el-option v-for="type in SALES_ORDER_TYPES" :key="type" :label="type" :value="type" />
              </el-select>
            </el-form-item>
            <el-form-item label="内部项目号">
              <el-input v-model="orderEditDraft.internalProjectNo" clearable :disabled="salesOrderEditLocked" placeholder="内部项目号" />
            </el-form-item>
            <el-form-item label="订单未税总价">
              <el-input :model-value="formatMoney(displayOrderUntaxedTotalPrice)" class="order-total-readonly" readonly />
            </el-form-item>
            <el-form-item label="订单含税总价">
              <el-input :model-value="formatMoney(displayOrderTaxIncludedTotalPrice)" class="order-total-readonly" readonly />
            </el-form-item>
          </el-form>
        </section>

        <div class="auto-release-summary">
          <div class="auto-release-summary-card">
            <span>自动提单行</span>
            <strong>{{ autoReleaseSummary.ticketCount }}</strong>
          </div>
          <div class="auto-release-summary-card">
            <span>自动提单数量</span>
            <strong>{{ autoReleaseSummary.ticketQty }}</strong>
          </div>
          <div class="auto-release-summary-card">
            <span>已提 / 总数</span>
            <strong>{{ autoReleaseSummary.releasedQuantity }} / {{ autoReleaseSummary.totalQuantity }}</strong>
          </div>
          <div class="auto-release-summary-card">
            <span>工艺表行</span>
            <strong>{{ autoReleaseSummary.processSheetCount }}</strong>
          </div>
        </div>

        <div class="detail-actions">
          <el-alert
            :title="`当前缺图零件：${drawingSummary?.missingDrawingCount || 0} 个`"
            type="warning"
            :closable="false"
            show-icon
          />
          <el-button v-permission="'market:sales-order:supplement-drawing'" type="primary" plain :disabled="Number(drawingSummary?.missingDrawingCount || 0) === 0" @click="openSupplementDialog">
            补传图纸
          </el-button>
          <el-button
            v-permission="'market:sales-order:supplement-drawing'"
            plain
            :loading="replaceSalesOrderDrawingUploadPending"
            :disabled="replaceSalesOrderDrawingUploadPending"
            @click="openReplaceSalesOrderDrawingDialog"
          >
            导入替换图纸
          </el-button>
          <el-button
            v-permission="'market:sales-order:production-task-notice-export'"
            plain
            :loading="productionTaskNoticeExportPending"
            :disabled="productionTaskNoticeExportPending"
            @click="handleProductionTaskNoticeExport"
          >
            导出生产任务通知单
          </el-button>
        </div>

        <el-tabs v-model="activeDetailTab" class="detail-tabs" @tab-change="handleDetailTabChange">
          <el-tab-pane label="零件明细" name="items">
            <div class="item-maintenance-actions">
              <div class="item-maintenance-action-meta">
                <el-tag type="info">{{ detailItemTotal }} 行</el-tag>
                <el-tag v-if="dirtyItemCount > 0" type="warning">已修改 {{ dirtyItemCount }} 行</el-tag>
              </div>
              <div class="item-maintenance-action-buttons">
                <el-button
                  v-permission="'market:sales-order:item-detail-export'"
                  plain
                  :loading="itemDetailExportPending"
                  :disabled="itemDetailExportPending"
                  @click="handleSalesOrderItemDetailExport"
                >
                  导出明细表
                </el-button>
                <el-tooltip :disabled="!salesOrderEditLocked" :content="salesOrderEditLockedReason" placement="top">
                  <span class="sales-order-action-wrap">
                    <el-button
                      v-permission="SALES_ORDER_UPDATE_PERMISSION"
                      type="warning"
                      plain
                      :loading="itemPriceConversionPending('UNTAXED_TO_TAX_INCLUDED')"
                      :disabled="salesOrderEditLocked"
                      @click="handleSalesOrderPriceConversion('UNTAXED_TO_TAX_INCLUDED')"
                    >
                      未税转含税
                    </el-button>
                  </span>
                </el-tooltip>
                <el-tooltip :disabled="!salesOrderEditLocked" :content="salesOrderEditLockedReason" placement="top">
                  <span class="sales-order-action-wrap">
                    <el-button
                      v-permission="SALES_ORDER_UPDATE_PERMISSION"
                      type="success"
                      plain
                      :loading="itemPriceConversionPending('TAX_INCLUDED_TO_UNTAXED')"
                      :disabled="salesOrderEditLocked"
                      @click="handleSalesOrderPriceConversion('TAX_INCLUDED_TO_UNTAXED')"
                    >
                      含税转未税
                    </el-button>
                  </span>
                </el-tooltip>
                <el-tooltip :disabled="!salesOrderEditLocked" :content="salesOrderEditLockedReason" placement="top">
                  <span class="sales-order-action-wrap">
                    <el-button
                      v-permission="SALES_ORDER_UPDATE_PERMISSION"
                      type="primary"
                      plain
                      :loading="itemEditSaving"
                      :disabled="salesOrderEditLocked"
                      @click="saveSalesOrderItemChanges"
                    >
                      保存修改
                    </el-button>
                  </span>
                </el-tooltip>
                <el-tooltip :disabled="!salesOrderEditLocked" :content="salesOrderEditLockedReason" placement="top">
                  <span class="sales-order-action-wrap">
                    <el-button
                      v-permission="SALES_ORDER_UPDATE_PERMISSION"
                      plain
                      :loading="itemDataReplacementBusy"
                      :disabled="salesOrderEditLocked"
                      @click="openSalesOrderItemDataReplacementDialog"
                    >
                      表格数据替换
                    </el-button>
                  </span>
                </el-tooltip>
              </div>
            </div>
            <div class="item-field-replacement-row">
              <div class="item-field-replacement-title">整单零件字段替换</div>
              <div
                v-for="field in SALES_ORDER_ITEM_FIELD_REPLACEMENTS"
                :key="field.key"
                class="item-field-replacement-control"
              >
                <label>{{ field.label }}</label>
                <el-input
                  v-if="field.type !== 'select'"
                  v-model="itemFieldReplacementDrafts[field.key]"
                  clearable
                  :disabled="salesOrderEditLocked || isItemFieldReplacementPending(field.key)"
                  :placeholder="`${field.label}，留空保存可清空`"
                />
                <el-select
                  v-else
                  v-model="itemFieldReplacementDrafts[field.key]"
                  clearable
                  :disabled="salesOrderEditLocked || isItemFieldReplacementPending(field.key)"
                  placeholder="订单类型"
                >
                  <el-option v-for="type in SALES_ORDER_TYPES" :key="type" :label="type" :value="type" />
                </el-select>
                <el-tooltip :disabled="!salesOrderEditLocked" :content="salesOrderEditLockedReason" placement="top">
                  <span class="sales-order-action-wrap">
                    <el-button
                      v-permission="SALES_ORDER_UPDATE_PERMISSION"
                      type="primary"
                      plain
                      :loading="isItemFieldReplacementPending(field.key)"
                      :disabled="salesOrderEditLocked || isItemFieldReplacementPending(field.key)"
                      @click="saveSalesOrderItemFieldReplacement(field)"
                    >
                      保存
                    </el-button>
                  </span>
                </el-tooltip>
              </div>
            </div>
            <el-table :data="detailItemList" border stripe height="380" v-loading="detailItemsLoading">
              <el-table-column prop="partNo" label="图号" min-width="150" fixed show-overflow-tooltip />
              <el-table-column prop="lineNo" label="行号" width="80" align="center" />
              <el-table-column prop="version" label="版本" width="110" align="center">
                <template #default="{ row }">
                  <el-input
                    v-if="itemDrafts[row.id]"
                    v-model="itemDrafts[row.id].version"
                    class="item-edit-input"
                    clearable
                    :disabled="salesOrderEditLocked"
                    placeholder="版本"
                  />
                  <span v-else>{{ row.version || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="materialNo" label="物料号" min-width="130" show-overflow-tooltip />
              <el-table-column prop="materialNoStatus" label="物料号状态" width="120" show-overflow-tooltip />
              <el-table-column prop="matchStatus" label="匹配状态" width="120" align="center" show-overflow-tooltip>
                <template #default="scope">
                  <el-tag :type="matchStatusTagType(scope.row.matchStatus)" size="small">{{ scope.row.matchStatus || '-' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="matchMessage" label="匹配说明" min-width="180" show-overflow-tooltip>
                <template #default="scope">{{ scope.row.matchMessage || '-' }}</template>
              </el-table-column>
              <el-table-column prop="qty" label="数量" width="126" align="right" class-name="sales-order-item-qty-column">
                <template #default="{ row }">
                  <el-tooltip
                    v-if="itemDrafts[row.id]"
                    :disabled="!salesOrderItemQtyDisabledReason(row)"
                    :content="salesOrderItemQtyDisabledReason(row)"
                    placement="top"
                  >
                    <span class="sales-order-item-qty-input-trigger">
                      <el-input-number
                        v-model="itemDrafts[row.id].qty"
                        class="item-edit-input item-edit-number"
                        :controls="false"
                        :min="0"
                        :precision="0"
                        :disabled="Boolean(salesOrderItemQtyDisabledReason(row))"
                      />
                    </span>
                  </el-tooltip>
                  <span v-else>{{ row.qty || 0 }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="allocationType" label="拆行类型" width="120" align="center">
                <template #default="{ row }">{{ allocationTypeText(row.allocationType) }}</template>
              </el-table-column>
              <el-table-column prop="status" label="订单状态" width="140" align="center" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-input
                    v-if="itemDrafts[row.id]"
                    v-model="itemDrafts[row.id].status"
                    class="item-edit-input"
                    clearable
                    :disabled="salesOrderEditLocked"
                    placeholder="订单状态"
                  />
                  <span v-else>{{ row.status || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="U9/变更" width="330" align="center">
                <template #default="{ row }">
                  <div class="item-u9-change-exclusion-cell">
                    <el-tooltip
                      :content="row.u9ChangeExcluded ? '仅从 U9 同步和销售变更排除，不影响 MES 物料号、WMS 库存、已提/待产处理和 MES 生产下发' : '该零件仍参与 U9 同步和销售变更'"
                      placement="top"
                    >
                      <el-tag :type="row.u9ChangeExcluded ? 'warning' : 'success'" size="small">
                        {{ row.u9ChangeExcluded ? '已废弃' : '正常' }}
                      </el-tag>
                    </el-tooltip>
                    <el-tooltip :disabled="!salesOrderItemU9ChangeExclusionLocked" :content="salesOrderItemU9ChangeExclusionLockedReason" placement="top">
                      <span class="sales-order-action-wrap">
                        <el-button
                          v-permission="SALES_ORDER_UPDATE_PERMISSION"
                          size="small"
                          :type="row.u9ChangeExcluded ? 'success' : 'warning'"
                          plain
                          :loading="isItemU9ChangeExclusionPending(row)"
                          :disabled="salesOrderItemU9ChangeExclusionLocked || isItemU9ChangeExclusionPending(row)"
                          @click="handleSalesOrderItemU9ChangeExclusion(row)"
                        >
                          {{ row.u9ChangeExcluded ? '恢复' : '废弃' }}
                        </el-button>
                      </span>
                    </el-tooltip>
                    <el-tooltip :disabled="!salesOrderItemU9ChangeExclusionLocked" :content="salesOrderItemLinkedDiscardLockedReason" placement="top">
                      <span class="sales-order-action-wrap">
                        <el-button
                          v-permission="SALES_ORDER_UPDATE_PERMISSION"
                          size="small"
                          :type="row.linkedDiscarded ? 'success' : 'warning'"
                          plain
                          :loading="isItemLinkedDiscardPending(row)"
                          :disabled="salesOrderItemU9ChangeExclusionLocked || isItemLinkedDiscardPending(row)"
                          @click="handleSalesOrderItemLinkedDiscard(row)"
                        >
                          {{ row.linkedDiscarded ? '关联恢复' : '关联废弃' }}
                        </el-button>
                      </span>
                    </el-tooltip>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="图纸" width="90" align="center">
                <template #default="scope">
                  <el-tag :type="scope.row.drawingUrl ? 'success' : 'warning'" size="small">
                    {{ scope.row.drawingUrl ? '已绑定' : '缺图' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="图纸预览" width="100" align="center">
                <template #default="scope">
                  <el-button
                    v-if="scope.row.drawingUrl"
                    type="primary"
                    size="small"
                    plain
                    @click.stop="openDrawingPreview(scope.row)"
                  >
                    预览
                  </el-button>
                  <el-tag v-else type="info" size="small">无图</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="customerOrderTitle" label="客户下单抬头" min-width="170" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-input
                    v-if="itemDrafts[row.id]"
                    v-model="itemDrafts[row.id].customerOrderTitle"
                    class="item-edit-input"
                    clearable
                    :disabled="salesOrderEditLocked"
                    placeholder="客户下单抬头"
                  />
                  <span v-else>{{ row.customerOrderTitle || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="orderType" label="订单类型" width="140" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-select
                    v-if="itemDrafts[row.id]"
                    v-model="itemDrafts[row.id].orderType"
                    class="item-edit-input"
                    clearable
                    :disabled="salesOrderEditLocked"
                    placeholder="订单类型"
                  >
                    <el-option v-for="type in SALES_ORDER_TYPES" :key="type" :label="type" :value="type" />
                  </el-select>
                  <span v-else>{{ row.orderType || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="customerOrderNo" label="客户订单号" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-input
                    v-if="itemDrafts[row.id]"
                    v-model="itemDrafts[row.id].customerOrderNo"
                    class="item-edit-input"
                    clearable
                    :disabled="salesOrderEditLocked"
                    placeholder="客户订单号"
                  />
                  <span v-else>{{ row.customerOrderNo || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="customerProjectNo" label="客户项目号" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-input
                    v-if="itemDrafts[row.id]"
                    v-model="itemDrafts[row.id].customerProjectNo"
                    class="item-edit-input"
                    clearable
                    :disabled="salesOrderEditLocked"
                    placeholder="客户项目号"
                  />
                  <span v-else>{{ row.customerProjectNo || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="internalProjectNo" label="内部项目号" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-input
                    v-if="itemDrafts[row.id]"
                    v-model="itemDrafts[row.id].internalProjectNo"
                    class="item-edit-input"
                    clearable
                    :disabled="salesOrderEditLocked"
                    placeholder="内部项目号"
                  />
                  <span v-else>{{ row.internalProjectNo || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="customerDeliveryDate" label="客户交期" width="148">
                <template #default="{ row }">
                  <el-date-picker
                    v-if="itemDrafts[row.id]"
                    v-model="itemDrafts[row.id].customerDeliveryDate"
                    class="item-edit-input item-edit-date"
                    type="date"
                    format="YYYY-MM-DD"
                    value-format="YYYY-MM-DD"
                    clearable
                    :disabled="salesOrderEditLocked"
                    placeholder="客户交期"
                  />
                  <span v-else>{{ row.customerDeliveryDate || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="internalDeliveryDate" label="内部交期" width="148">
                <template #default="{ row }">
                  <el-date-picker
                    v-if="itemDrafts[row.id]"
                    v-model="itemDrafts[row.id].internalDeliveryDate"
                    class="item-edit-input item-edit-date"
                    type="date"
                    format="YYYY-MM-DD"
                    value-format="YYYY-MM-DD"
                    clearable
                    :disabled="salesOrderEditLocked"
                    placeholder="内部交期"
                  />
                  <span v-else>{{ row.internalDeliveryDate || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="materialName" label="材料" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-input
                    v-if="itemDrafts[row.id]"
                    v-model="itemDrafts[row.id].materialName"
                    class="item-edit-input"
                    clearable
                    :disabled="salesOrderEditLocked"
                    placeholder="材料"
                  />
                  <span v-else>{{ row.materialName || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="description" label="物料描述" min-width="220" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-input
                    v-if="itemDrafts[row.id]"
                    v-model="itemDrafts[row.id].description"
                    class="item-edit-input"
                    clearable
                    :disabled="salesOrderEditLocked"
                    placeholder="物料描述"
                  />
                  <span v-else>{{ row.description || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="surfaceTreatment" label="表面处理" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-input
                    v-if="itemDrafts[row.id]"
                    v-model="itemDrafts[row.id].surfaceTreatment"
                    class="item-edit-input"
                    clearable
                    :disabled="salesOrderEditLocked"
                    placeholder="表面处理"
                  />
                  <span v-else>{{ row.surfaceTreatment || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="未税单价" width="110" align="right">
                <template #default="{ row }">
                  <el-input
                    v-if="itemDrafts[row.id]"
                    v-model="itemDrafts[row.id].untaxedUnitPrice"
                    class="item-edit-input item-edit-money"
                    inputmode="decimal"
                    :disabled="salesOrderEditLocked"
                    placeholder="未税单价"
                  />
                  <span v-else>{{ formatMoney(row.untaxedUnitPrice) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="含税单价" width="110" align="right">
                <template #default="{ row }">
                  <el-input
                    v-if="itemDrafts[row.id]"
                    v-model="itemDrafts[row.id].taxIncludedUnitPrice"
                    class="item-edit-input item-edit-money"
                    inputmode="decimal"
                    :disabled="salesOrderEditLocked"
                    placeholder="含税单价"
                  />
                  <span v-else>{{ formatMoney(row.taxIncludedUnitPrice) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="未税总价" width="120" align="right">
                <template #default="{ row }">{{ formatMoney(lineUntaxedTotal(row, itemDrafts[row.id])) }}</template>
              </el-table-column>
              <el-table-column label="含税总价" width="120" align="right">
                <template #default="{ row }">{{ formatMoney(lineTaxIncludedTotal(row, itemDrafts[row.id])) }}</template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-input
                    v-if="itemDrafts[row.id]"
                    v-model="itemDrafts[row.id].remark"
                    class="item-edit-input"
                    clearable
                    :disabled="salesOrderEditLocked"
                    placeholder="备注"
                  />
                  <span v-else>{{ row.remark || '-' }}</span>
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
            <SalesOrderItemDataReplacementDialog
              v-model="itemDataReplacementVisible"
              :sales-order-id="orderDetail?.orderInfo?.id"
              :disabled="salesOrderEditLocked"
              @busy-change="itemDataReplacementBusy = $event"
              @confirmed="handleSalesOrderItemDataReplacementConfirmed"
            />
          </el-tab-pane>

          <el-tab-pane label="锁库拆行" name="allocationSplits">
            <div class="item-maintenance-actions allocation-split-toolbar">
              <div class="item-maintenance-action-meta">
                <span class="erp-toolbar-description">按本订单数量大于 0 的待产 Allocation 获取库存，结果会拆分为锁库数量和剩余待产数量。</span>
              </div>
              <div class="item-maintenance-action-buttons">
                <el-button
                  v-permission="SALES_ORDER_WMS_ALLOCATE_PERMISSION"
                  type="primary"
                  plain
                  :loading="wmsAllocationPending"
                  @click="handleWmsAllocation"
                >
                  获取库存信息
                </el-button>
                <el-button
                  v-permission="SALES_ORDER_WMS_ALLOCATE_PERMISSION"
                  type="primary"
                  plain
                  :loading="wmsAllocationPending"
                  :disabled="selectedAllocationSplitIds.length === 0 || wmsAllocationPending"
                  @click="handleSelectedWmsAllocation"
                >
                  批量获取库存信息
                </el-button>
                <el-button
                  v-permission="SALES_ORDER_UPDATE_PERMISSION"
                  type="success"
                  plain
                  :disabled="allocationSplitDirtyCount === 0"
                  :loading="allocationSplitSaving"
                  @click="saveAllocationSplitChanges"
                >
                  保存拆分数量
                </el-button>
              </div>
            </div>
            <div v-if="wmsAllocationTask" class="sales-order-wms-task-result">
              <div class="erp-toolbar-group">
                <el-tag :type="wmsTaskTagType(wmsAllocationTask.status)" effect="plain">
                  {{ wmsTaskStatusText(wmsAllocationTask.status) }}
                </el-tag>
                <span>库存任务 #{{ wmsAllocationTask.id }} · {{ wmsAllocationTask.errorMessage || '可选库存获取不会影响未获取库存零件的 MES 全量生产。' }}</span>
                <el-button
                  v-if="['PENDING', 'RUNNING'].includes(String(wmsAllocationTask.status || '').toUpperCase())"
                  size="small"
                  :loading="wmsAllocationRefreshing"
                  @click="() => refreshWmsAllocationTask()"
                >
                  刷新任务
                </el-button>
              </div>
              <el-table v-if="wmsAllocationRows.length" :data="wmsAllocationRows" border size="small" max-height="220">
                <el-table-column prop="allocationId" label="Allocation" width="110" />
                <el-table-column prop="ticketId" label="来源提单" width="100" />
                <el-table-column label="处理结果" width="120">
                  <template #default="{ row }">
                    <el-tag :type="wmsTaskTagType(row.status)" size="small">{{ wmsTaskStatusText(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="message" label="说明" min-width="240" show-overflow-tooltip />
                <el-table-column prop="externalBizId" label="WMS批次号" min-width="180" show-overflow-tooltip />
              </el-table>
            </div>
            <el-table
              :data="allocationSplitList"
              border
              stripe
              height="330"
              v-loading="allocationSplitsLoading"
              row-key="allocationId"
              @selection-change="handleAllocationSplitSelectionChange"
            >
              <el-table-column
                type="selection"
                width="48"
                fixed
                align="center"
                :selectable="isAllocationWmsSelectable"
              />
              <el-table-column prop="salesOrderItemLineNo" label="销售订单行号" width="120" align="center" />
              <el-table-column prop="allocationId" label="Allocation" width="110" align="center" />
              <el-table-column prop="ticketId" label="提单行" width="100" />
              <el-table-column prop="allocationType" label="拆行类型" width="130">
                <template #default="{ row }">{{ allocationTypeText(row.allocationType) }}</template>
              </el-table-column>
              <el-table-column prop="partNo" label="图号" min-width="150" show-overflow-tooltip />
              <el-table-column prop="version" label="版本" width="80" align="center" />
              <el-table-column prop="materialNo" label="物料号" min-width="130" show-overflow-tooltip />
              <el-table-column prop="qty" label="拆分数量" width="130" align="center">
                <template #default="{ row }">
                  <el-input-number
                    v-if="allocationSplitDrafts[row.allocationId]"
                    v-model="allocationSplitDrafts[row.allocationId].qty"
                    :min="0"
                    :precision="0"
                    :controls="false"
                    :disabled="salesOrderEditLocked"
                    size="small"
                  />
                  <span v-else>{{ row.qty ?? 0 }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="ticketQty" label="提单总量" width="100" align="center" />
              <el-table-column prop="internalProjectNo" label="内部项目号" min-width="140" show-overflow-tooltip />
              <el-table-column prop="customerOrderNo" label="客户订单号" min-width="140" show-overflow-tooltip />
              <el-table-column prop="customerProjectNo" label="客户项目号" min-width="140" show-overflow-tooltip />
              <el-table-column prop="wmsReservationTicketId" label="WMS锁库行" width="110" />
              <el-table-column prop="mesProductionStatus" label="MES生产状态" width="130" />
              <el-table-column prop="stockPickingStatus" label="提库存工单" width="130" />
            </el-table>
            <div class="erp-pagination detail-pagination">
              <el-pagination
                v-model:current-page="allocationSplitPagination.page"
                v-model:page-size="allocationSplitPagination.pageSize"
                :page-sizes="[20, 50, 100]"
                layout="total, sizes, prev, pager, next, jumper"
                :total="allocationSplitTotal"
                background
                :pager-count="7"
                @size-change="allocationSplitPagination.page = 1"
              />
            </div>
          </el-tab-pane>

          <el-tab-pane label="提单行" name="tickets">
            <el-table :data="detailTicketList" border stripe height="330" v-loading="detailTicketsLoading">
              <el-table-column prop="internalProjectNo" label="内部项目号" min-width="130" show-overflow-tooltip />
              <el-table-column prop="partNo" label="图号" min-width="150" show-overflow-tooltip />
              <el-table-column prop="version" label="版本" width="80" align="center" />
              <el-table-column prop="materialNo" label="物料号" min-width="130" show-overflow-tooltip />
              <el-table-column prop="qty" label="数量" width="80" align="center" />
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
            <el-table :data="detailProcessSheetList" border stripe height="330" v-loading="detailProcessSheetsLoading">
              <el-table-column prop="internalProjectNo" label="内部项目号" min-width="130" show-overflow-tooltip />
              <el-table-column prop="partNo" label="图号" min-width="150" show-overflow-tooltip />
              <el-table-column prop="version" label="版本" width="80" align="center" />
              <el-table-column prop="description" label="物料描述" min-width="180" show-overflow-tooltip />
              <el-table-column prop="qty" label="数量" width="80" align="center" />
              <el-table-column prop="materialName" label="材料" min-width="130" show-overflow-tooltip />
              <el-table-column prop="surfaceTreatment" label="表面处理" min-width="130" show-overflow-tooltip />
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
      v-model:folder-files="supplementDrawingFolderFiles"
      v-model:folder-name="supplementDrawingFolderName"
      title="补传销售订单图纸"
      :order-label="orderDetail?.orderInfo?.salesOrderNo || '-'"
      :customer-name="orderDetail?.orderInfo?.customerName || selectedCustomer || '-'"
      :missing-items="missingDrawingItems"
      :result="supplementResult"
      :pending="supplementUploadPending"
      :permission="'market:sales-order:supplement-drawing'"
      @submit="submitSupplementDrawings"
    />
    <SupplementDrawingDialog
      v-model="replaceSalesOrderDrawingVisible"
      v-model:files="replaceSalesOrderDrawingFiles"
      v-model:version-files="replaceSalesOrderVersionDrawingFiles"
      v-model:folder-files="replaceSalesOrderDrawingFolderFiles"
      v-model:folder-name="replaceSalesOrderDrawingFolderName"
      title="导入替换图纸"
      submit-text="上传并替换"
      list-title="当前销售订单零件"
      part-upload-title="按图号替换"
      version-upload-title="按图号+版本替换"
      part-pending-title="按图号待替换图纸"
      version-pending-title="按图号+版本待替换图纸"
      bound-result-label="已替换"
      already-bound-result-label="已跳过"
      :order-label="orderDetail?.orderInfo?.salesOrderNo || '-'"
      :customer-name="orderDetail?.orderInfo?.customerName || selectedCustomer || '-'"
      :missing-items="detailItemList"
      :result="replaceSalesOrderDrawingResult"
      :pending="replaceSalesOrderDrawingUploadPending"
      :permission="'market:sales-order:supplement-drawing'"
      @submit="submitReplaceSalesOrderDrawings"
    />
    <CustomerPickerDialog
      v-model="customerPickerVisible"
      :current-customer="selectedCustomer"
      :current-customer-code="selectedCustomerCode"
      customer-scope="sales-order"
      @select="handleCustomerSelected"
    />

    <DrawingFolderViewer
      v-model="folderViewerVisible"
      :folder-url="currentDrawingFolderUrl"
      :title="`附件文件夹：${currentDrawingFolderTitle || '未命名文件夹'}`"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import CustomerPickerDialog from './CustomerPickerDialog.vue'
import CustomerAutocomplete from './CustomerAutocomplete.vue'
import SupplementDrawingDialog from './order/SupplementDrawingDialog.vue'
import SalesOrderItemDataReplacementDialog from './sales-order/SalesOrderItemDataReplacementDialog.vue'
import DrawingFolderViewer from './common/DrawingFolderViewer.vue'
import { usePendingAction } from '../composables/usePendingAction'
import { isDrawingFolderUrl, openAuthenticatedFileInNewWindow } from '../api'
import {
  convertSalesOrderTaxIncludedToUntaxed,
  convertSalesOrderUntaxedToTaxIncluded,
  discardSalesOrder,
  discardSalesOrderLinked,
  discardSalesOrderItemLinked,
  discardSalesOrderItemForU9Change,
  downloadSalesOrderItemDetails,
  downloadSalesOrderProductionTaskNotice,
  getSalesOrder,
  getSalesOrderDrawingSummary,
  listSalesOrderAllocationSplits,
  listSalesOrderItems,
  listSalesOrders,
  listSalesOrderProcessSheets,
  listSalesOrderTickets,
  saveSalesOrderHeader,
  saveSalesOrderAllocationSplits,
  saveSalesOrderItems,
  replaceSalesOrderItemField,
  restoreSalesOrder,
  restoreSalesOrderLinked,
  restoreSalesOrderItemLinked,
  restoreSalesOrderItemForU9Change,
  resetSalesOrderU9SyncStatus,
  replaceSalesOrderDrawings,
  submitSalesOrderWmsAllocationBatch,
  supplementSalesOrderDrawings
} from '../services/orderApi'
import { getTask } from '../services/taskApi'

const { isPending, runAction } = usePendingAction()
const route = useRoute()

const SALES_ORDER_STATUSES = ['已报价', '部分已提单', '已生成提单', '废弃']
const SALES_ORDER_TYPES = ['备货订单', '正式订单']
const SALES_ORDER_UPDATE_PERMISSION = 'market:sales-order:update'
const SALES_ORDER_WMS_ALLOCATE_PERMISSION = 'market:sales-order:wms-allocate'
const SALES_ORDER_EDIT_LOCKED_U9_STATUSES = ['同步中', '结果待确认']
const SALES_ORDER_PRICE_CONVERSIONS = {
  UNTAXED_TO_TAX_INCLUDED: {
    label: '未税转含税',
    sourceLabel: '未税单价',
    targetLabel: '含税单价',
    handler: convertSalesOrderUntaxedToTaxIncluded
  },
  TAX_INCLUDED_TO_UNTAXED: {
    label: '含税转未税',
    sourceLabel: '含税单价',
    targetLabel: '未税单价',
    handler: convertSalesOrderTaxIncludedToUntaxed
  }
}
const salesOrderActionKeys = {
  statusChange: (orderId) => `sales-order:status-change:${orderId || 'none'}`,
  linkedStatusChange: (orderId) => `sales-order:linked-status-change:${orderId || 'none'}`,
  headerSave: (orderId) => `sales-order:header-save:${orderId || 'none'}`,
  itemSave: (orderId) => `sales-order:item-save:${orderId || 'none'}`,
  allocationSplitSave: (orderId) => `sales-order:allocation-split-save:${orderId || 'none'}`,
  itemFieldReplacement: (orderId, field) => `sales-order:item-field-replacement:${field || 'none'}:${orderId || 'none'}`,
  itemPriceConversion: (orderId, direction) => `sales-order:item-price-conversion:${direction}:${orderId || 'none'}`,
  itemU9ChangeExclusion: (itemId) => `sales-order:item-u9-change-exclusion:${itemId || 'none'}`,
  itemLinkedDiscard: (itemId) => `sales-order:item-linked-discard:${itemId || 'none'}`,
  u9ResyncReset: (orderId) => `sales-order:u9-resync-reset:${orderId || 'none'}`,
  itemDetailExport: (orderId) => `sales-order:item-detail-export:${orderId || 'none'}`,
  supplementDrawings: (orderId) => `sales-order:supplement-drawings:${orderId || 'none'}`,
  salesOrderDrawingReplace: (orderId) => `sales-order:drawing-replace:${orderId || 'none'}`,
  productionTaskNoticeExport: (orderId) => `sales-order:production-task-notice-export:${orderId || 'none'}`,
  wmsAllocation: (orderId) => `sales-order:wms-allocation:${orderId || 'none'}`
}
const SALES_ORDER_HEADER_EDITABLE_FIELDS = [
  'documentTypeCode',
  'salesOrderDate',
  'customerOrderNo',
  'customerProjectNo',
  'customerOrderTitle',
  'orderType',
  'internalProjectNo'
]
const SALES_ORDER_ITEM_EDITABLE_FIELDS = [
  'version',
  'qty',
  'status',
  'untaxedUnitPrice',
  'taxIncludedUnitPrice',
  'customerDeliveryDate',
  'internalDeliveryDate',
  'customerOrderNo',
  'customerProjectNo',
  'customerOrderTitle',
  'internalProjectNo',
  'orderType',
  'description',
  'materialName',
  'surfaceTreatment',
  'remark'
]
const SALES_ORDER_ITEM_FIELD_REPLACEMENTS = [
  { key: 'customerOrderTitle', label: '客户下单抬头', type: 'text' },
  { key: 'customerOrderNo', label: '客户订单号', type: 'text' },
  { key: 'customerProjectNo', label: '客户项目号', type: 'text' },
  { key: 'internalProjectNo', label: '内部项目号', type: 'text' },
  { key: 'orderType', label: '订单类型', type: 'select' }
]
const SALES_ORDER_STATUS_ACTIONS = [
  {
    status: '已生成提单',
    label: '废弃',
    permission: 'market:sales-order:discard',
    handler: discardSalesOrder,
    type: 'danger',
    reason: '销售订单：废弃订单',
    successMessage: '销售订单已废弃。',
    errorMessage: '废弃销售订单失败。',
    confirmTitle: '废弃销售订单',
    confirmButtonText: '确认废弃',
    confirmMessage: (row) => `确认废弃销售订单「${row.salesOrderNo || row.id}」吗？系统只调整销售订单头和零件状态，不清空价格、数量、交期、图纸、提单快照或物料号。`
  },
  {
    status: '废弃',
    label: '恢复为已生成提单',
    permission: 'market:sales-order:restore',
    handler: restoreSalesOrder,
    type: 'success',
    reason: '销售订单：恢复废弃订单',
    successMessage: '销售订单已恢复为已生成提单。',
    errorMessage: '恢复销售订单失败。',
    confirmTitle: '恢复销售订单',
    confirmButtonText: '确认恢复',
    confirmMessage: (row) => `确认将废弃销售订单「${row.salesOrderNo || row.id}」恢复为“已生成提单”吗？恢复后可继续进入 U9 同步选择订单池。`
  }
]


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
const detailTicketList = ref([])
const allocationSplitList = ref([])
const allocationSplitDrafts = reactive({})
const allocationSplitSaving = ref(false)
const wmsAllocationTask = ref(null)
const wmsAllocationRefreshing = ref(false)
const detailProcessSheetList = ref([])
const drawingSummary = ref(null)
const detailItemsLoading = ref(false)
const detailTicketsLoading = ref(false)
const allocationSplitsLoading = ref(false)
const detailProcessSheetsLoading = ref(false)
const detailItemPagination = reactive({ page: 1, pageSize: 20 })
const detailTicketPagination = reactive({ page: 1, pageSize: 20 })
const allocationSplitPagination = reactive({ page: 1, pageSize: 20 })
const detailProcessSheetPagination = reactive({ page: 1, pageSize: 20 })
const detailItemTotal = ref(0)
const detailTicketTotal = ref(0)
const allocationSplitTotal = ref(0)
const selectedAllocationSplitRows = ref([])
const selectedAllocationSplitIds = computed(() => selectedAllocationSplitRows.value
  .map(row => Number(row?.allocationId))
  .filter(id => Number.isInteger(id) && id > 0))
const detailProcessSheetTotal = ref(0)
const supplementVisible = ref(false)
const supplementFiles = ref([])
const supplementVersionFiles = ref([])
const supplementDrawingFolderFiles = ref([])
const supplementDrawingFolderName = ref('')
const supplementResult = ref(null)
const replaceSalesOrderDrawingVisible = ref(false)
const replaceSalesOrderDrawingFiles = ref([])
const replaceSalesOrderVersionDrawingFiles = ref([])
const replaceSalesOrderDrawingFolderFiles = ref([])
const replaceSalesOrderDrawingFolderName = ref('')
const replaceSalesOrderDrawingResult = ref(null)
const folderViewerVisible = ref(false)
const currentDrawingFolderUrl = ref('')
const currentDrawingFolderTitle = ref('')
const customerPickerVisible = ref(false)
const orderEditDraft = reactive({
  documentTypeCode: '',
  salesOrderDate: '',
  customerOrderNo: '',
  customerProjectNo: '',
  customerOrderTitle: '',
  orderType: '',
  internalProjectNo: ''
})
const itemDrafts = reactive({})
const itemFieldReplacementDrafts = reactive(Object.fromEntries(
  SALES_ORDER_ITEM_FIELD_REPLACEMENTS.map(field => [field.key, ''])
))
const itemDataReplacementVisible = ref(false)
const itemDataReplacementBusy = ref(false)

const orderEditSaving = computed(() => isPending(salesOrderActionKeys.headerSave(orderDetail.value?.orderInfo?.id)))
const itemEditSaving = computed(() => isPending(salesOrderActionKeys.itemSave(orderDetail.value?.orderInfo?.id)))
const allocationSplitDirtyCount = computed(() => allocationSplitList.value.reduce((count, row) => {
  const draft = allocationSplitDrafts[row?.allocationId]
  return draft && Number(draft.qty ?? 0) !== Number(row?.qty ?? 0) ? count + 1 : count
}, 0))
const isItemFieldReplacementPending = (field) => isPending(salesOrderActionKeys.itemFieldReplacement(orderDetail.value?.orderInfo?.id, field))
const itemPriceConversionPending = (direction) => isPending(salesOrderActionKeys.itemPriceConversion(orderDetail.value?.orderInfo?.id, direction))

const missingDrawingItems = computed(() => drawingSummary.value?.missingItems || detailItemList.value.filter(item => !item.drawingUrl))
const supplementUploadPending = computed(() => isPending(salesOrderActionKeys.supplementDrawings(orderDetail.value?.orderInfo?.id)))
const replaceSalesOrderDrawingUploadPending = computed(() => isPending(salesOrderActionKeys.salesOrderDrawingReplace(orderDetail.value?.orderInfo?.id)))
const itemDetailExportPending = computed(() => isPending(salesOrderActionKeys.itemDetailExport(orderDetail.value?.orderInfo?.id)))
const productionTaskNoticeExportPending = computed(() => isPending(salesOrderActionKeys.productionTaskNoticeExport(orderDetail.value?.orderInfo?.id)))
const salesOrderEditLocked = computed(() => SALES_ORDER_EDIT_LOCKED_U9_STATUSES.includes(String(orderDetail.value?.orderInfo?.u9SyncStatus || '').trim()))
const salesOrderItemQtyDisabledReason = (row) => {
  if (salesOrderEditLocked.value) return salesOrderEditLockedReason.value
  return row?.allocationId == null ? '物料号获取成功并关联 Allocation 后才能修改数量' : ''
}
const salesOrderEditLockedReason = computed(() => salesOrderEditLocked.value ? `当前 U9 状态不允许修改销售订单：${orderDetail.value?.orderInfo?.u9SyncStatus || '-'}` : '')
const salesOrderItemU9ChangeExclusionLocked = computed(() => ['同步中', '结果待确认'].includes(String(orderDetail.value?.orderInfo?.u9SyncStatus || '').trim()))
const salesOrderItemU9ChangeExclusionLockedReason = computed(() => salesOrderItemU9ChangeExclusionLocked.value ? `当前 U9 状态不能废弃或恢复销售订单零件：${orderDetail.value?.orderInfo?.u9SyncStatus || '-'}` : '')
const salesOrderItemLinkedDiscardLockedReason = computed(() => salesOrderItemU9ChangeExclusionLocked.value ? `当前 U9 状态不能关联废弃或恢复销售订单零件：${orderDetail.value?.orderInfo?.u9SyncStatus || '-'}` : '')
const dirtyItemCount = computed(() => detailItemList.value.filter(row => hasItemDraftChanges(row)).length)
const autoReleaseSummary = computed(() => {
  const summary = orderDetail.value?.summary || {}
  return {
    ticketCount: numberOrZero(summary.ticketCount),
    ticketQty: numberOrZero(summary.ticketQty),
    releasedQuantity: numberOrZero(summary.releasedQuantity),
    totalQuantity: numberOrZero(summary.totalQuantity),
    processSheetCount: numberOrZero(summary.processSheetCount)
  }
})
const currentPageOrderTotalDeltas = computed(() => detailItemList.value.reduce((deltas, row) => {
  const draft = itemDrafts[row.id]
  if (!draft) return deltas
  if (itemPriceDraftChanged(row, draft, 'untaxedUnitPrice')) {
    deltas.untaxed += lineUntaxedTotal(row, itemDrafts[row.id]) - lineUntaxedTotal(row)
  }
  if (itemPriceDraftChanged(row, draft, 'taxIncludedUnitPrice')) {
    deltas.taxIncluded += lineTaxIncludedTotal(row, itemDrafts[row.id]) - lineTaxIncludedTotal(row)
  }
  return deltas
}, { untaxed: 0, taxIncluded: 0 }))
const displayOrderUntaxedTotalPrice = computed(() => {
  const summary = orderDetail.value?.summary || {}
  return numberOrZero(summary.orderUntaxedTotalPrice) + currentPageOrderTotalDeltas.value.untaxed
})
const displayOrderTaxIncludedTotalPrice = computed(() => {
  const summary = orderDetail.value?.summary || {}
  return numberOrZero(summary.orderTaxIncludedTotalPrice) + currentPageOrderTotalDeltas.value.taxIncluded
})

watch([page, pageSize], () => {
  loadOrders()
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

watch(() => [allocationSplitPagination.page, allocationSplitPagination.pageSize], () => {
  if (detailVisible.value && activeDetailTab.value === 'allocationSplits') {
    fetchAllocationSplits()
  }
})

watch(() => [detailProcessSheetPagination.page, detailProcessSheetPagination.pageSize], () => {
  if (detailVisible.value && activeDetailTab.value === 'processSheets') {
    fetchDetailProcessSheets()
  }
})

const loadOrders = async () => {
  loading.value = true
  try {
    const response = await listSalesOrders({
      customerCode: selectedCustomerCode.value || undefined,
      customerName: selectedCustomer.value || undefined,
      keyword: keyword.value.trim() || undefined,
      status: statusFilter.value || undefined,
      page: page.value,
      pageSize: pageSize.value
    })
    orders.value = response.records || []
    total.value = response.total || 0
  } catch (error) {
    console.error(error)
    ElMessage.error('销售订单列表加载失败。')
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

const handleCustomerClear = async () => {
  selectedCustomer.value = ''
  selectedCustomerCode.value = ''
  keyword.value = ''
  statusFilter.value = ''
  page.value = 1
  orderDetail.value = null
  detailVisible.value = false
  resetOrderEditDraft()
  resetItemDrafts()
  await loadOrders()
}

const resetDetailPagination = () => {
  activeDetailTab.value = 'items'
  detailItemPagination.page = 1
  detailTicketPagination.page = 1
  allocationSplitPagination.page = 1
  detailProcessSheetPagination.page = 1
  detailItemList.value = []
  detailTicketList.value = []
  allocationSplitList.value = []
  Object.keys(allocationSplitDrafts).forEach(key => delete allocationSplitDrafts[key])
  wmsAllocationTask.value = null
  detailProcessSheetList.value = []
  detailItemTotal.value = 0
  detailTicketTotal.value = 0
  allocationSplitTotal.value = 0
  detailProcessSheetTotal.value = 0
  drawingSummary.value = null
  resetItemDrafts()
}

const normalizeRouteSalesOrderId = (value) => {
  const raw = Array.isArray(value) ? value[0] : value
  const id = Number(raw)
  return Number.isInteger(id) && id > 0 ? id : null
}

const openDetailBySalesOrderId = async (salesOrderId, options = {}) => {
  salesOrderId = normalizeRouteSalesOrderId(salesOrderId)
  if (!salesOrderId) return
  detailVisible.value = true
  detailLoading.value = true
  resetDetailPagination()
  try {
    orderDetail.value = await getSalesOrder(salesOrderId)
    const orderInfo = orderDetail.value?.orderInfo || {}
    selectedCustomer.value = orderInfo.customerName || selectedCustomer.value
    selectedCustomerCode.value = orderInfo.customerCode || selectedCustomerCode.value
    resetOrderEditDraft(orderInfo)
    await fetchDrawingSummary(salesOrderId)
    await fetchCurrentDetailTab()
    if (options.syncList) {
      keyword.value = ''
      statusFilter.value = ''
      page.value = 1
      await loadOrders()
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('销售订单详情加载失败。')
  } finally {
    detailLoading.value = false
  }
}

const openDetail = async (row) => {
  await openDetailBySalesOrderId(row?.id)
}

const applySalesOrderDeepLink = async (value) => {
  const salesOrderId = normalizeRouteSalesOrderId(value)
  if (!salesOrderId || (salesOrderId === orderDetail.value?.orderInfo?.id && detailVisible.value)) return
  await openDetailBySalesOrderId(salesOrderId, { syncList: true })
}

const fetchDrawingSummary = async (salesOrderId = orderDetail.value?.orderInfo?.id) => {
  if (!salesOrderId) return
  drawingSummary.value = await getSalesOrderDrawingSummary(salesOrderId)
}

const fetchDetailItems = async () => {
  const salesOrderId = orderDetail.value?.orderInfo?.id
  if (!salesOrderId) return
  detailItemsLoading.value = true
  try {
    const response = await listSalesOrderItems(salesOrderId, {
      page: detailItemPagination.page,
      pageSize: detailItemPagination.pageSize
    })
    detailItemList.value = response.records || []
    resetItemDrafts(detailItemList.value)
    detailItemTotal.value = Number(response.total ?? 0)
  } catch (error) {
    console.error(error)
    ElMessage.error('销售订单零件明细加载失败。')
  } finally {
    detailItemsLoading.value = false
  }
}

const fetchDetailTickets = async () => {
  const salesOrderId = orderDetail.value?.orderInfo?.id
  if (!salesOrderId) return
  detailTicketsLoading.value = true
  try {
    const response = await listSalesOrderTickets(salesOrderId, {
      page: detailTicketPagination.page,
      pageSize: detailTicketPagination.pageSize
    })
    detailTicketList.value = response.records || []
    detailTicketTotal.value = Number(response.total ?? 0)
  } catch (error) {
    console.error(error)
    ElMessage.error('销售订单提单行加载失败。')
  } finally {
    detailTicketsLoading.value = false
  }
}

const fetchAllocationSplits = async () => {
  const salesOrderId = orderDetail.value?.orderInfo?.id
  if (!salesOrderId) return
  allocationSplitsLoading.value = true
  selectedAllocationSplitRows.value = []
  try {
    const response = await listSalesOrderAllocationSplits(salesOrderId, {
      page: allocationSplitPagination.page,
      pageSize: allocationSplitPagination.pageSize
    })
    allocationSplitList.value = response.records || []
    allocationSplitTotal.value = Number(response.total ?? 0)
    Object.keys(allocationSplitDrafts).forEach(key => delete allocationSplitDrafts[key])
    allocationSplitList.value.forEach(row => {
      if (row?.allocationId != null) {
        allocationSplitDrafts[row.allocationId] = { qty: Number(row.qty ?? 0) }
      }
    })
  } catch (error) {
    console.error(error)
    ElMessage.error('销售订单锁库拆行加载失败。')
  } finally {
    allocationSplitsLoading.value = false
  }
}

const wmsAllocationPending = computed(() => isPending(salesOrderActionKeys.wmsAllocation(orderDetail.value?.orderInfo?.id))
  || ['PENDING', 'RUNNING'].includes(String(wmsAllocationTask.value?.status || '').toUpperCase()))

const isAllocationWmsSelectable = (row) => {
  if (!row || String(row.allocationType || '').toUpperCase() !== 'PRODUCTION_REQUIRED') return false
  if (Boolean(row.discarded) || Number(row.qty || 0) <= 0 || !String(row.materialNo || '').trim()) return false
  if (row.wmsReservationTicketId) return false
  return !['PROCESSING', 'SUBMITTING', 'RESULT_UNKNOWN', 'SUCCESS']
    .includes(String(row.mesProductionStatus || '').toUpperCase())
}

const handleAllocationSplitSelectionChange = (rows) => {
  selectedAllocationSplitRows.value = rows || []
}

const saveAllocationSplitChanges = async () => {
  const salesOrderId = orderDetail.value?.orderInfo?.id
  if (!salesOrderId) return ElMessage.warning('请先打开销售订单详情。')
  const items = allocationSplitList.value
    .filter(row => {
      const draft = allocationSplitDrafts[row?.allocationId]
      return draft && Number(draft.qty ?? 0) !== Number(row?.qty ?? 0)
    })
    .map(row => ({ allocationId: row.allocationId, qty: Number(allocationSplitDrafts[row.allocationId].qty) }))
  if (!items.length) return ElMessage.info('拆分数量没有变化。')
  if (items.some(item => !Number.isInteger(item.qty) || item.qty < 0)) {
    return ElMessage.warning('拆分数量必须是大于或等于 0 的整数。')
  }
  allocationSplitSaving.value = true
  try {
    const response = await saveSalesOrderAllocationSplits(salesOrderId, { items })
    ElMessage.success(response?.message || '锁库拆分数量已保存。')
    await loadOrders()
    await refreshCurrentDetail()
    await Promise.all([fetchDetailItems(), fetchAllocationSplits()])
    if (wmsAllocationTask.value?.id || wmsAllocationTask.value?.taskId) {
      await refreshWmsAllocationTask()
    }
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || error.message || '锁库拆分数量保存失败。')
  } finally {
    allocationSplitSaving.value = false
  }
}

const wmsAllocationRows = computed(() => {
  const result = parseTaskResult(wmsAllocationTask.value)
  const rows = result?.results || result?.rows || []
  return Array.isArray(rows) ? rows : []
})

const parseTaskResult = (task) => {
  const result = task?.resultJson || task?.result || task?.task?.resultJson
  if (!result) return null
  if (typeof result === 'object') return result
  if (typeof result === 'string') {
    try {
      const parsed = JSON.parse(result)
      return parsed && typeof parsed === 'object' ? parsed : null
    } catch {
      return null
    }
  }
  return null
}

const wmsTaskStatusText = (value) => ({
  PENDING: '排队中',
  RUNNING: '处理中',
  SUCCESS: '已完成',
  FAILED: '任务失败',
  RESULT_UNKNOWN: '结果待核对',
  APPLY_FAILED: '本地应用待修复',
  SKIPPED: '跳过',
  FAILED_ITEM: '失败'
}[String(value || '').toUpperCase()] || String(value || '未知状态'))

const wmsTaskTagType = (value) => ({
  SUCCESS: 'success',
  FAILED: 'danger',
  FAILED_ITEM: 'danger',
  APPLY_FAILED: 'danger',
  RESULT_UNKNOWN: 'warning',
  PENDING: 'info',
  RUNNING: 'warning',
  SKIPPED: 'info'
}[String(value || '').toUpperCase()] || 'info')

const allocationTypeText = (value) => ({
  IN_STOCK_LOCK: '锁库待产',
  IN_PRODUCTION_LOCK: '锁库生产中',
  PRODUCTION_REQUIRED: '待产'
}[String(value || '').toUpperCase()] || String(value || '-'))

const handleWmsAllocation = async () => {
  const salesOrderId = orderDetail.value?.orderInfo?.id
  if (!salesOrderId) return
  try {
    await ElMessageBox.confirm(
      '确认对该销售订单数量大于 0 的待产 Allocation 获取库存信息？获取成功的数量将从待产数量中拆分为锁库数量，未获取库存的零件仍可全量下发 MES 生产。',
      '获取库存信息',
      { confirmButtonText: '确认提交', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  return runAction(salesOrderActionKeys.wmsAllocation(salesOrderId), async () => {
    try {
      const task = await submitSalesOrderWmsAllocationBatch(salesOrderId)
      const taskId = task?.id || task?.taskId
      wmsAllocationTask.value = task
      ElMessage.success(taskId ? `库存信息获取任务已提交，任务ID：${taskId}` : '库存信息获取任务已提交')
      await fetchAllocationSplits()
      if (taskId) await refreshWmsAllocationTask(true)
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || error.message || '库存信息获取任务提交失败。')
    }
  })
}

const handleSelectedWmsAllocation = async () => {
  const salesOrderId = orderDetail.value?.orderInfo?.id
  const allocationIds = selectedAllocationSplitIds.value
  if (!salesOrderId) return
  if (!allocationIds.length) {
    ElMessage.warning('请先选择需要获取库存信息的待产零件。')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认仅对选中的 ${allocationIds.length} 条待产 Allocation 获取库存信息？未选中的零件保持当前状态。`,
      '批量获取库存信息',
      { confirmButtonText: '确认提交', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  return runAction(salesOrderActionKeys.wmsAllocation(salesOrderId), async () => {
    try {
      const task = await submitSalesOrderWmsAllocationBatch(salesOrderId, allocationIds)
      const taskId = task?.id || task?.taskId
      wmsAllocationTask.value = task
      selectedAllocationSplitRows.value = []
      ElMessage.success(taskId ? `选中零件库存信息获取任务已提交，任务ID：${taskId}` : '选中零件库存信息获取任务已提交')
      await fetchAllocationSplits()
      if (taskId) await refreshWmsAllocationTask(true)
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || error.message || '选中零件库存信息获取任务提交失败。')
    }
  })
}

const waitForWmsTask = (milliseconds) => new Promise(resolve => globalThis.setTimeout(resolve, milliseconds))

const refreshWmsAllocationTask = async (pollUntilComplete = false) => {
  const taskId = wmsAllocationTask.value?.id || wmsAllocationTask.value?.taskId
  if (!taskId || wmsAllocationRefreshing.value) return
  wmsAllocationRefreshing.value = true
  try {
    const attempts = pollUntilComplete ? 20 : 1
    for (let attempt = 0; attempt < attempts; attempt += 1) {
      const latestTask = await getTask(taskId)
      wmsAllocationTask.value = latestTask?.task || latestTask
      const status = String(wmsAllocationTask.value?.status || '').toUpperCase()
      if (!['PENDING', 'RUNNING'].includes(status)) break
      if (attempt + 1 < attempts) await waitForWmsTask(1500)
    }
    await fetchAllocationSplits()
  } catch (error) {
    console.warn('Sales order WMS task refresh failed', error)
    if (!pollUntilComplete) ElMessage.warning(error.response?.data?.message || '任务状态刷新失败，请稍后重试。')
  } finally {
    wmsAllocationRefreshing.value = false
  }
}

const fetchDetailProcessSheets = async () => {
  const salesOrderId = orderDetail.value?.orderInfo?.id
  if (!salesOrderId) return
  detailProcessSheetsLoading.value = true
  try {
    const response = await listSalesOrderProcessSheets(salesOrderId, {
      page: detailProcessSheetPagination.page,
      pageSize: detailProcessSheetPagination.pageSize
    })
    detailProcessSheetList.value = response.records || []
    detailProcessSheetTotal.value = Number(response.total ?? 0)
  } catch (error) {
    console.error(error)
    ElMessage.error('销售订单工艺表行加载失败。')
  } finally {
    detailProcessSheetsLoading.value = false
  }
}

const fetchCurrentDetailTab = async () => {
  if (activeDetailTab.value === 'tickets') {
    await fetchDetailTickets()
  } else if (activeDetailTab.value === 'allocationSplits') {
    await fetchAllocationSplits()
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
  supplementDrawingFolderFiles.value = []
  supplementDrawingFolderName.value = ''
  supplementVisible.value = true
}

const openReplaceSalesOrderDrawingDialog = () => {
  replaceSalesOrderDrawingResult.value = null
  replaceSalesOrderDrawingFiles.value = []
  replaceSalesOrderVersionDrawingFiles.value = []
  replaceSalesOrderDrawingFolderFiles.value = []
  replaceSalesOrderDrawingFolderName.value = ''
  replaceSalesOrderDrawingVisible.value = true
}

const appendDrawingFolderFields = (formData, files, folderName) => {
  if (!files?.length) return
  files.forEach(file => {
    formData.append('drawingFolderFiles', file)
  })
  formData.append('drawingFolderName', folderName || '')
}

const openDrawingPreview = async (row) => {
  const url = row?.drawingUrl
  if (!url) return ElMessage.warning('当前零件没有绑定图纸。')
  if (isDrawingFolderUrl(url)) {
    currentDrawingFolderUrl.value = url
    currentDrawingFolderTitle.value = row?.partNo || '未命名文件夹'
    folderViewerVisible.value = true
    return
  }
  const result = await openAuthenticatedFileInNewWindow(url)
  if (!result.success) {
    ElMessage.error(result.blocked ? '浏览器阻止了预览窗口，请允许弹窗后重试。' : (result.message || '图纸预览失败。'))
  }
}

const handleProductionTaskNoticeExport = async () => {
  const salesOrderId = orderDetail.value?.orderInfo?.id
  if (!salesOrderId) return ElMessage.warning('请先打开销售订单详情。')
  const salesOrderNo = orderDetail.value?.orderInfo?.salesOrderNo || salesOrderId
  return runAction(salesOrderActionKeys.productionTaskNoticeExport(salesOrderId), async () => {
    const result = await downloadSalesOrderProductionTaskNotice(salesOrderId, `${salesOrderNo}-生产任务通知单.xlsx`)
    if (result?.success === false) {
      throw new Error(result.message || '导出生产任务通知单失败。')
    }
    ElMessage.success('生产任务通知单已开始下载。')
  }).catch((error) => {
    if (error) {
      console.error(error)
      ElMessage.error(error.message || '导出生产任务通知单失败。')
    }
  })
}

const handleSalesOrderItemDetailExport = async () => {
  const salesOrderId = orderDetail.value?.orderInfo?.id
  if (!salesOrderId) return ElMessage.warning('请先打开销售订单详情。')
  const salesOrderNo = orderDetail.value?.orderInfo?.salesOrderNo || salesOrderId
  return runAction(salesOrderActionKeys.itemDetailExport(salesOrderId), async () => {
    const result = await downloadSalesOrderItemDetails(salesOrderId, `${salesOrderNo}-零件明细表.xlsx`)
    if (result?.success === false) {
      throw new Error(result.message || '导出明细表失败。')
    }
    ElMessage.success('明细表已开始下载。')
  }).catch((error) => {
    if (error) {
      console.error(error)
      ElMessage.error(error.message || '导出明细表失败。')
    }
  })
}

const refreshCurrentDetail = async () => {
  if (!orderDetail.value?.orderInfo?.id) return
  const currentId = orderDetail.value.orderInfo.id
  const latestDetail = await getSalesOrder(currentId)
  orderDetail.value = { ...orderDetail.value, ...latestDetail }
  resetOrderEditDraft(orderDetail.value?.orderInfo)
  await fetchDrawingSummary(currentId)
  await fetchCurrentDetailTab()
}

const resetOrderEditDraft = (orderInfo = {}) => {
  SALES_ORDER_HEADER_EDITABLE_FIELDS.forEach(field => {
    orderEditDraft[field] = draftValue(orderInfo?.[field])
  })
}

const resetItemDrafts = (rows = []) => {
  Object.keys(itemDrafts).forEach(key => delete itemDrafts[key])
  rows.forEach(row => {
    if (!row?.id) return
    itemDrafts[row.id] = buildItemDraft(row)
  })
}

const buildItemDraft = (row = {}) => ({
  version: draftValue(row.version),
  qty: row.qty ?? null,
  status: draftValue(row.status),
  untaxedUnitPrice: draftValue(row.untaxedUnitPrice),
  taxIncludedUnitPrice: draftValue(row.taxIncludedUnitPrice),
  customerDeliveryDate: draftValue(row.customerDeliveryDate),
  internalDeliveryDate: draftValue(row.internalDeliveryDate),
  customerOrderNo: draftValue(row.customerOrderNo),
  customerProjectNo: draftValue(row.customerProjectNo),
  customerOrderTitle: draftValue(row.customerOrderTitle),
  internalProjectNo: draftValue(row.internalProjectNo),
  orderType: draftValue(row.orderType),
  description: draftValue(row.description),
  materialName: draftValue(row.materialName),
  surfaceTreatment: draftValue(row.surfaceTreatment),
  remark: draftValue(row.remark)
})

const buildOrderPayload = () => {
  const source = orderDetail.value?.orderInfo || {}
  return SALES_ORDER_HEADER_EDITABLE_FIELDS.reduce((payload, field) => {
    if (!sameDraftValue(source[field], orderEditDraft[field])) {
      payload[field] = payloadValue(orderEditDraft[field])
    }
    return payload
  }, {})
}

const saveSalesOrderHeaderChanges = async () => {
  const salesOrderId = orderDetail.value?.orderInfo?.id
  if (!salesOrderId) return ElMessage.warning('请先打开销售订单详情。')
  if (salesOrderEditLocked.value) return ElMessage.warning(salesOrderEditLockedReason.value)
  const payload = buildOrderPayload()
  if (Object.keys(payload).length === 0) return ElMessage.info('订单信息没有变化。')
  return runAction(salesOrderActionKeys.headerSave(salesOrderId), async () => {
    try {
      const response = await saveSalesOrderHeader(salesOrderId, payload)
      ElMessage.success(response?.message || '销售订单信息已保存。')
      await loadOrders()
      await refreshCurrentDetail()
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || '销售订单信息保存失败。')
    }
  })
}

const itemPayload = (row) => {
  const draft = itemDrafts[row?.id]
  if (!draft) return null
  const payload = { salesOrderItemId: row.id }
  SALES_ORDER_ITEM_EDITABLE_FIELDS.forEach(field => {
    if (!sameDraftValue(row[field], draft[field])) {
      payload[field] = payloadValue(draft[field])
    }
  })
  return Object.keys(payload).length > 1 ? payload : null
}

const saveSalesOrderItemChanges = async () => {
  const salesOrderId = orderDetail.value?.orderInfo?.id
  if (!salesOrderId) return ElMessage.warning('请先打开销售订单详情。')
  if (salesOrderEditLocked.value) return ElMessage.warning(salesOrderEditLockedReason.value)
  const items = detailItemList.value.map(itemPayload).filter(Boolean)
  if (items.length === 0) return ElMessage.info('零件明细没有变化。')
  return runAction(salesOrderActionKeys.itemSave(salesOrderId), async () => {
    try {
      const response = await saveSalesOrderItems(salesOrderId, { items })
      ElMessage.success(response?.message || '销售订单零件信息已保存。')
      await loadOrders()
      await refreshCurrentDetail()
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || '销售订单零件信息保存失败。')
    }
  })
}

const saveSalesOrderItemFieldReplacement = async (field) => {
  const salesOrderId = orderDetail.value?.orderInfo?.id
  if (!salesOrderId) return ElMessage.warning('请先打开销售订单详情。')
  if (!field?.key) return ElMessage.warning('请选择需要整单替换的字段。')
  if (salesOrderEditLocked.value) return ElMessage.warning(salesOrderEditLockedReason.value)
  if (dirtyItemCount.value > 0) {
    return ElMessage.warning('当前页有未保存修改，请先保存修改或刷新后再进行整单字段替换。')
  }
  const value = payloadValue(itemFieldReplacementDrafts[field.key])
  const displayValue = value === null ? '空值' : `“${value}”`
  try {
    await ElMessageBox.confirm(
      `确认将整单所有零件的${field.label}替换为${displayValue}吗？`,
      `整单替换${field.label}`,
      {
        type: 'warning',
        confirmButtonText: '确认替换',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }
  return runAction(salesOrderActionKeys.itemFieldReplacement(salesOrderId, field.key), async () => {
    try {
      const response = await replaceSalesOrderItemField(salesOrderId, { field: field.key, value })
      ElMessage.success(response?.message || `${field.label}已整单替换。`)
      await loadOrders()
      await refreshCurrentDetail()
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || `${field.label}整单替换失败。`)
    }
  })
}

const handleSalesOrderPriceConversion = async (direction) => {
  const action = SALES_ORDER_PRICE_CONVERSIONS[direction]
  const salesOrderId = orderDetail.value?.orderInfo?.id
  if (!action) return
  if (!salesOrderId) return ElMessage.warning('请先打开销售订单详情。')
  if (salesOrderEditLocked.value) return ElMessage.warning(salesOrderEditLockedReason.value)
  if (dirtyItemCount.value > 0) {
    return ElMessage.warning('当前页有未保存修改，请先保存修改或刷新后再进行整单价格换算。')
  }
  try {
    await ElMessageBox.confirm(
      `确认按材料工艺百分比费用中的税率，将整单所有零件的${action.targetLabel}由${action.sourceLabel}重新计算并覆盖吗？`,
      action.label,
      {
        type: 'warning',
        confirmButtonText: '确认转换',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }
  return runAction(salesOrderActionKeys.itemPriceConversion(salesOrderId, direction), async () => {
    try {
      const response = await action.handler(salesOrderId)
      ElMessage.success(response?.message || `${action.label}完成。`)
      await loadOrders()
      await refreshCurrentDetail()
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || `${action.label}失败。`)
    }
  })
}

const openSalesOrderItemDataReplacementDialog = () => {
  if (!orderDetail.value?.orderInfo?.id) return ElMessage.warning('请先打开销售订单详情。')
  if (salesOrderEditLocked.value) return ElMessage.warning(salesOrderEditLockedReason.value)
  itemDataReplacementVisible.value = true
}

const isItemU9ChangeExclusionPending = (row) => isPending(salesOrderActionKeys.itemU9ChangeExclusion(row?.id))
const isItemLinkedDiscardPending = (row) => isPending(salesOrderActionKeys.itemLinkedDiscard(row?.id))

const handleSalesOrderItemU9ChangeExclusion = async (row) => {
  const salesOrderId = orderDetail.value?.orderInfo?.id
  if (!salesOrderId || !row?.id) return ElMessage.warning('请先打开销售订单详情。')
  if (salesOrderItemU9ChangeExclusionLocked.value) return ElMessage.warning(salesOrderItemU9ChangeExclusionLockedReason.value)
  const restoring = Boolean(row.u9ChangeExcluded)
  const title = restoring ? '恢复销售订单零件' : '废弃销售订单零件'
  const placeholder = restoring ? '请输入恢复原因' : '请输入废弃原因'
  let reason = ''
  try {
    const result = await ElMessageBox.prompt(
      `${title}：${row.partNo || row.id}${row.version ? ` / ${row.version}` : ''}。仅从 U9 同步和销售变更排除，不影响 MES 物料号、WMS 库存、已提/待产处理和 MES 生产下发。`,
      title,
      {
        inputPlaceholder: placeholder,
        inputPattern: /\S+/,
        inputErrorMessage: '原因不能为空',
        confirmButtonText: restoring ? '确认恢复' : '确认废弃',
        cancelButtonText: '取消',
        type: restoring ? 'success' : 'warning'
      }
    )
    reason = result.value?.trim() || ''
  } catch {
    return
  }
  const handler = restoring ? restoreSalesOrderItemForU9Change : discardSalesOrderItemForU9Change
  return runAction(salesOrderActionKeys.itemU9ChangeExclusion(row.id), async () => {
    try {
      const response = await handler(salesOrderId, row.id, { reason })
      ElMessage.success(response?.message || (restoring ? '销售订单零件已恢复。' : '销售订单零件已废弃。'))
      await loadOrders()
      await refreshCurrentDetail()
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || (restoring ? '恢复销售订单零件失败。' : '废弃销售订单零件失败。'))
    }
  })
}

const handleSalesOrderItemLinkedDiscard = async (row) => {
  const salesOrderId = orderDetail.value?.orderInfo?.id
  if (!salesOrderId || !row?.id) return ElMessage.warning('请先打开销售订单详情。')
  if (salesOrderItemU9ChangeExclusionLocked.value) return ElMessage.warning(salesOrderItemLinkedDiscardLockedReason.value)
  const restoring = Boolean(row.linkedDiscarded)
  const title = restoring ? '关联恢复销售订单零件' : '关联废弃销售订单零件'
  const placeholder = restoring ? '请输入关联恢复原因' : '请输入关联废弃原因'
  let reason = ''
  try {
    const result = await ElMessageBox.prompt(
      `${title}：${row.partNo || row.id}${row.version ? ` / ${row.version}` : ''}。将同时处理仍在已提、待产和锁库工作池中的关联零件，已完成外部流程的记录会跳过。`,
      title,
      {
        inputPlaceholder: placeholder,
        inputPattern: /\S+/,
        inputErrorMessage: '原因不能为空',
        confirmButtonText: restoring ? '确认关联恢复' : '确认关联废弃',
        cancelButtonText: '取消',
        type: restoring ? 'success' : 'warning'
      }
    )
    reason = result.value?.trim() || ''
  } catch {
    return
  }
  const handler = restoring ? restoreSalesOrderItemLinked : discardSalesOrderItemLinked
  return runAction(salesOrderActionKeys.itemLinkedDiscard(row.id), async () => {
    try {
      const response = await handler(salesOrderId, row.id, { reason })
      ElMessage.success(response?.message || (restoring ? '销售订单零件已关联恢复。' : '销售订单零件已关联废弃。'))
      await loadOrders()
      await refreshCurrentDetail()
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || (restoring ? '关联恢复销售订单零件失败。' : '关联废弃销售订单零件失败。'))
    }
  })
}

const handleSalesOrderItemDataReplacementConfirmed = async () => {
  itemDataReplacementVisible.value = false
  await loadOrders()
  await refreshCurrentDetail()
}

const hasItemDraftChanges = (row) => Boolean(itemPayload(row))

const draftValue = (value) => value === null || value === undefined ? '' : String(value)

const payloadValue = (value) => {
  if (value === null || value === undefined) return null
  if (typeof value === 'string') {
    const trimmed = value.trim()
    return trimmed === '' ? null : trimmed
  }
  return value
}

const sameDraftValue = (source, draft) => draftValue(source).trim() === draftValue(draft).trim()

const itemPriceDraftChanged = (row = {}, draft = {}, unitPriceField) => {
  return !sameDraftValue(row.qty, draft.qty) || !sameDraftValue(row[unitPriceField], draft[unitPriceField])
}

const lineUntaxedTotal = (row = {}, draft = null) => {
  if (draft && !itemPriceDraftChanged(row, draft, 'untaxedUnitPrice')) {
    return lineUntaxedTotal(row)
  }
  if (!draft && row.untaxedTotalPrice !== null && row.untaxedTotalPrice !== undefined && row.untaxedTotalPrice !== '') {
    return numberOrZero(row.untaxedTotalPrice)
  }
  const source = draft || row
  return numberOrZero(source.qty) * numberOrZero(source.untaxedUnitPrice)
}

const lineTaxIncludedTotal = (row = {}, draft = null) => {
  if (draft && !itemPriceDraftChanged(row, draft, 'taxIncludedUnitPrice')) {
    return lineTaxIncludedTotal(row)
  }
  if (!draft && row.taxIncludedTotalPrice !== null && row.taxIncludedTotalPrice !== undefined && row.taxIncludedTotalPrice !== '') {
    return numberOrZero(row.taxIncludedTotalPrice)
  }
  const source = draft || row
  return numberOrZero(source.qty) * numberOrZero(source.taxIncludedUnitPrice)
}

const isOrderStatusPending = (row) => isPending(salesOrderActionKeys.statusChange(row?.id))

const canResetU9Sync = (row) => row?.u9SyncStatus === '已同步'
const isU9ResyncResetPending = (row) => isPending(salesOrderActionKeys.u9ResyncReset(row?.id))

const rowStatusActions = (row) => {
  if (!row) {
    return []
  }
  return SALES_ORDER_STATUS_ACTIONS.filter(action => action.status === row.status)
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
  return runAction(salesOrderActionKeys.statusChange(row.id), async () => {
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

const isOrderLinkedStatusPending = (row) => isPending(salesOrderActionKeys.linkedStatusChange(row?.id))

const handleLinkedStatusAction = async (row) => {
  if (!row?.id) return
  const restoring = Boolean(row.linkedDiscarded)
  const action = restoring
    ? {
        handler: restoreSalesOrderLinked,
        type: 'success',
        reason: '销售订单：整单关联恢复',
        title: '关联恢复销售订单',
        confirmButtonText: '确认关联恢复',
        successMessage: '销售订单及关联下游零件已恢复。',
        errorMessage: '整单关联恢复销售订单失败。',
        message: `确认关联恢复销售订单「${row.salesOrderNo || row.id}」吗？系统将恢复本次关联废弃产生的下游零件，人工废弃和已完成外部流程的零件不会被恢复。`
      }
    : {
        handler: discardSalesOrderLinked,
        type: 'warning',
        reason: '销售订单：整单关联废弃',
        title: '关联废弃销售订单',
        confirmButtonText: '确认关联废弃',
        successMessage: '销售订单及关联下游零件已废弃。',
        errorMessage: '整单关联废弃销售订单失败。',
        message: `确认关联废弃销售订单「${row.salesOrderNo || row.id}」吗？系统将废弃订单全部零件，并处理仍在已提、待产、锁库工作池中的关联零件。`
      }
  try {
    await ElMessageBox.confirm(action.message, action.title, {
      type: action.type,
      confirmButtonText: action.confirmButtonText,
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  return runAction(salesOrderActionKeys.linkedStatusChange(row.id), async () => {
    try {
      const response = await action.handler(row.id, { reason: action.reason })
      ElMessage.success(response?.message || action.successMessage)
      await loadOrders()
      await refreshCurrentDetail()
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || action.errorMessage)
    }
  })
}

const submitSupplementDrawings = async () => {
  if (!orderDetail.value?.orderInfo?.id) return ElMessage.warning('请先打开销售订单详情。')
  if (supplementDrawingFolderFiles.value.length === 0 && supplementFiles.value.length === 0 && supplementVersionFiles.value.length === 0) return ElMessage.warning('请先选择需要补传的 PDF 图纸或订单附件文件夹。')

  const salesOrderId = orderDetail.value.orderInfo.id
  return runAction(salesOrderActionKeys.supplementDrawings(salesOrderId), async () => {
    const formData = new FormData()
    supplementFiles.value.forEach(file => formData.append('drawings', file))
    supplementVersionFiles.value.forEach(file => formData.append('versionDrawings', file))
    supplementDrawingFolderFiles.value.forEach(file => formData.append('drawingFolderFiles', file))
    if (supplementDrawingFolderFiles.value.length > 0) {
      formData.append('drawingFolderName', supplementDrawingFolderName.value || '')
    }
    const response = await supplementSalesOrderDrawings(salesOrderId, formData)
    supplementResult.value = response
    ElMessage.success(response.message || '补传图纸处理完成。')
    supplementFiles.value = []
    supplementVersionFiles.value = []
    supplementDrawingFolderFiles.value = []
    supplementDrawingFolderName.value = ''
    await fetchDetailItems()
    await fetchDrawingSummary()
    await loadOrders()
  }).catch((error) => {
    if (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || '补传图纸失败。')
    }
  })
}

const submitReplaceSalesOrderDrawings = async () => {
  if (!orderDetail.value?.orderInfo?.id) return ElMessage.warning('请先打开销售订单详情。')
  if (replaceSalesOrderDrawingFolderFiles.value.length === 0 && replaceSalesOrderDrawingFiles.value.length === 0 && replaceSalesOrderVersionDrawingFiles.value.length === 0) return ElMessage.warning('请先选择需要替换的 PDF 图纸或订单附件文件夹。')

  const salesOrderId = orderDetail.value.orderInfo.id
  return runAction(salesOrderActionKeys.salesOrderDrawingReplace(salesOrderId), async () => {
    const formData = new FormData()
    replaceSalesOrderDrawingFiles.value.forEach(file => formData.append('drawings', file))
    replaceSalesOrderVersionDrawingFiles.value.forEach(file => formData.append('versionDrawings', file))
    replaceSalesOrderDrawingFolderFiles.value.forEach(file => formData.append('drawingFolderFiles', file))
    if (replaceSalesOrderDrawingFolderFiles.value.length > 0) {
      formData.append('drawingFolderName', replaceSalesOrderDrawingFolderName.value || '')
    }
    const response = await replaceSalesOrderDrawings(salesOrderId, formData)
    replaceSalesOrderDrawingResult.value = {
      ...(response || {}),
      boundCount: response?.replacedCount ?? response?.boundCount ?? 0,
      boundItems: response?.replacedItems || response?.boundItems || [],
      alreadyBoundCount: response?.alreadyBoundCount || 0,
      alreadyBoundItems: response?.alreadyBoundItems || []
    }
    replaceSalesOrderDrawingFiles.value = []
    replaceSalesOrderVersionDrawingFiles.value = []
    replaceSalesOrderDrawingFolderFiles.value = []
    replaceSalesOrderDrawingFolderName.value = ''
    await fetchDetailItems()
    await fetchDrawingSummary()
    await loadOrders()
    if (Number(replaceSalesOrderDrawingResult.value.unmatchedCount || 0) > 0 || Number(replaceSalesOrderDrawingResult.value.skippedCount || 0) > 0) {
      ElMessage.warning(response?.message || '图纸替换处理完成，存在未匹配或跳过文件。')
    } else {
      ElMessage.success(response?.message || '图纸替换处理完成。')
    }
  }).catch((error) => {
    if (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || '图纸替换失败。')
    }
  })
}

const statusTagType = (status) => {
  if (status === '已报价') return 'success'
  if (status === '部分已提单') return 'warning'
  if (status === '已生成提单') return 'info'
  if (status === '废弃') return 'danger'
  return 'info'
}

const matchStatusTagType = (status) => {
  if (status === '已匹配') return 'success'
  if (status === '未匹配') return 'warning'
  if (status === '匹配失败') return 'danger'
  return 'info'
}

function numberOrZero(value) {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

const formatMoney = (value) => {
  if (value === null || value === undefined || value === '') return '-'
  const number = Number(value)
  if (Number.isNaN(number)) return String(value)
  return `¥ ${number.toFixed(2)}`
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
    .map(formatProcessRouteRow)
    .filter(Boolean)
    .join(' / ')
}

const formatProcessRouteRow = (row = {}) => {
  const isFee = String(row.routeType || '').toUpperCase() === 'FEE' || row.sourceType === 'extra'
  if (isFee) {
    const feeName = row.processName || row.detailName || row.name || '费用'
    return row.lineCost ? `${feeName}（${formatCompactMoney(row.lineCost)}）` : feeName
  }
  const identity = [row.processCode, row.categoryName, row.detailName || row.processName || row.name]
    .filter(Boolean)
    .join(' / ')
  const metrics = []
  const workHours = row.unitWorkHours ?? row.inputVal
  if (workHours) metrics.push(`${formatCompactNumber(workHours)}h`)
  if (row.hourlyRate ?? row.unitRate) metrics.push(formatCompactMoney(row.hourlyRate ?? row.unitRate))
  if (row.lineCost) metrics.push(formatCompactMoney(row.lineCost))
  if (!identity) return metrics.join(' × ')
  return metrics.length > 0 ? `${identity}（${metrics.join(' × ')}）` : identity
}

const formatCompactNumber = (value) => {
  const number = Number(value)
  if (!Number.isFinite(number)) return String(value || '')
  return number.toFixed(3).replace(/\.?0+$/, '')
}

const formatCompactMoney = (value) => `¥${formatCompactNumber(value)}`

const handleResetU9Sync = async (row) => {
  if (!canResetU9Sync(row)) return
  let reason = ''
  try {
    const result = await ElMessageBox.prompt(
      `请确认已在 U9 删除销售订单「${row.salesOrderNo || row.id}」后再继续。重置后 QPO 将清除当前 U9 绑定并允许重新同步。`,
      '重新同步 U9',
      {
        inputPlaceholder: '请输入重置原因，例如：已在 U9 删除原销售订单',
        inputPattern: /\S+/,
        inputErrorMessage: '重置原因不能为空',
        confirmButtonText: '确认重置',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    reason = result.value?.trim() || ''
  } catch {
    return
  }
  return runAction(salesOrderActionKeys.u9ResyncReset(row.id), async () => {
    try {
      const response = await resetSalesOrderU9SyncStatus(row.id, { reason })
      ElMessage.success(response?.message || '销售订单已重置为未同步，可重新同步 U9。')
      await loadOrders()
      await refreshCurrentDetail()
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || '重置 U9 同步状态失败。')
    }
  })
}

watch(
  () => route.query.salesOrderId,
  applySalesOrderDeepLink,
  { immediate: true }
)

onMounted(() => {
  if (!normalizeRouteSalesOrderId(route.query.salesOrderId)) {
    loadOrders()
  }
})
</script>

<style scoped>
.sales-order-filter-group {
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

.status-filter {
  width: 150px;
  flex: 0 0 150px;
}

.search-input {
  width: 320px;
  flex: 0 0 320px;
}

.sales-order-table-surface {
  overflow: hidden;
}

.sales-order-table-surface .erp-table-body {
  padding: var(--erp-space-3);
}

.sales-order-empty-state {
  margin: var(--erp-space-4);
}

.sales-order-row-actions {
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

.order-maintenance-panel {
  margin-top: 14px;
  padding: 14px;
  border: 1px solid var(--erp-border-soft);
  border-radius: 8px;
  background: var(--erp-surface-soft);
}

.order-maintenance-title-row,
.item-maintenance-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.order-maintenance-lock-alert {
  margin-top: 10px;
}

.order-maintenance-form {
  display: grid;
  grid-template-columns: repeat(3, minmax(220px, 1fr));
  gap: 12px 16px;
  margin-top: 12px;
}

.order-maintenance-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.order-maintenance-form :deep(.el-date-editor),
.order-maintenance-form :deep(.el-select) {
  width: 100%;
}

.order-total-readonly :deep(.el-input__wrapper) {
  background: #f8fafc;
  box-shadow: 0 0 0 1px var(--erp-border-soft) inset;
}

.order-total-readonly :deep(.el-input__inner) {
  color: var(--erp-text);
  font-weight: 600;
  text-align: right;
}

.sales-order-action-wrap {
  display: inline-flex;
}

.item-maintenance-actions {
  margin-bottom: 10px;
}

.item-field-replacement-row {
  display: grid;
  grid-template-columns: 120px repeat(auto-fit, minmax(210px, 1fr));
  gap: 8px;
  align-items: end;
  margin-bottom: 0;
  padding: 10px 12px;
  border: 1px solid var(--erp-border-soft);
  border-bottom: 0;
  border-radius: 8px 8px 0 0;
  background: #f8fafc;
}

.item-field-replacement-title {
  align-self: center;
  color: var(--erp-text);
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
}

.item-field-replacement-control {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 6px;
  align-items: center;
  min-width: 0;
}

.item-field-replacement-control label {
  grid-column: 1 / -1;
  color: var(--erp-text-secondary);
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
}

.item-field-replacement-control :deep(.el-input),
.item-field-replacement-control :deep(.el-select) {
  width: 100%;
}

.item-maintenance-action-meta,
.item-maintenance-action-buttons {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.item-edit-input {
  width: 100%;
}

.item-edit-number,
.item-edit-money {
  min-width: 96px;
}

:deep(.sales-order-item-qty-column .cell) {
  text-overflow: clip;
}

.sales-order-item-qty-input-trigger {
  display: inline-flex;
  width: 100%;
}

:deep(.sales-order-item-qty-column .item-edit-number) {
  min-width: 0;
  width: 100%;
}

.item-edit-date {
  min-width: 130px;
}

.item-u9-change-exclusion-cell {
  align-items: center;
  display: inline-flex;
  gap: 8px;
  justify-content: center;
}

.auto-release-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.auto-release-summary-card {
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--erp-border-soft);
  border-radius: 8px;
  background: var(--erp-surface-soft);
}

.auto-release-summary-card span {
  display: block;
  color: var(--erp-text-secondary);
  font-size: 12px;
}

.auto-release-summary-card strong {
  display: block;
  margin-top: 6px;
  color: var(--erp-text);
  font-size: 18px;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.supplement-dialog {
  display: flex;
  flex-direction: column;
  gap: 14px;
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
  .sales-order-filter-group {
    justify-content: flex-start;
  }

  .auto-release-summary {
    grid-template-columns: repeat(2, minmax(120px, 1fr));
  }

  .item-field-replacement-row {
    grid-template-columns: repeat(2, minmax(260px, 1fr));
  }

  .item-field-replacement-title {
    grid-column: 1 / -1;
  }
}

@media (max-width: 760px) {
  .customer-picker-trigger,
  .search-input,
  .status-filter {
    width: 100%;
    flex-basis: auto;
  }

  .auto-release-summary {
    grid-template-columns: 1fr;
  }

  .item-field-replacement-row {
    grid-template-columns: 1fr;
  }
}
</style>
