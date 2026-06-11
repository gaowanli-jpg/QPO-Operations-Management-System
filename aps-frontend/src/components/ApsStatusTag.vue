<script setup>
import { computed } from 'vue'
import { label, statusClass } from '../helpers'

const props = defineProps({
  value: {
    type: [String, Number, Boolean],
    default: ''
  },
  tone: {
    type: String,
    default: ''
  }
})

const tagTone = computed(() => props.tone || statusClass(props.value))

const tagType = computed(() => ({
  ok: 'success',
  warn: 'warning',
  bad: 'danger',
  muted: 'info'
}[tagTone.value] || 'info'))

const text = computed(() => props.tone ? (props.value || '-') : label(props.value))
</script>

<template>
  <el-tag
    class="status-tag"
    :class="tagTone"
    :type="tagType"
    disable-transitions
  >
    {{ text }}
  </el-tag>
</template>
