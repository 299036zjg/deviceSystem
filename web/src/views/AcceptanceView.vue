<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">待验收工单</h2>
        <div class="page-subtitle">维修人员已提交完工的工单；请对照故障现场照片与维修完成照片核实后通过或退回</div>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <div class="table-card">
      <el-table v-loading="loading" :data="rows" size="small" stripe empty-text="当前没有待验收工单">
        <el-table-column prop="orderNo" label="工单编号" width="160" />
        <el-table-column prop="orderTypeLabel" label="类型" width="70" />
        <el-table-column prop="equipmentName" label="设备" min-width="120" />
        <el-table-column prop="ownerName" label="负责人" width="90" />
        <el-table-column prop="taskContent" label="任务内容" min-width="220" show-overflow-tooltip />
        <el-table-column prop="plannedDate" label="计划日期" width="110" />
        <el-table-column prop="totalCost" label="总费用(元)" width="110" />
        <el-table-column prop="startedAt" label="开工时间" width="160" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/orders/${row.orderId}`)">查看详情</el-button>
            <el-button link type="success" @click="open(row, 'PASS')">通过</el-button>
            <el-button link type="danger" @click="open(row, 'RETURN')">退回</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination layout="total, prev, pager, next" :total="total" v-model:current-page="query.page"
                       v-model:page-size="query.size" @current-change="load" />
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.result === 'PASS' ? '验收通过' : '退回整改'" width="520px">
      <div class="muted tip">{{ current?.orderNo }} · {{ current?.equipmentName }} · 负责人 {{ current?.ownerName }}</div>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="验收意见" prop="opinion">
          <el-input v-model="form.opinion" type="textarea" :rows="3" maxlength="500" show-word-limit
                    :placeholder="form.result === 'PASS' ? '选填，例如：处理到位，验收通过' : '必填，说明退回原因与整改要求'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button :type="form.result === 'PASS' ? 'success' : 'danger'" :loading="submitting" @click="submit">
          确认{{ form.result === 'PASS' ? '通过' : '退回' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { orderApi } from '@/api/modules'

const router = useRouter()
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10 })

const dialogVisible = ref(false)
const submitting = ref(false)
const current = ref(null)
const formRef = ref()
const form = reactive({ result: 'PASS', opinion: '' })
const rules = ref([])

async function load() {
  loading.value = true
  try {
    const data = await orderApi.pendingAcceptances({ page: query.page, size: query.size })
    rows.value = data.items
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function open(row, result) {
  current.value = row
  form.result = result
  form.opinion = ''
  rules.value = result === 'RETURN'
    ? [{ required: true, message: '退回时必须填写退回原因', trigger: 'blur' }]
    : []
  dialogVisible.value = true
}

async function submit() {
  if (form.result === 'RETURN' && !form.opinion.trim()) {
    ElMessage.warning('退回时必须填写退回原因')
    return
  }
  submitting.value = true
  try {
    await orderApi.acceptance(current.value.orderId, { ...form })
    ElMessage.success(form.result === 'PASS' ? '验收通过，工单已完成' : '已退回整改')
    dialogVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.tip {
  background: #f7f9fc;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 14px;
}
</style>
