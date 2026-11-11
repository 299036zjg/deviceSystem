<template>
  <div class="page" v-loading="loading">
    <div class="page-header">
      <div>
        <h2 class="page-title">工单详情</h2>
        <div class="page-subtitle" v-if="detail.summary">
          {{ detail.summary.orderNo }} · {{ detail.summary.orderTypeLabel }}工单 · 创建于 {{ detail.summary.createdAt }}
        </div>
      </div>
      <div>
        <el-button @click="router.back()">返回</el-button>
        <template v-if="canStart">
          <el-button type="primary" @click="openStart">开始维修</el-button>
        </template>
        <template v-if="canSubmit">
          <el-button type="primary" @click="openSubmit">提交完工</el-button>
        </template>
        <template v-if="canAccept">
          <el-button type="success" @click="openAcceptance('PASS')">验收通过</el-button>
          <el-button type="danger" @click="openAcceptance('RETURN')">退回整改</el-button>
        </template>
      </div>
    </div>

    <template v-if="detail.summary">
      <div class="table-card">
        <div class="detail-grid">
          <div class="detail-item"><span class="label">工单状态</span><StatusTag :status="detail.summary.status" /></div>
          <div class="detail-item"><span class="label">工单类型</span>{{ detail.summary.orderTypeLabel }}</div>
          <div class="detail-item"><span class="label">设备</span>{{ detail.summary.equipmentCode }} {{ detail.summary.equipmentName }}</div>
          <div class="detail-item"><span class="label">所属部门</span>{{ detail.summary.department }}</div>
          <div class="detail-item"><span class="label">使用位置</span>{{ detail.summary.location }}</div>
          <div class="detail-item"><span class="label">负责人</span>{{ detail.summary.ownerName }}</div>
          <div class="detail-item"><span class="label">计划日期</span>{{ detail.summary.plannedDate }}</div>
          <div class="detail-item"><span class="label">开工时间</span>{{ detail.summary.startedAt || '-' }}</div>
          <div class="detail-item"><span class="label">完成时间</span>{{ detail.summary.completedAt || '-' }}</div>
          <div class="detail-item"><span class="label">本单总费用</span>
            {{ detail.summary.totalCost === null ? '无权限查看' : detail.summary.totalCost + ' 元' }}
          </div>
          <div v-if="detail.summary.requestNo" class="detail-item">
            <span class="label">来源报修</span>
            <el-link type="primary" @click="router.push(`/repairs/${detail.summary.requestId}`)">
              {{ detail.summary.requestNo }}
            </el-link>
          </div>
          <div v-if="detail.summary.reporterName" class="detail-item">
            <span class="label">报修人</span>{{ detail.summary.reporterName }}
          </div>
        </div>

        <div class="section-title">任务内容</div>
        <div class="desc">{{ detail.description }}</div>

        <template v-if="detail.repairPhotos.length">
          <div class="section-title">故障现场照片</div>
          <div class="photo-row">
            <div v-for="photo in detail.repairPhotos" :key="photo.attachmentId" class="photo-item">
              <AuthImage :src="photo.url" :width="140" :height="105" :alt="photo.fileName"
                         :meta="`${photo.uploaderName} 上传于 ${photo.uploadedAt}`" />
              <div class="photo-meta">{{ photo.uploaderName }} · {{ photo.uploadedAt }}</div>
            </div>
          </div>
        </template>
      </div>

      <div class="table-card" style="margin-top: 14px">
        <div class="section-title" style="margin-top: 0">处理记录（{{ detail.records.length }} 次提交）</div>
        <div v-if="!detail.records.length" class="empty-hint">尚未提交处理结果</div>
        <div v-for="(record, index) in detail.records" :key="record.recordId" class="timeline-card">
          <div class="record-head">
            <span>第 {{ index + 1 }} 次提交 · {{ record.operatorName }} · {{ record.submittedAt }}</span>
            <span v-if="record.totalCostSnapshot !== null" class="muted">当次总费用 {{ record.totalCostSnapshot }} 元</span>
          </div>
          <div class="record-body">
            <div v-if="record.cause"><span class="label">故障原因：</span>{{ record.cause }}</div>
            <div v-if="record.process"><span class="label">维修过程：</span>{{ record.process }}</div>
            <div><span class="label">处理结果：</span>{{ record.result }}</div>
            <div v-if="record.replacedParts"><span class="label">更换零部件：</span>{{ record.replacedParts }}</div>
          </div>
          <div v-if="record.attachments.length" class="photo-row" style="margin-top: 10px">
            <AuthImage v-for="photo in record.attachments" :key="photo.attachmentId" :src="photo.url"
                       :width="120" :height="90" :alt="photo.fileName"
                       :meta="`${photo.uploaderName} 上传于 ${photo.uploadedAt}`" />
          </div>
        </div>
      </div>

      <div v-if="detail.acceptances.length" class="table-card" style="margin-top: 14px">
        <div class="section-title" style="margin-top: 0">验收记录</div>
        <el-table :data="detail.acceptances" size="small">
          <el-table-column label="结论" width="90">
            <template #default="{ row }"><StatusTag :status="row.result" /></template>
          </el-table-column>
          <el-table-column prop="opinion" label="验收意见" min-width="240" />
          <el-table-column prop="inspectorName" label="验收人" width="100" />
          <el-table-column prop="acceptedAt" label="验收时间" width="170" />
        </el-table>
      </div>
    </template>

    <el-dialog v-model="startVisible" title="开始维修" width="460px">
      <el-form label-width="96px">
        <el-form-item label="设备编号">
          <el-input v-model="startForm.equipmentCode" placeholder="选填：输入设备编号核对是否一致" />
        </el-form-item>
      </el-form>
      <div class="muted">开工后工单转为「处理中」并记录开始时间，不能跳过开工直接提交完工。</div>
      <template #footer>
        <el-button @click="startVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitStart">确认开工</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="submitVisible" title="提交完工" width="660px">
      <el-form ref="submitFormRef" :model="submitForm" :rules="submitRules" label-width="104px">
        <el-form-item label="故障原因" prop="cause">
          <el-input v-model="submitForm.cause" maxlength="500" placeholder="选填，例如：液压油缸密封圈老化" />
        </el-form-item>
        <el-form-item label="维修过程" prop="process">
          <el-input v-model="submitForm.process" type="textarea" :rows="2" maxlength="1000" show-word-limit
                    placeholder="选填，说明拆检、更换、调试等过程" />
        </el-form-item>
        <el-form-item label="处理结果" prop="result">
          <el-input v-model="submitForm.result" type="textarea" :rows="2" maxlength="1500" show-word-limit
                    placeholder="必填，说明处理完成情况与验证结果" />
        </el-form-item>
        <el-form-item label="更换零部件" prop="replacedParts">
          <el-input v-model="submitForm.replacedParts" maxlength="500" placeholder="选填，例如：密封圈套件 ×1" />
        </el-form-item>
        <el-form-item label="本单总费用" prop="totalCost">
          <el-input-number v-model="submitForm.totalCost" :min="0" :max="99999999.99" :precision="2"
                           :step="10" controls-position="right" style="width: 200px" />
          <span class="muted" style="margin-left: 8px">元（整单当前总额，最多两位小数）</span>
        </el-form-item>
        <el-form-item label="完工照片" required>
          <div>
            <el-upload v-model:file-list="workFiles" list-type="picture-card" :auto-upload="true"
                       :http-request="uploadWorkPhoto" :limit="6" accept="image/jpeg,image/png"
                       :before-upload="beforeUpload">
              <el-icon><Plus /></el-icon>
            </el-upload>
            <div class="muted">至少 1 张、最多 6 张，单张不超过 5 MB（无完工照片不能提交完工）</div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="submitVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitWork">提交完工</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="acceptanceVisible" :title="acceptanceForm.result === 'PASS' ? '验收通过' : '退回整改'" width="520px">
      <el-form ref="acceptanceFormRef" :model="acceptanceForm" :rules="acceptanceRules" label-width="90px">
        <el-form-item label="验收意见" prop="opinion">
          <el-input v-model="acceptanceForm.opinion" type="textarea" :rows="3" maxlength="500" show-word-limit
                    :placeholder="acceptanceForm.result === 'PASS' ? '选填，例如：整改到位，验收通过' : '必填，说明退回原因与整改要求'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="acceptanceVisible = false">取消</el-button>
        <el-button :type="acceptanceForm.result === 'PASS' ? 'success' : 'danger'" :loading="submitting"
                   @click="submitAcceptance">
          确认{{ acceptanceForm.result === 'PASS' ? '通过' : '退回' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { fileApi, orderApi } from '@/api/modules'
import { useAuthStore } from '@/stores/auth'
import AuthImage from '@/components/AuthImage.vue'
import StatusTag from '@/components/StatusTag.vue'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const detail = ref({ records: [], acceptances: [], repairPhotos: [], workPhotos: [], summary: null })
const submitting = ref(false)

const startVisible = ref(false)
const startForm = reactive({ equipmentCode: '' })

const submitVisible = ref(false)
const submitFormRef = ref()
const workFiles = ref([])
const submitForm = reactive({ cause: '', process: '', result: '', replacedParts: '', totalCost: 0 })
const submitRules = {
  result: [{ required: true, message: '处理结果不能为空', trigger: 'blur' }],
  totalCost: [{ required: true, message: '请填写本单总费用', trigger: 'blur' }]
}

const acceptanceVisible = ref(false)
const acceptanceFormRef = ref()
const acceptanceForm = reactive({ result: 'PASS', opinion: '' })
const acceptanceRules = computed(() => ({
  opinion: acceptanceForm.result === 'RETURN'
    ? [{ required: true, message: '退回时必须填写退回原因', trigger: 'blur' }]
    : []
}))

const canStart = computed(() => auth.isTechnician && detail.value.summary?.statusCode === 'PENDING'
  && detail.value.summary?.ownerId === auth.user?.userId)
const canSubmit = computed(() => auth.isTechnician && detail.value.summary?.statusCode === 'PROCESSING'
  && detail.value.summary?.ownerId === auth.user?.userId)
const canAccept = computed(() => auth.isAdmin && detail.value.summary?.statusCode === 'PENDING_ACCEPTANCE')

async function load() {
  loading.value = true
  try {
    detail.value = await orderApi.detail(route.params.id)
  } finally {
    loading.value = false
  }
}

function openStart() {
  startForm.equipmentCode = ''
  startVisible.value = true
}

async function submitStart() {
  submitting.value = true
  try {
    await orderApi.start(route.params.id, { equipmentCode: startForm.equipmentCode || undefined })
    ElMessage.success('已开工，工单转为处理中')
    startVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

function openSubmit() {
  submitForm.cause = ''
  submitForm.process = ''
  submitForm.result = ''
  submitForm.replacedParts = ''
  submitForm.totalCost = detail.value.summary?.totalCost ?? 0
  workFiles.value = []
  submitVisible.value = true
}

function beforeUpload(file) {
  if (!['image/jpeg', 'image/png'].includes(file.type)) {
    ElMessage.error('仅支持 JPG、PNG 格式的图片')
    return false
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('单张图片不能超过 5 MB')
    return false
  }
  if (workFiles.value.length >= 6) {
    ElMessage.warning('最多上传 6 张完工照片')
    return false
  }
  return true
}

async function uploadWorkPhoto(options) {
  const result = await fileApi.upload(options.file, 'WORK', workFiles.value.length)
  const entry = workFiles.value.find((item) => item.raw === options.file)
  if (entry) {
    entry.attachmentId = result.attachmentId
  }
  ElMessage.success(`完工照片 ${result.fileName} 上传成功`)
  options.onSuccess(result)
}

async function submitWork() {
  await submitFormRef.value.validate()
  const uploaded = workFiles.value.map((file) => file.attachmentId).filter(Boolean)
  if (uploaded.length !== workFiles.value.length) {
    ElMessage.warning('仍有照片在上传中，请稍候再提交')
    return
  }
  if (!uploaded.length) {
    ElMessage.warning('请至少上传 1 张维修完成照片')
    return
  }
  submitting.value = true
  try {
    await orderApi.submit(route.params.id, {
      cause: submitForm.cause || null,
      process: submitForm.process || null,
      result: submitForm.result,
      replacedParts: submitForm.replacedParts || null,
      totalCost: submitForm.totalCost,
      attachmentIds: uploaded,
      submitToken: (window.crypto && window.crypto.randomUUID)
        ? window.crypto.randomUUID()
        : 'submit-' + Date.now() + '-' + Math.random().toString(16).slice(2)
    })
    ElMessage.success('已提交完工，等待管理员验收')
    submitVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

function openAcceptance(result) {
  acceptanceForm.result = result
  acceptanceForm.opinion = ''
  acceptanceVisible.value = true
}

async function submitAcceptance() {
  await acceptanceFormRef.value.validate()
  submitting.value = true
  try {
    await orderApi.acceptance(route.params.id, { ...acceptanceForm })
    ElMessage.success(acceptanceForm.result === 'PASS' ? '验收通过，工单已完成' : '已退回整改')
    acceptanceVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.desc {
  background: #f8fafc;
  border-radius: 8px;
  padding: 12px 14px;
  line-height: 1.8;
  white-space: pre-wrap;
}

.photo-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.photo-meta {
  font-size: 11px;
  color: var(--sems-muted);
  max-width: 140px;
}

.record-head {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  margin-bottom: 8px;
}

.record-body {
  line-height: 1.8;
  font-size: 13px;
}

.record-body .label {
  color: var(--sems-muted);
}
</style>
