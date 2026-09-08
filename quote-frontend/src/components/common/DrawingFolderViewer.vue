<template>
  <el-dialog
    :model-value="modelValue"
    :title="viewerTitle"
    width="760px"
    top="6vh"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <DrawingFolderFileBrowser
      :folder-url="folderUrl"
      :active="modelValue"
      :show-notice="true"
      table-height="360"
    />
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import DrawingFolderFileBrowser from './DrawingFolderFileBrowser.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  folderUrl: { type: String, default: '' },
  title: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue'])

const viewerTitle = computed(() => props.title || '图纸文件夹')
</script>
