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
        <el-button v-permission="'engineer:dict:material-manage'" v-if="activeTab === 'materials'" type="primary" @click="openMaterialDialog()">新增材料</el-button>
        <el-button v-permission="'engineer:dict:process-manage'" v-else-if="activeTab === 'processCategories'" type="primary" @click="openProcessCategoryDialog()">新增工艺大类</el-button>
        <el-button v-permission="'engineer:dict:process-manage'" v-else-if="activeTab === 'processDetails'" type="primary" @click="openProcessDetailDialog()">新增具体工艺</el-button>
        <el-button v-permission="'engineer:dict:fee-manage'" v-else-if="activeTab === 'markupFees'" type="primary" @click="openMarkupFeeDialog()">新增费用类</el-button>
        <el-button v-permission="'engineer:dict:fee-manage'" v-else-if="activeTab === 'assemblyMarkupFees'" type="primary" @click="openAssemblyMarkupFeeDialog()">新增组装件费用类</el-button>
        <el-button v-permission="'engineer:dict:fee-manage'" v-else type="primary" @click="openExtraFeeDialog()">新增非工时费用工艺</el-button>
      </div>
    </section>

    <section class="erp-table-surface material-table-surface">
      <div class="erp-table-body">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="材料费用" name="materials">
            <el-table :data="materials" border stripe height="560" v-loading="loading">
              <el-table-column prop="materialName" label="材料" width="120" fixed show-overflow-tooltip />
              <el-table-column prop="materialType" label="材质" width="110" show-overflow-tooltip />
              <el-table-column prop="remarks" label="备注" min-width="150" show-overflow-tooltip />
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
              <el-table-column label="操作" width="150" align="center" fixed="right">
                <template #default="scope">
                  <div class="erp-table-actions">
                    <el-button v-permission="'engineer:dict:material-manage'" type="primary" size="small" :disabled="isMaterialDeletePending(scope.row)" @click="openMaterialDialog(scope.row)">编辑</el-button>
                    <el-button v-permission="'engineer:dict:material-manage'" type="danger" size="small" plain class="erp-danger-action" :loading="isMaterialDeletePending(scope.row)" :disabled="isMaterialDeletePending(scope.row)" @click="deleteMaterial(scope.row)">删除</el-button>
                  </div>
                </template>
              </el-table-column>
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
                <el-table-column label="状态" width="90" align="center">
                  <template #default="scope">
                    <el-tag :type="scope.row.enabled ? 'success' : 'info'" size="small">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="remarks" label="备注" min-width="180" show-overflow-tooltip />
                <el-table-column label="操作" width="120" align="center" fixed="right">
                  <template #default="scope">
                    <div class="erp-table-actions">
                      <el-button v-permission="'engineer:dict:process-manage'" type="primary" size="small" @click="openProcessCategoryDialog(scope.row)">编辑</el-button>
                    </div>
                  </template>
                </el-table-column>
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

    <el-dialog v-model="materialDialogVisible" :title="materialForm.id ? '编辑材料' : '新增材料'" width="760px">
      <el-form :model="materialForm" label-width="140px" class="erp-dialog-form">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="材料" required>
              <el-input v-model="materialForm.materialName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="材质">
              <el-input v-model="materialForm.materialType" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="materialForm.remarks" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="板材密度">
              <div class="unit-input">
                <el-input-number v-model="materialForm.plateDensity" :precision="4" :min="0" controls-position="right" class="unit-input-control" />
                <span class="unit-label">kg/m³</span>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="板材单价">
              <div class="unit-input">
                <el-input-number v-model="materialForm.platePrice" :precision="2" :min="0" controls-position="right" class="unit-input-control" />
                <span class="unit-label">元/kg</span>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="棒材密度">
              <div class="unit-input">
                <el-input-number v-model="materialForm.barDensity" :precision="4" :min="0" controls-position="right" class="unit-input-control" />
                <span class="unit-label">kg/m³</span>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="棒材单价">
              <div class="unit-input">
                <el-input-number v-model="materialForm.barPrice" :precision="2" :min="0" controls-position="right" class="unit-input-control" />
                <span class="unit-label">元/kg</span>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="管材密度">
              <div class="unit-input">
                <el-input-number v-model="materialForm.tubeDensity" :precision="4" :min="0" controls-position="right" class="unit-input-control" />
                <span class="unit-label">kg/m³</span>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="管材单价">
              <div class="unit-input">
                <el-input-number v-model="materialForm.tubePrice" :precision="2" :min="0" controls-position="right" class="unit-input-control" />
                <span class="unit-label">元/kg</span>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="materialDialogVisible = false">取消</el-button>
          <el-button v-permission="'engineer:dict:material-manage'" type="primary" :loading="isPending(dictActionKeys.saveMaterialDialog)" :disabled="isPending(dictActionKeys.saveMaterialDialog)" @click="saveMaterial">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="processCategoryDialogVisible" :title="processCategoryForm.id ? '编辑工艺大类' : '新增工艺大类'" width="720px">
      <el-form :model="processCategoryForm" label-width="120px" class="erp-dialog-form process-dialog-grid">
        <el-form-item label="序号">
          <el-input-number v-model="processCategoryForm.sortNo" :min="0" controls-position="right" class="dialog-number-input" />
        </el-form-item>
        <el-form-item label="工艺编码" required>
          <el-input v-model="processCategoryForm.processCode" placeholder="例如：PACKAGING" />
        </el-form-item>
        <el-form-item label="工艺名称" required>
          <el-input v-model="processCategoryForm.categoryName" placeholder="例如：包装" />
        </el-form-item>
        <el-form-item label="小时价格" required>
          <div class="unit-input">
            <el-input-number v-model="processCategoryForm.hourlyRate" :precision="2" :min="0" controls-position="right" class="unit-input-control" />
            <span class="unit-label wide">元/小时</span>
          </div>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="processCategoryForm.sourceType" class="dialog-select">
            <el-option label="本地维护" value="LOCAL" />
            <el-option label="MES同步" value="MES" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="processCategoryForm.enabled" />
        </el-form-item>
        <el-form-item label="MES外部ID">
          <el-input v-model="processCategoryForm.mesExternalId" placeholder="MES 同步后可记录外部 ID" />
        </el-form-item>
        <el-form-item label="备注" class="process-dialog-wide">
          <el-input v-model="processCategoryForm.remarks" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="processCategoryDialogVisible = false">取消</el-button>
          <el-button v-permission="'engineer:dict:process-manage'" type="primary" :loading="isPending(dictActionKeys.saveProcessCategoryDialog)" :disabled="isPending(dictActionKeys.saveProcessCategoryDialog)" @click="saveProcessCategory">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="processDetailDialogVisible" :title="processDetailForm.id ? '编辑具体工艺' : '新增具体工艺'" width="680px">
      <el-form :model="processDetailForm" label-width="120px" class="erp-dialog-form process-dialog-grid">
        <el-form-item label="序号">
          <el-input-number v-model="processDetailForm.sortNo" :min="0" controls-position="right" class="dialog-number-input" />
        </el-form-item>
        <el-form-item label="工艺大类" required>
          <el-select v-model="processDetailForm.categoryId" filterable class="dialog-select" placeholder="选择工艺大类">
            <el-option v-for="category in processCategories" :key="category.id" :label="categoryLabel(category)" :value="category.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="具体工艺" required>
          <el-input v-model="processDetailForm.detailName" placeholder="例如：精细包装" />
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
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteAssemblyMarkupFee as deleteAssemblyMarkupFeeRequest,
  deleteExtraFeeItem,
  deleteMarkupFee as deleteMarkupFeeRequest,
  deleteMaterial as deleteMaterialRequest,
  saveAssemblyMarkupFee as saveAssemblyMarkupFeeRequest,
  saveExtraFeeItem,
  saveMarkupFee as saveMarkupFeeRequest,
  saveMaterial as saveMaterialRequest,
  saveProcessCategory as saveProcessCategoryRequest,
  saveProcessDetail as saveProcessDetailRequest
} from '../services/dictApi'
import { useDictionaryStore } from '../stores/dictionaryStore'
import { usePendingAction } from '../composables/usePendingAction'

const dictionaryStore = useDictionaryStore()
const activeTab = ref('materials')
const materials = computed(() => dictionaryStore.materials)
const processCategories = computed(() => dictionaryStore.processCategories)
const processDetails = computed(() => dictionaryStore.processDetails)
const markupFees = computed(() => dictionaryStore.markupFees)
const assemblyMarkupFees = computed(() => dictionaryStore.assemblyMarkupFees)
const extraFeeItems = computed(() => dictionaryStore.extraFeeItems)
const loading = ref(false)
const { isPending, runAction } = usePendingAction()

const dictActionKeys = {
  saveMaterialDialog: 'engineer:dict:material-save-dialog',
  saveProcessCategoryDialog: 'engineer:dict:process-category-save-dialog',
  saveProcessDetailDialog: 'engineer:dict:process-detail-save-dialog',
  saveMarkupFeeDialog: 'engineer:dict:markup-fee-save-dialog',
  saveAssemblyMarkupFeeDialog: 'engineer:dict:assembly-markup-fee-save-dialog',
  saveExtraFeeDialog: 'engineer:dict:extra-fee-save-dialog',
  deleteMaterial: (materialId) => `engineer:dict:material-delete:${materialId || 'none'}`,
  deleteMarkupFee: (feeId) => `engineer:dict:markup-fee-delete:${feeId || 'none'}`,
  deleteAssemblyMarkupFee: (feeId) => `engineer:dict:assembly-markup-fee-delete:${feeId || 'none'}`,
  deleteExtraFee: (feeId) => `engineer:dict:extra-fee-delete:${feeId || 'none'}`
}

const isMaterialDeletePending = (row) => isPending(dictActionKeys.deleteMaterial(row?.id))
const isMarkupFeeDeletePending = (row) => isPending(dictActionKeys.deleteMarkupFee(row?.id))
const isAssemblyMarkupFeeDeletePending = (row) => isPending(dictActionKeys.deleteAssemblyMarkupFee(row?.id))
const isExtraFeeDeletePending = (row) => isPending(dictActionKeys.deleteExtraFee(row?.id))

const materialDialogVisible = ref(false)
const processCategoryDialogVisible = ref(false)
const processDetailDialogVisible = ref(false)
const markupFeeDialogVisible = ref(false)
const assemblyMarkupFeeDialogVisible = ref(false)
const extraFeeDialogVisible = ref(false)

const materialForm = reactive(createEmptyMaterial())
const processCategoryForm = reactive(createEmptyProcessCategory())
const processDetailForm = reactive(createEmptyProcessDetail())
const markupFeeForm = reactive(createEmptyMarkupFee())
const assemblyMarkupFeeForm = reactive(createEmptyMarkupFee())
const extraFeeForm = reactive(createEmptyExtraFee())

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
const categoryLabel = (category) => `${category.processCode || '-'} / ${category.categoryName || '-'} / ${formatHourlyRate(category.hourlyRate)}`

function createEmptyMaterial() {
  return {
    id: null,
    materialName: '',
    materialType: '',
    remarks: '',
    plateDensity: 0,
    platePrice: 0,
    barDensity: 0,
    barPrice: 0,
    tubeDensity: 0,
    tubePrice: 0
  }
}

function createEmptyProcessCategory() {
  return {
    id: null,
    sortNo: 0,
    processCode: '',
    categoryName: '',
    hourlyRate: 0,
    remarks: '',
    sourceType: 'LOCAL',
    mesExternalId: '',
    enabled: true
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
  materialDialogVisible.value = true
}

const openProcessCategoryDialog = (row) => {
  Object.assign(processCategoryForm, createEmptyProcessCategory(), row || {})
  processCategoryForm.sourceType = processCategoryForm.sourceType || 'LOCAL'
  processCategoryForm.enabled = processCategoryForm.enabled !== false
  processCategoryDialogVisible.value = true
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

const saveMaterial = async () => {
  if (!materialForm.materialName) return ElMessage.warning('请填写材料名称。')
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

const saveProcessCategory = async () => {
  if (!processCategoryForm.processCode) return ElMessage.warning('请填写工艺编码。')
  if (!processCategoryForm.categoryName) return ElMessage.warning('请填写工艺名称。')
  return runAction(dictActionKeys.saveProcessCategoryDialog, async () => {
    try {
      const response = await saveProcessCategoryRequest(processCategoryForm)
      ElMessage.success(response.message || '保存成功')
      processCategoryDialogVisible.value = false
      await dictionaryStore.reloadAfterMutation()
    } catch (error) {
      console.error(error)
      ElMessage.error('工艺大类保存失败。')
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

const deleteMaterial = async (row) => {
  return runAction(dictActionKeys.deleteMaterial(row.id), async () => {
    await ElMessageBox.confirm(`确认删除材料「${row.materialName}」？`, '删除确认', { type: 'warning' })
    await deleteMaterialRequest(row.id)
    ElMessage.success('材料已删除')
    await dictionaryStore.reloadAfterMutation()
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

@media (max-width: 900px) {
  .material-toolbar {
    justify-content: flex-start;
  }

  .process-dialog-grid {
    display: block;
  }
}
</style>
