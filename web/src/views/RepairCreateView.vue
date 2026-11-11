<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">提交故障报修</h2>
        <div class="page-subtitle">仅可对本部门启用设备报修；提交后可上传 0~6 张故障现场照片，最多 5 MB/张</div>
      </div>
      <el-button @click="router.back()">返回</el-button>
    </div>

    <div class="table-card">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" style="max-width: 760px">
        <el-form-item label="故障设备" prop="equipmentId">
          <el-select v-model="form.equipmentId" filterable placeholder="请选择设备（本部门启用设备）" style="width: 100%"
                     @change="onEquipmentChange">
            <el-option v-for="item in equipments" :key="item.equipmentId"
                       :label="`${item.equipmentCode} ${item.equipmentName}（${item.location}）`"
                       :value="item.equipmentId" />
          </el-select>
        </el-form-item>

        <el-form-item label="故障描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="1000" show-word-limit
                    placeholder="请描述故障现象、发生时间与已采取的临时措施（1~1000 字）" />
        </el-form-item>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="故障类型" prop="faultType">
              <el-select v-model="form.faultType" placeholder="请选择" style="width: 100%">
                <el-option v-for="t in dictionaries.faultTypes" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="紧急程度" prop="urgency">
              <el-select v-model="form.urgency" placeholder="请选择" style="width: 100%">
                <el-option v-for="u in dictionaries.urgencies" :key="u" :label="u" :value="u" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="发现时间" prop="discoveredAt">
          <el-date-picker v-model="form.discoveredAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                          placeholder="不得晚于当前时间" style="width: 260px" :disabled-date="disableFuture" />
        </el-form-item>

        <el-form-item label="现场照片">
          <div class="uploader">
            <el-upload v-model:file-list="fileList" list-type="picture-card" :auto-upload="true"
                       :http-request="uploadRequest" :limit="6" accept="image/jpeg,image/png"
                       :before-upload="beforeUpload">
              <el-icon><Plus /></el-icon>
            </el-upload>
            <div class="muted upload-hint">支持 JPG / PNG，单张不超过 5 MB，最多 6 张；上传失败可删除后重新选择</div>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submit">提交报修</el-button>
          <el-button @click="reset">清空</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { equipmentApi, fileApi, repairApi, statApi } from '@/api/modules'

const router = useRouter()
const formRef = ref()
const submitting = ref(false)
const equipments = ref([])
const fileList = ref([])
const dictionaries = ref({ faultTypes: [], urgencies: [] })
let submitToken = createToken()

const form = reactive({
  equipmentId: null,
  description: '',
  faultType: '',
  urgency: '一般',
  discoveredAt: nowString()
})

const rules = {
  equipmentId: [{ required: true, message: '请选择故障设备', trigger: 'change' }],
  description: [
    { required: true, message: '故障描述不能为空', trigger: 'blur' },
    { min: 1, max: 1000, message: '故障描述为 1~1000 字符', trigger: 'blur' }
  ],
  faultType: [{ required: true, message: '请选择故障类型', trigger: 'change' }],
  urgency: [{ required: true, message: '请选择紧急程度', trigger: 'change' }]
}

function createToken() {
  if (window.crypto && window.crypto.randomUUID) return window.crypto.randomUUID()
  return 'web-' + Date.now() + '-' + Math.random().toString(16).slice(2)
}

function nowString() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function disableFuture(date) {
  return date.getTime() > Date.now()
}

function beforeUpload(file) {
  const okType = ['image/jpeg', 'image/png'].includes(file.type)
  if (!okType) {
    ElMessage.error('仅支持 JPG、PNG 格式的图片')
    return false
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('单张图片不能超过 5 MB')
    return false
  }
  if (fileList.value.length >= 6) {
    ElMessage.warning('最多上传 6 张照片')
    return false
  }
  return true
}

async function uploadRequest(options) {
  const sortNo = fileList.value.length
  const result = await fileApi.upload(options.file, 'REPAIR', sortNo)
  // 关联到对应的文件项：el-upload 保留原始 File 对象引用
  const entry = fileList.value.find((item) => item.raw === options.file)
  if (entry) {
    entry.attachmentId = result.attachmentId
  }
  ElMessage.success(`照片 ${result.fileName} 上传成功`)
  options.onSuccess(result)
}

function onEquipmentChange(id) {
  const equipment = equipments.value.find((item) => item.equipmentId === id)
  if (equipment && !form.faultType) {
    form.faultType = ''
  }
}

async function submit() {
  await formRef.value.validate()
  const uploaded = fileList.value.map((file) => file.attachmentId).filter(Boolean)
  if (uploaded.length !== fileList.value.length) {
    ElMessage.warning('仍有照片在上传中，请稍候再提交')
    return
  }
  submitting.value = true
  try {
    const detail = await repairApi.create({
      equipmentId: form.equipmentId,
      description: form.description.trim(),
      faultType: form.faultType,
      urgency: form.urgency,
      discoveredAt: form.discoveredAt,
      attachmentIds: uploaded,
      submitToken,
      source: 'WEB'
    })
    ElMessage.success(`报修已提交，报修编号 ${detail.summary.requestNo}`)
    submitToken = createToken()
    router.push(`/repairs/${detail.summary.requestId}`)
  } finally {
    submitting.value = false
  }
}

function reset() {
  form.equipmentId = null
  form.description = ''
  form.faultType = ''
  form.urgency = '一般'
  form.discoveredAt = nowString()
  fileList.value = []
  formRef.value?.clearValidate()
}

onMounted(async () => {
  dictionaries.value = await statApi.dictionaries()
  const page = await equipmentApi.list({ page: 1, size: 50 })
  equipments.value = page.items
})
</script>

<style scoped>
.uploader {
  width: 100%;
}

.upload-hint {
  font-size: 12px;
  margin-top: 4px;
}
</style>
