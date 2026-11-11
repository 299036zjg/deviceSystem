<template>
  <el-tag :type="type" size="small" effect="light">{{ status }}</el-tag>
</template>

<script setup>
import { computed } from 'vue'

/**
 * 状态标签：状态用文字表达，颜色只作为辅助（NFR06：状态不得只用颜色表示）。
 */
const props = defineProps({
  status: { type: String, default: '' }
})

const typeMap = {
  待受理: 'warning',
  已驳回: 'danger',
  已受理: 'primary',
  待处理: 'warning',
  处理中: 'primary',
  待验收: 'warning',
  已完成: 'success',
  PASS: 'success',
  RETURN: 'danger'
}

const type = computed(() => typeMap[props.status] || 'info')
</script>
