<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">查询统计</h2>
        <div class="page-subtitle">
          设备总数为全部台账；未完成工单含待处理、处理中、待验收；完成数量与维修费用按完成日期范围统计
        </div>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <div class="filter-bar">
      <span class="muted">完成日期范围</span>
      <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" range-separator="至"
                      start-placeholder="开始日期" end-placeholder="结束日期" style="width: 260px" @change="load" />
      <el-button type="primary" @click="load">统计</el-button>
    </div>

    <el-row :gutter="14">
      <el-col :xs="12" :sm="8" :md="8">
        <div class="stat-card blue">
          <div class="stat-label">设备总数</div>
          <div class="stat-value">{{ data.equipmentCount ?? '-' }}</div>
          <div class="stat-foot">全部设备台账数量</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="8" :md="8">
        <div class="stat-card orange">
          <div class="stat-label">未完成工单</div>
          <div class="stat-value">{{ data.unfinishedOrderCount ?? '-' }}</div>
          <div class="stat-foot">待处理 + 处理中 + 待验收</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="8" :md="8">
        <div class="stat-card green">
          <div class="stat-label">已完成维修</div>
          <div class="stat-value">{{ data.doneRepairCount ?? '-' }}</div>
          <div class="stat-foot">按完成时间落在所选范围内</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="8" :md="8">
        <div class="stat-card green">
          <div class="stat-label">已完成保养</div>
          <div class="stat-value">{{ data.doneMaintenanceCount ?? '-' }}</div>
          <div class="stat-foot">按完成时间落在所选范围内</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="8" :md="8">
        <div class="stat-card blue">
          <div class="stat-label">维修费用合计（元）</div>
          <div class="stat-value">{{ data.repairCostTotal ?? 0 }}</div>
          <div class="stat-foot">仅统计已完成维修工单，保养费用不计入</div>
        </div>
      </el-col>
    </el-row>

    <div class="table-card">
      <div class="section-title" style="margin-top: 0">统计口径说明</div>
      <ul class="note">
        <li>设备总数按台账全部记录统计，包含已停用设备。</li>
        <li>未完成任务按当前状态统计，不按日期过滤。</li>
        <li>维修费用取工单保存的当前总费用（两位小数），处理记录中的费用仅为各次提交快照，不累加。</li>
        <li>页面不提供导出与复杂图表；需要明细时请到「报修管理」或「工单管理」中按条件查询。</li>
      </ul>
      <div class="muted">当前统计范围：{{ data.from }} 至 {{ data.to }}</div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { statApi } from '@/api/modules'

const data = ref({})
const range = ref([monthStart(), today()])

function pad(n) {
  return String(n).padStart(2, '0')
}

function today() {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function monthStart() {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-01`
}

async function load() {
  const params = range.value && range.value.length === 2
    ? { from: range.value[0], to: range.value[1] }
    : {}
  data.value = await statApi.statistics(params)
}

onMounted(load)
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

.stat-label {
  color: var(--sems-muted);
  font-size: 13px;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  margin: 6px 0 2px;
}

.stat-foot {
  color: var(--sems-muted);
  font-size: 12px;
}

.note {
  line-height: 1.9;
  margin: 0 0 10px;
  padding-left: 20px;
  color: var(--sems-text);
}
</style>
