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
        <el-button v-permission="'engineer:dict:fee-manage'" v-if="activeTab === 'markupFees'" type="primary" @click="openMarkupFeeDialog()">新增费用类</el-button>
        <el-button v-permission="'engineer:dict:fee-manage'" v-else-if="activeTab === 'assemblyMarkupFees'" type="primary" @click="openAssemblyMarkupFeeDialog()">新增组装件费用类</el-button>
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
            <el-table :data="filteredMaterials" border stripe height="560" v-loading="loading">
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
              <el-table :data="processCategories" border stripe height="560" v-loading="loading">
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
            </div>
          </el-tab-pane>

          <el-tab-pane label="具体工艺" name="processDetails">
            <div class="process-master-grid">
              <el-table :data="processDetails" border stripe height="560" v-loading="loading">
                <el-table-column prop="sortNo" label="序号" width="80" align="center" fixed />
                <el-table-column prop="detailName" label="具体工艺" min-width="160" fixed show-overflow-tooltip />
                <el-table-column prop="processCode" label="大类编码" width="150" show-overflow-tooltip />
                <el-table-column prop="categoryName" label="大类名称" min-width="150" show-overflow-tooltip />
                <el-table-column label="大类小时价格" width="150" align="right">
                  <template #default="scope">{{ formatHourlyRate(scope.row.hourlyRate) }}</template>
                </el-table-column>
                <el-table-column prop="detailRemarks" label="具体备注" min-width="200" show-overflow-tooltip />
                <el-table-column label="状态" width="90" align="center">
                  <template #default="scope">
                    <el-tag :type="scope.row.enabled ? 'success' : 'info'" size="small">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="120" align="center" fixed="right">
                  <template #default="scope">
                    <div class="erp-table-actions">
                      <el-button v-permission="'engineer:dict:process-manage'" type="primary" size="small" @click="openProcessDetailDialog(scope.row)">编辑</el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>

          <el-tab-pane label="百分比费用类" name="markupFees">
            <el-table :data="markupFees" border stripe height="560" v-loading="loading">
              <el-table-column prop="sortNo" label="序号" width="80" align="center" fixed />
              <el-table-column prop="feeName" label="费用名称" min-width="150" fixed show-overflow-tooltip />
              <el-table-column label="费率" width="120" align="right">
                <template #default="scope">{{ Number(scope.row.ratePercent || 0).toFixed(2) }}%</template>
              </el-table-column>
              <el-table-column label="启用" width="90" align="center">
                <template #default="scope">
                  <el-tag :type="scope.row.enabled ? 'success' : 'info'" size="small">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="160" align="center" fixed="right">
                <template #default="scope">
                  <div class="erp-table-actions">
                    <el-button v-permission="'engineer:dict:fee-manage'" type="primary" size="small" :disabled="isMarkupFeeDeletePending(scope.row)" @click="openMarkupFeeDialog(scope.row)">编辑</el-button>
                    <el-button v-permission="'engineer:dict:fee-manage'" type="danger" size="small" plain class="erp-danger-action" :loading="isMarkupFeeDeletePending(scope.row)" :disabled="isMarkupFeeDeletePending(scope.row)" @click="deleteMarkupFee(scope.row)">删除</el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="组装件百分比费用类" name="assemblyMarkupFees">
            <el-table :data="assemblyMarkupFees" border stripe height="560" v-loading="loading">
              <el-table-column prop="sortNo" label="序号" width="80" align="center" fixed />
              <el-table-column prop="feeName" label="费用名称" min-width="150" fixed show-overflow-tooltip />
              <el-table-column label="费率" width="120" align="right">
                <template #default="scope">{{ Number(scope.row.ratePercent || 0).toFixed(2) }}%</template>
              </el-table-column>
              <el-table-column label="启用" width="90" align="center">
                <template #default="scope">
                  <el-tag :type="scope.row.enabled ? 'success' : 'info'" size="small">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="160" align="center" fixed="right">
                <template #default="scope">
                  <div class="erp-table-actions">
                    <el-button v-permission="'engineer:dict:fee-manage'" type="primary" size="small" :disabled="isAssemblyMarkupFeeDeletePending(scope.row)" @click="openAssemblyMarkupFeeDialog(scope.row)">编辑</el-button>
                    <el-button v-permission="'engineer:dict:fee-manage'" type="danger" size="small" plain class="erp-danger-action" :loading="isAssemblyMarkupFeeDeletePending(scope.row)" :disabled="isAssemblyMarkupFeeDeletePending(scope.row)" @click="deleteAssemblyMarkupFee(scope.row)">删除</el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
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
            <el-option v-for="category in processCategories" :key="category.id" :label="categoryLabel(category)" :value="category.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="具体工艺" required>
          <el-input v-model="processDetailForm.detailName" readonly placeholder="例如：精细包装" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="processDetailForm.enabled" />
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

    <el-dialog v-model="markupFeeDialogVisible" :title="markupFeeForm.id ? '编辑费用类' : '新增费用类'" width="520px">
      <el-form :model="markupFeeForm" label-width="100px" class="erp-dialog-form">
        <el-form-item label="序号">
          <el-input-number v-model="markupFeeForm.sortNo" :min="0" controls-position="right" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="费用名称" required>
          <el-input v-model="markupFeeForm.feeName" placeholder="例如：运输费、管理费、利润、税费" />
        </el-form-item>
        <el-form-item label="费率(%)" required>
          <el-input-number v-model="markupFeeForm.ratePercent" :precision="2" :min="0" controls-position="right" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="markupFeeForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="markupFeeDialogVisible = false">取消</el-button>
          <el-button v-permission="'engineer:dict:fee-manage'" type="primary" :loading="isPending(dictActionKeys.saveMarkupFeeDialog)" :disabled="isPending(dictActionKeys.saveMarkupFeeDialog)" @click="saveMarkupFee">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="assemblyMarkupFeeDialogVisible" :title="assemblyMarkupFeeForm.id ? '编辑组装件费用类' : '新增组装件费用类'" width="520px">
      <el-form :model="assemblyMarkupFeeForm" label-width="100px" class="erp-dialog-form">
        <el-form-item label="序号">
          <el-input-number v-model="assemblyMarkupFeeForm.sortNo" :min="0" controls-position="right" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="费用名称" required>
          <el-input v-model="assemblyMarkupFeeForm.feeName" placeholder="例如：组装管理费、组装利润、组装税费" />
        </el-form-item>
        <el-form-item label="费率(%)" required>
          <el-input-number v-model="assemblyMarkupFeeForm.ratePercent" :precision="2" :min="0" controls-position="right" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="assemblyMarkupFeeForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="assemblyMarkupFeeDialogVisible = false">取消</el-button>
          <el-button v-permission="'engineer:dict:fee-manage'" type="primary" :loading="isPending(dictActionKeys.saveAssemblyMarkupFeeDialog)" :disabled="isPending(dictActionKeys.saveAssemblyMarkupFeeDialog)" @click="saveAssemblyMarkupFee">保存</el-button>
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
        <el-form label-width="150px" class="mes-schedule-form">
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
import { computed, reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteAssemblyMarkupFee as deleteAssemblyMarkupFeeRequest,
  deleteExtraFeeItem,
  deleteMarkupFee as deleteMarkupFeeRequest,
  getMesMaterialSyncSchedule,
  getMesProcessSyncSchedule,
  saveAssemblyMarkupFee as saveAssemblyMarkupFeeRequest,
  saveExtraFeeItem,
  saveMarkupFee as saveMarkupFeeRequest,
  saveMaterial as saveMaterialRequest,
  saveMesMaterialSyncSchedule,
  saveMesProcessSyncSchedule,
  saveProcessDetail as saveProcessDetailRequest,
  syncMesMaterials as syncMesMaterialsRequest,
  syncMesProcessTypes as syncMesProcessTypesRequest
} from '../services/dictApi'
import { useDictionaryStore } from '../stores/dictionaryStore'
import { usePendingAction } from '../composables/usePendingAction'

const dictionaryStore = useDictionaryStore()
const activeTab = ref('materials')
const materialTypes = computed(() => dictionaryStore.materialTypes)
const materials = computed(() => dictionaryStore.materials)
const processCategories = computed(() => dictionaryStore.processCategories)
const processDetails = computed(() => dictionaryStore.processDetails)
const markupFees = computed(() => dictionaryStore.markupFees)
const assemblyMarkupFees = computed(() => dictionaryStore.assemblyMarkupFees)
const extraFeeItems = computed(() => dictionaryStore.extraFeeItems)
const materialKeyword = ref('')
const filteredMaterials = computed(() => {
  const keyword = materialKeyword.value.trim().toLowerCase()
  if (!keyword) return materials.value
  return materials.value.filter((item) => [
    item.materialCode,
    item.materialName,
    item.materialTypeCode,
    item.materialTypeName,
    item.materialCategory,
    item.remarks
  ].some(value => String(value || '').toLowerCase().includes(keyword)))
})
const loading = ref(false)
const { isPending, runAction } = usePendingAction()

const dictActionKeys = {
  saveMaterialDialog: 'engineer:dict:material-save-dialog',
  syncMesMaterials: 'engineer:dict:material-sync-mes',
  syncMesProcessTypes: 'engineer:dict:process-sync-mes',
  saveMesSchedule: 'engineer:dict:mes-master-data-schedule-save',
  saveProcessDetailDialog: 'engineer:dict:process-detail-save-dialog',
  saveMarkupFeeDialog: 'engineer:dict:markup-fee-save-dialog',
  saveAssemblyMarkupFeeDialog: 'engineer:dict:assembly-markup-fee-save-dialog',
  saveExtraFeeDialog: 'engineer:dict:extra-fee-save-dialog',
  deleteMarkupFee: (feeId) => `engineer:dict:markup-fee-delete:${feeId || 'none'}`,
  deleteAssemblyMarkupFee: (feeId) => `engineer:dict:assembly-markup-fee-delete:${feeId || 'none'}`,
  deleteExtraFee: (feeId) => `engineer:dict:extra-fee-delete:${feeId || 'none'}`
}

const isMarkupFeeDeletePending = (row) => isPending(dictActionKeys.deleteMarkupFee(row?.id))
const isAssemblyMarkupFeeDeletePending = (row) => isPending(dictActionKeys.deleteAssemblyMarkupFee(row?.id))
const isExtraFeeDeletePending = (row) => isPending(dictActionKeys.deleteExtraFee(row?.id))

const materialDialogVisible = ref(false)
const processDetailDialogVisible = ref(false)
const markupFeeDialogVisible = ref(false)
const assemblyMarkupFeeDialogVisible = ref(false)
const extraFeeDialogVisible = ref(false)
const scheduleDialogVisible = ref(false)
const scheduleLoading = ref(false)
const scheduleTarget = ref('MES_MATERIAL')

const materialForm = reactive(createEmptyMaterial())
const processDetailForm = reactive(createEmptyProcessDetail())
const markupFeeForm = reactive(createEmptyMarkupFee())
const assemblyMarkupFeeForm = reactive(createEmptyMarkupFee())
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
    detailRemarks: '',
    enabled: true
  }
}

function createEmptyMarkupFee() {
  return {
    id: null,
    sortNo: 0,
    feeName: '',
    ratePercent: 0,
    enabled: true
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

const loadAll = async () => {
  loading.value = true
  try {
    await dictionaryStore.loadBaseDicts({ includeMarkupFees: true, includeAllExtraFees: true, force: true })
  } catch (error) {
    console.error(error)
    ElMessage.error('材料或工艺数据加载失败，请检查后端服务。')
  } finally {
    loading.value = false
  }
}

const openMaterialDialog = (row) => {
  Object.assign(materialForm, createEmptyMaterial(), row || {})
  materialForm.enabled = materialForm.enabled !== false
  materialDialogVisible.value = true
}

const openProcessDetailDialog = (row) => {
  Object.assign(processDetailForm, createEmptyProcessDetail(), row || {})
  processDetailForm.enabled = processDetailForm.enabled !== false
  processDetailDialogVisible.value = true
}

const openMarkupFeeDialog = (row) => {
  Object.assign(markupFeeForm, createEmptyMarkupFee(), row || {})
  markupFeeDialogVisible.value = true
}

const openAssemblyMarkupFeeDialog = (row) => {
  Object.assign(assemblyMarkupFeeForm, createEmptyMarkupFee(), row || {})
  assemblyMarkupFeeDialogVisible.value = true
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
      await dictionaryStore.reloadAfterMutation()
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
      await dictionaryStore.reloadAfterMutation()
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
      await dictionaryStore.reloadAfterMutation()
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
      const response = await saveProcessDetailRequest(processDetailForm)
      ElMessage.success(response.message || '保存成功')
      processDetailDialogVisible.value = false
      await dictionaryStore.reloadAfterMutation()
    } catch (error) {
      console.error(error)
      ElMessage.error('具体工艺保存失败。')
    }
  })
}

const saveMarkupFee = async () => {
  if (!markupFeeForm.feeName) return ElMessage.warning('请填写费用名称。')
  return runAction(dictActionKeys.saveMarkupFeeDialog, async () => {
    try {
      const response = await saveMarkupFeeRequest(markupFeeForm)
      ElMessage.success(response.message || '保存成功')
      markupFeeDialogVisible.value = false
      await dictionaryStore.reloadAfterMutation()
    } catch (error) {
      console.error(error)
      ElMessage.error('费用类保存失败。')
    }
  })
}

const saveAssemblyMarkupFee = async () => {
  if (!assemblyMarkupFeeForm.feeName) return ElMessage.warning('请填写费用名称。')
  return runAction(dictActionKeys.saveAssemblyMarkupFeeDialog, async () => {
    try {
      const response = await saveAssemblyMarkupFeeRequest(assemblyMarkupFeeForm)
      ElMessage.success(response.message || '保存成功')
      assemblyMarkupFeeDialogVisible.value = false
      await dictionaryStore.reloadAfterMutation()
    } catch (error) {
      console.error(error)
      ElMessage.error('组装件费用类保存失败。')
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
      await dictionaryStore.reloadAfterMutation()
    } catch (error) {
      console.error(error)
      ElMessage.error('非工时费用工艺保存失败。')
    }
  })
}

const deleteMarkupFee = async (row) => {
  return runAction(dictActionKeys.deleteMarkupFee(row.id), async () => {
    await ElMessageBox.confirm(`确认删除费用类「${row.feeName}」？`, '删除确认', { type: 'warning' })
    await deleteMarkupFeeRequest(row.id)
    ElMessage.success('费用类已删除')
    await dictionaryStore.reloadAfterMutation()
  })
}

const deleteAssemblyMarkupFee = async (row) => {
  return runAction(dictActionKeys.deleteAssemblyMarkupFee(row.id), async () => {
    await ElMessageBox.confirm(`确认删除组装件费用类「${row.feeName}」？`, '删除确认', { type: 'warning' })
    await deleteAssemblyMarkupFeeRequest(row.id)
    ElMessage.success('组装件费用类已删除')
    await dictionaryStore.reloadAfterMutation()
  })
}

const deleteExtraFee = async (row) => {
  return runAction(dictActionKeys.deleteExtraFee(row.id), async () => {
    await ElMessageBox.confirm(`确认删除非工时费用工艺「${row.feeName}」？`, '删除确认', { type: 'warning' })
    await deleteExtraFeeItem(row.id)
    ElMessage.success('非工时费用工艺已删除')
    await dictionaryStore.reloadAfterMutation()
  })
}

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
