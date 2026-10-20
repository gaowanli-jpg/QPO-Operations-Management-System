<template>
  <div class="erp-page system-org-page">
    <header class="erp-page-header">
      <div>
        <h1 class="erp-page-title">组织档案</h1>
        <p class="erp-page-description">维护部门和岗位这两类用户身份属性，不参与订单数据范围隔离。</p>
      </div>
    </header>

    <section class="erp-surface system-org-workspace">
      <el-tabs v-model="activeTab" class="system-org-tabs">
        <el-tab-pane label="部门管理" name="departments">
          <div class="erp-toolbar system-org-toolbar">
            <div class="erp-toolbar-group">
              <strong>部门管理</strong>
              <span class="system-org-note">树形展示组织层级，用于用户档案归属和审计展示。</span>
            </div>
            <div class="erp-toolbar-group">
              <el-button size="small" @click="loadDepartments">刷新</el-button>
              <el-button v-permission="'system:dept:manage'" type="primary" @click="openDepartmentDialog()">新增部门</el-button>
            </div>
          </div>

          <div class="erp-table-surface system-org-table">
            <div class="erp-table-body">
              <el-table
                :data="departmentTree"
                row-key="id"
                border
                default-expand-all
                v-loading="departmentLoading"
                :tree-props="{ children: 'children' }"
              >
                <el-table-column prop="deptCode" label="部门编码" min-width="140" show-overflow-tooltip />
                <el-table-column prop="deptName" label="部门名称" min-width="180" show-overflow-tooltip />
                <el-table-column label="负责人ID" width="110">
                  <template #default="{ row }">{{ row.leaderUserId || '-' }}</template>
                </el-table-column>
                <el-table-column prop="sortOrder" label="排序" width="90" />
                <el-table-column label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="280" fixed="right">
                  <template #default="{ row }">
                    <div class="erp-table-actions system-org-actions">
                      <el-button v-permission="'system:dept:manage'" size="small" :disabled="isDepartmentRowActionPending(row)" @click="openDepartmentDialog(row)">编辑</el-button>
                      <el-button v-permission="'system:dept:manage'" size="small" :disabled="isDepartmentRowActionPending(row)" @click="openDepartmentDialog({ parentId: row.id })">新增下级</el-button>
                      <el-button
                        v-permission="'system:dept:manage'"
                        size="small"
                        :type="row.enabled ? 'warning' : 'success'"
                        :loading="isDepartmentStatusPending(row)"
                        :disabled="isDepartmentRowActionPending(row)"
                        @click="toggleDepartmentStatus(row)"
                      >
                        {{ row.enabled ? '禁用' : '启用' }}
                      </el-button>
                      <el-button
                        v-permission="'system:dept:manage'"
                        size="small"
                        type="danger"
                        plain
                        class="erp-danger-action"
                        :loading="isDepartmentDeletePending(row)"
                        :disabled="isDepartmentRowActionPending(row)"
                        @click="deleteDepartment(row)"
                      >
                        删除
                      </el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="岗位管理" name="posts">
          <div class="erp-toolbar system-org-toolbar">
            <div class="erp-toolbar-group">
              <strong>岗位管理</strong>
              <span class="system-org-note">岗位是用户身份属性，不等同于角色权限。</span>
            </div>
            <div class="erp-toolbar-group">
              <el-button size="small" @click="loadPosts">刷新</el-button>
              <el-button v-permission="'system:post:manage'" type="primary" @click="openPostDialog()">新增岗位</el-button>
            </div>
          </div>

          <div class="erp-table-surface system-org-table">
            <div class="erp-table-body">
              <el-table :data="postRows" border v-loading="postLoading">
                <el-table-column prop="postCode" label="岗位编码" min-width="150" show-overflow-tooltip />
                <el-table-column prop="postName" label="岗位名称" min-width="180" show-overflow-tooltip />
                <el-table-column prop="sortOrder" label="排序" width="90" />
                <el-table-column label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="220" fixed="right">
                  <template #default="{ row }">
                    <div class="erp-table-actions system-org-actions">
                      <el-button v-permission="'system:post:manage'" size="small" :disabled="isPostRowActionPending(row)" @click="openPostDialog(row)">编辑</el-button>
                      <el-button
                        v-permission="'system:post:manage'"
                        size="small"
                        :type="row.enabled ? 'warning' : 'success'"
                        :loading="isPostStatusPending(row)"
                        :disabled="isPostRowActionPending(row)"
                        @click="togglePostStatus(row)"
                      >
                        {{ row.enabled ? '禁用' : '启用' }}
                      </el-button>
                      <el-button
                        v-permission="'system:post:manage'"
                        size="small"
                        type="danger"
                        plain
                        class="erp-danger-action"
                        :loading="isPostDeletePending(row)"
                        :disabled="isPostRowActionPending(row)"
                        @click="deletePost(row)"
                      >
                        删除
                      </el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog v-model="departmentDialogVisible" :title="departmentForm.id ? '编辑部门' : '新增部门'" width="520px">
      <el-form :model="departmentForm" label-width="96px" class="erp-dialog-form">
        <el-form-item label="上级部门">
          <el-select v-model="departmentForm.parentId" filterable style="width: 100%">
            <el-option label="无（顶级部门）" :value="0" />
            <el-option
              v-for="dept in selectableDepartmentParents"
              :key="dept.id"
              :label="dept.label"
              :value="dept.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="部门编码">
          <el-input v-model.trim="departmentForm.deptCode" placeholder="如 MARKET" />
        </el-form-item>
        <el-form-item label="部门名称">
          <el-input v-model.trim="departmentForm.deptName" placeholder="如 市场部" />
        </el-form-item>
        <el-form-item label="负责人ID">
          <el-input v-model.trim="departmentForm.leaderUserId" placeholder="可选，填写用户 ID" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="departmentForm.sortOrder" :min="0" :step="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="departmentForm.enabled" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="departmentDialogVisible = false">取消</el-button>
          <el-button v-permission="'system:dept:manage'" type="primary" :loading="isPending(orgActionKeys.saveDepartmentDialog)" :disabled="isPending(orgActionKeys.saveDepartmentDialog)" @click="submitDepartment">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="postDialogVisible" :title="postForm.id ? '编辑岗位' : '新增岗位'" width="480px">
      <el-form :model="postForm" label-width="96px" class="erp-dialog-form">
        <el-form-item label="岗位编码">
          <el-input v-model.trim="postForm.postCode" placeholder="如 ENGINEER" />
        </el-form-item>
        <el-form-item label="岗位名称">
          <el-input v-model.trim="postForm.postName" placeholder="如 工程核价员" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="postForm.sortOrder" :min="0" :step="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="postForm.enabled" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="postDialogVisible = false">取消</el-button>
          <el-button v-permission="'system:post:manage'" type="primary" :loading="isPending(orgActionKeys.savePostDialog)" :disabled="isPending(orgActionKeys.savePostDialog)" @click="submitPost">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteDepartment as deleteDepartmentRequest,
  deletePost as deletePostRequest,
  listDepartmentsTree,
  listPosts,
  saveDepartment,
  savePost,
  updateDepartmentStatus,
  updatePostStatus
} from '../services/adminApi'
import { usePendingAction } from '../composables/usePendingAction'

const activeTab = ref('departments')
const departmentTree = ref([])
const postRows = ref([])
const departmentLoading = ref(false)
const postLoading = ref(false)
const departmentDialogVisible = ref(false)
const postDialogVisible = ref(false)
const { isPending, runAction } = usePendingAction()

const orgActionKeys = {
  saveDepartmentDialog: 'system:org:department-save-dialog',
  savePostDialog: 'system:org:post-save-dialog',
  departmentStatus: (deptId) => `system:org:department-status:${deptId || 'none'}`,
  departmentDelete: (deptId) => `system:org:department-delete:${deptId || 'none'}`,
  postStatus: (postId) => `system:org:post-status:${postId || 'none'}`,
  postDelete: (postId) => `system:org:post-delete:${postId || 'none'}`
}

const isDepartmentStatusPending = (row) => isPending(orgActionKeys.departmentStatus(row?.id))
const isDepartmentDeletePending = (row) => isPending(orgActionKeys.departmentDelete(row?.id))
const isDepartmentRowActionPending = (row) => isDepartmentStatusPending(row) || isDepartmentDeletePending(row)
const isPostStatusPending = (row) => isPending(orgActionKeys.postStatus(row?.id))
const isPostDeletePending = (row) => isPending(orgActionKeys.postDelete(row?.id))
const isPostRowActionPending = (row) => isPostStatusPending(row) || isPostDeletePending(row)

const departmentForm = reactive({
  id: null,
  parentId: 0,
  deptCode: '',
  deptName: '',
  leaderUserId: '',
  sortOrder: 0,
  enabled: true
})

const postForm = reactive({
  id: null,
  postCode: '',
  postName: '',
  sortOrder: 0,
  enabled: true
})

const requestErrorMessage = (error, fallback) => error?.response?.data?.message || error?.message || fallback

const loadDepartments = async () => {
  departmentLoading.value = true
  try {
    departmentTree.value = await listDepartmentsTree() || []
  } catch (error) {
    departmentTree.value = []
    ElMessage.error(requestErrorMessage(error, '部门列表加载失败'))
  } finally {
    departmentLoading.value = false
  }
}

const loadPosts = async () => {
  postLoading.value = true
  try {
    postRows.value = await listPosts() || []
  } catch (error) {
    postRows.value = []
    ElMessage.error(requestErrorMessage(error, '岗位列表加载失败'))
  } finally {
    postLoading.value = false
  }
}

const flattenDepartments = (nodes = [], level = 0, rows = []) => {
  for (const node of nodes || []) {
    rows.push({
      ...node,
      label: `${'　'.repeat(level)}${node.deptName || node.deptCode || node.id}`
    })
    flattenDepartments(node.children || [], level + 1, rows)
  }
  return rows
}

const collectDescendantIds = (nodes = [], targetId, active = false, ids = new Set()) => {
  for (const node of nodes || []) {
    const nextActive = active || node.id === targetId
    if (nextActive && node.id !== targetId) {
      ids.add(node.id)
    }
    collectDescendantIds(node.children || [], targetId, nextActive, ids)
  }
  return ids
}

const selectableDepartmentParents = computed(() => {
  const blockedIds = collectDescendantIds(departmentTree.value, departmentForm.id)
  if (departmentForm.id) {
    blockedIds.add(departmentForm.id)
  }
  return flattenDepartments(departmentTree.value).filter(dept => !blockedIds.has(dept.id))
})

const resetDepartmentForm = () => {
  departmentForm.id = null
  departmentForm.parentId = 0
  departmentForm.deptCode = ''
  departmentForm.deptName = ''
  departmentForm.leaderUserId = ''
  departmentForm.sortOrder = 0
  departmentForm.enabled = true
}

const openDepartmentDialog = (row = null) => {
  resetDepartmentForm()
  if (row?.id) {
    departmentForm.id = row.id
    departmentForm.parentId = row.parentId || 0
    departmentForm.deptCode = row.deptCode || ''
    departmentForm.deptName = row.deptName || ''
    departmentForm.leaderUserId = row.leaderUserId || ''
    departmentForm.sortOrder = row.sortOrder || 0
    departmentForm.enabled = row.enabled !== false
  } else if (row?.parentId) {
    departmentForm.parentId = row.parentId
  }
  departmentDialogVisible.value = true
}

const normalizeOptionalNumber = (value) => {
  const text = String(value ?? '').trim()
  if (!text) return null
  const number = Number(text)
  return Number.isFinite(number) ? number : null
}

const submitDepartment = async () => {
  if (!departmentForm.deptCode || !departmentForm.deptName) {
    ElMessage.warning('请填写部门编码和部门名称')
    return
  }
  return runAction(orgActionKeys.saveDepartmentDialog, async () => {
    const response = await saveDepartment({
      ...departmentForm,
      parentId: departmentForm.parentId || 0,
      leaderUserId: normalizeOptionalNumber(departmentForm.leaderUserId),
      sortOrder: departmentForm.sortOrder || 0
    })
    ElMessage.success(response?.message || '部门已保存')
    departmentDialogVisible.value = false
    await loadDepartments()
  })
}

const toggleDepartmentStatus = async (row) => {
  const nextEnabled = !row.enabled
  return runAction(orgActionKeys.departmentStatus(row.id), async () => {
    await ElMessageBox.confirm(
      `确认${nextEnabled ? '启用' : '禁用'}部门 ${row.deptName || row.deptCode}？`,
      '确认操作',
      {
        type: 'warning',
        confirmButtonText: nextEnabled ? '启用' : '禁用',
        cancelButtonText: '取消'
      }
    )
    await updateDepartmentStatus(row.id, nextEnabled)
    ElMessage.success('部门状态已更新')
    await loadDepartments()
  })
}

const deleteDepartment = async (row) => {
  return runAction(orgActionKeys.departmentDelete(row.id), async () => {
    await ElMessageBox.confirm(
      `确认删除部门 ${row.deptName || row.deptCode}？删除后无法作为新用户归属使用。`,
      '删除部门确认',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消'
      }
    )
    await deleteDepartmentRequest(row.id)
    ElMessage.success('部门已删除')
    await loadDepartments()
  })
}

const resetPostForm = () => {
  postForm.id = null
  postForm.postCode = ''
  postForm.postName = ''
  postForm.sortOrder = 0
  postForm.enabled = true
}

const openPostDialog = (row = null) => {
  resetPostForm()
  if (row?.id) {
    postForm.id = row.id
    postForm.postCode = row.postCode || ''
    postForm.postName = row.postName || ''
    postForm.sortOrder = row.sortOrder || 0
    postForm.enabled = row.enabled !== false
  }
  postDialogVisible.value = true
}

const submitPost = async () => {
  if (!postForm.postCode || !postForm.postName) {
    ElMessage.warning('请填写岗位编码和岗位名称')
    return
  }
  return runAction(orgActionKeys.savePostDialog, async () => {
    const response = await savePost({
      ...postForm,
      sortOrder: postForm.sortOrder || 0
    })
    ElMessage.success(response?.message || '岗位已保存')
    postDialogVisible.value = false
    await loadPosts()
  })
}

const togglePostStatus = async (row) => {
  const nextEnabled = !row.enabled
  return runAction(orgActionKeys.postStatus(row.id), async () => {
    await ElMessageBox.confirm(
      `确认${nextEnabled ? '启用' : '禁用'}岗位 ${row.postName || row.postCode}？`,
      '确认操作',
      {
        type: 'warning',
        confirmButtonText: nextEnabled ? '启用' : '禁用',
        cancelButtonText: '取消'
      }
    )
    await updatePostStatus(row.id, nextEnabled)
    ElMessage.success('岗位状态已更新')
    await loadPosts()
  })
}

const deletePost = async (row) => {
  return runAction(orgActionKeys.postDelete(row.id), async () => {
    await ElMessageBox.confirm(
      `确认删除岗位 ${row.postName || row.postCode}？删除后无法作为新用户岗位使用。`,
      '删除岗位确认',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消'
      }
    )
    await deletePostRequest(row.id)
    ElMessage.success('岗位已删除')
    await loadPosts()
  })
}

onMounted(async () => {
  await Promise.all([
    loadDepartments(),
    loadPosts()
  ])
})
</script>

<style scoped>
.system-org-workspace {
  padding: var(--erp-space-4);
}

.system-org-tabs :deep(.el-tabs__header) {
  margin-bottom: var(--erp-space-4);
}

.system-org-toolbar {
  margin-bottom: var(--erp-space-3);
}

.system-org-note {
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
}

.system-org-table .erp-table-body {
  padding: var(--erp-space-3);
}

.system-org-actions {
  justify-content: flex-start;
}

.system-org-actions .el-button {
  min-width: 54px;
}
</style>
