<template>
  <el-dialog
    v-model="visible"
    :title="`${draft.specName || '规格'}价格详情`"
    width="520px"
    destroy-on-close
  >
    <el-form label-width="110px" class="erp-dialog-form spec-detail-form">
      <el-form-item label="统一价格">
        <div class="unit-input">
          <el-input-number
            :model-value="draft.unifiedPrice"
            :min="0"
            :precision="2"
            :value-on-clear="null"
            clearable
            controls-position="right"
            class="unit-input-control"
            :disabled="readonly"
            @update:model-value="value => updateField('unifiedPrice', value)"
          />
          <span class="unit-label">元/kg</span>
        </div>
      </el-form-item>
      <el-form-item label="板材价格">
        <div class="unit-input">
          <el-input-number
            :model-value="draft.platePrice"
            :min="0"
            :precision="2"
            :value-on-clear="null"
            clearable
            controls-position="right"
            class="unit-input-control"
            :disabled="readonly"
            @update:model-value="value => updateField('platePrice', value)"
          />
          <span class="unit-label">元/kg</span>
        </div>
      </el-form-item>
      <el-form-item label="棒材价格">
        <div class="unit-input">
          <el-input-number
            :model-value="draft.barPrice"
            :min="0"
            :precision="2"
            :value-on-clear="null"
            clearable
            controls-position="right"
            class="unit-input-control"
            :disabled="readonly"
            @update:model-value="value => updateField('barPrice', value)"
          />
          <span class="unit-label">元/kg</span>
        </div>
      </el-form-item>
      <el-form-item label="管材价格">
        <div class="unit-input">
          <el-input-number
            :model-value="draft.tubePrice"
            :min="0"
            :precision="2"
            :value-on-clear="null"
            clearable
            controls-position="right"
            class="unit-input-control"
            :disabled="readonly"
            @update:model-value="value => updateField('tubePrice', value)"
          />
          <span class="unit-label">元/kg</span>
        </div>
      </el-form-item>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="板材、棒材、管材价格为空或 0 时，核价会使用统一价格兜底。"
      />
    </el-form>
    <template #footer>
      <div class="erp-dialog-footer">
        <el-button type="primary" @click="visible = false">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, watch } from 'vue'
import { normalizePriceSpec } from '../../utils/materialPriceSpecs'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  spec: { type: Object, default: () => ({}) },
  readonly: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'update:spec'])

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})

const draft = reactive(normalizePriceSpec())

const syncDraft = () => {
  Object.assign(draft, normalizePriceSpec(props.spec))
}

const updateField = (field, value) => {
  draft[field] = value === '' ? null : value
  emit('update:spec', normalizePriceSpec({ ...draft }))
}

watch(() => props.spec, syncDraft, { immediate: true, deep: true })
watch(() => props.modelValue, (value) => {
  if (value) syncDraft()
})
</script>

<style scoped>
.spec-detail-form {
  padding: 0;
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
  flex: 0 0 58px;
  color: var(--erp-text-secondary);
  font-size: 13px;
}
</style>
