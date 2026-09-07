<template>
  <div class="erp-page release-page workflow-page">
    <header class="erp-page-header release-page-header">
          <div>
            <h2>议价提单</h2>
            <p>同一客户可选择多个询价单，一批生成一张销售订单和对应提单。</p>
          </div>
          <div class="erp-page-actions release-page-actions"><el-button v-permission="'market:release:order-select'" :icon="DocumentChecked" type="primary" :loading="loading" @click="openOrderPicker">选择订单</el-button></div>

    </header>

    <section v-if="releaseResult?.salesOrderId" class="erp-surface release-derived-order-result">
      <div class="release-result-header">
        <div>
          <div class="release-result-kicker">批次处理完成</div>
          <h3>{{ releaseResultTitle }}</h3>
          <p>{{ releaseResultDescription }}</p>
        </div>
        <div class="release-result-actions">
          <el-button
            v-permission="'market:sales-order:view'"
            size="small"
            plain
            @click="openDerivedOrderSalesOrder"
          >
            查看销售订单
          </el-button>
          <el-button
            v-permission="'market:u9-sync:view'"
            size="small"
            type="primary"
            plain
            @click="openDerivedOrderU9"
          >
            进入 U9 同步
          </el-button>
          <el-button
            v-permission="'market:delivered-item:view'"
            size="small"
            plain
            @click="openDerivedOrderDeliveredItems"
          >
            查看已提零件
          </el-button>
        </div>
      </div>
      <div class="release-result-summary-grid">
        <div><span>销售订单号</span><strong>{{ releaseResult.salesOrderNo || '-' }}</strong></div>
        <div><span>单据编号</span><strong>{{ releaseResult.documentNo || '-' }}</strong></div>
        <div><span>来源 RFQ</span><strong>{{ releaseResult.sourceRfqIds?.length ?? 0 }}</strong></div>
        <div><span>提单行数</span><strong>{{ releaseResult.ticketCount ?? 0 }}</strong></div>
        <div><span>MES 已确认</span><strong class="release-result-success">{{ releaseResult.confirmedMaterialCount ?? 0 }}</strong></div>
        <div><span>MES 待获取</span><strong class="release-result-warning">{{ releaseResult.pendingMaterialCount ?? 0 }}</strong></div>
      </div>
    </section>

    <section class="erp-surface workflow-workspace release-workspace">
      <el-row :gutter="16">
        <el-col :span="6">
          <div class="erp-surface workflow-side-panel release-panel">
            <el-table
              class="selected-orders-mini-table"
              :data="selectedOrders"
              height="132"
              size="small"
              border
              stripe
              empty-text="请在右上角选择订单"
            >
              <el-table-column prop="customerName" label="客户" min-width="86" show-overflow-tooltip />
              <el-table-column prop="sourceOrderNo" label="来源单号" min-width="122" show-overflow-tooltip />
              <el-table-column prop="status" label="订单状态" width="94" align="center">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)" size="small">{{ row.status || '-' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="58" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button link type="danger" size="small" :disabled="submitting" @click.stop="removeSelectedOrder(row)">移除</el-button>
                </template>
              </el-table-column>
            </el-table>

            <section class="release-order-default-panel">
              <div class="panel-title compact">订单默认信息</div>
              <p class="release-order-default-hint">仅用于生成正式提单：一批生成一张销售订单，零件值优先，空值继承订单默认信息；追加销售提单零件值优先，空值继承目标销售订单头，左侧必填项不参与。</p>
              <el-form label-width="96px" class="ticket-form release-order-default-form">
                <el-form-item label="单据类型" required>
                  <el-input
                    v-model="releaseForm.documentTypeCode"
                    maxlength="50"
                    placeholder="请输入单据类型编码"
                    clearable
                  />
                </el-form-item>
                <el-form-item label="下单日期" required>
                  <el-date-picker
                    v-model="releaseForm.salesOrderDate"
                    type="date"
                    value-format="YYYY-MM-DD"
                    class="full-width"
                  />
                </el-form-item>
                <el-form-item label="客户订单号" required>
                  <el-input v-model="releaseForm.customerOrderNo" placeholder="请输入客户订单号" maxlength="100" clearable />
                </el-form-item>
                <el-form-item label="客户项目号">
                  <el-input v-model="releaseForm.customerProjectNo" placeholder="请输入客户项目号（可选）" maxlength="100" clearable />
                </el-form-item>
                <el-form-item label="客户下单抬头">
                  <el-input v-model="releaseForm.customerOrderTitle" placeholder="请输入客户下单抬头（可选）" maxlength="255" clearable />
                </el-form-item>
                <el-form-item label="订单类型" required>
                  <el-select v-model="releaseForm.orderType" class="full-width release-order-type-select" placeholder="请选择订单类型" clearable>
                    <el-option label="预测订单" value="预测订单" />
                    <el-option label="备货订单" value="备货订单" />
                    <el-option label="正式订单" value="正式订单" />
                  </el-select>
                </el-form-item>
                <el-form-item label="内部项目号" required>
                  <el-input v-model="releaseForm.internalProjectNo" placeholder="请输入内部项目号" maxlength="100" clearable />
                </el-form-item>
                <el-form-item label="安排类型">
                  <el-select v-model="releaseForm.arrangeType" class="full-width">
                    <el-option label="计划" value="计划" />
                    <el-option label="加急" value="加急" />
                    <el-option label="备货" value="备货" />
                  </el-select>
                </el-form-item>
              </el-form>
            </section>

            <div class="internal-lead-panel">
              <div class="panel-title compact">内部目标交期调整</div>
              <el-input-number
                v-model="internalShiftDays"
                :min="0"
                :max="365"
                :precision="0"
                controls-position="right"
                class="full-width"
              />
              <div class="erp-table-actions workflow-muted-actions lead-actions">
                <el-button
                  plain
                  class="workflow-soft-button"
                  :disabled="selectedRows.length === 0 || adjustingInternalLeadTime || submitting"
                  :loading="adjustingInternalLeadTime"
                  @click="adjustInternalLeadTime(1)"
                >
                  增加
                </el-button>
                <el-button
                  plain
                  class="workflow-soft-button"
                  :disabled="selectedRows.length === 0 || adjustingInternalLeadTime || submitting"
                  :loading="adjustingInternalLeadTime"
                  @click="adjustInternalLeadTime(-1)"
                >
                  减少
                </el-button>
              </div>
              <div class="market-manual-date-row">
                <el-date-picker
                  v-model="manualInternalTargetDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  format="YYYY-MM-DD"
                  placeholder="手动指定内部目标交期"
                  clearable
                  class="full-width manual-date-picker market-manual-date-input"
                />
                <el-button
                  plain
                  class="workflow-soft-button market-manual-date-button"
                  :disabled="selectedRows.length === 0 || !manualInternalTargetDate || settingInternalTargetLeadTime || submitting"
                  :loading="settingInternalTargetLeadTime"
                  @click="setInternalTargetLeadTime"
                >
                  指定
                </el-button>
              </div>
              <div class="selection-count">已选 {{ selectedRows.length }} 项</div>
            </div>

            <div class="erp-dialog-footer main-actions">
              <el-button v-permission="'market:release:create'"
                type="primary"
                size="large"
                class="workflow-primary-action"
                :loading="submitting"
                :disabled="selectedRows.length === 0 || selectedRowsWithUnsavedOrderFields.length > 0 || !releaseForm.orderType || !releaseForm.documentTypeCode.trim() || !releaseForm.salesOrderDate || !releaseForm.internalProjectNo.trim() || !releaseForm.customerOrderNo.trim() || submitting || dataReplacementBusy"
                @click="submitTicket"
              >
                生成正式提单
              </el-button>
              <el-button
                v-permission="'market:release:create'"
                type="primary"
                plain
                size="large"
                class="workflow-primary-action"
                :loading="appendingSalesOrder"
                :disabled="appendSalesOrderDisabled"
                @click="openAppendSalesOrderPicker"
              >
                追加销售提单
              </el-button>
            </div>
          </div>
        </el-col>

        <el-col :span="18">
          <div class="erp-table-surface workflow-main-panel release-table-panel">
            <div class="erp-table-header panel-title-row">
              <div class="panel-title">通过零件选择</div>
              <div class="release-table-actions import-action-row">
                <el-button
                  v-permission="'market:release:create'"
                  size="small"
                  plain
                  class="workflow-soft-button"
                  :disabled="dataReplacementDisabled"
                  :loading="dataReplacementBusy"
                  @click="openDataReplacementDialog"
                >
                  表格数据替换
                </el-button>
                <el-button
                  v-permission="'market:release:create'"
                  size="small"
                  plain
                  class="workflow-soft-button"
                  :disabled="bulkSaveDisabled"
                  :loading="savingReleaseDrafts"
                  @click="saveAllReleaseDraftChanges"
                >
                  保存修改
                </el-button>
                <el-button
                  v-permission="'market:release:create'"
                  size="small"
                  plain
                  class="workflow-soft-button"
                  :disabled="selectedOrders.length === 0 || replacingDrawings || submitting || dataReplacementBusy"
                  :loading="replacingDrawings"
                  @click="openDrawingReplaceDialog"
                >
                  导入替换图纸
                </el-button>
                <el-upload
                  ref="selectionUploadRef"
                  action="#"
                  :auto-upload="false"
                  :show-file-list="false"
                  accept=".xlsx, .xls"
                  :on-change="handleSelectionExcelChange"
                >
                  <template #trigger>
                    <el-button
                      size="small"
                      plain
                      class="workflow-soft-button"
                      :disabled="selectedOrders.length === 0 || releaseItemTotal === 0 || importSelecting || submitting"
                      :loading="importSelecting"
                    >
                      导入表格选定
                    </el-button>
                  </template>
                </el-upload>
              </div>
            </div>
            <el-table
              ref="itemTableRef"
              class="release-selection-table"
              :data="pagedReleaseItems"
              :row-key="rowKey"
              border
              stripe
              height="640"
              scrollbar-always-on
              v-loading="loading"
              @selection-change="handleSelectionChange"
            >
              <el-table-column type="selection" width="45" :selectable="canSelectItem" :reserve-selection="true" />
              <el-table-column prop="sourceOrderNo" label="来源单号" width="170" fixed show-overflow-tooltip>
                <template #default="{ row }">
                  <div class="source-order-cell">
                    <el-tag type="info" size="small" effect="plain">
                      {{ sourceTypeLabel(row.sourceType) }}
                    </el-tag>
                    <span class="source-order-no">{{ row.sourceOrderNo || '-' }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="partNo" label="图号" width="130" fixed show-overflow-tooltip />
              <el-table-column prop="version" label="版本" width="150" align="center">
                <template #default="{ row }">
                  <div class="row-version-editor">
                    <el-input
                      v-model="row.versionDraft"
                      size="small"
                      maxlength="20"
                      clearable
                      :disabled="!canSelectItem(row)"
                      class="row-version-input"
                    />
                    <el-button
                      size="small"
                      type="primary"
                      plain
                      :disabled="!canSelectItem(row) || isVersionSaving(row) || submitting"
                      :loading="isVersionSaving(row)"
                      @click.stop="saveReleaseItemVersion(row)"
                    >
                      保存
                    </el-button>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="类型" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="isAssemblyItem(row) ? 'warning' : 'info'" size="small">
                    {{ isAssemblyItem(row) ? '组合件' : '普通件' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="图纸" width="80" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.hasDrawing || row.drawingUrl ? 'success' : 'warning'" size="small">
                    {{ row.hasDrawing || row.drawingUrl ? '已绑定' : '缺图' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="图纸预览" width="100" align="center">
                <template #default="{ row }">
                  <el-button
                    v-if="row.drawingUrl"
                    type="primary"
                    size="small"
                    plain
                    @click.stop="openPdfPreview(row.drawingUrl, row.partNo)"
                  >
                    预览
                  </el-button>
                  <el-tag v-else type="info" size="small">无图</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="可选状态" width="150" align="center">
                <template #default="{ row }">
                  <el-tag v-if="canSelectItem(row)" type="success" size="small">可提单</el-tag>
                  <el-tooltip v-else :content="releaseBlockedReasonText(row)" placement="top">
                    <el-tag type="warning" size="small" class="release-status-tag">
                      {{ releaseBlockedReasonText(row) }}
                    </el-tag>
                  </el-tooltip>
                </template>
              </el-table-column>
              <el-table-column prop="qty" label="询价数量" width="90" align="center" />
              <el-table-column prop="ticketedQty" label="已提单数量" width="105" align="center" />
              <el-table-column prop="remainingQty" label="剩余可提数量" width="110" align="center" />
              <el-table-column prop="leadTime" label="客户询价交期" width="120" show-overflow-tooltip>
                <template #default="{ row }">{{ row.leadTime || '-' }}</template>
              </el-table-column>
              <el-table-column prop="quoteLeadTime" label="报价承诺交期" width="120" show-overflow-tooltip>
                <template #default="{ row }">{{ row.quoteLeadTime || '-' }}</template>
              </el-table-column>
              <el-table-column label="内部目标交期" width="210">
                <template #default="{ row }">
                  <div class="row-date-editor">
                    <el-date-picker
                      v-model="row.internalTargetLeadTimeDraft"
                      type="date"
                      value-format="YYYY-MM-DD"
                      format="YYYY-MM-DD"
                      placeholder="默认沿用报价交期"
                      clearable
                      :disabled="!canSelectItem(row)"
                      class="row-date-picker"
                    />
                    <el-button
                      size="small"
                      type="primary"
                      plain
                      :disabled="!canSelectItem(row) || isInternalLeadTimeSaving(row) || submitting"
                      :loading="isInternalLeadTimeSaving(row)"
                      @click.stop="saveInternalTargetLeadTime(row)"
                    >
                      保存
                    </el-button>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="提单交期" width="120" show-overflow-tooltip>
                <template #default="{ row }">{{ resolveDeliveryLeadTime(row) }}</template>
              </el-table-column>
              <el-table-column label="通过数量" width="130" align="center">
                <template #default="scope">
                  <el-input-number
                    v-model="acceptedQtyMap[rowKey(scope.row)]"
                    :min="1"
                    :disabled="!canSelectItem(scope.row)"
                    controls-position="right"
                    class="qty-input"
                  />
                </template>
              </el-table-column>
              <el-table-column prop="engineeringMaterialName" label="材料" width="110" show-overflow-tooltip />
              <el-table-column prop="materialName" label="材料名称" width="240" show-overflow-tooltip>
                <template #default="{ row }">
                  <div class="row-field-editor">
                    <el-input
                      v-model="row.materialNameDraft"
                      size="small"
                      maxlength="500"
                      clearable
                      :disabled="!canSelectItem(row)"
                      class="row-field-input"
                    />
                    <el-button
                      size="small"
                      type="primary"
                      plain
                      :disabled="!canSelectItem(row) || isFieldSaving(row) || submitting"
                      :loading="isFieldSaving(row)"
                      @click.stop="saveReleaseItemFields(row)"
                    >
                      保存
                    </el-button>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="surfaceTreatment" label="表面处理" width="220" show-overflow-tooltip>
                <template #default="{ row }">
                  <div class="row-field-editor">
                    <el-input
                      v-model="row.surfaceTreatmentDraft"
                      size="small"
                      maxlength="200"
                      clearable
                      :disabled="!canSelectItem(row)"
                      class="row-field-input"
                    />
                    <el-button
                      size="small"
                      type="primary"
                      plain
                      :disabled="!canSelectItem(row) || isFieldSaving(row) || submitting"
                      :loading="isFieldSaving(row)"
                      @click.stop="saveReleaseItemFields(row)"
                    >
                      保存
                    </el-button>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" width="220" show-overflow-tooltip>
                <template #default="{ row }">
                  <div class="row-field-editor">
                    <el-input
                      v-model="row.remarkDraft"
                      size="small"
                      maxlength="500"
                      clearable
                      :disabled="!canSelectItem(row)"
                      class="row-field-input"
                    />
                    <el-button
                      size="small"
                      type="primary"
                      plain
                      :disabled="!canSelectItem(row) || isFieldSaving(row) || submitting"
                      :loading="isFieldSaving(row)"
                      @click.stop="saveReleaseItemFields(row)"
                    >
                      保存
                    </el-button>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="下单日期" width="220" show-overflow-tooltip>
                <template #default="{ row }">
                  <div class="row-field-editor row-order-field-editor">
                    <el-date-picker
                      v-model="row.salesOrderDateDraft"
                      type="date"
                      value-format="YYYY-MM-DD"
                      size="small"
                      clearable
                      :placeholder="orderFieldPlaceholder(row, 'salesOrderDate')"
                      :disabled="!canEditReleaseItemOrderFields(row)"
                      class="row-order-date-picker"
                    />
                    <el-button
                      v-permission="'market:release:create'"
                      size="small"
                      type="primary"
                      plain
                      :disabled="!canEditReleaseItemOrderFields(row) || !hasUnsavedOrderFieldDraft(row, 'salesOrderDate') || isOrderFieldSaving(row, 'salesOrderDate') || submitting"
                      :loading="isOrderFieldSaving(row, 'salesOrderDate')"
                      @click.stop="saveReleaseItemOrderField(row, 'salesOrderDate')"
                    >保存</el-button>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="客户订单号" width="220" show-overflow-tooltip>
                <template #default="{ row }">
                  <div class="row-field-editor row-order-field-editor">
                    <el-input
                      v-model="row.customerOrderNoDraft"
                      size="small"
                      maxlength="100"
                      clearable
                      :placeholder="orderFieldPlaceholder(row, 'customerOrderNo')"
                      :disabled="!canEditReleaseItemOrderFields(row)"
                      class="row-order-field-input"
                    />
                    <el-button
                      v-permission="'market:release:create'"
                      size="small"
                      type="primary"
                      plain
                      :disabled="!canEditReleaseItemOrderFields(row) || !hasUnsavedOrderFieldDraft(row, 'customerOrderNo') || isOrderFieldSaving(row, 'customerOrderNo') || submitting"
                      :loading="isOrderFieldSaving(row, 'customerOrderNo')"
                      @click.stop="saveReleaseItemOrderField(row, 'customerOrderNo')"
                    >保存</el-button>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="客户项目号" width="220" show-overflow-tooltip>
                <template #default="{ row }">
                  <div class="row-field-editor row-order-field-editor">
                    <el-input
                      v-model="row.customerProjectNoDraft"
                      size="small"
                      maxlength="100"
                      clearable
                      :placeholder="orderFieldPlaceholder(row, 'customerProjectNo')"
                      :disabled="!canEditReleaseItemOrderFields(row)"
                      class="row-order-field-input"
                    />
                    <el-button
                      v-permission="'market:release:create'"
                      size="small"
                      type="primary"
                      plain
                      :disabled="!canEditReleaseItemOrderFields(row) || !hasUnsavedOrderFieldDraft(row, 'customerProjectNo') || isOrderFieldSaving(row, 'customerProjectNo') || submitting"
                      :loading="isOrderFieldSaving(row, 'customerProjectNo')"
                      @click.stop="saveReleaseItemOrderField(row, 'customerProjectNo')"
                    >保存</el-button>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="客户下单抬头" width="240" show-overflow-tooltip>
                <template #default="{ row }">
                  <div class="row-field-editor row-order-field-editor">
                    <el-input
                      v-model="row.customerOrderTitleDraft"
                      size="small"
                      maxlength="255"
                      clearable
                      :placeholder="orderFieldPlaceholder(row, 'customerOrderTitle')"
                      :disabled="!canEditReleaseItemOrderFields(row)"
                      class="row-order-field-input"
                    />
                    <el-button
                      v-permission="'market:release:create'"
                      size="small"
                      type="primary"
                      plain
                      :disabled="!canEditReleaseItemOrderFields(row) || !hasUnsavedOrderFieldDraft(row, 'customerOrderTitle') || isOrderFieldSaving(row, 'customerOrderTitle') || submitting"
                      :loading="isOrderFieldSaving(row, 'customerOrderTitle')"
                      @click.stop="saveReleaseItemOrderField(row, 'customerOrderTitle')"
                    >保存</el-button>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="订单类型" width="220" show-overflow-tooltip>
                <template #default="{ row }">
                  <div class="row-field-editor row-order-field-editor">
                    <el-select
                      v-model="row.orderTypeDraft"
                      size="small"
                      clearable
                      :placeholder="orderFieldPlaceholder(row, 'orderType')"
                      :disabled="!canEditReleaseItemOrderFields(row)"
                      class="row-order-type-select"
                    >
                      <el-option label="预测订单" value="预测订单" />
                      <el-option label="备货订单" value="备货订单" />
                      <el-option label="正式订单" value="正式订单" />
                    </el-select>
                    <el-button
                      v-permission="'market:release:create'"
                      size="small"
                      type="primary"
                      plain
                      :disabled="!canEditReleaseItemOrderFields(row) || !hasUnsavedOrderFieldDraft(row, 'orderType') || isOrderFieldSaving(row, 'orderType') || submitting"
                      :loading="isOrderFieldSaving(row, 'orderType')"
                      @click.stop="saveReleaseItemOrderField(row, 'orderType')"
                    >保存</el-button>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="内部项目号" width="220" show-overflow-tooltip>
                <template #default="{ row }">
                  <div class="row-field-editor row-order-field-editor">
                    <el-input
                      v-model="row.internalProjectNoDraft"
                      size="small"
                      maxlength="100"
                      clearable
                      :placeholder="orderFieldPlaceholder(row, 'internalProjectNo')"
                      :disabled="!canEditReleaseItemOrderFields(row)"
                      class="row-order-field-input"
                    />
                    <el-button
                      v-permission="'market:release:create'"
                      size="small"
                      type="primary"
                      plain
                      :disabled="!canEditReleaseItemOrderFields(row) || !hasUnsavedOrderFieldDraft(row, 'internalProjectNo') || isOrderFieldSaving(row, 'internalProjectNo') || submitting"
                      :loading="isOrderFieldSaving(row, 'internalProjectNo')"
                      @click.stop="saveReleaseItemOrderField(row, 'internalProjectNo')"
                    >保存</el-button>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="110" align="center" />
            </el-table>
            <div v-if="releaseItemTotal" class="erp-pagination">
              <el-pagination
                v-model:current-page="releaseItemPagination.page"
                v-model:page-size="releaseItemPagination.pageSize"
                :page-sizes="CLIENT_PAGE_SIZES"
                :pager-count="7"
                :total="releaseItemTotal"
                layout="total, sizes, prev, pager, next, jumper"
                background
                @size-change="releaseItemPagination.page = 1"
              />
            </div>
          </div>

        </el-col>
      </el-row>
    </section>

    <el-dialog
      v-model="drawingReplaceVisible"
      title="导入替换图纸"
      width="720px"
      :close-on-click-modal="false"
    >
      <div class="drawing-replace-dialog">
        <el-alert
          :title="`将按图号匹配当前已选 ${selectedOrders.length} 个订单，匹配成功会覆盖旧图纸。`"
          description="系统会在当前选中的订单范围内按图号匹配全部零件，命中后替换图纸；无需勾选零件。"
          type="warning"
          :closable="false"
          show-icon
        />

        <div class="erp-upload-surface drawing-replace-upload">
          <div class="drawing-replace-dual-grid">
            <section class="drawing-replace-entry">
              <div class="drawing-replace-entry-title">按图号替换</div>
              <div
                v-permission="'market:release:create'"
                class="custom-dropzone drawing-replace-dropzone supplement-dropzone"
                @dragover.prevent
                @drop.prevent="handleReplacementDrop"
              >
                <el-icon class="el-icon--upload dropzone-icon"><upload-filled /></el-icon>
                <div class="el-upload__text dropzone-text">
                  将 <em>图纸文件夹</em> 或多个 PDF 图纸直接拖拽至此处
                </div>
                <div class="el-upload__tip">支持 图号.pdf、图号 空格说明.pdf，例如 L20100000733.pdf 或 L20100000733 定位块.pdf；文件名中的 - 会按图号内容处理。</div>
                <div class="folder-select-row">或点击下方按钮选择多个 PDF / 图纸文件夹</div>
                <div class="drawing-replace-upload-actions import-upload-button-stack">
                  <el-button v-permission="'market:release:create'" plain class="workflow-soft-button folder-select-button" :disabled="replacingDrawings || submitting" @click="triggerReplacementFileSelect">选择多个 PDF</el-button>
                  <el-button v-permission="'market:release:create'" plain class="workflow-soft-button folder-select-button" :disabled="replacingDrawings || submitting" @click="triggerReplacementFolderSelect">选择文件夹</el-button>
                </div>
                <input type="file" ref="replacementFileInput" accept=".pdf" multiple class="hidden-input" @change="handleReplacementFileSelect" />
                <input type="file" ref="replacementFolderInput" webkitdirectory directory multiple class="hidden-input" @change="handleReplacementFolderSelect" />
              </div>

              <div v-if="replacementDrawingFiles.length > 0" class="replacement-file-stats">
                <h4>按图号待替换图纸（{{ replacementDrawingFiles.length }} 份）</h4>
                <ul class="replacement-pdf-list">
                  <li v-for="(file, index) in replacementDrawingFiles" :key="`${file.name}-${file.size}-${index}`">
                    <span>{{ file.name }}</span>
                    <el-button v-permission="'market:release:create'" link type="danger" size="small" :disabled="replacingDrawings || submitting" @click="removeReplacementDrawingFile(index)">移除</el-button>
                  </li>
                </ul>
              </div>
            </section>

            <section class="drawing-replace-entry">
              <div class="drawing-replace-entry-title">按图号+版本替换</div>
              <div
                v-permission="'market:release:create'"
                class="custom-dropzone drawing-replace-dropzone supplement-dropzone"
                @dragover.prevent
                @drop.prevent="handleReplacementVersionDrop"
              >
                <el-icon class="el-icon--upload dropzone-icon"><upload-filled /></el-icon>
                <div class="el-upload__text dropzone-text">
                  将 <em>图纸文件夹</em> 或多个 PDF 图纸直接拖拽至此处
                </div>
                <div class="el-upload__tip">支持 图号_版本.pdf、图号_版本 空格说明.pdf，例如 L20100000733_A.pdf；文件名中的 - 会按图号或版本内容处理。</div>
                <div class="folder-select-row">或点击下方按钮选择多个 PDF / 图纸文件夹</div>
                <div class="drawing-replace-upload-actions import-upload-button-stack">
                  <el-button v-permission="'market:release:create'" plain class="workflow-soft-button folder-select-button" :disabled="replacingDrawings || submitting" @click="triggerReplacementVersionFileSelect">选择多个 PDF</el-button>
                  <el-button v-permission="'market:release:create'" plain class="workflow-soft-button folder-select-button" :disabled="replacingDrawings || submitting" @click="triggerReplacementVersionFolderSelect">选择文件夹</el-button>
                </div>
                <input type="file" ref="replacementVersionFileInput" accept=".pdf" multiple class="hidden-input" @change="handleReplacementVersionFileSelect" />
                <input type="file" ref="replacementVersionFolderInput" webkitdirectory directory multiple class="hidden-input" @change="handleReplacementVersionFolderSelect" />
              </div>

              <div v-if="replacementVersionDrawingFiles.length > 0" class="replacement-file-stats">
                <h4>按图号+版本待替换图纸（{{ replacementVersionDrawingFiles.length }} 份）</h4>
                <ul class="replacement-pdf-list">
                  <li v-for="(file, index) in replacementVersionDrawingFiles" :key="`${file.name}-${file.size}-${index}`">
                    <span>{{ file.name }}</span>
                    <el-button v-permission="'market:release:create'" link type="danger" size="small" :disabled="replacingDrawings || submitting" @click="removeReplacementVersionDrawingFile(index)">移除</el-button>
                  </li>
                </ul>
              </div>
            </section>
          </div>
        </div>

        <div v-if="clientSkippedReplacementFiles.length > 0 && !replacementDrawingResult" class="replacement-result">
          <el-alert
            :title="`已跳过 ${clientSkippedReplacementFiles.length} 个非 PDF 文件。`"
            type="warning"
            :closable="false"
            show-icon
          />
        </div>

        <div v-if="replacementDrawingResult" class="replacement-result">
          <el-alert
            :title="replacementDrawingResult.message || '图纸替换处理完成。'"
            :type="replacementResultHasWarnings ? 'warning' : 'success'"
            :closable="false"
            show-icon
          />
          <div class="replacement-summary-grid">
            <div><strong>{{ replacementDrawingResult.uploadCount || 0 }}</strong><span>上传PDF</span></div>
            <div><strong>{{ replacementDrawingResult.matchedCount || 0 }}</strong><span>匹配零件</span></div>
            <div><strong>{{ replacementDrawingResult.replacedCount || 0 }}</strong><span>已替换</span></div>
            <div><strong>{{ replacementDrawingResult.unmatchedCount || 0 }}</strong><span>未匹配</span></div>
            <div><strong>{{ replacementSkippedFiles.length }}</strong><span>非PDF</span></div>
          </div>
          <el-tabs>
            <el-tab-pane :label="`已替换 ${replacementReplacedItems.length}`">
              <ul v-if="replacementReplacedItems.length" class="result-list replacement-result-list">
                <li v-for="(item, index) in replacementReplacedItems" :key="index">
                  <span class="result-main">{{ item.partNo || '-' }} / {{ item.version || '-' }}</span>
                  <span v-if="item.rfqNo" class="result-sub"> - {{ item.rfqNo }}</span>
                  <span v-if="item.fileName" class="result-sub"> - {{ item.fileName }}</span>
                </li>
              </ul>
              <el-empty v-else description="无记录" :image-size="60" />
            </el-tab-pane>
            <el-tab-pane :label="`未匹配 ${replacementUnmatchedDrawings.length}`">
              <ul v-if="replacementUnmatchedDrawings.length" class="result-list replacement-result-list">
                <li v-for="(item, index) in replacementUnmatchedDrawings" :key="index">
                  <span class="result-main">{{ item.fileName || '-' }}</span>
                  <span v-if="item.reason" class="result-reason"> - {{ item.reason }}</span>
                </li>
              </ul>
              <el-empty v-else description="无记录" :image-size="60" />
            </el-tab-pane>
            <el-tab-pane :label="`非PDF ${replacementSkippedFiles.length}`">
              <ul v-if="replacementSkippedFiles.length" class="result-list replacement-result-list">
                <li v-for="(item, index) in replacementSkippedFiles" :key="index">
                  <span class="result-main">{{ item.fileName || '-' }}</span>
                  <span v-if="item.reason" class="result-reason"> - {{ item.reason }}</span>
                </li>
              </ul>
              <el-empty v-else description="无记录" :image-size="60" />
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>

      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="drawingReplaceVisible = false">关闭</el-button>
          <el-button
            v-permission="'market:release:create'"
            type="primary"
            :loading="replacingDrawings"
            :disabled="(replacementDrawingFiles.length === 0 && replacementVersionDrawingFiles.length === 0) || replacingDrawings || submitting"
            @click="submitReplacementDrawings"
          >
            上传并替换
          </el-button>
        </div>
      </template>
    </el-dialog>

    <ReleaseItemDataReplacementDialog
      v-model="dataReplacementVisible"
      :sources="selectedOrderSources"
      :selected-rows-by-key="dataReplacementDraftRowsByKey"
      :disabled="submitting || selectedOrders.length === 0"
      @busy-change="dataReplacementBusy = $event"
      @confirmed="handleDataReplacementConfirmed"
    />

    <OrderPickerDialog
      v-model="orderPickerVisible"
      title="选择市场部议价订单"
      customer-scope="release"
      order-scope="release"
      :allowed-statuses="RELEASE_ALLOWED_STATUSES"
      :multiple="true"
      :locked-customer-name="selectedCustomerName"
      :selected-order-ids="selectedOrderKeys"
      @select="handleOrderSelected"
    />

    <OrderPickerDialog
      v-model="appendSalesOrderPickerVisible"
      title="选择要追加的销售订单"
      customer-scope="release"
      order-scope="release-append-sales-order"
      :allowed-statuses="['已生成提单']"
      default-status-after-customer="已生成提单"
      :multiple="false"
      :locked-customer-name="selectedCustomerName"
      :locked-customer-code="selectedCustomerCode"
      @select="appendToSalesOrder"
    />

    <el-dialog
      v-model="pdfVisible"
      :title="`图纸预览：${pdfTitle}`"
      width="75%"
      top="5vh"
      destroy-on-close
    >
      <div v-loading="!pdfLoaded" class="pdf-frame-shell">
        <iframe
          v-if="currentPdfSrc"
          :src="currentPdfSrc"
          width="100%"
          height="100%"
          frameborder="0"
          @load="pdfLoaded = true"
        ></iframe>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DocumentChecked, UploadFilled } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import OrderPickerDialog from './OrderPickerDialog.vue'
import ReleaseItemDataReplacementDialog from './market-release/ReleaseItemDataReplacementDialog.vue'
import { collectPdfFilesFromDataTransfer, isPdfFile, mergePdfFiles } from '../utils/drawingUpload'
import {
  generateTicket,
  getReleaseSummary,
  appendReleaseToSalesOrder,
  listReleaseItemsBatch,
  parsePartSelectionBatch,
  replaceReleaseDrawings,
  setInternalTargetLeadTime as setInternalTargetLeadTimeRequest,
  shiftInternalTargetLeadTime,
  updateInternalTargetLeadTime,
  updateReleaseItemFields,
  updateReleaseItemOrderFields,
  updateReleaseItemVersion
} from '../services/marketApi'
import { downloadSalesOrderProductionTaskNotice } from '../services/orderApi'
import {
  CLIENT_PAGE_SIZES,
  DEFAULT_CLIENT_PAGE_SIZE
} from '../utils/clientPagination'
import {
  createAuthenticatedObjectUrl,
  readFileErrorMessage,
  revokeObjectUrl
} from '../api'
import { usePendingAction } from '../composables/usePendingAction'
import { applyReleaseItemDataReplacementResult } from './marketReleaseDataReplacement'
import {
  collectReleaseRowsWithDraftChanges,
  hydrateReleaseItemDrafts,
  releaseDraftChangeSummary
} from './marketReleaseDrafts'

const selectedRfqId = ref(null)
const selectedOrders = ref([])
const orderPickerVisible = ref(false)
const appendSalesOrderPickerVisible = ref(false)
const itemList = ref([])
const releaseItemTotal = ref(0)
const selectedItemIdMap = reactive({})
const itemTableRef = ref(null)
const selectionUploadRef = ref(null)
const replacementFileInput = ref(null)
const replacementFolderInput = ref(null)
const replacementVersionFileInput = ref(null)
const replacementVersionFolderInput = ref(null)
const releaseItemPagination = reactive({ page: 1, pageSize: DEFAULT_CLIENT_PAGE_SIZE })
const acceptedQtyMap = reactive({})
const internalShiftDays = ref(0)
const manualInternalTargetDate = ref('')
const drawingReplaceVisible = ref(false)
const dataReplacementVisible = ref(false)
const dataReplacementBusy = ref(false)
const replacementDrawingFiles = ref([])
const replacementVersionDrawingFiles = ref([])
const replacementDrawingResult = ref(null)
const clientSkippedReplacementFiles = ref([])
const releaseResult = ref(null)
const pdfVisible = ref(false)
const pdfTitle = ref('')
const pdfLoaded = ref(false)
const currentPdfSrc = ref('')

const loading = ref(false)
let syncingReleaseItemSelection = false
const router = useRouter()
const requestErrorMessage = (error, fallback) => error?.response?.data?.message || error?.payload?.message || error?.message || fallback

const RELEASE_ALLOWED_STATUSES = ['已报价', '部分已提单', '已生成提单']

const toReleaseOrderSource = (order) => ({
  sourceType: 'RFQ',
  sourceOrderId: Number(order?.sourceOrderId ?? order?.rfqId ?? order?.id)
})
const sourceKey = (source) => `${source?.sourceType || 'RFQ'}:${source?.sourceOrderId ?? source?.id ?? ''}`
const rowKey = (row) => row?.releaseItemKey || `${row?.sourceType || 'RFQ'}:${row?.sourceItemId ?? row?.id ?? ''}`
const selectedOrderSources = computed(() => selectedOrders.value.map(toReleaseOrderSource))
const selectedOrderKeys = computed(() => selectedOrderSources.value.map(sourceKey))
const selectedRfqIds = computed(() => selectedOrderSources.value
  .filter(source => source.sourceType === 'RFQ')
  .map(source => source.sourceOrderId))
const selectedCustomerName = computed(() => selectedOrders.value[0]?.customerName || '')
const selectedCustomerCode = computed(() => selectedOrders.value[0]?.customerCode || '')
const selectedSourcesIncludeNonRfq = computed(() => selectedOrderSources.value.some(source => source.sourceType !== 'RFQ'))
const { isPending, runAction } = usePendingAction()
const releaseActionKeys = {
  importSelection: 'market:release:import-selection',
  replaceDrawings: 'market:release:replace-drawings',
  bulkSave: 'market:release:bulk-save-drafts',
  shiftInternalLeadTime: 'market:release:shift-internal-lead-time',
  setInternalTargetLeadTime: 'market:release:set-internal-target-lead-time',
  fields: (releaseItemKey) => `market:release:fields:${releaseItemKey}`,
  orderField: (releaseItemKey, field) => `market:release:order-fields:${releaseItemKey}:${field}`,
  version: (itemId) => `market:release:version:${itemId}`,
  internalLeadTime: (itemId) => `market:release:internal-lead-time:${itemId}`,
  generateTicket: () => `market:release:generate-ticket:${selectedOrderKeys.value.join(',') || 'none'}`,
  appendSalesOrder: () => `market:release:append-sales-order:${selectedOrderKeys.value.join(',') || 'none'}`
}
const generatingTicket = computed(() => isPending(releaseActionKeys.generateTicket()))
const appendingSalesOrder = computed(() => isPending(releaseActionKeys.appendSalesOrder()))
const submitting = computed(() => generatingTicket.value || appendingSalesOrder.value)
const adjustingInternalLeadTime = computed(() => isPending(releaseActionKeys.shiftInternalLeadTime))
const settingInternalTargetLeadTime = computed(() => isPending(releaseActionKeys.setInternalTargetLeadTime))
const importSelecting = computed(() => isPending(releaseActionKeys.importSelection))
const replacingDrawings = computed(() => isPending(releaseActionKeys.replaceDrawings))
const savingReleaseDrafts = computed(() => isPending(releaseActionKeys.bulkSave))
const releaseDraftRowsForBulkSave = computed(() => collectReleaseRowsWithDraftChanges([
  ...Object.values(selectedItemIdMap),
  ...itemList.value
], rowKey))
const bulkSaveDisabled = computed(() => submitting.value || dataReplacementBusy.value || replacingDrawings.value || savingReleaseDrafts.value)
const dataReplacementDisabled = computed(() => selectedOrders.value.length === 0 || dataReplacementBusy.value || submitting.value || selectedSourcesIncludeNonRfq.value)
const dataReplacementDraftRowsByKey = computed(() => {
  const rowsByKey = {}
  itemList.value.forEach(row => {
    rowsByKey[rowKey(row)] = row
  })
  Object.assign(rowsByKey, selectedItemIdMap)
  return rowsByKey
})
const isVersionSaving = (row) => isPending(releaseActionKeys.version(row?.id))
const isFieldSaving = (row) => isPending(releaseActionKeys.fields(rowKey(row)))
const isInternalLeadTimeSaving = (row) => isPending(releaseActionKeys.internalLeadTime(row?.id))

const today = new Date().toISOString().slice(0, 10)
const releaseForm = reactive({
  documentTypeCode: 'SO06',
  salesOrderDate: today,
  customerOrderTitle: '',
  orderType: '',
  internalProjectNo: '',
  customerOrderNo: '',
  customerProjectNo: '',
  arrangeType: '计划'
})
const releaseBatchState = reactive({ fingerprint: '', key: '' })
const appendBatchState = reactive({ fingerprint: '', key: '' })
const releaseResultMode = computed(() => releaseResult.value?.resultMode || 'CREATE')
const releaseResultTitle = computed(() => (
  releaseResultMode.value === 'APPEND' ? '已追加到销售订单' : '已生成提单和 U9 销售订单'
))
const releaseResultDescription = computed(() => (
  releaseResultMode.value === 'APPEND'
    ? '本批次已进入已提零件，后续可在 U9 同步页面继续处理。'
    : '本批次已进入已提零件，后续可在 U9 同步页面继续处理。'
))

const openDerivedOrderU9 = () => {
  const salesOrderId = Number(releaseResult.value?.salesOrderId)
  if (!Number.isInteger(salesOrderId) || salesOrderId <= 0) {
    ElMessage.warning('当前提单结果缺少有效的派生销售订单。')
    return
  }
  router.push({ path: '/u9-sync', query: { salesOrderId: String(salesOrderId) } })
}

const openDerivedOrderSalesOrder = () => {
  const salesOrderId = Number(releaseResult.value?.salesOrderId)
  if (!Number.isInteger(salesOrderId) || salesOrderId <= 0) {
    ElMessage.warning('当前提单结果缺少有效的派生销售订单。')
    return
  }
  router.push({ path: '/sales-orders', query: { salesOrderId: String(salesOrderId) } })
}

const openDerivedOrderDeliveredItems = () => {
  const salesOrderId = Number(releaseResult.value?.salesOrderId)
  if (!Number.isInteger(salesOrderId) || salesOrderId <= 0) {
    ElMessage.warning('当前提单结果缺少有效的派生销售订单。')
    return
  }
  router.push({ path: '/delivered-items', query: { salesOrderId: String(salesOrderId) } })
}

const autoExportProductionTaskNotice = async (result) => {
  const salesOrderId = Number(result?.salesOrderId)
  if (!Number.isInteger(salesOrderId) || salesOrderId <= 0) {
    ElMessage.warning('正式提单已生成，但缺少销售订单 ID，未自动导出生产任务通知单。')
    return { success: false }
  }

  const salesOrderNo = result?.documentNo || result?.salesOrderNo || salesOrderId
  try {
    const exportResult = await downloadSalesOrderProductionTaskNotice(salesOrderId, `${salesOrderNo}-生产任务通知单.xlsx`)
    if (!exportResult?.success) {
      ElMessage.warning(exportResult?.message || '生产任务通知单导出失败，请在销售订单详情中手动导出。')
    }
    return exportResult
  } catch (error) {
    console.error(error)
    ElMessage.warning(error?.message || '生产任务通知单导出失败，请在销售订单详情中手动导出。')
    return { success: false }
  }
}

const pagedReleaseItems = computed(() => itemList.value)
const selectedRows = computed(() => Object.values(selectedItemIdMap))
const selectedRowsWithUnsavedOrderFields = computed(() => selectedRows.value.filter(hasUnsavedOrderFieldDraft))
const appendSalesOrderDisabled = computed(() => (selectedRows.value.length === 0 || selectedRowsWithUnsavedOrderFields.value.length > 0 || selectedSourcesIncludeNonRfq.value || submitting.value || dataReplacementBusy.value))
const selectedReleaseItemIds = computed(() => new Set(selectedRows.value.map(row => rowKey(row))))
const replacementReplacedItems = computed(() => replacementDrawingResult.value?.replacedItems || [])
const replacementUnmatchedDrawings = computed(() => replacementDrawingResult.value?.unmatchedDrawings || [])
const replacementSkippedFiles = computed(() => replacementDrawingResult.value?.skippedFiles || [])
const replacementResultHasWarnings = computed(() => (
  Number(replacementDrawingResult.value?.unmatchedCount || 0) > 0
  || replacementSkippedFiles.value.length > 0
))

const resetReleasePagination = () => {
  releaseItemPagination.page = 1
}

const clearSelectedRows = () => {
  Object.keys(selectedItemIdMap).forEach(id => delete selectedItemIdMap[id])
}

const syncReleaseItemTableSelection = async () => {
  syncingReleaseItemSelection = true
  await nextTick()
  itemTableRef.value?.clearSelection()
  pagedReleaseItems.value.forEach(row => {
    if (selectedReleaseItemIds.value.has(rowKey(row))) {
      itemTableRef.value?.toggleRowSelection(row, true)
    }
  })
  await nextTick()
  syncingReleaseItemSelection = false
}

watch(
  () => [releaseItemPagination.page, releaseItemPagination.pageSize],
  () => {
    if (selectedOrders.value.length) {
      fetchReleaseItemsBatch()
    }
  }
)

const openOrderPicker = () => {
  orderPickerVisible.value = true
}

const firstSelectedRfqId = () => selectedOrderSources.value
  .find(source => source.sourceType === 'RFQ')?.sourceOrderId || null

const handleOrderSelected = async (orderOrOrders) => {
  const incomingOrders = Array.isArray(orderOrOrders) ? orderOrOrders : [orderOrOrders]
  const existingKeys = new Set(selectedOrderKeys.value)
  const nextOrders = incomingOrders
    .filter(order => (order?.sourceType || 'RFQ') === 'RFQ')
    .filter(order => order?.id && !existingKeys.has(sourceKey(toReleaseOrderSource(order))))
  if (nextOrders.length === 0) return

  const wasEmpty = selectedOrders.value.length === 0
  if (wasEmpty) {
    resetReleaseFormForOrders()
    clearSelectedRows()
  }
  selectedOrders.value = [...selectedOrders.value, ...nextOrders]
  selectedRfqId.value = firstSelectedRfqId()
  resetReleasePagination()
  loading.value = true
  try {
    await fetchReleaseItemsBatch()
  } finally {
    loading.value = false
  }
}

const removeSelectedOrder = async (order) => {
  const orderKey = sourceKey(toReleaseOrderSource(order))
  if (!orderKey) return

  const relatedRows = [
    ...itemList.value,
    ...Object.values(selectedItemIdMap)
  ].filter(row => sourceKey({
    sourceType: row?.sourceType || 'RFQ',
    sourceOrderId: row?.sourceOrderId ?? row?.rfqId ?? selectedRfqId.value
  }) === orderKey)

  relatedRows.forEach(row => {
    delete selectedItemIdMap[rowKey(row)]
    delete acceptedQtyMap[rowKey(row)]
  })

  selectedOrders.value = selectedOrders.value.filter(item => sourceKey(toReleaseOrderSource(item)) !== orderKey)
  selectedRfqId.value = firstSelectedRfqId()
  resetReleasePagination()

  if (selectedOrders.value.length === 0) {
    setItemList([])
    releaseItemTotal.value = 0
    await nextTick()
    itemTableRef.value?.clearSelection()
    return
  }

  loading.value = true
  try {
    await fetchReleaseItemsBatch()
  } finally {
    loading.value = false
  }
}

const loadOrder = async (rfqId) => {
  if (!rfqId) return
  loading.value = true
  try {
    const response = await getReleaseSummary(rfqId)
    const refreshedOrder = response.orderInfo || {}
    selectedOrders.value = selectedOrders.value.map(order => order.id === rfqId ? { ...order, ...refreshedOrder } : order)
    selectedRfqId.value = firstSelectedRfqId()
    await fetchReleaseItemsBatch()
    clearSelectedRows()
    await nextTick()
    itemTableRef.value?.clearSelection()
  } catch (error) {
    console.error(error)
    ElMessage.error('询价单明细加载失败。')
  } finally {
    loading.value = false
  }
}

const fetchReleaseItemsBatch = async () => {
  if (selectedOrderSources.value.length === 0) {
    setItemList([])
    releaseItemTotal.value = 0
    return
  }
  const response = await listReleaseItemsBatch({
    sources: selectedOrderSources.value,
    rfqIds: selectedRfqIds.value,
    page: releaseItemPagination.page,
    pageSize: releaseItemPagination.pageSize
  })
  setItemList(response.records || [])
  releaseItemTotal.value = Number(response.total ?? 0)
  await syncReleaseItemTableSelection()
}

const resetReleaseFormForOrders = () => {
  releaseForm.documentTypeCode = 'SO06'
  releaseForm.salesOrderDate = today
  releaseForm.customerOrderTitle = ''
  releaseForm.orderType = ''
  releaseForm.internalProjectNo = ''
  releaseForm.customerOrderNo = ''
  releaseForm.customerProjectNo = ''
  releaseBatchState.fingerprint = ''
  releaseBatchState.key = ''
  appendBatchState.fingerprint = ''
  appendBatchState.key = ''
}

const setItemList = (rows) => {
  itemList.value = (rows || []).map(row => (
    hydrateReleaseItemDrafts(row, selectedItemIdMap[rowKey(row)])
  ))
  itemList.value.forEach(item => {
    const key = rowKey(item)
    const acceptedQty = Number(acceptedQtyMap[key] ?? 0)
    if (!Number.isFinite(acceptedQty) || acceptedQty <= 0) {
      acceptedQtyMap[key] = defaultAcceptedQtyFor(item)
    }
    if (selectedItemIdMap[key]) {
      selectedItemIdMap[key] = { ...selectedItemIdMap[key], ...item }
    }
  })
}

const remainingQtyFor = (row) => {
  const qty = Number(row?.remainingQty ?? row?.qty ?? 0)
  return Number.isFinite(qty) ? Math.max(0, qty) : 0
}

const defaultAcceptedQtyFor = (row) => {
  const remainingQty = remainingQtyFor(row)
  if (remainingQty > 0) return remainingQty
  const sourceQty = Number(row?.qty ?? 0)
  return Number.isFinite(sourceQty) && sourceQty > 0 ? sourceQty : 1
}

const canSelectItem = (row) => {
  return row?.canSelect !== false
}

const releaseBlockedReasonText = (row) => {
  if (!row) return '不可提单'
  if (row.releaseBlockedReason) return row.releaseBlockedReason
  if (row?.status === '已核价' && row?.taxIncludedUnitPrice != null) return '补充报价尚未确认'
  return '不可提单'
}

const canEditReleaseItemOrderFields = (row) => row?.canSelect !== false

const normalizeOrderFieldDraft = (value) => {
  if (value === null || value === undefined) return null
  const normalized = String(value).trim()
  return normalized || null
}

const orderFieldDefaults = {
  salesOrderDate: 'salesOrderDate',
  customerOrderNo: 'customerOrderNo',
  customerProjectNo: 'customerProjectNo',
  customerOrderTitle: 'customerOrderTitle',
  orderType: 'orderType',
  internalProjectNo: 'internalProjectNo'
}

const orderFieldLabels = {
  salesOrderDate: '下单日期',
  customerOrderNo: '客户订单号',
  customerProjectNo: '客户项目号',
  customerOrderTitle: '客户下单抬头',
  orderType: '订单类型',
  internalProjectNo: '内部项目号'
}

const orderFieldPlaceholder = (row, field) => {
  if (normalizeOrderFieldDraft(row?.[field])) return ''
  const defaultValue = releaseForm[orderFieldDefaults[field]]
  return defaultValue ? `继承 ${defaultValue}` : '继承订单默认'
}

const hasUnsavedOrderFieldDraft = (row, targetField) => Object.entries(orderFieldDefaults).some(([field, draftField]) => {
  if (targetField && field !== targetField) return false
  return normalizeOrderFieldDraft(row?.[`${draftField}Draft`]) !== normalizeOrderFieldDraft(row?.[field])
})

const orderFieldPayloadForSingleSave = (row, field) => {
  const payload = {}
  Object.entries(orderFieldDefaults).forEach(([fieldName, draftField]) => {
    const sourceValue = fieldName === field ? row?.[`${draftField}Draft`] : row?.[fieldName]
    payload[fieldName] = normalizeOrderFieldDraft(sourceValue)
  })
  return payload
}

const mergeSavedOrderFieldDrafts = (row, savedData, savedField) => {
  const saved = { ...row, ...savedData }
  Object.entries(orderFieldDefaults).forEach(([field, draftField]) => {
    const draftKey = `${draftField}Draft`
    saved[draftKey] = field === savedField
      ? (savedData[field] || '')
      : (row?.[draftKey] ?? '')
  })
  return saved
}

const isOrderFieldSaving = (row, field) => isPending(releaseActionKeys.orderField(rowKey(row), field))

const isAssemblyItem = (row) => String(row?.quoteMode || '').toUpperCase() === 'ASSEMBLY'

const mergeSavedReleaseItemRow = (row, saved) => {
  const key = rowKey(row)
  const index = itemList.value.findIndex(item => rowKey(item) === key)
  if (index >= 0) {
    itemList.value.splice(index, 1, saved)
  }
  if (selectedItemIdMap[key]) {
    selectedItemIdMap[key] = { ...selectedItemIdMap[key], ...saved }
  }
  return saved
}

const requireReleaseRowId = (row) => {
  if (!row?.id) throw new Error('零件缺少有效 ID，无法保存。')
}

const requireReleaseRowRfqId = (row) => {
  const rowRfqId = row?.rfqId || row?.sourceOrderId || selectedRfqId.value
  if (!rowRfqId) throw new Error('零件缺少订单信息，无法保存。')
  return rowRfqId
}

const persistReleaseItemVersion = async (row) => {
  requireReleaseRowId(row)
  const rowRfqId = row?.rfqId || selectedRfqId.value
  if (!rowRfqId) throw new Error('零件缺少订单信息，无法保存。')
  const version = String(row.versionDraft ?? '').trim()
  row.versionDraft = version
  const response = await updateReleaseItemVersion(row.id, {
    rfqId: rowRfqId,
    version
  })
  const savedData = response?.data || {}
  return mergeSavedReleaseItemRow(row, {
    ...row,
    ...savedData,
    versionDraft: savedData.version ?? version
  })
}

const persistReleaseItemFields = async (row) => {
  requireReleaseRowId(row)
  row.materialNameDraft = String(row.materialNameDraft ?? '').trim()
  row.surfaceTreatmentDraft = String(row.surfaceTreatmentDraft ?? '').trim()
  row.remarkDraft = String(row.remarkDraft ?? '').trim()
  const response = await updateReleaseItemFields(row.id, {
    sourceType: row.sourceType || 'RFQ',
    sourceOrderId: row.sourceOrderId,
    rfqId: row.rfqId || selectedRfqId.value,
    materialName: row.materialNameDraft,
    surfaceTreatment: row.surfaceTreatmentDraft,
    remark: row.remarkDraft
  })
  const savedData = response?.data || {}
  return mergeSavedReleaseItemRow(row, {
    ...row,
    ...savedData,
    materialNameDraft: savedData.materialName ?? '',
    surfaceTreatmentDraft: savedData.surfaceTreatment ?? '',
    remarkDraft: savedData.remark ?? ''
  })
}

const orderFieldPayloadForBulkSave = (row) => {
  const payload = {}
  Object.entries(orderFieldDefaults).forEach(([fieldName, draftField]) => {
    payload[fieldName] = normalizeOrderFieldDraft(row?.[`${draftField}Draft`])
  })
  return payload
}

const mergeSavedAllOrderFieldDrafts = (row, savedData) => {
  const saved = { ...row, ...savedData }
  Object.entries(orderFieldDefaults).forEach(([field, draftField]) => {
    saved[`${draftField}Draft`] = savedData[field] ?? ''
  })
  return saved
}

const persistReleaseItemOrderFields = async (row) => {
  requireReleaseRowId(row)
  const rowRfqId = requireReleaseRowRfqId(row)
  if (!canEditReleaseItemOrderFields(row)) {
    throw new Error('当前零件不能修改订单信息。')
  }
  const response = await updateReleaseItemOrderFields(row.id, {
    rfqId: rowRfqId,
    ...orderFieldPayloadForBulkSave(row)
  })
  const savedData = response?.data || {}
  return mergeSavedReleaseItemRow(row, mergeSavedAllOrderFieldDrafts(row, savedData))
}

const persistInternalTargetLeadTime = async (row) => {
  requireReleaseRowId(row)
  const rowRfqId = row?.rfqId || selectedRfqId.value
  if (!rowRfqId) throw new Error('零件缺少订单信息，无法保存。')
  const response = await updateInternalTargetLeadTime({
    rfqId: rowRfqId,
    itemId: row.id,
    internalTargetLeadTime: row.internalTargetLeadTimeDraft
  })
  const savedData = response?.data || {}
  return mergeSavedReleaseItemRow(row, {
    ...row,
    ...savedData,
    internalTargetLeadTimeDraft: savedData.internalTargetLeadTime ?? ''
  })
}

const saveReleaseItemVersion = async (row) => {
  const rowRfqId = row?.rfqId || selectedRfqId.value
  if (!rowRfqId || !row?.id) return
  return runAction(releaseActionKeys.version(row.id), async () => {
    try {
      await persistReleaseItemVersion(row)
      ElMessage.success('版本已保存。')
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || '版本保存失败。')
    }
  })
}

const saveReleaseItemFields = async (row) => {
  if (!row?.id) return
  return runAction(releaseActionKeys.fields(rowKey(row)), async () => {
    try {
      await persistReleaseItemFields(row)
      ElMessage.success('零件信息已保存。')
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || '零件信息保存失败。')
    }
  })
}

const saveReleaseItemOrderField = async (row, field) => {
  const rowRfqId = row?.rfqId || row?.sourceOrderId || selectedRfqId.value
  if (!rowRfqId || !row?.id || !canEditReleaseItemOrderFields(row) || !orderFieldDefaults[field]) return

  return runAction(releaseActionKeys.orderField(rowKey(row), field), async () => {
    try {
      const payload = orderFieldPayloadForSingleSave(row, field)
      const response = await updateReleaseItemOrderFields(row.id, {
        rfqId: rowRfqId,
        ...payload
      })
      const savedData = response?.data || {}
      const saved = mergeSavedOrderFieldDrafts(row, savedData, field)
      const key = rowKey(row)
      const index = itemList.value.findIndex(item => rowKey(item) === key)
      if (index >= 0) {
        itemList.value.splice(index, 1, saved)
      }
      if (selectedItemIdMap[key]) {
        selectedItemIdMap[key] = { ...selectedItemIdMap[key], ...saved }
      }
      ElMessage.success(`${orderFieldLabels[field]}已保存。`)
    } catch (error) {
      console.error(error)
      ElMessage.error(requestErrorMessage(error, '零件订单信息保存失败。'))
    }
  })
}

const saveAllReleaseDraftChanges = async () => {
  const rows = releaseDraftRowsForBulkSave.value
  if (rows.length === 0) {
    ElMessage.info('没有需要保存的修改。')
    return
  }

  return runAction(releaseActionKeys.bulkSave, async () => {
    const failures = []
    let savedRows = 0

    for (const row of rows) {
      let workingRow = row
      let rowSaved = false
      try {
        const summary = releaseDraftChangeSummary(workingRow)
        if (summary.version) {
          workingRow = await persistReleaseItemVersion(workingRow)
          rowSaved = true
        }
        if (summary.internalTargetLeadTime) {
          workingRow = await persistInternalTargetLeadTime(workingRow)
          rowSaved = true
        }
        if (summary.fields) {
          workingRow = await persistReleaseItemFields(workingRow)
          rowSaved = true
        }
        if (summary.orderFields) {
          workingRow = await persistReleaseItemOrderFields(workingRow)
          rowSaved = true
        }
        if (rowSaved) savedRows += 1
      } catch (error) {
        console.error(error)
        failures.push({ row: workingRow, message: requestErrorMessage(error, '保存失败。') })
      }
    }

    if (failures.length === 0) {
      ElMessage.success(`已保存 ${savedRows} 个零件的修改。`)
    } else if (savedRows > 0) {
      ElMessage.warning(`已保存 ${savedRows} 个零件，${failures.length} 个零件保存失败。`)
    } else {
      ElMessage.error(`保存失败：${failures[0]?.message || '请稍后重试。'}`)
    }
  })
}

const handleSelectionChange = (rows) => {
  if (syncingReleaseItemSelection) return
  const currentPageIds = new Set(itemList.value.map(row => rowKey(row)))
  currentPageIds.forEach(id => delete selectedItemIdMap[id])
  rows.forEach(row => {
    if (canSelectItem(row)) {
      selectedItemIdMap[rowKey(row)] = row
    }
  })
}

const selectedRowsInItemOrder = () => {
  return selectedRows.value.slice().sort((left, right) => {
    const leftSource = `${left?.sourceType || 'RFQ'}:${Number(left?.sourceOrderId ?? left?.rfqId ?? 0)}`
    const rightSource = `${right?.sourceType || 'RFQ'}:${Number(right?.sourceOrderId ?? right?.rfqId ?? 0)}`
    const sourceCompare = leftSource.localeCompare(rightSource)
    return sourceCompare !== 0 ? sourceCompare : Number(left.id || 0) - Number(right.id || 0)
  })
}

const selectedRowsRfqIds = () => {
  return [...new Set(selectedRows.value
    .filter(row => (row?.sourceType || 'RFQ') === 'RFQ')
    .map(row => row.rfqId || row.sourceOrderId || selectedRfqId.value)
    .filter(Boolean))]
}

const orderSourceNo = (order, source) => {
  return order?.sourceOrderNo || order?.rfqNo || source?.sourceOrderId || sourceKey(source)
}

const nextReleaseBatchKey = (payload) => {
  const fingerprint = JSON.stringify(payload)
  if (releaseBatchState.fingerprint !== fingerprint || !releaseBatchState.key) {
    releaseBatchState.fingerprint = fingerprint
    releaseBatchState.key = globalThis.crypto?.randomUUID?.()
      || `rfq-release-${Date.now()}-${Math.random().toString(16).slice(2)}`
  }
  return releaseBatchState.key
}

const nextAppendBatchKey = (payload) => {
  const fingerprint = JSON.stringify(payload)
  if (appendBatchState.fingerprint !== fingerprint || !appendBatchState.key) {
    appendBatchState.fingerprint = fingerprint
    appendBatchState.key = globalThis.crypto?.randomUUID?.()
      || `rfq-release-append-${Date.now()}-${Math.random().toString(16).slice(2)}`
  }
  return appendBatchState.key
}

const refreshSelectedOrdersAfterGeneration = async (successItemIds = new Set()) => {
  successItemIds.forEach(id => delete selectedItemIdMap[id])
  const refreshedRfqOrders = await Promise.all(selectedRfqIds.value.map(async (rfqId) => {
    try {
      const response = await getReleaseSummary(rfqId)
      return { sourceType: 'RFQ', sourceOrderId: rfqId, orderInfo: response.orderInfo || {} }
    } catch (error) {
      console.error(error)
      return { sourceType: 'RFQ', sourceOrderId: rfqId, orderInfo: null }
    }
  }))
  const refreshedOrders = refreshedRfqOrders
  selectedOrders.value = selectedOrders.value.map(order => {
    const source = toReleaseOrderSource(order)
    const refreshed = refreshedOrders.find(item =>
      item.sourceType === source.sourceType && Number(item.sourceOrderId) === Number(source.sourceOrderId)
    )
    return refreshed?.orderInfo ? { ...order, ...refreshed.orderInfo } : order
  })
  selectedRfqId.value = firstSelectedRfqId()
  await fetchReleaseItemsBatch()
}

const requireSingleSelectedRowsRfqId = (actionText) => {
  if (!selectedOrders.value.length) {
    ElMessage.warning('请先选择订单。')
    return null
  }
  const rfqIds = selectedRowsRfqIds()
  if (rfqIds.length !== 1) {
    ElMessage.warning(`${actionText}暂只支持同一订单下的已选零件，多订单统一处理将在后续阶段接入。`)
    return null
  }
  return rfqIds[0]
}

const handleSelectionExcelChange = async (uploadFile) => {
  const file = uploadFile?.raw
  selectionUploadRef.value?.clearFiles()
  if (selectedOrderSources.value.length === 0) return ElMessage.warning('请先选择订单。')
  if (!file) return

  const fileName = String(file.name || '').toLowerCase()
  if (!fileName.endsWith('.xlsx') && !fileName.endsWith('.xls')) {
    return ElMessage.warning('请上传 xlsx 或 xls 表格。')
  }

  const formData = new FormData()
  formData.append('excelFile', file)
  formData.append('rfqIds', JSON.stringify(selectedRfqIds.value))
  formData.append('sources', JSON.stringify(selectedOrderSources.value))
  return runAction(releaseActionKeys.importSelection, async () => {
    try {
      const response = await parsePartSelectionBatch(formData)
      clearSelectedRows()
      ;(response?.data || []).forEach(row => {
        selectedItemIdMap[rowKey(row)] = row
        acceptedQtyMap[rowKey(row)] = defaultAcceptedQtyFor(row)
      })
      await syncReleaseItemTableSelection()

      const importedCount = Number(response?.importedCount ?? 0)
      const matchedCount = Number(response?.matchedCount ?? 0)
      const selectedCount = Number(response?.selectedCount ?? 0)
      const skippedCount = Number(response?.skippedCount ?? 0)
      if (importedCount === 0) {
        ElMessage.warning('表格中未读取到有效的图号+版本。')
      } else if (selectedCount === 0) {
        ElMessage.warning(`已读取 ${importedCount} 个图号+版本，但没有匹配到当前订单中可选的零件。`)
      } else {
        const skippedText = skippedCount > 0 ? `，${skippedCount} 项因不可选未勾选` : ''
        ElMessage.success(`已读取 ${importedCount} 个图号+版本，匹配 ${matchedCount} 项，已勾选 ${selectedCount} 项${skippedText}。`)
      }
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || '导入表格选定失败。')
    }
  })
}

const openDataReplacementDialog = () => {
  if (dataReplacementDisabled.value) {
    if (selectedOrders.value.length === 0) {
      ElMessage.warning('请先选择订单。')
    } else if (selectedSourcesIncludeNonRfq.value) {
      ElMessage.warning('表格数据替换只支持 RFQ 询价订单。')
    }
    return
  }
  dataReplacementVisible.value = true
}

const handleDataReplacementConfirmed = async (response) => {
  const applied = applyReleaseItemDataReplacementResult({
    itemList: itemList.value,
    selectedItemIdMap,
    acceptedQtyMap,
    draftRowsByKey: dataReplacementDraftRowsByKey.value,
    response,
    rowKey
  })
  itemList.value = applied.itemList
  await syncReleaseItemTableSelection()
}

const openDrawingReplaceDialog = () => {
  if (selectedOrders.value.length === 0) {
    ElMessage.warning('请先选择订单。')
    return
  }
  replacementDrawingFiles.value = []
  replacementVersionDrawingFiles.value = []
  replacementDrawingResult.value = null
  clientSkippedReplacementFiles.value = []
  drawingReplaceVisible.value = true
}

const addClientSkippedReplacementFiles = (files = []) => {
  const skipped = Array.from(files || [])
    .filter(file => !isPdfFile(file))
    .map(file => ({
      fileName: file.name || '-',
      reason: '仅支持 PDF 图纸'
    }))
  if (skipped.length === 0) return

  const seen = new Set(clientSkippedReplacementFiles.value.map(item => item.fileName))
  clientSkippedReplacementFiles.value = [
    ...clientSkippedReplacementFiles.value,
    ...skipped.filter(item => {
      if (seen.has(item.fileName)) return false
      seen.add(item.fileName)
      return true
    })
  ]
  ElMessage.warning(`已跳过 ${skipped.length} 个非 PDF 文件。`)
}

const addReplacementDrawingFiles = (files, sourceLabel, targetFiles = replacementDrawingFiles) => {
  const incomingFiles = Array.from(files || [])
  addClientSkippedReplacementFiles(incomingFiles)
  const beforeCount = targetFiles.value.length
  targetFiles.value = mergePdfFiles(targetFiles.value, incomingFiles)
  const addedCount = targetFiles.value.length - beforeCount
  replacementDrawingResult.value = null
  if (addedCount > 0) {
    ElMessage.success(`成功${sourceLabel} ${addedCount} 份 PDF。`)
  } else if (incomingFiles.length > 0) {
    ElMessage.warning('未找到新的 PDF 图纸。')
  }
}

const handleReplacementDrop = async (event) => {
  if (replacingDrawings.value || submitting.value) return
  addClientSkippedReplacementFiles(event.dataTransfer?.files)
  const pdfs = await collectPdfFilesFromDataTransfer(event.dataTransfer)
  addReplacementDrawingFiles(pdfs, '提取')
}

const handleReplacementVersionDrop = async (event) => {
  if (replacingDrawings.value || submitting.value) return
  addClientSkippedReplacementFiles(event.dataTransfer?.files)
  const pdfs = await collectPdfFilesFromDataTransfer(event.dataTransfer)
  addReplacementDrawingFiles(pdfs, '提取', replacementVersionDrawingFiles)
}

const triggerReplacementFileSelect = () => {
  if (!replacingDrawings.value && !submitting.value) replacementFileInput.value?.click()
}

const triggerReplacementFolderSelect = () => {
  if (!replacingDrawings.value && !submitting.value) replacementFolderInput.value?.click()
}

const triggerReplacementVersionFileSelect = () => {
  if (!replacingDrawings.value && !submitting.value) replacementVersionFileInput.value?.click()
}

const triggerReplacementVersionFolderSelect = () => {
  if (!replacingDrawings.value && !submitting.value) replacementVersionFolderInput.value?.click()
}

const handleReplacementFileSelect = (event) => {
  addReplacementDrawingFiles(event.target.files, '选择')
  event.target.value = ''
}

const handleReplacementFolderSelect = (event) => {
  addReplacementDrawingFiles(event.target.files, '从选中的文件夹中提取')
  event.target.value = ''
}

const handleReplacementVersionFileSelect = (event) => {
  addReplacementDrawingFiles(event.target.files, '选择', replacementVersionDrawingFiles)
  event.target.value = ''
}

const handleReplacementVersionFolderSelect = (event) => {
  addReplacementDrawingFiles(event.target.files, '从选中的文件夹中提取', replacementVersionDrawingFiles)
  event.target.value = ''
}

const removeReplacementDrawingFile = (index) => {
  replacementDrawingFiles.value.splice(index, 1)
}

const removeReplacementVersionDrawingFile = (index) => {
  replacementVersionDrawingFiles.value.splice(index, 1)
}

const submitReplacementDrawings = async () => {
  if (selectedOrderSources.value.length === 0) return ElMessage.warning('请先选择订单。')
  if (replacementDrawingFiles.value.length === 0 && replacementVersionDrawingFiles.value.length === 0) return ElMessage.warning('请先选择需要替换的 PDF 图纸。')

  const formData = new FormData()
  formData.append('rfqIds', JSON.stringify(selectedRfqIds.value))
  formData.append('sources', JSON.stringify(selectedOrderSources.value))
  replacementDrawingFiles.value.forEach(file => formData.append('drawings', file))
  replacementVersionDrawingFiles.value.forEach(file => formData.append('versionDrawings', file))

  return runAction(releaseActionKeys.replaceDrawings, async () => {
    try {
      const response = await replaceReleaseDrawings(formData)
      const skippedFiles = [
        ...(response?.skippedFiles || []),
        ...clientSkippedReplacementFiles.value
      ]
      replacementDrawingResult.value = {
        ...(response || {}),
        skippedFiles,
        skippedCount: skippedFiles.length
      }
      replacementDrawingFiles.value = []
      replacementVersionDrawingFiles.value = []
      clientSkippedReplacementFiles.value = []
      await fetchReleaseItemsBatch()

      if (Number(replacementDrawingResult.value.unmatchedCount || 0) > 0 || skippedFiles.length > 0) {
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

const adjustInternalLeadTime = async (direction) => {
  if (selectedRows.value.length === 0) return ElMessage.warning('请至少选择一个零件。')
  const rfqId = requireSingleSelectedRowsRfqId('内部目标交期调整')
  if (!rfqId) return
  const days = Math.abs(Number(internalShiftDays.value || 0)) * direction
  if (!days) return ElMessage.warning('调整天数为 0，不需要调整。')

  return runAction(releaseActionKeys.shiftInternalLeadTime, async () => {
    try {
      const response = await shiftInternalTargetLeadTime({
        rfqId,
        itemIds: selectedRows.value.map(row => row.id),
        days
      })
      clearSelectedRows()
      await fetchReleaseItemsBatch()
      const adjusted = response.adjustedCount || 0
      const skipped = response.skippedCount || 0
      if (skipped > 0) {
        ElMessage.warning(`已调整 ${adjusted} 项，${skipped} 项无法自动调整。`)
      } else {
        ElMessage.success(`已调整 ${adjusted} 项。`)
      }
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || '内部目标交期调整失败。')
    }
  })
}

const setInternalTargetLeadTime = async () => {
  if (selectedRows.value.length === 0) return ElMessage.warning('请至少选择一个零件。')
  const rfqId = requireSingleSelectedRowsRfqId('指定内部目标交期')
  if (!rfqId) return
  if (!manualInternalTargetDate.value) return ElMessage.warning('请先选择内部目标交期。')

  return runAction(releaseActionKeys.setInternalTargetLeadTime, async () => {
    try {
      const response = await setInternalTargetLeadTimeRequest({
        rfqId,
        itemIds: selectedRows.value.map(row => row.id),
        internalTargetLeadTime: manualInternalTargetDate.value
      })
      clearSelectedRows()
      await fetchReleaseItemsBatch()
      const adjusted = response.adjustedCount || 0
      const skipped = response.skippedCount || 0
      if (skipped > 0) {
        ElMessage.warning(`已指定 ${adjusted} 项，${skipped} 项未处理。`)
      } else {
        ElMessage.success(`已指定 ${adjusted} 项内部目标交期。`)
      }
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || '指定内部目标交期失败。')
    }
  })
}

const saveInternalTargetLeadTime = async (row) => {
  const rowRfqId = row?.rfqId || selectedRfqId.value
  if (!rowRfqId || !row?.id) return
  return runAction(releaseActionKeys.internalLeadTime(row.id), async () => {
    try {
      await persistInternalTargetLeadTime(row)
      ElMessage.success('内部目标交期已保存。')
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || '内部目标交期保存失败。')
    }
  })
}

const cleanupPdfSrc = () => {
  revokeObjectUrl(currentPdfSrc.value)
  currentPdfSrc.value = ''
}

const openPdfPreview = async (url, partNo) => {
  if (!url) {
    return ElMessage.warning('当前零件没有绑定图纸。')
  }
  pdfTitle.value = partNo || '未命名图纸'
  pdfLoaded.value = false
  cleanupPdfSrc()
  pdfVisible.value = true
  try {
    currentPdfSrc.value = await createAuthenticatedObjectUrl(url)
  } catch (error) {
    pdfVisible.value = false
    ElMessage.error(await readFileErrorMessage(error) || '图纸预览失败。')
  }
}

const openAppendSalesOrderPicker = () => {
  if (selectedRows.value.length === 0) return ElMessage.warning('请至少选择一个通过零件。')
  if (selectedRowsWithUnsavedOrderFields.value.length > 0) {
    return ElMessage.warning('请先保存已选零件的行级订单字段草稿，再追加销售提单。')
  }
  if (!selectedOrders.value.length) return ElMessage.warning('请先选择订单。')
  if (selectedSourcesIncludeNonRfq.value) return ElMessage.warning('追加销售提单只支持 RFQ 询价订单。')

  const orderedSelectedRows = selectedRowsInItemOrder()
  const unquoted = orderedSelectedRows.filter(row => row.taxIncludedUnitPrice == null)
  if (unquoted.length > 0) {
    return ElMessage.warning(`仍有 ${unquoted.length} 个零件未报价，暂不追加销售提单。`)
  }
  if (selectedRowsRfqIds().length === 0) {
    return ElMessage.warning('已选零件缺少来源询价单信息，无法追加销售提单。')
  }

  appendSalesOrderPickerVisible.value = true
}

const appendToSalesOrder = async (targetOrder) => {
  const targetSalesOrderId = Number(targetOrder?.salesOrderId ?? targetOrder?.sourceOrderId ?? targetOrder?.id)
  if (!Number.isInteger(targetSalesOrderId) || targetSalesOrderId <= 0) {
    ElMessage.warning('请选择有效的目标销售订单。')
    return
  }

  const orderedSelectedRows = selectedRowsInItemOrder()
  const batchRfqIds = selectedRowsRfqIds()
  if (orderedSelectedRows.length === 0 || batchRfqIds.length === 0) {
    ElMessage.warning('已选零件缺少来源询价单信息，无法追加销售提单。')
    return
  }

  const payload = {
    targetSalesOrderId,
    rfqIds: batchRfqIds,
    items: orderedSelectedRows.map(row => ({
      sourceType: 'RFQ',
      sourceOrderId: Number(row?.sourceOrderId ?? row?.rfqId ?? selectedRfqId.value),
      sourceItemId: Number(row?.sourceItemId ?? row?.id),
      itemId: row.id,
      acceptedQty: acceptedQtyMap[rowKey(row)] || defaultAcceptedQtyFor(row)
    }))
  }
  const appendBatchKey = nextAppendBatchKey(payload)
  payload.appendBatchKey = appendBatchKey

  return runAction(releaseActionKeys.appendSalesOrder(), async () => {
    await ElMessageBox.confirm(
      `确认将 ${batchRfqIds.length} 个询价单中已选的 ${orderedSelectedRows.length} 个零件追加到销售订单 ${targetOrder?.salesOrderNo || targetSalesOrderId}？本次追加零件值优先，空值继承目标销售订单头，不使用左侧订单默认信息。`,
      '追加销售提单确认',
      { type: 'warning', confirmButtonText: '确认追加', cancelButtonText: '取消' }
    )

    try {
      const response = await appendReleaseToSalesOrder(payload)
      if (!response?.success) {
        throw new Error(response?.message || '追加销售提单失败。')
      }
      releaseResult.value = { ...response, resultMode: 'APPEND' }
      await refreshSelectedOrdersAfterGeneration(new Set(orderedSelectedRows.map(rowKey)))
      appendBatchState.fingerprint = ''
      appendBatchState.key = ''
      appendSalesOrderPickerVisible.value = false
      ElMessage.success(response.message || `已追加 ${response.ticketCount || orderedSelectedRows.length} 项提单。`)
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || error.message || '追加销售提单请求失败。')
    }
  })
}

const submitTicket = async () => {
  if (selectedRows.value.length === 0) return ElMessage.warning('请至少选择一个通过零件。')
  if (selectedRowsWithUnsavedOrderFields.value.length > 0) {
    return ElMessage.warning('请先保存已选零件的订单信息草稿，再生成正式提单。')
  }
  if (!selectedOrders.value.length) return ElMessage.warning('请先选择订单。')
  if (!releaseForm.orderType) return ElMessage.warning('请选择订单类型后再生成正式提单。')
  const documentTypeCode = releaseForm.documentTypeCode.trim()
  if (!documentTypeCode) return ElMessage.warning('请填写单据类型编码后再生成正式提单。')
  if (!releaseForm.salesOrderDate) return ElMessage.warning('请选择下单日期后再生成正式提单。')
  const internalProjectNo = releaseForm.internalProjectNo.trim()
  if (!internalProjectNo) return ElMessage.warning('请填写内部项目号后再生成正式提单。')
  const customerOrderNo = releaseForm.customerOrderNo.trim()
  if (!customerOrderNo) return ElMessage.warning('请填写客户订单号后再生成正式提单。')
  const customerProjectNo = releaseForm.customerProjectNo.trim()
  const orderedSelectedRows = selectedRowsInItemOrder()
  const batchRfqIds = selectedRowsRfqIds()

  const unquoted = orderedSelectedRows.filter(row => row.taxIncludedUnitPrice == null)
  if (unquoted.length > 0) {
    return ElMessage.warning(`仍有 ${unquoted.length} 个零件未报价，暂不生成提单。`)
  }

  if (batchRfqIds.length === 0) {
    return ElMessage.warning('已选零件缺少订单信息，无法生成正式提单。')
  }

  return runAction(releaseActionKeys.generateTicket(), async () => {
    await ElMessageBox.confirm(
      `确认将同一客户的 ${batchRfqIds.length} 个询价单合并为一个提单批次，共 ${orderedSelectedRows.length} 个零件？`,
      '生成提单确认',
      { type: 'warning', confirmButtonText: '确认生成', cancelButtonText: '取消' }
    )

    try {
      const payload = {
        rfqIds: batchRfqIds,
        documentTypeCode,
        salesOrderDate: releaseForm.salesOrderDate,
        customerOrderTitle: releaseForm.customerOrderTitle.trim(),
        orderType: releaseForm.orderType,
        internalProjectNo,
        customerOrderNo,
        customerProjectNo,
        arrangeType: releaseForm.arrangeType,
        items: orderedSelectedRows.map(row => ({
          sourceType: 'RFQ',
          sourceOrderId: Number(row?.sourceOrderId ?? row?.rfqId ?? selectedRfqId.value),
          sourceItemId: Number(row?.sourceItemId ?? row?.id),
          itemId: row.id,
          acceptedQty: acceptedQtyMap[rowKey(row)] || defaultAcceptedQtyFor(row)
        }))
      }
      const releaseBatchKey = nextReleaseBatchKey(payload)
      payload.releaseBatchKey = releaseBatchKey
      const response = await generateTicket(payload)
      if (!response?.success) {
        throw new Error(response?.message || '提单生成失败。')
      }
      releaseResult.value = response
      await refreshSelectedOrdersAfterGeneration(new Set(orderedSelectedRows.map(rowKey)))
      releaseBatchState.fingerprint = ''
      releaseBatchState.key = ''
      const noticeExportResult = await autoExportProductionTaskNotice(response)
      const releaseSuccessMessage = response.message || `已生成 ${response.ticketCount || orderedSelectedRows.length} 项提单。`
      ElMessage.success(noticeExportResult?.success
        ? `${releaseSuccessMessage.replace(/[。.]$/, '')}，生产任务通知单已开始导出。`
        : releaseSuccessMessage)
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || error.message || '提单生成请求失败。')
    }
  })
}

const resolveDeliveryLeadTime = (row) => {
  return row.effectiveDeliveryLeadTime
    || row.internalTargetLeadTime
    || row.quoteLeadTime
    || row.leadTime
    || '-'
}

const statusTagType = (status) => {
  if (status === '已生成提单') return 'success'
  if (status === '已报价') return 'primary'
  if (status === '部分已提单') return 'warning'
  if (status === '已核价') return 'warning'
  if (status === '未核价') return 'info'
  return 'info'
}

const sourceTypeLabel = () => {
  return 'RFQ'
}

watch(pdfVisible, (visible) => {
  if (!visible) {
    pdfLoaded.value = false
    cleanupPdfSrc()
  }
})

onBeforeUnmount(() => {
  cleanupPdfSrc()
})

</script>

<style scoped>
.release-page {
  padding: 0;
}

.release-page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.release-derived-order-result {
  margin: 0 0 16px;
  padding: 16px;
}

.release-result-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.release-result-kicker {
  color: var(--erp-success);
  font-size: 12px;
  font-weight: 700;
}

.release-result-header h3 {
  margin: 4px 0 0;
  color: var(--erp-text);
  font-size: 16px;
}

.release-result-header p {
  margin: 5px 0 0;
  color: var(--erp-text-secondary);
  font-size: 13px;
}

.release-result-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.release-result-actions :deep(.el-button) {
  margin-left: 0;
}

.release-result-summary-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 8px;
  margin-top: 16px;
}

.release-result-summary-grid > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid var(--erp-border-soft);
  border-radius: 6px;
  background: var(--erp-surface-soft);
}

.release-result-summary-grid span {
  color: var(--erp-text-secondary);
  font-size: 12px;
}

.release-result-summary-grid strong {
  overflow: hidden;
  color: var(--erp-text);
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.release-result-summary-grid .release-result-success {
  color: var(--erp-success);
}

.release-result-summary-grid .release-result-warning {
  color: var(--erp-warning);
}

.release-page-header h2 {
  margin: 0;
  color: var(--erp-text);
  font-size: 20px;
  line-height: 1.3;
}

.release-page-header p {
  margin: 6px 0 0;
  color: var(--erp-text-secondary);
  font-size: 13px;
}

.release-panel,
.release-table-panel {
  padding: 14px;
}

.panel-title {
  margin-bottom: 12px;
  padding-left: 10px;
  border-left: 3px solid var(--erp-primary);
  color: var(--erp-text);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.2;
}

.panel-title.compact {
  margin-bottom: 10px;
}

.selected-orders-mini-table {
  width: 100%;
  border-radius: 6px;
}

.selected-orders-mini-table :deep(.el-table__cell) {
  padding: 5px 0;
}

.selected-orders-mini-table :deep(.el-table__empty-text) {
  color: var(--erp-text-secondary);
  font-size: 13px;
}

.panel-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.panel-title-row .panel-title {
  margin-bottom: 0;
}

.ticket-form {
  margin-top: 0;
}

.release-order-default-form {
  --release-default-control-width: 236px;
}

.release-order-default-form :deep(.el-form-item__label) {
  white-space: nowrap;
}

.release-order-default-form :deep(.el-form-item__content) {
  flex: 0 1 var(--release-default-control-width);
  max-width: var(--release-default-control-width);
  min-width: 0;
}

.release-order-default-form :deep(.el-input),
.release-order-default-form :deep(.el-select),
.release-order-default-form :deep(.el-date-editor) {
  width: 100%;
}

.release-order-default-panel {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--erp-border-soft);
}

.release-order-default-hint {
  margin: -4px 0 14px;
  color: var(--erp-text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.import-action-row {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 10px;
  width: 100%;
}

.release-table-actions {
  justify-content: flex-end;
  width: auto;
  margin-top: 0;
}

.release-table-actions :deep(.el-upload) {
  display: flex;
}

.release-table-actions :deep(.el-button) {
  margin-left: 0;
}

.source-order-cell {
  display: inline-flex;
  max-width: 100%;
  align-items: center;
  gap: 6px;
  overflow: hidden;
  vertical-align: middle;
}

.source-order-cell :deep(.el-tag) {
  flex: 0 0 auto;
}

.source-order-no {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.full-width {
  width: 100%;
}

.qty-input {
  width: 105px;
}

.release-status-tag {
  max-width: 128px;
}

.release-status-tag :deep(.el-tag__content) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.internal-lead-panel {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--erp-border-soft);
}

.internal-project-panel {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--erp-border-soft);
}

.release-meta-input {
  margin-top: 10px;
}

.lead-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-top: 10px;
}

.lead-actions :deep(.el-button) {
  margin-left: 0;
}

.manual-date-picker,
.set-date-btn {
  margin-top: 10px;
}

.market-manual-date-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 76px;
  gap: 8px;
  align-items: center;
  margin-top: 10px;
}

.market-manual-date-input {
  width: 100%;
  max-width: 100%;
  min-width: 0;
}

.market-manual-date-row :deep(.market-manual-date-input.el-date-editor.el-input) {
  width: 100%;
  max-width: 100%;
  min-width: 0;
}

.market-manual-date-row .manual-date-picker {
  margin-top: 0;
}

.market-manual-date-button {
  width: 100%;
  margin-left: 0;
  white-space: nowrap;
}

.set-date-btn {
  width: 100%;
  margin-left: 0;
}

.row-date-editor {
  display: flex;
  align-items: center;
  gap: 8px;
}

.row-date-picker {
  width: 128px;
}

.row-version-editor {
  display: flex;
  align-items: center;
  gap: 6px;
}

.row-version-input {
  width: 72px;
}

.row-field-editor {
  display: flex;
  align-items: center;
  gap: 6px;
}

.row-field-input {
  min-width: 130px;
}

.row-order-date-picker,
.row-order-field-input,
.row-order-type-select {
  flex: 1 1 auto;
  width: auto;
  min-width: 0;
}

.row-order-field-editor {
  min-width: 0;
}

.row-order-field-editor :deep(.el-button) {
  flex: 0 0 auto;
  margin-left: 0;
}

.selection-count {
  margin-top: 10px;
  color: var(--erp-text-secondary);
  font-size: 13px;
}

.main-actions {
  display: grid;
  gap: 8px;
  padding-top: 14px;
  border-top: 1px solid var(--erp-border-soft);
}

.main-actions :deep(.el-button) {
  width: 100%;
  margin-left: 0;
}

.ticket-panel {
  margin-top: 14px;
}

.drawing-replace-dialog {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.pdf-frame-shell {
  width: 100%;
  height: 70vh;
}

.drawing-replace-upload {
  padding: 0;
  border: none;
  background: transparent;
}

.drawing-replace-dual-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: stretch;
  gap: 14px;
}

.drawing-replace-entry {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.drawing-replace-entry-title {
  min-height: 24px;
  display: flex;
  align-items: center;
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

.dropzone-icon {
  margin-bottom: 12px;
  color: #8c98aa;
  font-size: 42px;
}

.dropzone-text {
  color: var(--erp-text-secondary);
  font-size: 15px;
}

.drawing-replace-dropzone {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  min-height: 280px;
  box-sizing: border-box;
}

.drawing-replace-dropzone :deep(.el-upload__tip) {
  min-height: 48px;
  margin-top: 8px;
  color: var(--erp-text-secondary);
}

.folder-select-row {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 38px;
  margin-top: 12px;
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
}

.drawing-replace-upload-actions {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 10px;
  width: 100%;
  margin-top: auto;
  padding-top: 12px;
}

.drawing-replace-upload-actions :deep(.el-button) {
  margin-left: 0;
}

.import-upload-button-stack {
  flex-direction: column;
}

.folder-select-button {
  width: 100%;
  min-height: 32px;
  min-width: 150px;
  padding-inline: 14px;
}

.hidden-input {
  display: none;
}

.replacement-file-stats {
  padding: 12px 14px;
  border: 1px solid #cfe8d2;
  border-radius: 8px;
  background: #f4fbf5;
  color: var(--erp-success);
}

.replacement-file-stats h4 {
  margin: 0 0 8px;
  font-size: 14px;
}

.replacement-pdf-list,
.result-list {
  list-style: none;
  padding: 0;
  margin: 0;
  max-height: 150px;
  overflow-y: auto;
}

.replacement-pdf-list li,
.result-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 0;
  border-bottom: 1px solid #dcefe0;
  color: #256b2b;
}

.replacement-pdf-list li:last-child,
.result-list li:last-child {
  border-bottom: none;
}

.replacement-result {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.replacement-summary-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
}

.replacement-summary-grid > div {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 56px;
  border: 1px solid var(--erp-border-soft);
  border-radius: 6px;
  background: var(--erp-surface-soft);
}

.replacement-summary-grid strong {
  color: var(--erp-text);
  font-size: 18px;
}

.replacement-summary-grid span,
.result-sub {
  color: var(--erp-text-secondary);
  font-size: 12px;
}

.result-main {
  color: var(--erp-text);
  font-weight: 700;
}

.result-reason {
  color: var(--erp-warning);
}

@media (max-width: 1180px) {
  .release-page :deep(.el-row) {
    display: block;
  }

  .release-page :deep(.el-col) {
    max-width: 100%;
    width: 100%;
    margin-bottom: 14px;
  }
}

@media (max-width: 760px) {
  .release-page-header,
  .panel-title-row {
    grid-template-columns: 1fr;
    flex-direction: column;
    align-items: stretch;
  }

  .release-table-actions {
    justify-content: stretch;
    width: 100%;
  }

  .release-table-actions :deep(.el-button),
  .release-table-actions :deep(.el-upload) {
    width: 100%;
  }

  .replacement-summary-grid {
    grid-template-columns: 1fr;
  }

  .release-result-header {
    display: grid;
    grid-template-columns: 1fr;
  }

  .release-result-actions {
    justify-content: flex-start;
  }

  .release-result-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .drawing-replace-dual-grid {
    grid-template-columns: 1fr;
  }
}
</style>
