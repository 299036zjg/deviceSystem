<template>
  <div class="auth-image" :style="boxStyle" @click="openPreview">
    <img v-if="thumbUrl" :src="thumbUrl" :alt="alt" />
    <div v-else class="image-placeholder">
      <el-icon v-if="loading"><Loading /></el-icon>
      <span v-else>无图</span>
    </div>
  </div>

  <el-dialog v-model="previewVisible" :title="alt || '图片预览'" width="70%" append-to-body destroy-on-close>
    <div class="preview-wrap">
      <img v-if="fullUrl" :src="fullUrl" class="preview-img" />
      <div v-else class="empty-hint">原图加载中…</div>
    </div>
    <div v-if="meta" class="preview-meta">{{ meta }}</div>
  </el-dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { fetchImageBlob } from '@/api/http'

/**
 * 受鉴权保护的图片展示组件：图片接口不能直接放进 img src，
 * 这里用带令牌的请求取回二进制后转成 blob URL（设计文档 5.5、BR12）。
 */
const props = defineProps({
  src: { type: String, required: true },
  width: { type: Number, default: 96 },
  height: { type: Number, default: 72 },
  alt: { type: String, default: '' },
  meta: { type: String, default: '' }
})

const thumbUrl = ref('')
const fullUrl = ref('')
const loading = ref(false)
const previewVisible = ref(false)

const boxStyle = computed(() => ({ width: `${props.width}px`, height: `${props.height}px` }))

let thumbObjectUrl = ''
let fullObjectUrl = ''

async function loadThumb() {
  if (!props.src) return
  loading.value = true
  try {
    thumbObjectUrl = await fetchImageBlob(props.src, 320)
    thumbUrl.value = thumbObjectUrl
  } catch (e) {
    thumbUrl.value = ''
  } finally {
    loading.value = false
  }
}

async function openPreview() {
  if (!props.src) return
  previewVisible.value = true
  if (fullUrl.value) return
  try {
    fullObjectUrl = await fetchImageBlob(props.src)
    fullUrl.value = fullObjectUrl
  } catch (e) {
    fullUrl.value = ''
  }
}

watch(() => props.src, () => {
  if (thumbObjectUrl) URL.revokeObjectURL(thumbObjectUrl)
  if (fullObjectUrl) URL.revokeObjectURL(fullObjectUrl)
  thumbUrl.value = ''
  fullUrl.value = ''
  loadThumb()
}, { immediate: true })

onBeforeUnmount(() => {
  if (thumbObjectUrl) URL.revokeObjectURL(thumbObjectUrl)
  if (fullObjectUrl) URL.revokeObjectURL(fullObjectUrl)
})
</script>

<style scoped>
.auth-image {
  border: 1px solid var(--sems-border);
  border-radius: 6px;
  overflow: hidden;
  cursor: zoom-in;
  background: #f5f6f8;
  display: flex;
  align-items: center;
  justify-content: center;
}

.auth-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.image-placeholder {
  color: var(--sems-muted);
  font-size: 12px;
}

.preview-wrap {
  display: flex;
  justify-content: center;
  max-height: 70vh;
  overflow: auto;
}

.preview-img {
  max-width: 100%;
}

.preview-meta {
  margin-top: 8px;
  color: var(--sems-muted);
  font-size: 12px;
  text-align: center;
}
</style>
