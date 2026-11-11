<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">你好，{{ auth.displayName }}</h2>
        <div class="page-subtitle">{{ auth.user?.department }} · {{ auth.roleLabel }} · 数据在打开页面或点击刷新时加载</div>
      </div>
      <el-button :icon="Refresh" @click="loadAll">刷新</el-button>
    </div>

    <el-row :gutter="14">
      <el-col v-for="card in cards" :key="card.label" :xs="12" :sm="8" :md="6">
        <div class="stat-card" :class="card.tone">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-foot">{{ card.foot }}</div>
        </div>
      </el-col>
    </el-row>

    <div class="section-title">快捷入口</div>
    <div class="quick">
      <el-button v-for="link in quickLinks" :key="link.path" type="primary" plain @click="router.push(link.path)">
        {{ link.title }}
      </el-button>
    </div>

    <template v-if="auth.isAdmin">
      <div class="section-title">待受理报修（前 5 条）</div>
      <div class="table-card">
        <el-table :data="pendingRepairs" size="small" empty-text="暂无待受理报修">
          <el-table-column prop="requestNo" label="报修编号" width="160" />
          <el-table-column prop="equipmentName" label="设备" min-width="120" />
          <el-table-column prop="department" label="部门" width="100" />
          <el-table-column prop="description" label="故障描述" min-width="220" show-overflow-tooltip />
          <el-table-column prop="reporterName" label="报修人" width="90" />
          <el-table-column prop="submittedAt" label="提交时间" width="160" />
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button link type="primary" @click="router.push(`/repairs/${row.requestId}`)">处理</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <template v-if="auth.isTechnician">
      <div class="section-title">我的待处理工单（前 5 条）</div>
      <div class="table-card">
        <el-table :data="myOrders" size="small" empty-text="暂无待处理工单">
          <el-table-column prop="orderNo" label="工单编号" width="160" />
          <el-table-column prop="orderTypeLabel" label="类型" width="70" />
          <el-table-column prop="equipmentName" label="设备" min-width="120" />
          <el-table-column prop="location" label="位置" min-width="130" show-overflow-tooltip />
          <el-table-column prop="taskContent" label="任务内容" min-width="220" show-overflow-tooltip />
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><StatusTag :status="row.status" /></template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button link type="primary" @click="router.push(`/orders/${row.orderId}`)">处理</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <template v-if="auth.isReporter">
      <div class="section-title">我的报修（最近 5 条）</div>
      <div class="table-card">
        <el-table :data="myRepairs" size="small" empty-text="暂无报修记录">
          <el-table-column prop="requestNo" label="报修编号" width="160" />
          <el-table-column prop="equipmentName" label="设备" min-width="120" />
          <el-table-column prop="description" label="故障描述" min-width="220" show-overflow-tooltip />
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><StatusTag :status="row.status" /></template>
          </el-table-column>
          <el-table-column prop="submittedAt" label="提交时间" width="160" />
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button link type="primary" @click="router.push(`/repairs/${row.requestId}`)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { orderApi, repairApi, statApi } from '@/api/modules'
import StatusTag from '@/components/StatusTag.vue'

const auth = useAuthStore()
const router = useRouter()
const overview = ref({})
const pendingRepairs = ref([])
const myOrders = ref([])
const myRepairs = ref([])

const cards = computed(() => {
  if (auth.isAdmin) {
    return [
      { label: '设备总数', value: overview.value.equipmentCount ?? '-', foot: '全部台账数量', tone: 'blue' },
      { label: '待受理报修', value: overview.value.pendingRepairCount ?? '-', foot: '需要判断是否受理', tone: 'orange' },
      { label: '待验收工单', value: overview.value.pendingAcceptanceCount ?? '-', foot: '维修人员已提交完工', tone: 'green' }
    ]
  }
  if (auth.isTechnician) {
    return [
      { label: '待处理', value: overview.value.myPendingOrderCount ?? '-', foot: '尚未开工', tone: 'orange' },
      { label: '处理中', value: overview.value.myProcessingOrderCount ?? '-', foot: '现场处理中', tone: 'blue' },
      { label: '待验收', value: overview.value.myPendingAcceptanceOrderCount ?? '-', foot: '等待管理员验收', tone: 'green' },
      { label: '未完成合计', value: overview.value.myUnfinishedOrderCount ?? '-', foot: '含退回整改', tone: 'gray' }
    ]
  }
  return [
    { label: '我的待受理报修', value: overview.value.myPendingRepairCount ?? '-', foot: '等待管理员受理', tone: 'orange' },
    { label: '可报修设备', value: overview.value.equipmentCount ?? '-', foot: '本部门启用设备', tone: 'blue' }
  ]
})

const quickLinks = computed(() => {
  if (auth.isAdmin) {
    return [
      { title: '设备台账', path: '/equipments' },
      { title: '报修管理', path: '/repairs' },
      { title: '待验收', path: '/acceptances' },
      { title: '查询统计', path: '/statistics' }
    ]
  }
  if (auth.isTechnician) {
    return [
      { title: '我的工单', path: '/orders' },
      { title: '个人中心', path: '/profile' }
    ]
  }
  return [
    { title: '提交报修', path: '/repairs/create' },
    { title: '我的报修', path: '/repairs' }
  ]
})

async function loadAll() {
  overview.value = await statApi.dashboard()
  if (auth.isAdmin) {
    const page = await repairApi.list({ status: '待受理', page: 1, size: 5 })
    pendingRepairs.value = page.items
  } else if (auth.isTechnician) {
    const page = await orderApi.mine({ status: '待处理', page: 1, size: 5 })
    myOrders.value = page.items
  } else {
    const page = await repairApi.mine({ page: 1, size: 5 })
    myRepairs.value = page.items
  }
}

onMounted(loadAll)
</script>

<style scoped>
.stat-card {
  background: #fff;
  border-radius: 10px;
  padding: 16px 18px;
  margin-bottom: 14px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);
  border-left: 4px solid var(--sems-border);
}

.stat-card.blue {
  border-left-color: #2f6fed;
}

.stat-card.orange {
  border-left-color: #e6a23c;
}

.stat-card.green {
  border-left-color: #67c23a;
}

.stat-card.gray {
  border-left-color: #909399;
}

.stat-label {
  color: var(--sems-muted);
  font-size: 13px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  margin: 6px 0 2px;
}

.stat-foot {
  color: var(--sems-muted);
  font-size: 12px;
}

.quick {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
</style>
