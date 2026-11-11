<template>
  <div class="page" v-loading="loading">
    <div class="page-header">
      <div>
        <h2 class="page-title">报修详情</h2>
        <div class="page-subtitle" v-if="detail.summary">
          {{ detail.summary.requestNo }} · 提交于 {{ detail.summary.submittedAt }}
        </div>
      </div>
      <div>
        <el-button @click="router.back()">返回</el-button>
        <template v-if="auth.isAdmin && detail.summary?.statusCode === 'PENDING'">
          <el-button type="success" @click="acceptVisible = true">受理派工</el-button>
          <el-button type="danger" @click="rejectVisible = true">驳回</el-button>
        </template>
        <el-button v-if="detail.order" type="primary" @click="router.push(`/orders/${detail.order.orderId}`)">
          查看工单
        </el-button>
      </div>
    </div>

    <template v-if="detail.summary">
      <div class="table-card">
        <div class="detail-grid">
          <div class="detail-item"><span class="label">当前状态</span><StatusTag :status="detail.summary.status" /></div>
          <div class="detail-item"><span class="label">设备</span>{{ detail.summary.equipmentCode }} {{ detail.summary.equipmentName }}</div>
          <div class="detail-item"><span class="label">所属部门</span>{{ detail.summary.department }}</div>
          <div class="detail-item"><span class="label">使用位置</span>{{ detail.summary.location }}</div>
          <div class="detail-item"><span class="label">报修人</span>{{ detail.summary.reporterName }}</div>
          <div class="detail-item"><span class="label">提交来源</span>{{ detail.summary.source === 'MINI' ? '微信小程序' : 'Web 后台' }}</div>
          <div class="detail-item"><span class="label">故障类型</span>{{ detail.summary.faultType || '-' }}</div>
          <div class="detail-item"><span class="label">紧急程度</span>{{ detail.summary.urgency || '-' }}</div>
          <div class="detail-item"><span class="label">发现时间</span>{{ detail.summary.discoveredAt }}</div>
          <div class="detail-item"><span class="label">提交时间</span>{{ detail.summary.submittedAt }}</div>
          <div v-if="detail.summary.ownerName" class="detail-item">
            <span class="label">维修负责人</span>{{ detail.summary.ownerName }}
          </div>
          <div v-if="detail.order" class="detail-item">
            <span class="label">工单状态</span><StatusTag :status="detail.order.status" />
          </div>
          <div v-if="detail.order && detail.order.totalCost !== null" class="detail-item">
            <span class="label">本单总费用</span>{{ detail.order.totalCost }} 元
          </div>
        </div>

        <div class="section-title">故障描述</div>
        <div class="desc">{{ detail.summary.description }}</div>

        <template v-if="detail.decisionReason">
          <div class="section-title">驳回原因</div>
          <div class="desc danger">
            {{ detail.decisionReason }}
            <span class="muted">（{{ detail.decidedByName }} {{ detail.decidedAt }}）</span>
          </div>
        </template>

        <div class="section-title">故障现场照片（{{ detail.attachments.length }} 张）</div>
        <div v-if="detail.attachments.length" class="photo-row">
          <div v-for="photo in detail.attachments" :key="photo.attachmentId" class="photo-item">
            <AuthImage :src="photo.url" :width="140" :height="105" :alt="photo.fileName"
                       :meta="`${photo.uploaderName} 上传于 ${photo.uploadedAt}`" />
            <div class="photo-meta">{{ photo.uploaderName }} · {{ photo.uploadedAt }}</div>
          </div>
        </div>
        <div v-else class="empty-hint">该报修没有上传现场照片</div>
      </div>

      <div v-if="detail.records.length" class="table-card" style="margin-top: 14px">
        <div class="section-title" style="margin-top: 0">处理记录（{{ detail.records.length }} 次提交）</div>
        <div v-for="record in detail.records" :key="record.recordId" class="timeline-card">
          <div class="record-head">
            <span>{{ record.operatorName }} 提交于 {{ record.submittedAt }}</span>
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
                       :width="120" :height="90" :alt="photo.fileName" />
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

    <el-dialog v-model="acceptVisible" title="受理报修并派工" width="520px">
      <el-form ref="acceptFormRef" :model="acceptForm" :rules="acceptRules" label-width="96px">
        <el-form-item label="维修负责人" prop="ownerId">
          <el-select v-model="acceptForm.ownerId" placeholder="请选择启用的维修人员" style="width: 100%">
            <el-option v-for="t in technicians" :key="t.userId" :label="`${t.name}（${t.department}）`" :value="t.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划日期" prop="plannedDate">
          <el-date-picker v-model="acceptForm.plannedDate" type="date" value-format="YYYY-MM-DD"
                          style="width: 100%" :disabled-date="disablePast" />
        </el-form-item>
        <el-form-item label="处理意见">
          <el-input v-model="acceptForm.opinion" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="acceptVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAccept">确认受理</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rejectVisible" title="驳回报修" width="480px">
      <el-form ref="rejectFormRef" :model="rejectForm" :rules="rejectRules" label-width="80px">
        <el-form-item label="驳回原因" prop="reason">
          <el-input v-model="rejectForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="submitReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { repairApi, userApi } from '@/api/modules'
import { useAuthStore } from '@/stores/auth'
import AuthImage from '@/components/AuthImage.vue'
import StatusTag from '@/components/StatusTag.vue'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const detail = ref({ attachments: [], records: [], acceptances: [] })
const acceptVisible = ref(false)
const rejectVisible = ref(false)
const submitting = ref(false)
const technicians = ref([])
const acceptFormRef = ref()
const rejectFormRef = ref()
const acceptForm = reactive({ ownerId: null, plannedDate: '', opinion: '' })
const rejectForm = reactive({ reason: '' })
const acceptRules = {
  ownerId: [{ required: true, message: '请选择维修负责人', trigger: 'change' }],
  plannedDate: [{ required: true, message: '请选择计划日期', trigger: 'change' }]
}
const rejectRules = {
  reason: [{ required: true, message: '驳回原因不能为空', trigger: 'blur' }]
}

function disablePast(date) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return date.getTime() < today.getTime()
}

async function load() {
  loading.value = true
  try {
    detail.value = await repairApi.detail(route.params.id)
    acceptForm.plannedDate = new Date().toISOString().slice(0, 10)
  } finally {
    loading.value = false
  }
}

async function submitAccept() {
  await acceptFormRef.value.validate()
  submitting.value = true
  try {
    const order = await repairApi.accept(route.params.id, { ...acceptForm })
    ElMessage.success(`已受理，工单编号 ${order.orderNo}`)
    acceptVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function submitReject() {
  await rejectFormRef.value.validate()
  submitting.value = true
  try {
    await repairApi.reject(route.params.id, { reason: rejectForm.reason })
    ElMessage.success('已驳回该报修')
    rejectVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await load()
  if (auth.isAdmin) {
    technicians.value = await userApi.technicians()
  }
})
</script>

<style scoped>
.desc {
  background: #f8fafc;
  border-radius: 8px;
  padding: 12px 14px;
  line-height: 1.8;
  white-space: pre-wrap;
}

.desc.danger {
  background: #fef4f4;
  color: #b42318;
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
  color: #1f2937;
}

.record-body {
  line-height: 1.8;
  font-size: 13px;
}

.record-body .label {
  color: var(--sems-muted);
}
</style>
