<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">{{ auth.isAdmin ? '报修管理' : '我的报修' }}</h2>
        <div class="page-subtitle">
          {{ auth.isAdmin
            ? '受理时指定负责人并创建维修工单；无效或重复报修请驳回并说明原因'
            : '报修提交后只读；受理后可查看负责人与维修进度，驳回后可查看原因' }}
        </div>
      </div>
      <el-button v-if="auth.isReporter" type="primary" :icon="Plus" @click="router.push('/repairs/create')">
        提交报修
      </el-button>
    </div>

    <div class="filter-bar">
      <el-input v-model="query.keyword" placeholder="报修编号 / 故障描述" clearable style="width: 220px"
                @keyup.enter="load" @clear="load" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="load">
        <el-option v-for="s in statusOptions" :key="s" :label="s" :value="s" />
      </el-select>
      <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至"
                      start-placeholder="提交起始日" end-placeholder="提交截止日" style="width: 260px"
                      @change="load" />
      <el-button type="primary" @click="load">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <div class="table-card">
      <el-table v-loading="loading" :data="rows" size="small" stripe>
        <el-table-column prop="requestNo" label="报修编号" width="160" />
        <el-table-column prop="equipmentName" label="设备" min-width="120" />
        <el-table-column prop="department" label="部门" width="100" />
        <el-table-column prop="description" label="故障描述" min-width="240" show-overflow-tooltip />
        <el-table-column prop="faultType" label="故障类型" width="110" />
        <el-table-column prop="urgency" label="紧急程度" width="90" />
        <el-table-column prop="reporterName" label="报修人" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><StatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column prop="source" label="来源" width="90">
          <template #default="{ row }">{{ row.source === 'MINI' ? '小程序' : 'Web' }}</template>
        </el-table-column>
        <el-table-column prop="submittedAt" label="提交时间" width="160" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/repairs/${row.requestId}`)">详情</el-button>
            <template v-if="auth.isAdmin && row.status === '待受理'">
              <el-button link type="success" @click="openAccept(row)">受理</el-button>
              <el-button link type="danger" @click="openReject(row)">驳回</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination layout="total, prev, pager, next, sizes" :total="total" v-model:current-page="query.page"
                       v-model:page-size="query.size" :page-sizes="[10, 20, 50]" @current-change="load"
                       @size-change="load" />
      </div>
    </div>

    <el-dialog v-model="acceptVisible" title="受理报修并派工" width="520px">
      <div class="muted accept-tip">{{ current?.requestNo }} · {{ current?.equipmentName }} · {{ current?.description }}</div>
      <el-form ref="acceptFormRef" :model="acceptForm" :rules="acceptRules" label-width="96px" class="accept-form">
        <el-form-item label="维修负责人" prop="ownerId">
          <el-select v-model="acceptForm.ownerId" placeholder="请选择启用的维修人员" style="width: 100%">
            <el-option v-for="t in technicians" :key="t.userId" :label="`${t.name}（${t.department}）`" :value="t.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划日期" prop="plannedDate">
          <el-date-picker v-model="acceptForm.plannedDate" type="date" value-format="YYYY-MM-DD"
                          placeholder="不得早于当前日期" style="width: 100%" :disabled-date="disablePast" />
        </el-form-item>
        <el-form-item label="处理意见">
          <el-input v-model="acceptForm.opinion" type="textarea" :rows="2" maxlength="500" show-word-limit
                    placeholder="选填，例如：现场检修，注意安全" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="acceptVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAccept">确认受理</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rejectVisible" title="驳回报修" width="480px">
      <div class="muted accept-tip">{{ current?.requestNo }} · {{ current?.equipmentName }}</div>
      <el-form ref="rejectFormRef" :model="rejectForm" :rules="rejectRules" label-width="80px">
        <el-form-item label="驳回原因" prop="reason">
          <el-input v-model="rejectForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit
                    placeholder="必填，例如：重复报修，已由现场人员处理" />
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
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { repairApi, userApi } from '@/api/modules'
import { useAuthStore } from '@/stores/auth'
import StatusTag from '@/components/StatusTag.vue'

const auth = useAuthStore()
const router = useRouter()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const statusOptions = ['待受理', '已驳回', '已受理', '已完成']
const dateRange = ref(null)
const query = reactive({ keyword: '', status: '', page: 1, size: 10 })

const acceptVisible = ref(false)
const rejectVisible = ref(false)
const submitting = ref(false)
const current = ref(null)
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
    const params = {
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      from: dateRange.value ? dateRange.value[0] : undefined,
      to: dateRange.value ? dateRange.value[1] : undefined,
      page: query.page,
      size: query.size
    }
    const data = auth.isAdmin ? await repairApi.list(params) : await repairApi.mine(params)
    rows.value = data.items
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function reset() {
  query.keyword = ''
  query.status = ''
  dateRange.value = null
  query.page = 1
  load()
}

async function openAccept(row) {
  current.value = row
  acceptForm.ownerId = null
  acceptForm.plannedDate = new Date().toISOString().slice(0, 10)
  acceptForm.opinion = ''
  acceptVisible.value = true
  if (!technicians.value.length) {
    technicians.value = await userApi.technicians()
  }
}

function openReject(row) {
  current.value = row
  rejectForm.reason = ''
  rejectVisible.value = true
}

async function submitAccept() {
  await acceptFormRef.value.validate()
  submitting.value = true
  try {
    const order = await repairApi.accept(current.value.requestId, { ...acceptForm })
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
    await repairApi.reject(current.value.requestId, { reason: rejectForm.reason })
    ElMessage.success('已驳回该报修')
    rejectVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.accept-tip {
  background: #f7f9fc;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 14px;
  line-height: 1.7;
}

.accept-form {
  margin-top: 4px;
}
</style>
