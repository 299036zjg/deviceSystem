<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">{{ auth.isAdmin ? '工单管理' : '我的工单' }}</h2>
        <div class="page-subtitle">
          维修与保养工单共用状态流程：待处理 → 处理中 → 待验收 → 已完成；退回后由原负责人整改
        </div>
      </div>
      <el-button v-if="auth.isAdmin" type="primary" :icon="Plus" @click="openMaintenance">安排保养</el-button>
    </div>

    <div class="filter-bar">
      <el-input v-model="query.keyword" placeholder="工单编号 / 任务内容" clearable style="width: 220px"
                @keyup.enter="load" @clear="load" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="load">
        <el-option v-for="s in statusOptions" :key="s" :label="s" :value="s" />
      </el-select>
      <el-select v-model="query.type" placeholder="类型" clearable style="width: 120px" @change="load">
        <el-option label="维修" value="REPAIR" />
        <el-option label="保养" value="MAINTENANCE" />
      </el-select>
      <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至"
                      start-placeholder="创建起始日" end-placeholder="创建截止日" style="width: 250px" @change="load" />
      <el-button type="primary" @click="load">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <div class="table-card">
      <el-table v-loading="loading" :data="rows" size="small" stripe>
        <el-table-column prop="orderNo" label="工单编号" width="160" />
        <el-table-column prop="orderTypeLabel" label="类型" width="70" />
        <el-table-column prop="equipmentName" label="设备" min-width="120" />
        <el-table-column prop="location" label="位置" min-width="130" show-overflow-tooltip />
        <el-table-column prop="taskContent" label="任务内容" min-width="220" show-overflow-tooltip />
        <el-table-column prop="ownerName" label="负责人" width="90" />
        <el-table-column prop="plannedDate" label="计划日期" width="110" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><StatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column prop="totalCost" label="总费用(元)" width="110">
          <template #default="{ row }">{{ row.totalCost === null ? '-' : row.totalCost }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/orders/${row.orderId}`)">
              {{ auth.isTechnician && row.status === '待处理' ? '处理' : '详情' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination layout="total, prev, pager, next, sizes" :total="total" v-model:current-page="query.page"
                       v-model:page-size="query.size" :page-sizes="[10, 20, 50]" @current-change="load"
                       @size-change="load" />
      </div>
    </div>

    <el-dialog v-model="maintenanceVisible" title="安排保养任务" width="560px">
      <el-form ref="maintenanceFormRef" :model="maintenanceForm" :rules="maintenanceRules" label-width="96px">
        <el-form-item label="保养设备" prop="equipmentId">
          <el-select v-model="maintenanceForm.equipmentId" filterable placeholder="请选择启用设备" style="width: 100%">
            <el-option v-for="item in equipments" :key="item.equipmentId"
                       :label="`${item.equipmentCode} ${item.equipmentName}（${item.department}）`"
                       :value="item.equipmentId" />
          </el-select>
        </el-form-item>
        <el-form-item label="保养日期" prop="plannedDate">
          <el-date-picker v-model="maintenanceForm.plannedDate" type="date" value-format="YYYY-MM-DD"
                          style="width: 100%" :disabled-date="disablePast" />
        </el-form-item>
        <el-form-item label="负责人" prop="ownerId">
          <el-select v-model="maintenanceForm.ownerId" placeholder="请选择启用的维修人员" style="width: 100%">
            <el-option v-for="t in technicians" :key="t.userId" :label="`${t.name}（${t.department}）`" :value="t.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="保养内容" prop="taskContent">
          <el-input v-model="maintenanceForm.taskContent" type="textarea" :rows="4" maxlength="1000" show-word-limit
                    placeholder="说明本次需要完成的保养内容，例如：清洁导轨、更换润滑油、校验精度" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="maintenanceVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitMaintenance">创建保养工单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { equipmentApi, orderApi, userApi } from '@/api/modules'
import { useAuthStore } from '@/stores/auth'
import StatusTag from '@/components/StatusTag.vue'

const auth = useAuthStore()
const router = useRouter()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const statusOptions = ['待处理', '处理中', '待验收', '已完成']
const dateRange = ref(null)
const query = reactive({ keyword: '', status: '', type: '', page: 1, size: 10 })

const maintenanceVisible = ref(false)
const submitting = ref(false)
const maintenanceFormRef = ref()
const equipments = ref([])
const technicians = ref([])
const maintenanceForm = reactive({
  equipmentId: null, plannedDate: '', ownerId: null, taskContent: '', createToken: createToken()
})
const maintenanceRules = {
  equipmentId: [{ required: true, message: '请选择保养设备', trigger: 'change' }],
  plannedDate: [{ required: true, message: '请选择保养日期', trigger: 'change' }],
  ownerId: [{ required: true, message: '请选择负责人', trigger: 'change' }],
  taskContent: [{ required: true, message: '保养内容不能为空', trigger: 'blur' }]
}

function createToken() {
  if (window.crypto && window.crypto.randomUUID) return window.crypto.randomUUID()
  return 'maint-' + Date.now() + '-' + Math.random().toString(16).slice(2)
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
      type: query.type || undefined,
      from: dateRange.value ? dateRange.value[0] : undefined,
      to: dateRange.value ? dateRange.value[1] : undefined,
      page: query.page,
      size: query.size
    }
    const data = auth.isAdmin ? await orderApi.list(params) : await orderApi.mine(params)
    rows.value = data.items
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function reset() {
  query.keyword = ''
  query.status = ''
  query.type = ''
  dateRange.value = null
  query.page = 1
  load()
}

async function openMaintenance() {
  maintenanceForm.equipmentId = null
  maintenanceForm.plannedDate = new Date().toISOString().slice(0, 10)
  maintenanceForm.ownerId = null
  maintenanceForm.taskContent = ''
  maintenanceForm.createToken = createToken()
  maintenanceVisible.value = true
  if (!equipments.value.length) {
    const page = await equipmentApi.list({ page: 1, size: 50, enabled: true })
    equipments.value = page.items
  }
  if (!technicians.value.length) {
    technicians.value = await userApi.technicians()
  }
}

async function submitMaintenance() {
  await maintenanceFormRef.value.validate()
  submitting.value = true
  try {
    const detail = await orderApi.createMaintenance({ ...maintenanceForm })
    ElMessage.success(`保养工单已创建：${detail.summary.orderNo}`)
    maintenanceVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>
