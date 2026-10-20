<template>
  <section class="erp-page sales-order-u9-sync-page">
    <header class="erp-page-header sales-order-u9-sync-header">
      <div>
        <h1 class="erp-page-title">U9同步</h1>
        <p class="erp-page-description">选择已提单销售订单，保存 Seller / SaleDepartment 后核对并同步到 U9。</p>
      </div>
      <div class="erp-page-actions sales-order-u9-sync-header-actions">
        <el-button
          v-permission="U9_SYNC_ORDER_SELECT_PERMISSION"
          :icon="DocumentChecked"
          type="primary"
          @click="openOrderPicker"
        >
          选择销售订单
        </el-button>
        <el-button :loading="previewLoading" @click="refreshCurrentState">刷新</el-button>
        <el-tooltip v-if="isPendingConfirmation" :disabled="canReconcileSelected" :content="reconcileDisabledReason" placement="top">
          <span class="sales-order-u9-sync-action-wrap">
            <el-button
              v-permission="U9_SYNC_SUBMIT_PERMISSION"
              type="warning"
              :loading="isPending(u9SyncActionKeys.reconcile)"
              :disabled="!canReconcileSelected"
              @click="handleReconcileOrder"
            >
              核对U9结果
            </el-button>
          </span>
        </el-tooltip>
        <el-tooltip v-else :disabled="!syncDisabledReason" :content="syncDisabledReason" placement="top">
          <span class="sales-order-u9-sync-action-wrap">
            <el-button
              v-permission="U9_SYNC_SUBMIT_PERMISSION"
              type="success"
              :loading="isPending(u9SyncActionKeys.sync)"
              :disabled="!canSyncSelected"
              @click="syncSelectedOrder"
            >
              同步U9
            </el-button>
          </span>
        </el-tooltip>
      </div>
    </header>

    <section class="erp-surface workflow-workspace sales-order-u9-sync-workspace">
      <aside class="erp-surface sales-order-u9-sync-side-panel">
        <div class="panel-title">销售订单</div>
        <div class="selected-order-text" :title="selectedOrderLabel">
          {{ selectedOrderLabel }}
        </div>

        <div class="u9-assignment-panel">
          <div class="panel-title-row compact-row">
            <div>
              <div class="panel-title compact">U9选择</div>
              <p class="panel-subtitle">Seller 和 SaleDepartment 可为空，保存后用于本订单同步。</p>
            </div>
            <div class="panel-title-actions">
              <el-button
                v-permission="U9_SYNC_SUBMIT_PERMISSION"
                size="small"
                :loading="optionsRefreshing"
                :disabled="optionsLoading || optionsRefreshing"
                @click="handleRefreshU9Options"
              >
                刷新U9选项
              </el-button>
            </div>
            <el-tag v-if="assignmentReadOnly" size="small" type="info">只读</el-tag>
          </div>
          <el-alert
            v-if="u9Options.stale || u9Options.warning"
            class="sales-order-u9-sync-alert"
            :title="u9Options.warning || 'U9主数据选项来自过期缓存。'"
            type="warning"
            :closable="false"
            show-icon
          />
          <div class="assignment-form-grid" v-loading="optionsLoading || optionsRefreshing">
            <label class="assignment-field">
              <span>Seller</span>
              <el-select
                v-model="assignmentDraft.sellerCode"
                aria-label="Seller"
                filterable
                clearable
                :disabled="assignmentReadOnly || optionsLoading || optionsRefreshing || !selectedOrderId"
                placeholder="选择U9销售员"
                @change="handleSellerChange"
              >
                <el-option
                  v-for="seller in u9SellerOptions"
                  :key="seller.code"
                  :label="sellerOptionLabel(seller)"
                  :value="seller.code"
                />
              </el-select>
            </label>
            <label class="assignment-field">
              <span>SaleDepartment</span>
              <el-select
                v-model="assignmentDraft.saleDepartmentCode"
                aria-label="SaleDepartment"
                filterable
                clearable
                :disabled="assignmentReadOnly || optionsLoading || optionsRefreshing || !selectedOrderId"
                placeholder="选择U9销售部门"
                @change="handleDepartmentChange"
              >
                <el-option
                  v-for="department in u9DepartmentOptions"
                  :key="department.code"
                  :label="departmentOptionLabel(department)"
                  :value="department.code"
                />
              </el-select>
            </label>
            <div class="assignment-selected-line">
              <span>{{ assignmentDisplayText }}</span>
            </div>
            <el-button
              v-permission="U9_SYNC_SUBMIT_PERMISSION"
              type="primary"
              plain
              :loading="assignmentSaving"
              :disabled="assignmentSaveDisabled"
              @click="handleSaveAssignment"
            >
              保存U9选择
            </el-button>
          </div>
        </div>

        <div class="u9-order-date-panel">
          <div class="panel-title-row compact-row">
            <div>
              <div class="panel-title compact">下单日期</div>
              <p class="panel-subtitle">保存后作为本订单同步 U9 的 BusinessDate。</p>
            </div>
            <el-tag v-if="orderDateReadOnly" size="small" type="info">只读</el-tag>
          </div>
          <div class="order-date-form-grid">
            <label class="assignment-field">
              <span>订单头下单日期</span>
              <el-date-picker
                v-model="orderDateDraft"
                type="date"
                value-format="YYYY-MM-DD"
                aria-label="订单头下单日期"
                :disabled="orderDateReadOnly || orderDateSaving || previewLoading"
                placeholder="选择下单日期"
              />
            </label>
            <div class="order-date-current-line">
              当前：<strong>{{ formatDate(savedOrderDate) }}</strong>
            </div>
            <el-tooltip :disabled="!orderDateSaveDisabledReason" :content="orderDateSaveDisabledReason" placement="top">
              <span class="sales-order-u9-sync-action-wrap">
                <el-button
                  v-permission="U9_SYNC_SUBMIT_PERMISSION"
                  type="primary"
                  plain
                  :loading="orderDateSaving"
                  :disabled="orderDateSaveDisabled"
                  @click="handleSaveOrderDate"
                >
                  保存下单日期
                </el-button>
              </span>
            </el-tooltip>
          </div>
        </div>

        <div class="u9-price-mode-panel">
          <div class="panel-title-row compact-row">
            <div>
              <div class="panel-title compact">U9价格口径</div>
              <p class="panel-subtitle">只切换同步到 U9 的价格字段，不发送税额或总额。</p>
            </div>
          </div>
          <el-radio-group
            v-model="u9PriceMode"
            class="u9-price-mode-group"
            :disabled="previewLoading || isPending(u9SyncActionKeys.sync) || isPendingConfirmation"
            @change="handleU9PriceModeChange"
          >
            <el-radio-button
              v-for="option in U9_PRICE_MODE_OPTIONS"
              :key="option.value"
              :label="option.value"
            >
              {{ option.label }}
            </el-radio-button>
          </el-radio-group>
          <p class="u9-price-mode-tip">{{ selectedU9PriceModeTip }}</p>
        </div>

        <div class="sync-condition-panel">
          <div class="panel-title compact">同步条件</div>
          <div class="condition-row">
            <span>订单状态</span>
            <el-tag :type="orderStatusTagType(selectedOrder?.status)" size="small">
              {{ selectedOrder?.status || '未选择' }}
            </el-tag>
          </div>
          <div class="condition-row">
            <span>U9状态</span>
            <el-tag :type="u9StatusTagType(currentU9Status)" size="small">{{ currentU9Status }}</el-tag>
          </div>
          <div class="condition-row">
            <span>稳定DocNo</span>
            <strong>{{ orderField('documentNo') }}</strong>
          </div>
          <div class="condition-row">
            <span>零件数</span>
            <strong>{{ readySummary.itemCount }}</strong>
          </div>
          <div class="condition-row">
            <span>已确认物料号</span>
            <strong>{{ readySummary.materialConfirmedItemCount }} / {{ readySummary.itemCount }}</strong>
          </div>
          <div class="condition-row">
            <span>缺失/未确认</span>
            <strong class="warning-text">{{ readySummary.missingMaterialItemCount }}</strong>
          </div>
          <div class="condition-row">
            <span>同步就绪</span>
            <el-tag :type="readySummary.syncReady ? 'success' : 'warning'" size="small">
              {{ readySummary.syncReady ? '可同步' : '不可同步' }}
            </el-tag>
          </div>
          <el-alert
            v-if="syncDisabledReason || reconcileDisabledReason"
            class="sales-order-u9-sync-alert"
            :title="isPendingConfirmation ? reconcileDisabledReason : syncDisabledReason"
            type="warning"
            :closable="false"
            show-icon
          />
        </div>

        <div class="selected-order-meta">
          <div class="panel-title compact">订单信息</div>
          <dl>
            <div>
              <dt>客户</dt>
              <dd>{{ customerDisplay }}</dd>
            </div>
            <div>
              <dt>订单来源</dt>
              <dd>{{ orderSourceLabel }}</dd>
            </div>
            <div>
              <dt>单据类型编码</dt>
              <dd>{{ orderField('documentTypeCode') }}</dd>
            </div>
            <div>
              <dt>客户订单号</dt>
              <dd>{{ orderField('customerOrderNo') }}</dd>
            </div>
            <div>
              <dt>客户项目号</dt>
              <dd>{{ orderField('customerProjectNo') }}</dd>
            </div>
            <div>
              <dt>订单类型</dt>
              <dd>{{ orderField('orderType') }}</dd>
            </div>
            <div>
              <dt>内部项目号</dt>
              <dd>{{ orderField('internalProjectNo') }}</dd>
            </div>
          </dl>
        </div>

        <div class="sync-result-panel">
          <div class="panel-title compact">同步结果</div>
          <div class="sync-result-code-row">
            <span>当前U9编码</span>
            <strong>{{ currentU9OrderCode }}</strong>
          </div>
          <el-empty v-if="!syncResult && !reconcileResult" description="暂无同步结果" :image-size="64" />
          <template v-if="syncResult">
            <div class="sync-result-summary">
              <el-tag :type="syncResult.success ? 'success' : 'danger'">{{ syncResult.status || '-' }}</el-tag>
              <span>成功 {{ numberOrZero(syncResult.successOrderCount) }}</span>
              <span>失败 {{ numberOrZero(syncResult.failedOrderCount) }}</span>
            </div>
            <el-alert
              v-if="syncResult.message"
              class="sales-order-u9-sync-result-alert"
              :title="syncResult.message"
              :type="syncResult.success ? 'success' : 'warning'"
              :closable="false"
              show-icon
            />
            <el-table :data="syncRows" border stripe max-height="180" empty-text="暂无返回明细">
              <el-table-column prop="salesOrderNo" label="销售订单号" min-width="128" show-overflow-tooltip />
              <el-table-column prop="u9OrderCode" label="U9编码" min-width="110" show-overflow-tooltip>
                <template #default="{ row }">{{ row.u9OrderCode || '-' }}</template>
              </el-table-column>
              <el-table-column prop="errorMessage" label="失败明细" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">{{ row.errorMessage || '-' }}</template>
              </el-table-column>
            </el-table>
          </template>
          <el-alert
            v-if="reconcileResult"
            class="sales-order-u9-sync-result-alert"
            :title="reconcileResult.message || '核对已完成'"
            :type="reconcileResult.u9SyncStatus === '已同步' ? 'success' : 'warning'"
            :closable="false"
            show-icon
          />
        </div>
      </aside>

      <main class="erp-table-surface sales-order-u9-sync-main-panel">
        <div class="erp-table-header panel-title-row">
          <div>
            <div class="panel-title">销售订单零件</div>
            <p class="panel-subtitle">{{ itemPanelTitle }}</p>
          </div>
          <div class="sales-order-u9-sync-table-actions">
            <div class="sales-order-u9-sync-result-tags">
              <el-tag :type="previewData?.syncReady ? 'success' : 'warning'">
                {{ previewData ? (previewData.syncReady ? '可同步' : '不可同步') : '待选择' }}
              </el-tag>
              <el-tag v-if="dirtyItemCount > 0" class="item-edit-dirty-tag" type="warning">
                已修改 {{ dirtyItemCount }} 行
              </el-tag>
              <el-tag type="info">U9同步使用已确认物料号</el-tag>
              <el-tag type="info">{{ selectedU9PriceTagText }}</el-tag>
            </div>
            <el-tooltip :disabled="!itemEditSaveDisabledReason" :content="itemEditSaveDisabledReason" placement="top">
              <span class="sales-order-u9-sync-action-wrap">
                <el-button
                  v-permission="U9_SYNC_SUBMIT_PERMISSION"
                  type="primary"
                  plain
                  :loading="itemEditSaving"
                  :disabled="itemEditSaveDisabled"
                  @click="handleSaveItemEdits"
                >
                  保存修改
                </el-button>
              </span>
            </el-tooltip>
            <el-tooltip :disabled="!itemDataReplacementDisabledReason" :content="itemDataReplacementDisabledReason" placement="top">
              <span class="sales-order-u9-sync-action-wrap">
                <el-button
                  v-permission="U9_SYNC_SUBMIT_PERMISSION"
                  plain
                  :loading="itemDataReplacementBusy"
                  :disabled="itemDataReplacementDisabled"
                  @click="openItemDataReplacementDialog"
                >
                  表格数据替换
                </el-button>
              </span>
            </el-tooltip>
          </div>
        </div>
        <div
          v-if="previewMessages.length"
          class="preview-message-summary"
          :class="{ blocked: previewMessageSummary.errorCount > 0 }"
        >
          <div class="preview-message-summary-main">
            <el-tag :type="previewMessageSummary.errorCount > 0 ? 'danger' : 'warning'" size="small">
              {{ previewMessageSummary.title }}
            </el-tag>
            <span class="preview-message-summary-text">{{ previewMessageSummary.subtitle }}</span>
          </div>
          <el-button
            class="preview-message-toggle"
            size="small"
            text
            @click="previewMessageDetailVisible = !previewMessageDetailVisible"
          >
            {{ previewMessageDetailVisible ? '收起详情' : '查看详情' }}
          </el-button>
        </div>
        <el-collapse-transition>
          <div v-show="previewMessageDetailVisible && previewMessages.length" class="preview-message-detail">
            <div
              v-for="message in previewMessages"
              :key="`${message.type}-${message.text}`"
              class="preview-message-detail-row"
              :class="message.type"
            >
              <span>{{ previewMessageTypeText(message.type) }}</span>
              <strong>{{ message.text }}</strong>
            </div>
          </div>
        </el-collapse-transition>
        <el-table
          :data="previewLines"
          border
          stripe
          height="640"
          v-loading="previewLoading"
          empty-text="请先选择销售订单"
        >
          <el-table-column type="expand" width="52" label="U9私有字段">
            <template #default="{ row }">
              <div class="private-field-grid">
                <div v-for="field in privateFieldDefinitions" :key="field.key" class="private-field-card">
                  <span>{{ field.u9Field }}</span>
                  <strong>{{ u9PrivateFieldValue(row, field) }}</strong>
                </div>
              </div>
              <div v-if="lineMessages(row).length" class="line-message-stack">
                <el-tag
                  v-for="message in lineMessages(row)"
                  :key="message"
                  size="small"
                  :type="message.includes('警告') ? 'warning' : 'danger'"
                >
                  {{ message }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="partNo" label="图号" min-width="130" fixed show-overflow-tooltip />
          <el-table-column prop="lineNo" label="行号" width="72" align="center" />
          <el-table-column prop="version" label="版本" width="88" align="center" show-overflow-tooltip />
          <el-table-column prop="materialNo" label="物料号" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.materialNo || '-' }}</template>
          </el-table-column>
          <el-table-column prop="materialNoStatus" label="物料号状态" width="116" align="center">
            <template #default="{ row }">
              <el-tag :type="materialStatusTagType(row.materialNoStatus)" size="small">
                {{ row.materialNoStatus || '未确认' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="qty" label="数量(PCS)" width="118" align="right">
            <template #default="{ row }">
              <el-input-number
                v-if="itemDrafts[row.salesOrderItemId]"
                v-model="itemDrafts[row.salesOrderItemId].qty"
                class="item-edit-input item-edit-number"
                :controls="false"
                :min="1"
                :precision="0"
                :disabled="itemEditReadOnly"
                @change="handleItemDraftChange(row)"
              />
              <span v-else>{{ numberOrZero(row.qty) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="就绪" width="88" align="center">
            <template #default="{ row }">
              <el-tag :type="row.ready ? 'success' : 'danger'" size="small">
                {{ row.ready ? '已就绪' : '阻断' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="错误/警告" min-width="150">
            <template #default="{ row }">
              <el-tooltip
                v-if="lineMessages(row).length"
                :content="lineMessages(row).join('；')"
                placement="top"
                effect="light"
              >
                <el-tag :type="row.blockingErrors?.length ? 'danger' : 'warning'" size="small" class="line-issue-tag">
                  {{ lineIssueSummary(row) }}
                </el-tag>
              </el-tooltip>
              <span v-else class="muted-placeholder">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="untaxedUnitPrice" label="未税单价" width="122" align="right">
            <template #default="{ row }">
              <el-input
                v-if="itemDrafts[row.salesOrderItemId]"
                v-model="itemDrafts[row.salesOrderItemId].untaxedUnitPrice"
                class="item-edit-input item-edit-money"
                inputmode="decimal"
                :disabled="itemEditReadOnly"
                placeholder="未税单价缺失"
                @input="handleItemDraftChange(row)"
              />
              <el-tag v-else-if="row.untaxedUnitPrice == null" type="danger" size="small">未税单价缺失</el-tag>
              <span v-else>{{ formatMoney(row.untaxedUnitPrice) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="taxIncludedUnitPrice" label="含税单价" width="122" align="right">
            <template #default="{ row }">
              <el-input
                v-if="itemDrafts[row.salesOrderItemId]"
                v-model="itemDrafts[row.salesOrderItemId].taxIncludedUnitPrice"
                class="item-edit-input item-edit-money"
                inputmode="decimal"
                :disabled="itemEditReadOnly"
                placeholder="含税单价缺失"
                @input="handleItemDraftChange(row)"
              />
              <el-tag v-else-if="row.taxIncludedUnitPrice == null" type="danger" size="small">含税单价缺失</el-tag>
              <span v-else>{{ formatMoney(row.taxIncludedUnitPrice) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="untaxedTotalPrice" label="未税金额" width="112" align="right">
            <template #default="{ row }">{{ formatMoney(row.untaxedTotalPrice) }}</template>
          </el-table-column>
          <el-table-column prop="taxIncludedTotalPrice" label="含税金额" width="112" align="right">
            <template #default="{ row }">{{ formatMoney(row.taxIncludedTotalPrice) }}</template>
          </el-table-column>
          <el-table-column prop="salesOrderDate" label="下单日期" width="116">
            <template #default="{ row }">{{ formatDate(row.salesOrderDate) }}</template>
          </el-table-column>
          <el-table-column prop="customerOrderTitle" label="客户下单抬头" min-width="140" show-overflow-tooltip />
          <el-table-column prop="customerOrderNo" label="客户订单号" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <el-input
                v-if="itemDrafts[row.salesOrderItemId]"
                v-model="itemDrafts[row.salesOrderItemId].customerOrderNo"
                class="item-edit-input"
                clearable
                :disabled="itemEditReadOnly"
                placeholder="客户订单号"
                @input="handleItemDraftChange(row)"
              />
              <span v-else>{{ row.customerOrderNo || orderField('customerOrderNo') }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="customerProjectNo" label="客户项目号" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <el-input
                v-if="itemDrafts[row.salesOrderItemId]"
                v-model="itemDrafts[row.salesOrderItemId].customerProjectNo"
                class="item-edit-input"
                clearable
                :disabled="itemEditReadOnly"
                placeholder="客户项目号"
                @input="handleItemDraftChange(row)"
              />
              <span v-else>{{ row.customerProjectNo || orderField('customerProjectNo') }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="internalProjectNo" label="内部项目号" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <el-input
                v-if="itemDrafts[row.salesOrderItemId]"
                v-model="itemDrafts[row.salesOrderItemId].internalProjectNo"
                class="item-edit-input"
                clearable
                :disabled="itemEditReadOnly"
                placeholder="内部项目号"
                @input="handleItemDraftChange(row)"
              />
              <span v-else>{{ row.internalProjectNo || orderField('internalProjectNo') }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="customerDeliveryDate" label="客户交期" width="148">
            <template #default="{ row }">
              <el-date-picker
                v-if="itemDrafts[row.salesOrderItemId]"
                v-model="itemDrafts[row.salesOrderItemId].customerDeliveryDate"
                class="item-edit-input item-edit-date"
                type="date"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                clearable
                :disabled="itemEditReadOnly"
                placeholder="客户交期"
                @change="handleItemDraftChange(row)"
              />
              <span v-else>{{ formatDate(row.customerDeliveryDate) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="internalDeliveryDate" label="内部交期(给生产的交期)" width="178">
            <template #default="{ row }">
              <el-date-picker
                v-if="itemDrafts[row.salesOrderItemId]"
                v-model="itemDrafts[row.salesOrderItemId].internalDeliveryDate"
                class="item-edit-input item-edit-date"
                type="date"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                clearable
                :disabled="itemEditReadOnly"
                placeholder="内部交期"
                @change="handleItemDraftChange(row)"
              />
              <span v-else>{{ formatDate(row.internalDeliveryDate) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
          <el-table-column prop="materialName" label="材料" min-width="110" show-overflow-tooltip />
          <el-table-column prop="engineeringMaterialCode" label="材料编码" min-width="120" show-overflow-tooltip />
          <el-table-column prop="engineeringMaterialTypeName" label="材料类型" min-width="120" show-overflow-tooltip />
          <el-table-column prop="surfaceTreatment" label="表面处理" min-width="110" show-overflow-tooltip />
          <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <el-input
                v-if="itemDrafts[row.salesOrderItemId]"
                v-model="itemDrafts[row.salesOrderItemId].remark"
                class="item-edit-input"
                clearable
                :disabled="itemEditReadOnly"
                placeholder="备注"
                @input="handleItemDraftChange(row)"
              />
              <span v-else>{{ row.remark || '-' }}</span>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="previewLineTotal" class="erp-pagination sales-order-u9-sync-pagination">
          <el-pagination
            :current-page="itemPagination.page"
            :page-size="itemPagination.pageSize"
            :page-sizes="CLIENT_PAGE_SIZES"
            :pager-count="7"
            :total="previewLineTotal"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @current-change="handleItemPageChange"
            @size-change="handleItemPageSizeChange"
          />
        </div>
      </main>
    </section>

    <OrderPickerDialog
      v-model="orderPickerVisible"
      title="选择销售订单"
      customer-scope="u9-sync"
      order-scope="u9-sync"
      :allowed-statuses="U9_SYNC_ALLOWED_STATUSES"
      @select="handleOrderSelected"
    />
    <SalesOrderU9ItemDataReplacementDialog
      v-model="itemDataReplacementVisible"
      :sales-order-id="selectedOrderId"
      :disabled="itemDataReplacementDisabled"
      @busy-change="itemDataReplacementBusy = $event"
      @confirmed="handleItemDataReplacementConfirmed"
    />
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DocumentChecked } from '@element-plus/icons-vue'
import { useRoute } from 'vue-router'
import { usePendingAction } from '../composables/usePendingAction'
import { useAuthStore } from '../stores/authStore'
import OrderPickerDialog from './OrderPickerDialog.vue'
import SalesOrderU9ItemDataReplacementDialog from './u9-sync/SalesOrderU9ItemDataReplacementDialog.vue'
import {
  getSalesOrderU9SyncOptions,
  previewSalesOrderU9Sync,
  reconcileSalesOrderU9,
  refreshSalesOrderU9SyncOptions,
  saveSalesOrderU9Assignment,
  saveSalesOrderU9OrderDate,
  saveSalesOrderU9Items,
  syncSalesOrderU9
} from '../services/marketApi'
import { CLIENT_PAGE_SIZES, DEFAULT_CLIENT_PAGE_SIZE } from '../utils/clientPagination'

const U9_SYNC_SUBMIT_PERMISSION = 'market:u9-sync:submit'
const U9_SYNC_ORDER_SELECT_PERMISSION = 'market:u9-sync:order-select'
const U9_SYNC_ALLOWED_STATUSES = ['部分已提单', '已生成提单']
const U9_PRICE_MODE_OPTIONS = [
  {
    value: 'UNTAXED',
    label: '按未税价同步',
    field: 'untaxedUnitPrice',
    name: '未税单价',
    missingText: '未税单价缺失',
    tagText: 'U9价格=未税单价'
  },
  {
    value: 'TAX_INCLUDED',
    label: '按含税价同步',
    field: 'taxIncludedUnitPrice',
    name: '含税单价',
    missingText: '含税单价缺失',
    tagText: 'U9价格=含税单价'
  }
]
const U9_ITEM_EDITABLE_FIELDS = [
  'qty',
  'customerDeliveryDate',
  'internalDeliveryDate',
  'untaxedUnitPrice',
  'taxIncludedUnitPrice',
  'customerOrderNo',
  'customerProjectNo',
  'internalProjectNo',
  'remark'
]
const TEXT_ITEM_EDITABLE_FIELDS = ['customerOrderNo', 'customerProjectNo', 'internalProjectNo', 'remark']
const NULLABLE_ITEM_EDITABLE_FIELDS = ['customerDeliveryDate', 'internalDeliveryDate', ...TEXT_ITEM_EDITABLE_FIELDS]

const u9SyncActionKeys = {
  sync: 'sales-order-u9-sync',
  reconcile: 'sales-order-u9-reconcile'
}

const privateFieldDefinitions = [
  { key: 'customerProjectNo', u9Field: 'PrivateDescSeg1 客户项目号', fallback: 'customerProjectNo' },
  { key: 'customerOrderNo', u9Field: 'PrivateDescSeg2 客户采购订单号', fallback: 'customerOrderNo' },
  { key: 'internalProjectNo', u9Field: 'PrivateDescSeg3 内部项目号', fallback: 'internalProjectNo' },
  { key: 'customerDeliveryDate', u9Field: 'PrivateDescSeg4 客户交期', format: 'date' },
  { key: 'internalDeliveryDate', u9Field: 'PrivateDescSeg5 内部交期', format: 'date' },
  { key: 'syncPrice', u9Field: 'PrivateDescSeg9 同步价格', format: 'money', syncPrice: true },
  { key: 'remark', u9Field: 'PubDescSeg2 销售备注' }
]

const { isPending, runAction } = usePendingAction()
const route = useRoute()
const authStore = useAuthStore()

const selectedOrder = ref(null)
const selectedOrderId = ref(null)
const previewData = ref(null)
const syncResult = ref(null)
const reconcileResult = ref(null)
const orderPickerVisible = ref(false)
const itemDataReplacementVisible = ref(false)
const previewLoading = ref(false)
const previewMessageDetailVisible = ref(false)
const optionsLoading = ref(false)
const optionsRefreshing = ref(false)
const assignmentSaving = ref(false)
const orderDateSaving = ref(false)
const itemEditSaving = ref(false)
const itemDataReplacementBusy = ref(false)
const u9Options = reactive({ orgCode: '', stale: false, refreshedAt: '', warning: '', sellers: [], departments: [] })
const assignmentDraft = ref({ sellerCode: '', saleDepartmentCode: '' })
const savedAssignment = ref({ sellerCode: '', saleDepartmentCode: '' })
const orderDateDraft = ref(null)
const savedOrderDate = ref(null)
const u9PriceMode = ref('TAX_INCLUDED')
const itemDrafts = reactive({})
const savedItemDrafts = reactive({})
const dirtyItemIds = ref(new Set())
const itemPagination = reactive({ page: 1, pageSize: DEFAULT_CLIENT_PAGE_SIZE })

const dateDraftValue = (value) => value ? String(value).slice(0, 10) : null

const selectedOrderLabel = computed(() => selectedOrder.value
  ? `${selectedOrder.value.salesOrderNo || selectedOrder.value.salesOrderId || '-'} / ${selectedOrder.value.customerName || '-'}`
  : '未选择订单')
const previewLineTotal = computed(() => Number(previewData.value?.total ?? previewData.value?.itemCount ?? 0))
const previewLines = computed(() => previewData.value?.records || previewData.value?.lines || [])
const syncRows = computed(() => syncResult.value?.rows || [])
const u9SellerOptions = computed(() => u9Options.sellers || [])
const u9DepartmentOptions = computed(() => u9Options.departments || [])
const currentU9Status = computed(() => reconcileResult.value?.u9SyncStatus || previewData.value?.u9SyncStatus || selectedOrder.value?.u9SyncStatus || '未同步')
const isPendingConfirmation = computed(() => currentU9Status.value === '结果待确认')
const currentU9OrderCode = computed(() => {
  const returnedCode = syncRows.value.find(row => row?.u9OrderCode)?.u9OrderCode
  return returnedCode || reconcileResult.value?.u9OrderCode || previewData.value?.u9OrderCode || selectedOrder.value?.u9OrderCode || '-'
})
const selectedU9PriceMode = computed(() => U9_PRICE_MODE_OPTIONS.find(option => option.value === u9PriceMode.value) || U9_PRICE_MODE_OPTIONS[0])
const selectedU9PriceField = computed(() => selectedU9PriceMode.value.field)
const selectedU9PriceName = computed(() => selectedU9PriceMode.value.name)
const selectedU9PriceMissingText = computed(() => selectedU9PriceMode.value.missingText)
const selectedU9PriceTagText = computed(() => selectedU9PriceMode.value.tagText)
const selectedU9PriceModeTip = computed(() => `${selectedU9PriceTagText.value}，U9 IsPriceIncludeTax=${u9PriceMode.value === 'TAX_INCLUDED' ? 'true' : 'false'}。`)

const customerDisplay = computed(() => {
  const source = previewData.value || selectedOrder.value || {}
  const customerName = source.customerName || ''
  const extras = [source.customerShortName, source.customerCode]
    .filter(Boolean)
    .filter((value, index, array) => array.indexOf(value) === index && value !== customerName)
  if (customerName && extras.length > 0) return `${customerName}（${extras.join(' / ')}）`
  return customerName || extras.join(' / ') || '-'
})

const orderSourceLabel = computed(() => {
  const source = previewData.value || selectedOrder.value || {}
  if (source.sourceLabel) return source.sourceLabel
  if (!source.sourceType && !source.sourceLabel) return '-'
  return source.sourceType === 'RFQ_RELEASE' ? 'RFQ 议价提单派生' : '销售订单导入'
})

const itemPanelTitle = computed(() => {
  if (!selectedOrder.value) return '请点击右上角选择销售订单。'
  if (previewLoading.value) return '正在加载销售订单零件。'
  return `${selectedOrder.value.salesOrderNo || '未编号销售订单'} / ${selectedOrder.value.customerName || '未指定客户'}`
})

const readySummary = computed(() => {
  const source = previewData.value || selectedOrder.value || {}
  return {
    itemCount: numberOrZero(source.itemCount),
    materialConfirmedItemCount: numberOrZero(source.materialConfirmedItemCount),
    missingMaterialItemCount: numberOrZero(source.missingMaterialItemCount),
    syncReady: source.syncReady === true
  }
})

const previewMessages = computed(() => {
  const result = []
  const source = previewData.value || {}
  ;(source.blockingErrors || []).forEach(text => result.push({ type: 'error', text }))
  if (source.blockedReason && !result.some(item => item.text === source.blockedReason)) {
    result.push({ type: 'warning', text: source.blockedReason })
  }
  ;(source.warnings || []).forEach(text => result.push({ type: 'warning', text }))
  return result
})

const previewMessageSummary = computed(() => {
  const messages = previewMessages.value
  const errorCount = messages.filter(message => message.type === 'error').length
  const warningCount = messages.filter(message => message.type === 'warning').length
  const leadMessage = messages[0]?.text || ''
  return {
    errorCount,
    warningCount,
    title: errorCount > 0 ? `${errorCount} 条阻断` : `${warningCount} 条警告`,
    subtitle: messages.length > 1 ? `${leadMessage} 等 ${messages.length} 条` : leadMessage
  }
})

const assignmentReadOnly = computed(() => ['已同步', '同步中', '结果待确认'].includes(currentU9Status.value))
const assignmentDirty = computed(() => assignmentDraft.value.sellerCode !== savedAssignment.value.sellerCode ||
  assignmentDraft.value.saleDepartmentCode !== savedAssignment.value.saleDepartmentCode)
const assignmentSaveDisabled = computed(() => assignmentSaving.value || optionsLoading.value || optionsRefreshing.value || !selectedOrderId.value || assignmentReadOnly.value || !assignmentDirty.value)
const hasItemEditPermission = computed(() => authStore.hasPermission(U9_SYNC_SUBMIT_PERMISSION))
const dirtyItemCount = computed(() => dirtyItemIds.value.size)
const orderDateDirty = computed(() => dateDraftValue(orderDateDraft.value) !== dateDraftValue(savedOrderDate.value))
const orderDateReadOnly = computed(() => !hasItemEditPermission.value || !selectedOrderId.value || ['已同步', '同步中', '结果待确认'].includes(currentU9Status.value))
const orderDateSaveDisabledReason = computed(() => {
  if (!selectedOrderId.value) return '请先选择销售订单'
  if (!hasItemEditPermission.value) return '当前账号无 U9 同步提交权限'
  if (['已同步', '同步中', '结果待确认'].includes(currentU9Status.value)) return '当前 U9 状态只读，不可修改下单日期'
  if (assignmentDirty.value) return '请先保存 U9 选择'
  if (dirtyItemCount.value > 0) return '请先保存零件行修改'
  if (!orderDateDraft.value) return '请选择下单日期'
  if (!orderDateDirty.value) return '下单日期未变化'
  return ''
})
const orderDateSaveDisabled = computed(() => orderDateSaving.value || previewLoading.value || Boolean(orderDateSaveDisabledReason.value))
const itemEditReadOnly = computed(() => !hasItemEditPermission.value || !selectedOrderId.value || ['已同步', '同步中', '结果待确认'].includes(currentU9Status.value))
const itemEditSaveDisabledReason = computed(() => {
  if (!selectedOrderId.value) return '请先选择销售订单'
  if (!hasItemEditPermission.value) return '当前账号无 U9 同步提交权限'
  if (['已同步', '同步中', '结果待确认'].includes(currentU9Status.value)) return '当前 U9 状态只读，不可修改零件行'
  if (dirtyItemCount.value === 0) return '暂无需要保存的零件行修改'
  return ''
})
const itemEditSaveDisabled = computed(() => itemEditSaving.value || previewLoading.value || itemEditReadOnly.value || dirtyItemCount.value === 0)
const itemDataReplacementDisabledReason = computed(() => {
  if (!selectedOrderId.value) return '请先选择销售订单'
  if (!hasItemEditPermission.value) return '当前账号无 U9 同步提交权限'
  if (['已同步', '同步中', '结果待确认'].includes(currentU9Status.value)) return '当前 U9 状态只读，不可替换零件行数据'
  if (itemEditSaving.value) return '零件行修改正在保存，请稍后替换'
  if (previewLoading.value) return 'U9 预览正在刷新，请稍后替换'
  return ''
})
const itemDataReplacementDisabled = computed(() => itemDataReplacementBusy.value || Boolean(itemDataReplacementDisabledReason.value))
const assignmentDisplayText = computed(() => {
  const seller = sellerByCode(assignmentDraft.value.sellerCode)
  const department = departmentByCode(assignmentDraft.value.saleDepartmentCode)
  const sellerText = seller ? sellerOptionLabel(seller) : 'Seller 空'
  const departmentText = department ? departmentOptionLabel(department) : 'SaleDepartment 空'
  return `${sellerText} / ${departmentText}`
})

const syncDisabledReason = computed(() => {
  if (isPending(u9SyncActionKeys.sync)) return 'U9同步正在提交，请稍候'
  if (!selectedOrder.value) return '请先选择销售订单'
  if (isPendingConfirmation.value) return '结果待确认，请先核对 U9 结果'
  if (!previewData.value || previewData.value.salesOrderId !== selectedOrderId.value) {
    return '请先选择销售订单并加载零件预览'
  }
  if (currentU9Status.value === '已同步') return '该销售订单已同步 U9'
  if (assignmentDirty.value) return '请先保存 Seller / SaleDepartment 选择'
  if (orderDateDirty.value) return '请先保存下单日期'
  if (dirtyItemCount.value > 0) return '请先保存零件行修改'
  if (previewData.value.syncReady !== true) {
    return previewData.value.blockedReason || selectedOrder.value.blockedReason || '未满足整单同步条件'
  }
  return ''
})

const reconcileDisabledReason = computed(() => {
  if (!isPendingConfirmation.value) return ''
  if (isPending(u9SyncActionKeys.reconcile)) return 'U9结果正在核对，请稍候'
  if (!selectedOrderId.value) return '请先选择销售订单'
  return ''
})

const canSyncSelected = computed(() => !syncDisabledReason.value)
const canReconcileSelected = computed(() => isPendingConfirmation.value && !reconcileDisabledReason.value)

const requestErrorMessage = (error, fallback) => error?.response?.data?.message || error?.payload?.message || error?.message || fallback

const openOrderPicker = () => {
  loadU9Options({ silent: true })
  orderPickerVisible.value = true
}

const openItemDataReplacementDialog = () => {
  if (itemDataReplacementDisabled.value) {
    ElMessage.warning(itemDataReplacementDisabledReason.value || '当前暂不可进行表格数据替换。')
    return
  }
  itemDataReplacementVisible.value = true
}

const normalizeSalesOrder = (order = {}) => {
  const salesOrderId = Number(order.salesOrderId ?? order.sourceOrderId ?? order.id)
  return {
    ...order,
    salesOrderId,
    salesOrderNo: order.salesOrderNo || order.sourceOrderNo || order.orderNo || '-'
  }
}

const handleOrderSelected = async (order) => {
  const normalized = normalizeSalesOrder(order)
  if (!normalized.salesOrderId) {
    ElMessage.warning('请选择有效的销售订单。')
    return
  }
  selectedOrder.value = normalized
  selectedOrderId.value = normalized.salesOrderId
  syncResult.value = null
  reconcileResult.value = null
  itemPagination.page = 1
  resetItemDrafts()
  hydrateAssignment(normalized)
  hydrateOrderDate(normalized)
  await previewSelectedOrder({ silent: true })
}

const refreshCurrentState = async () => {
  if (!selectedOrder.value) {
    ElMessage.warning('请先选择销售订单。')
    return
  }
  await Promise.all([
    loadU9Options({ silent: true }),
    previewSelectedOrder({ silent: true, preserveResult: true })
  ])
}

const loadU9Options = async ({ silent = false, force = false } = {}) => {
  if (optionsLoading.value || (optionsRefreshing.value && !force)) return
  optionsLoading.value = true
  try {
    const response = await getSalesOrderU9SyncOptions()
    Object.assign(u9Options, {
      orgCode: response?.orgCode || '',
      stale: response?.stale === true,
      refreshedAt: response?.refreshedAt || '',
      warning: response?.warning || '',
      sellers: response?.sellers || [],
      departments: response?.departments || []
    })
    if (!silent && u9Options.stale) {
      ElMessage.warning(u9Options.warning || 'U9主数据暂时不可用，已使用过期缓存。')
    }
  } catch (error) {
    if (!silent) ElMessage.error(requestErrorMessage(error, 'U9销售员和部门选项加载失败。'))
  } finally {
    optionsLoading.value = false
  }
}

const handleRefreshU9Options = async () => {
  if (optionsLoading.value || optionsRefreshing.value) return
  optionsRefreshing.value = true
  try {
    const response = await refreshSalesOrderU9SyncOptions()
    if (response?.success === false) {
      ElMessage.error(response.message || response.safeMessage || 'U9选项缓存刷新失败。')
      return
    }
    ElMessage.success(response?.message || 'U9选项缓存已刷新。')
    await loadU9Options({ silent: true, force: true })
  } catch (error) {
    ElMessage.error(requestErrorMessage(error, 'U9选项缓存刷新失败。'))
  } finally {
    optionsRefreshing.value = false
  }
}

const applyPreviewData = (response = {}, { preserveResult = false } = {}) => {
  previewData.value = response || {}
  if (response?.priceMode) {
    u9PriceMode.value = response.priceMode
  }
  selectedOrder.value = {
    ...selectedOrder.value,
    ...(response || {}),
    salesOrderId: selectedOrderId.value
  }
  hydrateAssignment(response || {})
  hydrateOrderDate(response || {})
  hydrateItemDrafts(response || {})
  if (!preserveResult) {
    syncResult.value = null
    reconcileResult.value = null
  }
}

const previewSelectedOrder = async ({ silent = false, preserveResult = false } = {}) => {
  if (!selectedOrderId.value) {
    if (!silent) ElMessage.warning('请先选择销售订单。')
    return
  }
  previewLoading.value = true
  try {
    const response = await previewSalesOrderU9Sync(selectedOrderId.value, {
      page: itemPagination.page,
      pageSize: itemPagination.pageSize,
      priceMode: u9PriceMode.value
    })
    applyPreviewData(response || {}, { preserveResult })
    if (!silent) {
      if (response?.syncReady) {
        ElMessage.success('U9同步预览已就绪，可以提交整单同步。')
      } else {
        ElMessage.warning(response?.blockedReason || '该销售订单暂不满足整单同步条件。')
      }
    }
  } catch (error) {
    previewData.value = null
    resetItemDrafts()
    if (!preserveResult) {
      syncResult.value = null
      reconcileResult.value = null
    }
    ElMessage.error(requestErrorMessage(error, 'U9同步预览失败。'))
  } finally {
    previewLoading.value = false
  }
}

const ensureCanChangeItemPage = () => {
  if (dirtyItemCount.value > 0) {
    ElMessage.warning('请先保存零件行修改后再翻页')
    return false
  }
  return true
}

const handleItemPageChange = async (nextPage) => {
  if (nextPage === itemPagination.page) return
  if (!ensureCanChangeItemPage()) return
  itemPagination.page = nextPage
  await previewSelectedOrder({ silent: true, preserveResult: true })
}

const handleItemPageSizeChange = async (nextPageSize) => {
  if (nextPageSize === itemPagination.pageSize) return
  if (!ensureCanChangeItemPage()) return
  itemPagination.pageSize = nextPageSize
  itemPagination.page = 1
  await previewSelectedOrder({ silent: true, preserveResult: true })
}

const previewMatchesItemPagination = (source) => Number(source?.page) === itemPagination.page &&
  Number(source?.pageSize) === itemPagination.pageSize
const previewMatchesCurrentView = (source) => previewMatchesItemPagination(source) &&
  (!source?.priceMode || source.priceMode === u9PriceMode.value)

const handleSellerChange = (sellerCode) => {
  const seller = sellerByCode(sellerCode)
  if (seller?.departmentCode) {
    assignmentDraft.value.saleDepartmentCode = seller.departmentCode
  }
}

const handleDepartmentChange = () => {
  assignmentDraft.value = { ...assignmentDraft.value }
}

const handleU9PriceModeChange = async () => {
  if (!selectedOrderId.value) return
  if (dirtyItemCount.value > 0) {
    ElMessage.warning('请先保存零件行修改，再切换 U9 价格口径刷新预览。')
    return
  }
  await previewSelectedOrder({ silent: true, preserveResult: true })
}

const handleSaveAssignment = async () => {
  if (assignmentSaveDisabled.value) return
  assignmentSaving.value = true
  try {
    const payload = {
      sellerCode: assignmentDraft.value.sellerCode || null,
      saleDepartmentCode: assignmentDraft.value.saleDepartmentCode || null
    }
    const response = await saveSalesOrderU9Assignment(selectedOrderId.value, payload)
    applyAssignmentResponse(response || payload)
    ElMessage.success('U9销售员和部门已保存。')
    await previewSelectedOrder({ silent: true, preserveResult: true })
  } catch (error) {
    ElMessage.error(requestErrorMessage(error, 'U9销售员和部门保存失败。'))
  } finally {
    assignmentSaving.value = false
  }
}

const hydrateOrderDate = (source = {}) => {
  const next = dateDraftValue(source.salesOrderDate)
  orderDateDraft.value = next
  savedOrderDate.value = next
}

const handleSaveOrderDate = async () => {
  if (orderDateSaveDisabled.value) {
    if (orderDateSaveDisabledReason.value) ElMessage.warning(orderDateSaveDisabledReason.value)
    return
  }
  orderDateSaving.value = true
  try {
    const payload = { salesOrderDate: orderDateDraft.value }
    const response = await saveSalesOrderU9OrderDate(selectedOrderId.value, payload)
    if (response?.preview && previewMatchesCurrentView(response.preview)) {
      applyPreviewData(response.preview, { preserveResult: true })
    } else {
      hydrateOrderDate(response || payload)
      await previewSelectedOrder({ silent: true, preserveResult: true })
    }
    ElMessage.success(response?.message || '销售订单下单日期已保存。')
  } catch (error) {
    ElMessage.error(requestErrorMessage(error, '销售订单下单日期保存失败。'))
  } finally {
    orderDateSaving.value = false
  }
}

const clearReactiveObject = (target) => {
  Object.keys(target).forEach(key => delete target[key])
}

const textDraftValue = (line, field) => {
  if (line?.[field] !== undefined && line?.[field] !== null) return String(line[field])
  const fallback = (previewData.value || selectedOrder.value || {})[field]
  return fallback === undefined || fallback === null ? '' : String(fallback)
}

const normalizeItemDraft = (line = {}) => ({
  qty: line.qty ?? null,
  customerDeliveryDate: dateDraftValue(line.customerDeliveryDate),
  internalDeliveryDate: dateDraftValue(line.internalDeliveryDate),
  untaxedUnitPrice: line.untaxedUnitPrice === undefined || line.untaxedUnitPrice === null ? '' : String(line.untaxedUnitPrice),
  taxIncludedUnitPrice: line.taxIncludedUnitPrice === undefined || line.taxIncludedUnitPrice === null ? '' : String(line.taxIncludedUnitPrice),
  customerOrderNo: textDraftValue(line, 'customerOrderNo'),
  customerProjectNo: textDraftValue(line, 'customerProjectNo'),
  internalProjectNo: textDraftValue(line, 'internalProjectNo'),
  remark: line.remark === undefined || line.remark === null ? '' : String(line.remark)
})

const resetItemDrafts = () => {
  clearReactiveObject(itemDrafts)
  clearReactiveObject(savedItemDrafts)
  dirtyItemIds.value = new Set()
}

const hydrateItemDrafts = (source = {}) => {
  clearReactiveObject(itemDrafts)
  clearReactiveObject(savedItemDrafts)
  ;(source.records || source.lines || []).forEach(line => {
    if (!line?.salesOrderItemId) return
    const key = String(line.salesOrderItemId)
    const draft = normalizeItemDraft(line)
    itemDrafts[key] = { ...draft }
    savedItemDrafts[key] = { ...draft }
  })
  dirtyItemIds.value = new Set()
}

const comparableItemValue = (field, value) => {
  if (NULLABLE_ITEM_EDITABLE_FIELDS.includes(field)) {
    const text = value === undefined || value === null ? '' : String(value).trim()
    return text || null
  }
  if (field === 'qty') {
    if (value === undefined || value === null || value === '') return null
    const number = Number(value)
    return Number.isFinite(number) ? number : String(value)
  }
  if (field === 'untaxedUnitPrice' || field === 'taxIncludedUnitPrice') {
    if (value === undefined || value === null || value === '') return null
    return String(value).trim()
  }
  return value ?? null
}

const itemFieldChanged = (key, field) => comparableItemValue(field, itemDrafts[key]?.[field]) !== comparableItemValue(field, savedItemDrafts[key]?.[field])

const refreshItemDirtyState = (salesOrderItemId) => {
  const key = String(salesOrderItemId || '')
  if (!key || !itemDrafts[key] || !savedItemDrafts[key]) return
  const next = new Set(dirtyItemIds.value)
  if (U9_ITEM_EDITABLE_FIELDS.some(field => itemFieldChanged(key, field))) {
    next.add(key)
  } else {
    next.delete(key)
  }
  dirtyItemIds.value = next
}

const handleItemDraftChange = (row) => {
  refreshItemDirtyState(row?.salesOrderItemId)
}

const itemRequestValue = (field, value) => {
  if (TEXT_ITEM_EDITABLE_FIELDS.includes(field)) {
    const text = value === undefined || value === null ? '' : String(value).trim()
    return text || null
  }
  if (field === 'customerDeliveryDate' || field === 'internalDeliveryDate') return value || null
  if (field === 'qty') return value === undefined || value === null || value === '' ? null : Number(value)
  if (field === 'untaxedUnitPrice' || field === 'taxIncludedUnitPrice') return value === undefined || value === null || value === '' ? null : String(value).trim()
  return value
}

const buildDirtyItemPayload = () => Array.from(dirtyItemIds.value)
  .map(key => {
    const line = { salesOrderItemId: Number(key) }
    U9_ITEM_EDITABLE_FIELDS.forEach(field => {
      if (itemFieldChanged(key, field)) {
        line[field] = itemRequestValue(field, itemDrafts[key]?.[field])
      }
    })
    return line
  })
  .filter(line => Object.keys(line).length > 1)

const handleSaveItemEdits = async () => {
  if (itemEditSaveDisabled.value) return
  const items = buildDirtyItemPayload()
  if (!items.length) {
    dirtyItemIds.value = new Set()
    return
  }
  itemEditSaving.value = true
  try {
    const response = await saveSalesOrderU9Items(selectedOrderId.value, { items })
    if (response?.preview && previewMatchesCurrentView(response.preview)) {
      applyPreviewData(response.preview, { preserveResult: true })
    } else {
      await previewSelectedOrder({ silent: true, preserveResult: true })
    }
    ElMessage.success(response?.message || `已保存 ${items.length} 行零件修改。`)
  } catch (error) {
    ElMessage.error(requestErrorMessage(error, '零件行修改保存失败。'))
  } finally {
    itemEditSaving.value = false
  }
}

const handleItemDataReplacementConfirmed = async (response = {}) => {
  try {
    if (response?.preview && previewMatchesCurrentView(response.preview)) {
      applyPreviewData(response.preview, { preserveResult: true })
    } else {
      await previewSelectedOrder({ silent: true, preserveResult: true })
    }
    dirtyItemIds.value = new Set()
    itemDataReplacementVisible.value = false
  } catch (error) {
    ElMessage.error(requestErrorMessage(error, '表格数据替换后刷新预览失败。'))
  }
}

const syncSelectedOrder = async () => {
  if (!canSyncSelected.value) {
    ElMessage.warning(syncDisabledReason.value || '该销售订单暂不可同步。')
    return
  }
  return runAction(u9SyncActionKeys.sync, async () => {
    try {
      await ElMessageBox.confirm(
        `确认将销售订单 ${selectedOrder.value.salesOrderNo || ''} 按${selectedU9PriceName.value}整单同步到 U9？`,
        '确认同步U9',
        {
          type: 'warning',
          confirmButtonText: '同步U9',
          cancelButtonText: '取消'
        }
      )
    } catch {
      return
    }
    try {
      const response = await syncSalesOrderU9(selectedOrderId.value, { priceMode: u9PriceMode.value })
      syncResult.value = response || {}
      reconcileResult.value = null
      if (response?.success) {
        ElMessage.success(response?.message || 'U9同步成功。')
      } else {
        ElMessage.warning(response?.message || 'U9同步返回失败，请查看失败明细。')
      }
      await previewSelectedOrder({ silent: true, preserveResult: true })
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, 'U9同步提交失败。'))
    }
  })
}

const handleReconcileOrder = async () => {
  if (!canReconcileSelected.value) {
    ElMessage.warning(reconcileDisabledReason.value || '当前销售订单不需要核对。')
    return
  }
  return runAction(u9SyncActionKeys.reconcile, async () => {
    try {
      const response = await reconcileSalesOrderU9(selectedOrderId.value)
      reconcileResult.value = response || {}
      syncResult.value = null
      if (response?.u9SyncStatus === '已同步') {
        ElMessage.success(response?.message || 'U9结果核对成功。')
      } else {
        ElMessage.warning(response?.message || 'U9结果仍需关注。')
      }
      await previewSelectedOrder({ silent: true, preserveResult: true })
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, 'U9结果核对失败。'))
    }
  })
}

const hydrateAssignment = (source = {}) => {
  const next = {
    sellerCode: source.sellerCode || '',
    saleDepartmentCode: source.saleDepartmentCode || ''
  }
  assignmentDraft.value = { ...next }
  savedAssignment.value = { ...next }
}

const applyAssignmentResponse = (source = {}) => {
  const next = {
    sellerCode: source.sellerCode || '',
    saleDepartmentCode: source.saleDepartmentCode || ''
  }
  assignmentDraft.value = { ...next }
  savedAssignment.value = { ...next }
  selectedOrder.value = {
    ...selectedOrder.value,
    sellerCode: next.sellerCode,
    sellerName: source.sellerName || sellerByCode(next.sellerCode)?.name || '',
    saleDepartmentCode: next.saleDepartmentCode,
    saleDepartmentName: source.saleDepartmentName || departmentByCode(next.saleDepartmentCode)?.name || ''
  }
}

const sellerByCode = (code) => u9SellerOptions.value.find(seller => seller.code === code)
const departmentByCode = (code) => u9DepartmentOptions.value.find(department => department.code === code)
const sellerOptionLabel = (seller) => `${seller.code || '-'} / ${seller.name || '-'}${seller.departmentCode ? ` / ${seller.departmentCode}` : ''}`
const departmentOptionLabel = (department) => `${department.code || '-'} / ${department.treeDisplayName || department.name || '-'}`

const orderValue = (field) => previewData.value?.[field] || selectedOrder.value?.[field] || ''
const orderField = (field) => orderValue(field) || '-'

function numberOrZero(value) {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

const orderStatusTagType = (status) => {
  if (status === '已生成提单') return 'success'
  if (status === '部分已提单') return 'warning'
  return 'info'
}

const u9StatusTagType = (status) => {
  if (status === '已同步') return 'success'
  if (status === '同步失败') return 'danger'
  if (status === '同步中' || status === '结果待确认') return 'warning'
  return 'info'
}

const materialStatusTagType = (status) => {
  if (status === '已确认') return 'success'
  if (status === '获取失败') return 'danger'
  if (status === '未确认') return 'warning'
  return 'info'
}

const lineMessages = (row) => [
  ...(row?.blockingErrors || []).map(message => `错误：${message}`),
  ...(row?.warnings || []).map(message => `警告：${message}`)
]

const lineIssueSummary = (row) => {
  const errorCount = row?.blockingErrors?.length || 0
  const warningCount = row?.warnings?.length || 0
  if (errorCount > 0 && warningCount > 0) return `${errorCount}错 / ${warningCount}警`
  if (errorCount > 0) return `${errorCount}条错误`
  if (warningCount > 0) return `${warningCount}条警告`
  return '-'
}

const previewMessageTypeText = (type) => type === 'error' ? '阻断' : '警告'

const u9PrivateFieldValue = (row, field) => {
  if (field.syncPrice) return formatMoney(row?.[selectedU9PriceField.value])
  const raw = row?.[field.key] ?? (field.fallback ? orderValue(field.fallback) : '')
  if (field.format === 'date') return formatDate(raw)
  if (field.format === 'money') return formatMoney(raw)
  return raw || '-'
}

const formatDate = (value) => {
  if (!value) return '-'
  return String(value).slice(0, 10)
}

const formatMoney = (value) => {
  if (value === null || value === undefined || value === '') return '-'
  const amount = Number(value)
  if (Number.isNaN(amount)) return String(value)
  return amount.toFixed(2)
}

const normalizeRouteSalesOrderId = (value) => {
  const raw = Array.isArray(value) ? value[0] : value
  const id = Number(raw)
  return Number.isInteger(id) && id > 0 ? id : null
}

watch(
  () => route.query.salesOrderId,
  async value => {
    const salesOrderId = normalizeRouteSalesOrderId(value)
    if (!salesOrderId || (salesOrderId === selectedOrderId.value && previewData.value)) return
    await handleOrderSelected({ salesOrderId })
  },
  { immediate: true }
)

onMounted(() => {
  loadU9Options({ silent: true })
})
</script>

<style scoped>
.sales-order-u9-sync-page {
  max-width: 100%;
  overflow-x: hidden;
  padding: 0;
}

.sales-order-u9-sync-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  min-width: 0;
}

.sales-order-u9-sync-header > div:first-child {
  min-width: 0;
}

.sales-order-u9-sync-header-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.sales-order-u9-sync-action-wrap {
  display: inline-flex;
}

.sales-order-u9-sync-workspace {
  display: grid;
  grid-template-columns: minmax(300px, 360px) minmax(0, 1fr);
  gap: var(--erp-space-4);
  max-width: 100%;
  min-width: 0;
}

.sales-order-u9-sync-side-panel,
.sales-order-u9-sync-main-panel {
  min-width: 0;
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
  margin-bottom: 6px;
}

.panel-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.compact-row {
  align-items: center;
  margin-bottom: 8px;
}

.panel-title-actions {
  display: flex;
  flex: 0 0 auto;
  justify-content: flex-end;
}

.panel-title-row .panel-title {
  margin-bottom: 4px;
}

.panel-subtitle {
  margin: 0;
  color: var(--erp-text-secondary);
  font-size: 12px;
}

.selected-order-text {
  min-height: 34px;
  display: flex;
  align-items: center;
  color: var(--erp-text);
  border: 1px solid var(--erp-border);
  border-radius: 6px;
  padding: 0 10px;
  background: var(--erp-surface-soft);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.u9-assignment-panel,
.u9-order-date-panel,
.u9-price-mode-panel,
.sync-condition-panel,
.selected-order-meta,
.sync-result-panel {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--erp-border-soft);
}

.assignment-form-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 10px;
}

.order-date-form-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 10px;
}

.assignment-field {
  display: grid;
  gap: 6px;
  min-width: 0;
  color: var(--erp-text-secondary);
  font-size: 12px;
}

.assignment-field :deep(.el-select) {
  width: 100%;
}

.assignment-field :deep(.el-date-editor) {
  width: 100%;
}

.assignment-selected-line {
  min-width: 0;
  color: var(--erp-text-secondary);
  font-size: 12px;
  word-break: break-all;
}

.order-date-current-line {
  color: var(--erp-text-secondary);
  font-size: 12px;
}

.order-date-current-line strong {
  color: var(--erp-text);
}

.u9-price-mode-group {
  width: 100%;
}

.u9-price-mode-group :deep(.el-radio-button) {
  width: 50%;
}

.u9-price-mode-group :deep(.el-radio-button__inner) {
  width: 100%;
}

.u9-price-mode-tip {
  margin: 8px 0 0;
  color: var(--erp-text-secondary);
  font-size: 12px;
}

.condition-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 32px;
  min-width: 0;
  color: var(--erp-text-secondary);
  font-size: 13px;
}

.condition-row strong {
  min-width: 0;
  color: var(--erp-text);
  font-weight: 700;
  text-align: right;
  word-break: break-all;
}

.warning-text {
  color: var(--erp-warning) !important;
}

.sales-order-u9-sync-alert {
  margin-top: 10px;
}

.preview-message-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 0 0 10px;
  padding: 8px 10px;
  border: 1px solid var(--erp-warning);
  border-radius: var(--erp-radius-md);
  background: var(--erp-warning-soft);
}

.preview-message-summary.blocked {
  border-color: var(--erp-danger);
  background: var(--erp-danger-soft);
}

.preview-message-summary-main {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.preview-message-summary-text {
  min-width: 0;
  color: var(--erp-warning);
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-message-summary.blocked .preview-message-summary-text {
  color: var(--erp-danger);
}

.preview-message-toggle {
  flex: 0 0 auto;
}

.preview-message-detail {
  display: grid;
  gap: 6px;
  margin: -2px 0 10px;
  padding: 8px 10px;
  border: 1px solid var(--erp-border-soft);
  border-radius: var(--erp-radius-md);
  background: var(--erp-surface-muted);
}

.preview-message-detail-row {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr);
  gap: 8px;
  align-items: start;
  color: var(--erp-text-secondary);
  font-size: 12px;
}

.preview-message-detail-row span {
  color: var(--erp-text-muted);
  font-weight: 600;
}

.preview-message-detail-row strong {
  min-width: 0;
  color: var(--erp-text);
  font-weight: 500;
  word-break: break-word;
}

.preview-message-detail-row.error strong {
  color: var(--erp-danger);
}

.preview-message-detail-row.warning strong {
  color: var(--erp-warning);
}

.line-issue-tag {
  max-width: 100%;
}

.line-issue-tag :deep(.el-tag__content) {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.muted-placeholder {
  color: var(--erp-text-muted);
}

.selected-order-meta dl {
  display: grid;
  gap: 10px;
  margin: 0;
}

.selected-order-meta dl > div {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
}

.selected-order-meta dt {
  color: var(--erp-text-secondary);
  font-size: 12px;
}

.selected-order-meta dd {
  min-width: 0;
  margin: 0;
  color: var(--erp-text);
  font-size: 13px;
  word-break: break-all;
}

.sync-result-code-row,
.sync-result-summary,
.sales-order-u9-sync-result-tags,
.line-message-stack {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.sales-order-u9-sync-result-tags {
  justify-content: flex-end;
}

.sales-order-u9-sync-table-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.item-edit-dirty-tag {
  font-weight: 600;
}

.item-edit-input {
  width: 100%;
}

.item-edit-input :deep(.el-input__wrapper) {
  min-height: 28px;
}

.item-edit-number :deep(.el-input__inner),
.item-edit-money :deep(.el-input__inner) {
  text-align: right;
}

.item-edit-date {
  min-width: 128px;
}

.sync-result-code-row {
  justify-content: space-between;
  margin-bottom: 10px;
  color: var(--erp-text-secondary);
  font-size: 13px;
}

.sync-result-code-row strong {
  min-width: 0;
  color: var(--erp-text);
  word-break: break-all;
}

.sync-result-summary {
  justify-content: flex-start;
  margin-bottom: 10px;
  color: var(--erp-text-secondary);
  font-size: 13px;
}

.sales-order-u9-sync-result-alert {
  margin-bottom: 10px;
}

.private-field-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px;
  max-width: 100%;
  min-width: 0;
}

.private-field-card {
  display: grid;
  gap: 4px;
  min-width: 0;
  padding: 8px 10px;
  border: 1px solid var(--erp-border-soft);
  border-radius: 6px;
  background: var(--erp-surface-soft);
}

.private-field-card span {
  color: var(--erp-text-secondary);
  font-size: 12px;
}

.private-field-card strong {
  min-width: 0;
  color: var(--erp-text);
  font-size: 13px;
  font-weight: 600;
  word-break: break-all;
}

.line-message-stack {
  margin-top: 10px;
}

@media (max-width: 1180px) {
  .sales-order-u9-sync-workspace {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 760px) {
  .sales-order-u9-sync-header,
  .panel-title-row {
    flex-direction: column;
    align-items: stretch;
  }

  .sales-order-u9-sync-header-actions,
  .sales-order-u9-sync-result-tags,
  .sales-order-u9-sync-table-actions {
    justify-content: stretch;
    width: 100%;
  }

  .sales-order-u9-sync-header-actions :deep(.el-button),
  .sales-order-u9-sync-action-wrap,
  .panel-title-actions,
  .panel-title-actions :deep(.el-button),
  .sales-order-u9-sync-table-actions :deep(.el-button),
  .order-date-form-grid :deep(.el-button),
  .assignment-form-grid :deep(.el-button) {
    width: 100%;
    margin-left: 0;
  }

  .selected-order-meta dl > div {
    grid-template-columns: minmax(0, 1fr);
    gap: 4px;
  }
}
</style>
