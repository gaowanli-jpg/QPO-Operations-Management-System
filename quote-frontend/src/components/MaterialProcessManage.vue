<template>
  <div class="erp-page material-process-page">
    <header class="erp-page-header">
      <div>
        <h1 class="erp-page-title">材料工艺</h1>
        <p class="erp-page-description">维护材料、两级工艺主数据和费用字典，工程核价会直接读取这里的数据。</p>
      </div>
    </header>

    <section class="erp-toolbar">
      <div class="erp-toolbar-group material-toolbar">
        <el-button @click="loadAll" :loading="loading">刷新</el-button>
        <el-button v-permission="'engineer:dict:material-manage'" v-if="activeTab === 'materials'" type="warning" plain :loading="isPending(dictActionKeys.syncMesMaterials)" :disabled="isPending(dictActionKeys.syncMesMaterials)" @click="syncMesMaterials">从 MES 同步材料</el-button>
        <el-button v-permission="'engineer:dict:material-manage'" v-if="activeTab === 'materials'" plain :loading="scheduleLoading && scheduleTarget === 'MES_MATERIAL'" :disabled="scheduleLoading || isPending(dictActionKeys.saveMesSchedule)" @click="openMesMaterialScheduleDialog">定时同步策略</el-button>
        <el-button v-permission="'engineer:dict:process-manage'" v-if="activeTab === 'processCategories'" type="warning" plain :loading="isPending(dictActionKeys.syncMesProcessTypes)" :disabled="isPending(dictActionKeys.syncMesProcessTypes)" @click="syncMesProcessTypes">从 MES 同步工艺</el-button>
        <el-button v-permission="'engineer:dict:process-manage'" v-if="activeTab === 'processCategories'" plain :loading="scheduleLoading && scheduleTarget === 'MES_PROCESS_TYPE'" :disabled="scheduleLoading || isPending(dictActionKeys.saveMesSchedule)" @click="openMesProcessScheduleDialog">定时同步策略</el-button>
        <el-button v-permission="'engineer:dict:fee-manage'" v-else-if="activeTab === 'pricingMarkupFees' && pricingMarkupFeeCategory === 'BASIC_PERCENT'" type="primary" @click="openPricingMarkupFeeDialog()">新增基础百分比费用</el-button>
        <el-button v-permission="'engineer:dict:fee-manage'" v-else-if="activeTab === 'extraFees'" type="primary" @click="openExtraFeeDialog()">新增非工时费用工艺</el-button>
      </div>
    </section>

    <section class="erp-table-surface material-table-surface">
      <div class="erp-table-body">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="材料费用" name="materials">
            <div class="material-filter-bar">
              <el-input v-model="materialKeyword" clearable placeholder="搜索材料编码、名称、材料类型或材质分类" />
            </div>
            <el-table :data="materials" border stripe height="520" v-loading="loading">
              <el-table-column prop="materialCode" label="材料编码" width="150" fixed show-overflow-tooltip />
              <el-table-column prop="materialName" label="材料名称" width="140" fixed show-overflow-tooltip />
              <el-table-column label="材料类型" width="170" show-overflow-tooltip>
                <template #default="scope">
                  <div class="material-type-cell">
                    <span>{{ scope.row.materialTypeName || '-' }}</span>
                    <small v-if="scope.row.materialTypeCode">{{ scope.row.materialTypeCode }}</small>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="materialCategory" label="材质分类" width="120" show-overflow-tooltip />
              <el-table-column label="来源" width="110" align="center">
                <template #default="scope">
                  <el-tag :type="scope.row.sourceType === 'MES' ? 'success' : 'info'" size="small">{{ sourceTypeText(scope.row.sourceType) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="MES 类型" width="120" align="center">
                <template #default="scope">{{ scope.row.mesMaterialType || '-' }}</template>
              </el-table-column>
              <el-table-column label="MES 密度" width="130" align="right">
                <template #default="scope">{{ formatDensity(scope.row.mesDensityRaw) }}</template>
              </el-table-column>
              <el-table-column label="同步时间" width="170" align="center">
                <template #default="scope">{{ formatSyncTime(scope.row.mesSyncedAt) }}</template>
              </el-table-column>
              <el-table-column label="单价状态" width="120" align="center">
                <template #default="scope">
                  <el-tag :type="materialPriceStatusType(scope.row)" size="small">{{ materialPriceStatusText(scope.row) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="板材密度" width="150" align="right">
                <template #default="scope">{{ formatDensity(scope.row.plateDensity) }}</template>
              </el-table-column>
              <el-table-column label="板材单价" width="150" align="right">
                <template #default="scope">{{ formatPrice(scope.row.platePrice) }}</template>
              </el-table-column>
              <el-table-column label="棒材密度" width="150" align="right">
                <template #default="scope">{{ formatDensity(scope.row.barDensity) }}</template>
              </el-table-column>
              <el-table-column label="棒材单价" width="150" align="right">
                <template #default="scope">{{ formatPrice(scope.row.barPrice) }}</template>
              </el-table-column>
              <el-table-column label="管材密度" width="150" align="right">
                <template #default="scope">{{ formatDensity(scope.row.tubeDensity) }}</template>
              </el-table-column>
              <el-table-column label="管材单价" width="150" align="right">
                <template #default="scope">{{ formatPrice(scope.row.tubePrice) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="90" align="center">
                <template #default="scope">
                  <el-tag :type="scope.row.enabled !== false ? 'success' : 'info'" size="small">{{ scope.row.enabled !== false ? '启用' : '停用' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="sortNo" label="排序" width="90" align="center" />
              <el-table-column prop="remarks" label="备注" min-width="150" show-overflow-tooltip />
              <el-table-column label="操作" width="90" align="center" fixed="right">
                <template #default="scope">
                  <div class="erp-table-actions">
                    <el-button v-permission="'engineer:dict:material-manage'" type="primary" size="small" @click="openMaterialDialog(scope.row)">编辑</el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
            <div v-if="materialPagination.total" class="erp-pagination material-dict-pagination">
              <el-pagination
                v-model:current-page="materialPagination.page"
                v-model:page-size="materialPagination.pageSize"
                :page-sizes="CLIENT_PAGE_SIZES"
                :pager-count="7"
                :total="materialPagination.total"
                layout="total, sizes, prev, pager, next, jumper"
                background
                @size-change="resetMaterialPage"
              />
            </div>
          </el-tab-pane>

          <el-tab-pane label="材料类型" name="materialTypes">
            <el-table :data="materialTypes" border stripe height="560" v-loading="loading">
              <el-table-column prop="sortNo" label="排序" width="80" align="center" fixed />
              <el-table-column prop="typeCode" label="类型编码" width="180" fixed show-overflow-tooltip />
              <el-table-column prop="typeName" label="类型名称" min-width="160" show-overflow-tooltip />
              <el-table-column label="状态" width="90" align="center">
                <template #default="scope">
                  <el-tag :type="scope.row.enabled !== false ? 'success' : 'info'" size="small">{{ scope.row.enabled !== false ? '启用' : '停用' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="remarks" label="备注" min-width="180" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="工艺大类" name="processCategories">
            <div class="process-master-grid">
              <div class="material-filter-bar">
                <el-input v-model="processCategoryKeyword" clearable placeholder="搜索工艺编码、工艺名称、来源或备注" />
              </div>
              <el-table :data="processCategories" border stripe height="520" v-loading="loading">
                <el-table-column prop="sortNo" label="序号" width="80" align="center" fixed />
                <el-table-column prop="processCode" label="工艺编码" width="150" fixed show-overflow-tooltip />
                <el-table-column prop="categoryName" label="工艺名称" min-width="160" show-overflow-tooltip />
                <el-table-column label="小时价格" width="140" align="right">
                  <template #default="scope">{{ formatHourlyRate(scope.row.hourlyRate) }}</template>
                </el-table-column>
                <el-table-column label="来源" width="110" align="center">
                  <template #default="scope">
                    <el-tag :type="scope.row.sourceType === 'MES' ? 'success' : 'info'" size="small">{{ sourceTypeText(scope.row.sourceType) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="同步时间" width="170" align="center">
                  <template #default="scope">{{ formatSyncTime(scope.row.mesSyncedAt) }}</template>
                </el-table-column>
                <el-table-column label="状态" width="90" align="center">
                  <template #default="scope">
                    <el-tag :type="scope.row.enabled ? 'success' : 'info'" size="small">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="remarks" label="备注" min-width="180" show-overflow-tooltip />
              </el-table>
              <div v-if="processCategoryPagination.total" class="erp-pagination material-dict-pagination">
                <el-pagination
                  v-model:current-page="processCategoryPagination.page"
                  v-model:page-size="processCategoryPagination.pageSize"
                  :page-sizes="CLIENT_PAGE_SIZES"
                  :pager-count="7"
                  :total="processCategoryPagination.total"
                  layout="total, sizes, prev, pager, next, jumper"
                  background
                  @size-change="resetProcessCategoryPage"
                />
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="具体工艺" name="processDetails">
            <div class="process-master-grid">
              <div class="material-filter-bar">
                <el-input v-model="processDetailKeyword" clearable placeholder="搜索具体工艺、大类编码、大类名称或具体备注" />
              </div>
              <el-table :data="processDetails" border stripe height="520" v-loading="loading">
                <el-table-column prop="sortNo" label="序号" width="80" align="center" fixed />
                <el-table-column prop="detailName" label="具体工艺" min-width="160" fixed show-overflow-tooltip />
                <el-table-column prop="processCode" label="大类编码" width="150" show-overflow-tooltip />
                <el-table-column prop="categoryName" label="大类名称" min-width="150" show-overflow-tooltip />
                <el-table-column label="大类状态" width="100" align="center">
                  <template #default="scope">
                    <el-tag :type="scope.row.categoryEnabled !== false ? 'success' : 'info'" size="small">{{ scope.row.categoryEnabled !== false ? '启用' : '停用' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="具体状态" width="100" align="center">
                  <template #default="scope">
                    <el-tag :type="(scope.row.detailEnabled ?? scope.row.enabled) !== false ? 'success' : 'info'" size="small">{{ (scope.row.detailEnabled ?? scope.row.enabled) !== false ? '启用' : '停用' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="核价可用" width="100" align="center">
                  <template #default="scope">
                    <el-tag :type="scope.row.effectiveEnabled !== false ? 'success' : 'danger'" size="small">{{ scope.row.effectiveEnabled !== false ? '可用' : '不可用' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="具体小时价格" width="150" align="right">
                  <template #default="scope">{{ formatDetailHourlyRate(scope.row.detailHourlyRate) }}</template>
                </el-table-column>
                <el-table-column label="大类小时价格" width="150" align="right">
                  <template #default="scope">{{ formatHourlyRate(scope.row.categoryHourlyRate) }}</template>
                </el-table-column>
                <el-table-column label="核价使用价格" width="150" align="right">
                  <template #default="scope">{{ formatHourlyRate(scope.row.effectiveHourlyRate ?? scope.row.hourlyRate) }}</template>
                </el-table-column>
                <el-table-column prop="detailRemarks" label="具体备注" min-width="200" show-overflow-tooltip />
                <el-table-column label="操作" width="120" align="center" fixed="right">
                  <template #default="scope">
                    <div class="erp-table-actions">
                      <el-button v-permission="'engineer:dict:process-manage'" type="primary" size="small" @click="openProcessDetailDialog(scope.row)">编辑</el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
              <div v-if="processDetailPagination.total" class="erp-pagination material-dict-pagination">
                <el-pagination
                  v-model:current-page="processDetailPagination.page"
                  v-model:page-size="processDetailPagination.pageSize"
                  :page-sizes="CLIENT_PAGE_SIZES"
                  :pager-count="7"
                  :total="processDetailPagination.total"
                  layout="total, sizes, prev, pager, next, jumper"
                  background
                  @size-change="resetProcessDetailPage"
                />
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="百分比费用" name="pricingMarkupFees">
            <div class="pricing-markup-panel">
              <div class="pricing-markup-controls">
                <div class="pricing-markup-control-group">
                  <span class="pricing-markup-control-label">适用范围</span>
                  <el-radio-group v-model="pricingMarkupScope" size="small">
                    <el-radio-button v-for="option in pricingMarkupScopeOptions" :key="option.value" :label="option.value">{{ option.label }}</el-radio-button>
                  </el-radio-group>
                </div>
                <div class="pricing-markup-control-group">
                  <span class="pricing-markup-control-label">费用分类</span>
                  <el-radio-group v-model="pricingMarkupFeeCategory" size="small">
                    <el-radio-button v-for="option in pricingMarkupFeeCategoryOptions" :key="option.value" :label="option.value">{{ option.label }}</el-radio-button>
                  </el-radio-group>
                </div>
                <el-input v-model="pricingMarkupKeyword" clearable placeholder="搜索费用名称、适用范围、分类或状态" />
              </div>
              <el-table :data="pagedPricingMarkupFees" border stripe height="520" v-loading="loading">
                <el-table-column prop="sortNo" label="序号" width="80" align="center" fixed />
                <el-table-column prop="feeName" label="费用名称" min-width="150" fixed show-overflow-tooltip />
                <el-table-column label="费率" width="120" align="right">
                  <template #default="{ row }">{{ Number(row.ratePercent || 0).toFixed(2) }}%</template>
                </el-table-column>
                <el-table-column prop="pricingScope" label="适用范围" width="120" align="center">
                  <template #default="{ row }">{{ pricingScopeText(row.pricingScope) }}</template>
                </el-table-column>
                <el-table-column prop="feeCategory" label="分类" width="140" align="center">
                  <template #default="{ row }">{{ pricingMarkupFeeCategoryText(row.feeCategory) }}</template>
                </el-table-column>
                <el-table-column label="启用" width="90" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="systemRequired" label="系统项" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.systemRequired ? 'warning' : 'info'" size="small">{{ row.systemRequired ? '系统项' : '普通项' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="180" align="center" fixed="right">
                  <template #default="{ row }">
                    <div class="erp-table-actions">
                      <el-button v-permission="'engineer:dict:fee-manage'" type="primary" size="small" :disabled="isPricingMarkupFeeDeletePending(row)" @click="openPricingMarkupFeeDialog(row)">编辑</el-button>
                      <el-button v-permission="'engineer:dict:fee-manage'" type="danger" size="small" plain class="erp-danger-action" :loading="isPricingMarkupFeeDeletePending(row)" :disabled="row.systemRequired || isPricingMarkupFeeDeletePending(row)" @click="deletePricingMarkupFee(row)">删除</el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
              <div v-if="filteredPricingMarkupFees.length" class="erp-pagination material-dict-pagination">
                <el-pagination
                  v-model:current-page="pricingMarkupPagination.page"
                  v-model:page-size="pricingMarkupPagination.pageSize"
                  :page-sizes="CLIENT_PAGE_SIZES"
                  :pager-count="7"
                  :total="filteredPricingMarkupFees.length"
                  layout="total, sizes, prev, pager, next, jumper"
                  background
                  @size-change="resetPricingMarkupFeePage"
                />
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="非工时费用工艺" name="extraFees">
            <el-table :data="extraFeeItems" border stripe height="560" v-loading="loading">
              <el-table-column prop="sortNo" label="序号" width="80" align="center" fixed />
              <el-table-column prop="feeName" label="工艺名称" min-width="180" fixed show-overflow-tooltip />
              <el-table-column prop="remarks" label="备注" min-width="180" show-overflow-tooltip />
              <el-table-column label="启用" width="90" align="center">
                <template #default="scope">
                  <el-tag :type="scope.row.enabled ? 'success' : 'info'" size="small">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="160" align="center" fixed="right">
                <template #default="scope">
                  <div class="erp-table-actions">
                    <el-button v-permission="'engineer:dict:fee-manage'" type="primary" size="small" :disabled="isExtraFeeDeletePending(scope.row)" @click="openExtraFeeDialog(scope.row)">编辑</el-button>
                    <el-button v-permission="'engineer:dict:fee-manage'" type="danger" size="small" plain class="erp-danger-action" :loading="isExtraFeeDeletePending(scope.row)" :disabled="isExtraFeeDeletePending(scope.row)" @click="deleteExtraFee(scope.row)">删除</el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>
    </section>

    <el-dialog v-model="materialDialogVisible" :title="materialForm.id ? '编辑材料' : '新增材料'" width="820px">
      <el-form :model="materialForm" label-width="120px" class="erp-dialog-form process-dialog-grid material-dialog-grid">
        <el-form-item label="材料编码" required>
          <el-input v-model="materialForm.materialCode" disabled placeholder="例如：MAT-000001" />
        </el-form-item>
        <el-form-item label="材料名称" required>
          <el-input v-model="materialForm.materialName" disabled placeholder="例如：45#" />
        </el-form-item>
        <el-form-item label="材料类型" required>
          <el-select v-model="materialForm.materialTypeId" filterable disabled class="dialog-select" placeholder="选择材料类型">
            <el-option v-for="type in materialTypes" :key="type.id" :label="materialTypeLabel(type)" :value="type.id" :disabled="type.enabled === false" />
          </el-select>
        </el-form-item>
        <el-form-item label="材质分类">
          <el-input v-model="materialForm.materialCategory" disabled placeholder="例如：钢铁、铝、塑料" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="materialForm.sortNo" :min="0" controls-position="right" class="dialog-number-input" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="materialForm.enabled" />
        </el-form-item>
        <el-form-item label="板材密度">
          <div class="unit-input">
            <el-input-number v-model="materialForm.plateDensity" :precision="4" :min="0" controls-position="right" class="unit-input-control" />
            <span class="unit-label">kg/m³</span>
          </div>
        </el-form-item>
        <el-form-item label="板材单价">
          <div class="unit-input">
            <el-input-number v-model="materialForm.platePrice" :precision="2" :min="0" controls-position="right" class="unit-input-control" />
            <span class="unit-label">元/kg</span>
          </div>
        </el-form-item>
        <el-form-item label="棒材密度">
          <div class="unit-input">
            <el-input-number v-model="materialForm.barDensity" :precision="4" :min="0" controls-position="right" class="unit-input-control" />
            <span class="unit-label">kg/m³</span>
          </div>
        </el-form-item>
        <el-form-item label="棒材单价">
          <div class="unit-input">
            <el-input-number v-model="materialForm.barPrice" :precision="2" :min="0" controls-position="right" class="unit-input-control" />
            <span class="unit-label">元/kg</span>
          </div>
        </el-form-item>
        <el-form-item label="管材密度">
          <div class="unit-input">
            <el-input-number v-model="materialForm.tubeDensity" :precision="4" :min="0" controls-position="right" class="unit-input-control" />
            <span class="unit-label">kg/m³</span>
          </div>
        </el-form-item>
        <el-form-item label="管材单价">
          <div class="unit-input">
            <el-input-number v-model="materialForm.tubePrice" :precision="2" :min="0" controls-position="right" class="unit-input-control" />
            <span class="unit-label">元/kg</span>
          </div>
        </el-form-item>
        <el-form-item label="备注" class="process-dialog-wide">
          <el-input v-model="materialForm.remarks" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="materialDialogVisible = false">取消</el-button>
          <el-button v-permission="'engineer:dict:material-manage'" type="primary" :loading="isPending(dictActionKeys.saveMaterialDialog)" :disabled="isPending(dictActionKeys.saveMaterialDialog)" @click="saveMaterial">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="processDetailDialogVisible" :title="processDetailForm.id ? '编辑具体工艺' : '新增具体工艺'" width="680px">
      <el-form :model="processDetailForm" label-width="120px" class="erp-dialog-form process-dialog-grid">
        <el-form-item label="序号">
          <el-input-number v-model="processDetailForm.sortNo" :min="0" controls-position="right" class="dialog-number-input" />
        </el-form-item>
        <el-form-item label="工艺大类" required>
          <el-select v-model="processDetailForm.categoryId" filterable disabled class="dialog-select" placeholder="选择工艺大类">
            <el-option v-for="category in processCategoryOptions" :key="category.id" :label="categoryLabel(category)" :value="category.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="具体工艺" required>
          <el-input v-model="processDetailForm.detailName" readonly placeholder="例如：精细包装" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="processDetailForm.enabled" />
        </el-form-item>
        <el-form-item label="具体小时价格">
          <div class="unit-input">
            <el-input-number v-model="processDetailForm.hourlyRate" :min="0" :precision="2" :value-on-clear="null" clearable controls-position="right" class="unit-input-control" />
            <span class="unit-label wide">元/小时</span>
          </div>
          <div class="form-helper-text">留空继承大类小时价格；填 0 表示该具体工艺按 0 元/小时核价。</div>
        </el-form-item>
        <el-form-item label="具体备注" class="process-dialog-wide">
          <el-input v-model="processDetailForm.detailRemarks" type="textarea" :rows="3" placeholder="例如：包装选择精细包装" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="processDetailDialogVisible = false">取消</el-button>
          <el-button v-permission="'engineer:dict:process-manage'" type="primary" :loading="isPending(dictActionKeys.saveProcessDetailDialog)" :disabled="isPending(dictActionKeys.saveProcessDetailDialog)" @click="saveProcessDetail">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="pricingMarkupFeeDialogVisible" :title="pricingMarkupFeeForm.id ? '编辑百分比费用' : '新增基础百分比费用'" width="620px">
      <el-form :model="pricingMarkupFeeForm" label-width="110px" class="erp-dialog-form process-dialog-grid">
        <el-form-item label="适用范围" required>
          <el-select v-model="pricingMarkupFeeForm.pricingScope" disabled class="dialog-select">
            <el-option v-for="option in pricingMarkupScopeOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="费用分类" required>
          <el-select v-model="pricingMarkupFeeForm.feeCategory" disabled class="dialog-select">
            <el-option v-for="option in pricingMarkupFeeCategoryOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="序号">
          <el-input-number v-model="pricingMarkupFeeForm.sortNo" :min="0" :disabled="!pricingMarkupIdentityEditable" controls-position="right" class="dialog-number-input" />
        </el-form-item>
        <el-form-item label="费用名称" required>
          <el-input v-model="pricingMarkupFeeForm.feeName" :disabled="!pricingMarkupIdentityEditable" placeholder="例如：运输费、管理费、利润" />
        </el-form-item>
        <el-form-item label="费率(%)" required>
          <el-input-number v-model="pricingMarkupFeeForm.ratePercent" :precision="2" :min="0" controls-position="right" class="dialog-number-input" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="pricingMarkupFeeForm.enabled" />
        </el-form-item>
        <el-form-item label="系统项">
          <el-tag :type="pricingMarkupFeeForm.systemRequired ? 'warning' : 'info'">{{ pricingMarkupFeeForm.systemRequired ? '不可删除' : '可删除' }}</el-tag>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="pricingMarkupFeeDialogVisible = false">取消</el-button>
          <el-button v-permission="'engineer:dict:fee-manage'" type="primary" :loading="isPending(dictActionKeys.savePricingMarkupFeeDialog)" :disabled="isPending(dictActionKeys.savePricingMarkupFeeDialog)" @click="savePricingMarkupFee">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="extraFeeDialogVisible" :title="extraFeeForm.id ? '编辑非工时费用工艺' : '新增非工时费用工艺'" width="560px">
      <el-form :model="extraFeeForm" label-width="120px" class="erp-dialog-form">
        <el-form-item label="序号">
          <el-input-number v-model="extraFeeForm.sortNo" :min="0" controls-position="right" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="工艺名称" required>
          <el-input v-model="extraFeeForm.feeName" placeholder="例如：EDM、热处理、表面处理、QA" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="extraFeeForm.remarks" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="extraFeeForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="extraFeeDialogVisible = false">取消</el-button>
          <el-button v-permission="'engineer:dict:fee-manage'" type="primary" :loading="isPending(dictActionKeys.saveExtraFeeDialog)" :disabled="isPending(dictActionKeys.saveExtraFeeDialog)" @click="saveExtraFee">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="scheduleDialogVisible"
      :title="scheduleDialogTitle"
      width="min(560px, 92vw)"
      class="mes-schedule-dialog"
      destroy-on-close
    >
      <div v-loading="scheduleLoading" class="mes-schedule-panel">
        <el-alert
          class="mes-schedule-alert"
          type="info"
          :closable="false"
          show-icon
          title="MES 会更新基础信息，不覆盖 QPO 材料单价和板/棒/管密度；策略默认关闭，保存后作为运行时配置。"
        />
        <el-form label-width="150px" class="erp-dialog-form" :class="'mes-schedule-form'">
          <el-form-item label="开启定时同步">
            <el-switch v-model="scheduleForm.enabled" active-text="开启" inactive-text="关闭" />
          </el-form-item>
          <el-form-item label="启动后首次执行">
            <el-input-number
              v-model="scheduleForm.startupDelaySeconds"
              :min="0"
              :max="86400"
              :step="30"
              controls-position="right"
            />
            <span class="mes-schedule-unit">秒</span>
          </el-form-item>
          <el-form-item label="完成后再次执行">
            <el-input-number
              v-model="scheduleForm.fixedDelayMinutes"
              :min="1"
              :max="10080"
              :step="5"
              controls-position="right"
            />
            <span class="mes-schedule-unit">分钟</span>
          </el-form-item>
        </el-form>
        <el-descriptions :column="1" size="small" border class="mes-schedule-status">
          <el-descriptions-item label="同步对象">{{ scheduleTargetLabel }}</el-descriptions-item>
          <el-descriptions-item label="配置来源">{{ scheduleForm.fallback ? '环境兜底' : '数据库策略' }}</el-descriptions-item>
          <el-descriptions-item label="上次定时状态">{{ scheduleStatusLabel(scheduleForm.lastScheduledStatus) }}</el-descriptions-item>
          <el-descriptions-item label="上次定时时间">{{ formatSyncTime(scheduleForm.lastScheduledRunAt) }}</el-descriptions-item>
          <el-descriptions-item label="同步批次">{{ scheduleForm.lastScheduledSyncRunId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="说明">{{ scheduleForm.lastScheduledMessage || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <div class="mes-schedule-footer">
          <el-button :disabled="isPending(dictActionKeys.saveMesSchedule)" @click="scheduleDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="isPending(dictActionKeys.saveMesSchedule)" :disabled="scheduleLoading || isPending(dictActionKeys.saveMesSchedule)" @click="saveMesScheduleSetting">保存策略</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteExtraFeeItem,
  deletePricingMarkupFee as deletePricingMarkupFeeRequest,
  getMesMaterialSyncSchedule,
  getMesProcessSyncSchedule,
  listExtraFeeItems,
  listMaterialTypes,
  listPricingMarkupFees,
  pageMaterials,
  pageProcessCategories,
  pageProcessDetails,
  createPricingMarkupFee,
  saveExtraFeeItem,
  saveMaterial as saveMaterialRequest,
  saveMesMaterialSyncSchedule,
  saveMesProcessSyncSchedule,
  saveProcessDetail as saveProcessDetailRequest,
  syncMesMaterials as syncMesMaterialsRequest,
  syncMesProcessTypes as syncMesProcessTypesRequest,
  updatePricingMarkupFee
} from '../services/dictApi'
import { useDictionaryStore } from '../stores/dictionaryStore'
import { usePendingAction } from '../composables/usePendingAction'
import { CLIENT_PAGE_SIZES, DEFAULT_CLIENT_PAGE_SIZE, paginateRows } from '../utils/clientPagination'

const dictionaryStore = useDictionaryStore()
const pricingMarkupScopeOptions = [
  { value: 'NORMAL', label: '普通件' },
  { value: 'ASSEMBLY', label: '组装件' }
]
const pricingMarkupFeeCategoryOptions = [
  { value: 'BASIC_PERCENT', label: '基础百分比费用' },
  { value: 'TAX', label: '税率' },
  { value: 'CUSTOMER_WEIGHT', label: '客户权重' }
]
const activeTab = ref('materials')
const materialTypes = ref([])
const materials = ref([])
const processCategories = ref([])
const processDetails = ref([])
const pricingMarkupFees = ref([])
const extraFeeItems = ref([])
const materialKeyword = ref('')
const processCategoryKeyword = ref('')
const processDetailKeyword = ref('')
const pricingMarkupKeyword = ref('')
const pricingMarkupScope = ref('NORMAL')
const pricingMarkupFeeCategory = ref('BASIC_PERCENT')
const createPagination = () => reactive({ page: 1, pageSize: DEFAULT_CLIENT_PAGE_SIZE, total: 0 })
const materialPagination = createPagination()
const processCategoryPagination = createPagination()
const processDetailPagination = createPagination()
const pricingMarkupPagination = reactive({ page: 1, pageSize: DEFAULT_CLIENT_PAGE_SIZE })
const processCategoryOptions = computed(() => {
  const options = new Map()
  for (const category of processCategories.value) {
    options.set(String(category.id), category)
  }
  for (const detail of processDetails.value) {
    const id = detail?.categoryId
    if (id != null && !options.has(String(id))) {
      options.set(String(id), {
        id,
        processCode: detail.processCode,
        categoryName: detail.categoryName,
        hourlyRate: detail.categoryHourlyRate,
        enabled: detail.categoryEnabled
      })
    }
  }
  return Array.from(options.values())
})
const resetMaterialPage = () => { materialPagination.page = 1 }
const resetProcessCategoryPage = () => { processCategoryPagination.page = 1 }
const resetProcessDetailPage = () => { processDetailPagination.page = 1 }
const resetPricingMarkupFeePage = () => { pricingMarkupPagination.page = 1 }
const resetPageAndLoad = (pagination, loader) => {
  if (pagination.page === 1) {
    loadPageWithLoading(loader)
    return
  }
  pagination.page = 1
}
const loading = ref(false)
const { isPending, runAction } = usePendingAction()

const dictActionKeys = {
  saveMaterialDialog: 'engineer:dict:material-save-dialog',
  syncMesMaterials: 'engineer:dict:material-sync-mes',
  syncMesProcessTypes: 'engineer:dict:process-sync-mes',
  saveMesSchedule: 'engineer:dict:mes-master-data-schedule-save',
  saveProcessDetailDialog: 'engineer:dict:process-detail-save-dialog',
  savePricingMarkupFeeDialog: 'engineer:dict:pricing-markup-fee-save-dialog',
  saveExtraFeeDialog: 'engineer:dict:extra-fee-save-dialog',
  deletePricingMarkupFee: (feeId) => `engineer:dict:pricing-markup-fee-delete:${feeId || 'none'}`,
  deleteExtraFee: (feeId) => `engineer:dict:extra-fee-delete:${feeId || 'none'}`
}

const isPricingMarkupFeeDeletePending = (row) => isPending(dictActionKeys.deletePricingMarkupFee(row?.id))
const isExtraFeeDeletePending = (row) => isPending(dictActionKeys.deleteExtraFee(row?.id))

const materialDialogVisible = ref(false)
const processDetailDialogVisible = ref(false)
const pricingMarkupFeeDialogVisible = ref(false)
const extraFeeDialogVisible = ref(false)
const scheduleDialogVisible = ref(false)
const scheduleLoading = ref(false)
const scheduleTarget = ref('MES_MATERIAL')

const materialForm = reactive(createEmptyMaterial())
const processDetailForm = reactive(createEmptyProcessDetail())
const pricingMarkupFeeForm = reactive(createEmptyPricingMarkupFee())
const extraFeeForm = reactive(createEmptyExtraFee())
const scheduleForm = reactive({
  enabled: false,
  startupDelaySeconds: 30,
  fixedDelayMinutes: 30,
  lastScheduledRunAt: '',
  lastScheduledSyncRunId: null,
  lastScheduledStatus: 'NEVER',
  lastScheduledMessage: '',
  fallback: false
})

const scheduleConfigs = {
  MES_MATERIAL: {
    title: 'MES 材料定时同步策略',
    label: 'MES 材料',
    load: getMesMaterialSyncSchedule,
    save: saveMesMaterialSyncSchedule,
    loadError: 'MES 材料定时同步策略加载失败',
    saveSuccess: 'MES 材料定时同步策略已保存',
    saveError: 'MES 材料定时同步策略保存失败'
  },
  MES_PROCESS_TYPE: {
    title: 'MES 工艺定时同步策略',
    label: 'MES 工艺',
    load: getMesProcessSyncSchedule,
    save: saveMesProcessSyncSchedule,
    loadError: 'MES 工艺定时同步策略加载失败',
    saveSuccess: 'MES 工艺定时同步策略已保存',
    saveError: 'MES 工艺定时同步策略保存失败'
  }
}

const activeScheduleConfig = computed(() => scheduleConfigs[scheduleTarget.value] || scheduleConfigs.MES_MATERIAL)
const scheduleDialogTitle = computed(() => activeScheduleConfig.value.title)
const scheduleTargetLabel = computed(() => activeScheduleConfig.value.label)

const formatNumber = (value, precision) => {
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue.toFixed(precision) : '-'
}
const formatDensity = (value) => {
  const text = formatNumber(value, 4)
  return text === '-' ? '-' : `${text} kg/m³`
}
const formatPrice = (value) => {
  const text = formatNumber(value, 2)
  return text === '-' ? '-' : `${text} 元/kg`
}
const formatHourlyRate = (value) => {
  const text = formatNumber(value, 2)
  return text === '-' ? '-' : `${text} 元/小时`
}
const formatDetailHourlyRate = (value) => (value == null || value === '' ? '继承大类' : formatHourlyRate(value))
const sourceTypeText = (value) => (value === 'MES' ? 'MES同步' : '本地维护')
const hasPositivePrice = (value) => Number(value) > 0
const materialPriceStatusText = (row = {}) => (
  hasPositivePrice(row.platePrice) || hasPositivePrice(row.barPrice) || hasPositivePrice(row.tubePrice)
    ? '单价已维护'
    : '单价未维护'
)
const materialPriceStatusType = (row = {}) => (materialPriceStatusText(row) === '单价已维护' ? 'success' : 'danger')
const formatSyncTime = (value) => (value ? String(value).replace('T', ' ').slice(0, 19) : '-')
const materialTypeLabel = (type) => `${type.typeCode || '-'} / ${type.typeName || '-'}`
const categoryLabel = (category) => `${category.processCode || '-'} / ${category.categoryName || '-'} / ${formatHourlyRate(category.hourlyRate)}`
const pricingScopeText = (value) => pricingMarkupScopeOptions.find((item) => item.value === value)?.label || value || '-'
const pricingMarkupFeeCategoryText = (value) => pricingMarkupFeeCategoryOptions.find((item) => item.value === value)?.label || value || '-'
const requestErrorMessage = (error, fallback) => error?.response?.data?.message || error?.payload?.message || error?.message || fallback
const scheduleStatusLabel = (status) => ({
  SUCCESS: '成功',
  FAILED: '失败',
  NEVER: '未执行'
}[status] || status || '-')

function createEmptyMaterial() {
  return {
    id: null,
    materialCode: '',
    materialName: '',
    materialTypeId: null,
    materialCategory: '',
    remarks: '',
    enabled: true,
    sortNo: 0,
    plateDensity: 0,
    platePrice: 0,
    barDensity: 0,
    barPrice: 0,
    tubeDensity: 0,
    tubePrice: 0
  }
}

function createEmptyProcessDetail() {
  return {
    id: null,
    sortNo: 0,
    categoryId: null,
    detailName: '',
    hourlyRate: null,
    detailRemarks: '',
    enabled: true
  }
}

function createEmptyPricingMarkupFee() {
  return {
    id: null,
    sortNo: 0,
    pricingScope: pricingMarkupScope.value,
    feeCategory: pricingMarkupFeeCategory.value,
    feeName: '',
    ratePercent: 0,
    enabled: true,
    systemRequired: false
  }
}

function createEmptyExtraFee() {
  return {
    id: null,
    sortNo: 0,
    feeName: '',
    enabled: true,
    remarks: ''
  }
}

const pageParams = (pagination, keyword) => ({
  keyword: keyword.value.trim(),
  page: pagination.page,
  pageSize: pagination.pageSize
})

const applyPageResult = (pagination, response = {}) => {
  pagination.total = Number(response.total || 0)
  pagination.page = Number(response.page || pagination.page)
  pagination.pageSize = Number(response.pageSize || pagination.pageSize)
  return response.records || []
}

const normalizePricingMarkupFee = (row = {}, pricingScope, feeCategory) => ({
  ...row,
  pricingScope: row.pricingScope || pricingScope,
  feeCategory: row.feeCategory || feeCategory,
  enabled: row.enabled !== false,
  systemRequired: row.systemRequired === true
})

const loadPricingMarkupFeeRows = async () => {
  const groups = await Promise.all(pricingMarkupScopeOptions.flatMap((scope) => (
    pricingMarkupFeeCategoryOptions.map((category) => (
      listPricingMarkupFees(scope.value, category.value)
        .then((rows) => (rows || []).map((row) => normalizePricingMarkupFee(row, scope.value, category.value)))
    ))
  )))
  return groups.flat()
}

const filteredPricingMarkupFees = computed(() => {
  const keyword = pricingMarkupKeyword.value.trim().toLowerCase()
  return pricingMarkupFees.value.filter((row) => {
    if (row.pricingScope !== pricingMarkupScope.value || row.feeCategory !== pricingMarkupFeeCategory.value) {
      return false
    }
    if (!keyword) return true
    return [
      row.feeName,
      pricingScopeText(row.pricingScope),
      pricingMarkupFeeCategoryText(row.feeCategory),
      row.enabled ? '启用' : '停用',
      row.systemRequired ? '系统项' : '普通项'
    ].some((value) => String(value || '').toLowerCase().includes(keyword))
  })
})
const pagedPricingMarkupFees = computed(() => paginateRows(filteredPricingMarkupFees.value, pricingMarkupPagination))
const pricingMarkupIdentityEditable = computed(() => (
  pricingMarkupFeeForm.feeCategory === 'BASIC_PERCENT' && !pricingMarkupFeeForm.systemRequired
))

const invalidateDictionaryCache = () => {
  dictionaryStore.$patch({
    loadedAt: null,
    lastIncludePricingMarkupFees: false,
    lastIncludeAllExtraFees: false
  })
}

const loadLookupDictionaries = async () => {
  const [types, pricingFees, extraFees] = await Promise.all([
    listMaterialTypes(),
    loadPricingMarkupFeeRows(),
    listExtraFeeItems({ includeDisabled: true })
  ])
  materialTypes.value = types || []
  pricingMarkupFees.value = pricingFees || []
  extraFeeItems.value = extraFees || []
}

const loadMaterialPage = async () => {
  const response = await pageMaterials({
    ...pageParams(materialPagination, materialKeyword)
  })
  materials.value = applyPageResult(materialPagination, response)
}

const loadProcessCategoryPage = async () => {
  const response = await pageProcessCategories({
    ...pageParams(processCategoryPagination, processCategoryKeyword)
  })
  processCategories.value = applyPageResult(processCategoryPagination, response)
}

const loadProcessDetailPage = async () => {
  const response = await pageProcessDetails({
    ...pageParams(processDetailPagination, processDetailKeyword)
  })
  processDetails.value = applyPageResult(processDetailPagination, response)
}

const loadPageWithLoading = async (loader) => {
  loading.value = true
  try {
    await loader()
  } catch (error) {
    console.error(error)
    ElMessage.error('材料或工艺数据加载失败，请检查后端服务。')
  } finally {
    loading.value = false
  }
}

const loadAll = async () => loadPageWithLoading(async () => {
  await Promise.all([
    loadLookupDictionaries(),
    loadMaterialPage(),
    loadProcessCategoryPage(),
    loadProcessDetailPage()
  ])
})

const reloadAfterMutation = async () => {
  invalidateDictionaryCache()
  await loadAll()
}

const openMaterialDialog = (row) => {
  Object.assign(materialForm, createEmptyMaterial(), row || {})
  materialForm.enabled = materialForm.enabled !== false
  materialDialogVisible.value = true
}

const openProcessDetailDialog = (row) => {
  Object.assign(processDetailForm, createEmptyProcessDetail(), row || {})
  processDetailForm.hourlyRate = row?.detailHourlyRate ?? null
  processDetailForm.enabled = processDetailForm.enabled !== false
  processDetailDialogVisible.value = true
}

const openPricingMarkupFeeDialog = (row) => {
  Object.assign(pricingMarkupFeeForm, createEmptyPricingMarkupFee(), row || {})
  pricingMarkupFeeForm.pricingScope = row?.pricingScope || pricingMarkupScope.value
  pricingMarkupFeeForm.feeCategory = row?.feeCategory || pricingMarkupFeeCategory.value
  pricingMarkupFeeForm.enabled = pricingMarkupFeeForm.enabled !== false
  pricingMarkupFeeForm.systemRequired = pricingMarkupFeeForm.systemRequired === true
  pricingMarkupFeeDialogVisible.value = true
}

const openExtraFeeDialog = (row) => {
  Object.assign(extraFeeForm, createEmptyExtraFee(), row || {})
  extraFeeDialogVisible.value = true
}

const syncMesMaterials = async () => {
  return runAction(dictActionKeys.syncMesMaterials, async () => {
    await ElMessageBox.confirm('MES 会更新基础信息，不覆盖 QPO 材料单价和板/棒/管密度。确认从 MES 同步材料主数据？', '同步确认', { type: 'warning' })
    try {
      const response = await syncMesMaterialsRequest()
      ElMessage.success(response.message || 'MES 材料同步完成')
      await reloadAfterMutation()
    } catch (error) {
      console.error(error)
      ElMessage.error('MES 材料同步失败。')
    }
  })
}

const syncMesProcessTypes = async () => {
  return runAction(dictActionKeys.syncMesProcessTypes, async () => {
    await ElMessageBox.confirm('MES 会更新基础信息，不覆盖 QPO 材料单价和板/棒/管密度。确认从 MES 同步工艺主数据？', '同步确认', { type: 'warning' })
    try {
      const response = await syncMesProcessTypesRequest()
      ElMessage.success(response.message || 'MES 工艺同步完成')
      await reloadAfterMutation()
    } catch (error) {
      console.error(error)
      ElMessage.error('MES 工艺同步失败。')
    }
  })
}

const openMesMaterialScheduleDialog = () => openMesScheduleDialog('MES_MATERIAL')
const openMesProcessScheduleDialog = () => openMesScheduleDialog('MES_PROCESS_TYPE')

const openMesScheduleDialog = async (syncType) => {
  if (scheduleLoading.value) return
  scheduleTarget.value = syncType
  scheduleDialogVisible.value = true
  scheduleLoading.value = true
  try {
    const response = await activeScheduleConfig.value.load()
    applyMesScheduleSetting(response)
  } catch (error) {
    console.error(error)
    ElMessage.error(requestErrorMessage(error, activeScheduleConfig.value.loadError))
  } finally {
    scheduleLoading.value = false
  }
}

const saveMesScheduleSetting = async () => {
  return runAction(dictActionKeys.saveMesSchedule, async () => {
    try {
      const payload = {
        enabled: !!scheduleForm.enabled,
        startupDelayMs: Number(scheduleForm.startupDelaySeconds || 0) * 1000,
        fixedDelayMs: Number(scheduleForm.fixedDelayMinutes || 1) * 60 * 1000
      }
      const response = await activeScheduleConfig.value.save(payload)
      applyMesScheduleSetting(response)
      ElMessage.success(activeScheduleConfig.value.saveSuccess)
      scheduleDialogVisible.value = false
    } catch (error) {
      console.error(error)
      ElMessage.error(requestErrorMessage(error, activeScheduleConfig.value.saveError))
    }
  })
}

const applyMesScheduleSetting = (setting = {}) => {
  scheduleForm.enabled = !!setting.enabled
  scheduleForm.startupDelaySeconds = Math.round((setting.startupDelayMs ?? 30000) / 1000)
  scheduleForm.fixedDelayMinutes = Math.max(1, Math.round((setting.fixedDelayMs ?? 1800000) / 60000))
  scheduleForm.lastScheduledRunAt = setting.lastScheduledRunAt || ''
  scheduleForm.lastScheduledSyncRunId = setting.lastScheduledSyncRunId || null
  scheduleForm.lastScheduledStatus = setting.lastScheduledStatus || 'NEVER'
  scheduleForm.lastScheduledMessage = setting.lastScheduledMessage || ''
  scheduleForm.fallback = !!setting.fallback
}

const saveMaterial = async () => {
  if (!materialForm.materialCode) return ElMessage.warning('请填写材料编码。')
  if (!materialForm.materialName) return ElMessage.warning('请填写材料名称。')
  if (!materialForm.materialTypeId) return ElMessage.warning('请选择材料类型。')
  return runAction(dictActionKeys.saveMaterialDialog, async () => {
    try {
      const payload = { ...materialForm, density: materialForm.plateDensity || materialForm.barDensity || materialForm.tubeDensity || 0 }
      const response = await saveMaterialRequest(payload)
      ElMessage.success(response.message || '保存成功')
      materialDialogVisible.value = false
      await reloadAfterMutation()
    } catch (error) {
      console.error(error)
      ElMessage.error('材料保存失败。')
    }
  })
}

const saveProcessDetail = async () => {
  if (!processDetailForm.categoryId) return ElMessage.warning('请选择工艺大类。')
  if (!processDetailForm.detailName) return ElMessage.warning('请填写具体工艺名称。')
  return runAction(dictActionKeys.saveProcessDetailDialog, async () => {
    try {
      const hourlyRate = processDetailForm.hourlyRate === '' || processDetailForm.hourlyRate == null
        ? null
        : Number(processDetailForm.hourlyRate)
      const response = await saveProcessDetailRequest({ ...processDetailForm, hourlyRate })
      ElMessage.success(response.message || '保存成功')
      processDetailDialogVisible.value = false
      await reloadAfterMutation()
    } catch (error) {
      console.error(error)
      ElMessage.error('具体工艺保存失败。')
    }
  })
}

const savePricingMarkupFee = async () => {
  if (!pricingMarkupFeeForm.feeName) return ElMessage.warning('请填写费用名称。')
  return runAction(dictActionKeys.savePricingMarkupFeeDialog, async () => {
    try {
      const payload = {
        pricingScope: pricingMarkupFeeForm.pricingScope,
        feeCategory: pricingMarkupFeeForm.feeCategory,
        feeName: pricingMarkupFeeForm.feeName,
        ratePercent: Number(pricingMarkupFeeForm.ratePercent || 0),
        sortNo: Number(pricingMarkupFeeForm.sortNo || 0),
        enabled: pricingMarkupFeeForm.enabled !== false
      }
      const response = pricingMarkupFeeForm.id
        ? await updatePricingMarkupFee(pricingMarkupFeeForm.id, payload)
        : await createPricingMarkupFee(payload)
      ElMessage.success(response.message || '保存成功')
      pricingMarkupFeeDialogVisible.value = false
      await reloadAfterMutation()
    } catch (error) {
      console.error(error)
      ElMessage.error(requestErrorMessage(error, '百分比费用保存失败。'))
    }
  })
}

const saveExtraFee = async () => {
  if (!extraFeeForm.feeName) return ElMessage.warning('请填写工艺名称。')
  return runAction(dictActionKeys.saveExtraFeeDialog, async () => {
    try {
      const response = await saveExtraFeeItem(extraFeeForm)
      ElMessage.success(response.message || '保存成功')
      extraFeeDialogVisible.value = false
      await reloadAfterMutation()
    } catch (error) {
      console.error(error)
      ElMessage.error('非工时费用工艺保存失败。')
    }
  })
}

const deletePricingMarkupFee = async (row) => {
  if (row.systemRequired) return ElMessage.warning('系统项不可删除，只能修改百分率或启停。')
  return runAction(dictActionKeys.deletePricingMarkupFee(row.id), async () => {
    await ElMessageBox.confirm(`确认删除百分比费用「${row.feeName}」？`, '删除确认', { type: 'warning' })
    await deletePricingMarkupFeeRequest(row.id)
    ElMessage.success('百分比费用已删除')
    await reloadAfterMutation()
  })
}

const deleteExtraFee = async (row) => {
  return runAction(dictActionKeys.deleteExtraFee(row.id), async () => {
    await ElMessageBox.confirm(`确认删除非工时费用工艺「${row.feeName}」？`, '删除确认', { type: 'warning' })
    await deleteExtraFeeItem(row.id)
    ElMessage.success('非工时费用工艺已删除')
    await reloadAfterMutation()
  })
}

watch(materialKeyword, () => {
  resetPageAndLoad(materialPagination, loadMaterialPage)
})
watch(processCategoryKeyword, () => {
  resetPageAndLoad(processCategoryPagination, loadProcessCategoryPage)
})
watch(processDetailKeyword, () => {
  resetPageAndLoad(processDetailPagination, loadProcessDetailPage)
})
watch(pricingMarkupKeyword, resetPricingMarkupFeePage)
watch(pricingMarkupScope, resetPricingMarkupFeePage)
watch(pricingMarkupFeeCategory, resetPricingMarkupFeePage)
watch(
  () => [materialPagination.page, materialPagination.pageSize],
  () => loadPageWithLoading(loadMaterialPage)
)
watch(
  () => [processCategoryPagination.page, processCategoryPagination.pageSize],
  () => loadPageWithLoading(loadProcessCategoryPage)
)
watch(
  () => [processDetailPagination.page, processDetailPagination.pageSize],
  () => loadPageWithLoading(loadProcessDetailPage)
)

onMounted(loadAll)
</script>

<style scoped>
.material-toolbar {
  justify-content: flex-end;
  flex-wrap: wrap;
}

.material-table-surface {
  overflow: hidden;
}

.material-filter-bar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.material-filter-bar .el-input {
  max-width: 360px;
  width: 100%;
}

.pricing-markup-panel {
  display: grid;
  min-width: 0;
  gap: 12px;
}

.pricing-markup-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.pricing-markup-control-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pricing-markup-control-label {
  color: #606266;
  font-size: 13px;
  white-space: nowrap;
}

.pricing-markup-controls .el-input {
  max-width: 340px;
  width: 100%;
}

.material-type-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.material-type-cell small {
  color: #909399;
  font-size: 12px;
  line-height: 1.2;
}

.process-master-grid {
  display: grid;
  min-width: 0;
  gap: 12px;
}

.process-dialog-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 16px;
}

.process-dialog-wide {
  grid-column: 1 / -1;
}

.unit-input {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 8px;
}

.unit-input-control {
  flex: 1;
  min-width: 0;
}

.unit-label {
  flex: 0 0 52px;
  color: #606266;
  font-size: 13px;
  text-align: left;
  white-space: nowrap;
}

.unit-label.wide {
  flex-basis: 64px;
}

.form-helper-text {
  grid-column: 1 / -1;
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  line-height: 1.4;
}

.dialog-number-input,
.dialog-select {
  width: 100%;
}

.mes-schedule-panel {
  display: flex;
  flex-direction: column;
  gap: var(--erp-space-4);
}

.mes-schedule-alert {
  margin: 0;
}

.mes-schedule-form {
  max-width: 100%;
}

.mes-schedule-form :deep(.el-input-number) {
  width: 180px;
  max-width: calc(100vw - 220px);
}

.mes-schedule-unit {
  margin-left: var(--erp-space-2);
  color: var(--erp-text-secondary);
}

.mes-schedule-status {
  width: 100%;
}

.mes-schedule-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--erp-space-2);
}

@media (max-width: 900px) {
  .material-toolbar {
    justify-content: flex-start;
  }

  .material-filter-bar {
    justify-content: flex-start;
  }

  .pricing-markup-controls,
  .pricing-markup-control-group {
    align-items: flex-start;
    flex-direction: column;
  }

  .pricing-markup-controls .el-input {
    max-width: 100%;
  }

  .process-dialog-grid {
    display: block;
  }

  .mes-schedule-form :deep(.el-form-item) {
    display: block;
  }

  .mes-schedule-form :deep(.el-form-item__label) {
    width: auto !important;
    justify-content: flex-start;
  }

  .mes-schedule-form :deep(.el-form-item__content) {
    margin-left: 0 !important;
  }

  .mes-schedule-form :deep(.el-input-number) {
    width: calc(100vw - 120px);
    max-width: 100%;
  }

  .mes-schedule-footer {
    flex-direction: column-reverse;
  }

  .mes-schedule-footer .el-button {
    width: 100%;
    margin-left: 0;
  }
}
</style>
