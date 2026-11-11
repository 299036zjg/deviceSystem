<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">设备台账</h2>
        <div class="page-subtitle">设备编号创建后不可修改；二维码内容为 EQ:设备编号，设备信息变更后无需重新生成</div>
      </div>
      <div>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增设备</el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="设备列表" name="list">
        <div class="filter-bar">
          <el-input v-model="query.keyword" placeholder="设备编号 / 名称 / 位置" clearable style="width: 240px"
                    @keyup.enter="load" @clear="load" />
          <el-select v-model="query.department" placeholder="所属部门" clearable style="width: 150px" @change="load">
            <el-option v-for="d in departments" :key="d" :label="d" :value="d" />
          </el-select>
          <el-select v-model="query.enabled" placeholder="启用状态" clearable style="width: 130px" @change="load">
            <el-option label="启用" :value="true" />
            <el-option label="停用" :value="false" />
          </el-select>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </div>

        <div class="table-card">
          <el-table v-loading="loading" :data="rows" size="small" stripe>
            <el-table-column prop="equipmentCode" label="设备编号" width="120" />
            <el-table-column prop="equipmentName" label="设备名称" min-width="140" />
            <el-table-column prop="category" label="分类" width="110" />
            <el-table-column prop="model" label="型号" width="110" />
            <el-table-column prop="department" label="所属部门" width="100" />
            <el-table-column prop="location" label="使用位置" min-width="140" show-overflow-tooltip />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="230" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button link type="primary" @click="openHistory(row)">履历</el-button>
                <el-button link type="primary" @click="generateOne(row)">二维码</el-button>
                <el-button v-if="row.enabled" link type="danger" @click="toggle(row, false)">停用</el-button>
                <el-button v-else link type="success" @click="toggle(row, true)">启用</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination layout="total, prev, pager, next, sizes" :total="total" v-model:current-page="query.page"
                           v-model:page-size="query.size" :page-sizes="[10, 20, 50]" @current-change="load"
                           @size-change="load" />
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="设备二维码" name="qrcode">
        <div class="filter-bar">
          <el-button type="primary" :icon="Picture" @click="generateSelected">生成所选设备二维码</el-button>
          <el-button @click="selectAll">全选本页</el-button>
          <el-button @click="clearQr">清空预览</el-button>
          <el-button :disabled="qrcodes.length === 0" @click="printAll">批量打印</el-button>
          <span class="muted">单次最多 50 台；停用设备仍可生成，扫码后提示已停用</span>
        </div>

        <div class="table-card">
          <el-table :data="rows" size="small" @selection-change="onSelectionChange" row-key="equipmentId">
            <el-table-column type="selection" width="46" />
            <el-table-column prop="equipmentCode" label="设备编号" width="120" />
            <el-table-column prop="equipmentName" label="设备名称" min-width="140" />
            <el-table-column prop="department" label="部门" width="100" />
            <el-table-column prop="location" label="位置" min-width="140" />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div v-if="qrcodes.length" class="qr-grid">
          <div v-for="item in qrcodes" :key="item.equipmentId" class="qr-card">
            <AuthImage :src="'/api/v1/equipments/' + item.equipmentId + '/qrcode'" :width="150" :height="150"
                       :alt="item.equipmentName" />
            <div class="qr-code">{{ item.equipmentCode }}</div>
            <div class="qr-name">{{ item.equipmentName }}</div>
            <div class="qr-loc">{{ item.department }} · {{ item.location }}</div>
            <div class="qr-actions">
              <el-button link type="primary" @click="download(item)">下载</el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="formVisible" :title="form.equipmentId ? '编辑设备' : '新增设备'" width="560px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="96px">
        <el-form-item label="设备编号" prop="equipmentCode">
          <el-input v-model="form.equipmentCode" :disabled="!!form.equipmentId" placeholder="如 SB-021，创建后不可修改" />
        </el-form-item>
        <el-form-item label="设备名称" prop="equipmentName">
          <el-input v-model="form.equipmentName" placeholder="如 数控车床" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-input v-model="form.category" placeholder="如 机加工设备" />
        </el-form-item>
        <el-form-item label="型号" prop="model">
          <el-input v-model="form.model" placeholder="如 CK6140" />
        </el-form-item>
        <el-form-item label="所属部门" prop="department">
          <el-select v-model="form.department" placeholder="请选择部门" style="width: 100%">
            <el-option v-for="d in departments" :key="d" :label="d" :value="d" />
          </el-select>
        </el-form-item>
        <el-form-item label="使用位置" prop="location">
          <el-input v-model="form.location" placeholder="如 一号车间 A 区" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="historyVisible" :title="historyTitle" size="62%">
      <div v-loading="historyLoading">
        <div class="detail-grid">
          <div class="detail-item"><span class="label">设备编号</span>{{ history.equipment?.equipmentCode }}</div>
          <div class="detail-item"><span class="label">设备名称</span>{{ history.equipment?.equipmentName }}</div>
          <div class="detail-item"><span class="label">型号</span>{{ history.equipment?.model || '-' }}</div>
          <div class="detail-item"><span class="label">部门</span>{{ history.equipment?.department }}</div>
          <div class="detail-item"><span class="label">位置</span>{{ history.equipment?.location }}</div>
          <div class="detail-item"><span class="label">状态</span>{{ history.equipment?.enabled ? '启用' : '停用' }}</div>
        </div>

        <div class="section-title">维修与保养工单</div>
        <el-table :data="history.orders || []" size="small" empty-text="暂无工单记录">
          <el-table-column prop="orderNo" label="工单编号" width="160" />
          <el-table-column prop="orderTypeLabel" label="类型" width="70" />
          <el-table-column prop="ownerName" label="负责人" width="90" />
          <el-table-column prop="taskContent" label="任务内容" min-width="200" show-overflow-tooltip />
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><StatusTag :status="row.status" /></template>
          </el-table-column>
          <el-table-column prop="completedAt" label="完成时间" width="160" />
        </el-table>

        <div class="section-title">报修记录</div>
        <el-table :data="history.repairs || []" size="small" empty-text="暂无报修记录">
          <el-table-column prop="requestNo" label="报修编号" width="160" />
          <el-table-column prop="reporterName" label="报修人" width="90" />
          <el-table-column prop="description" label="故障描述" min-width="200" show-overflow-tooltip />
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><StatusTag :status="row.status" /></template>
          </el-table-column>
          <el-table-column prop="submittedAt" label="提交时间" width="160" />
        </el-table>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Picture } from '@element-plus/icons-vue'
import { equipmentApi } from '@/api/modules'
import { fetchImageBlob } from '@/api/http'
import AuthImage from '@/components/AuthImage.vue'
import StatusTag from '@/components/StatusTag.vue'

const activeTab = ref('list')
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const departments = ['生产一部', '生产二部', '质检部', '动力车间', '设备科', '维修部']
const query = reactive({ keyword: '', department: '', enabled: null, page: 1, size: 10 })

const formVisible = ref(false)
const saving = ref(false)
const formRef = ref()
const form = reactive({
  equipmentId: null, equipmentCode: '', equipmentName: '', category: '', model: '', department: '', location: ''
})

const formRules = {
  equipmentCode: [{ required: true, message: '请输入设备编号', trigger: 'blur' }],
  equipmentName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  department: [{ required: true, message: '请选择所属部门', trigger: 'change' }],
  location: [{ required: true, message: '请输入使用位置', trigger: 'blur' }]
}

const historyVisible = ref(false)
const historyLoading = ref(false)
const history = ref({})
const historyTitle = computed(() => `设备履历 - ${history.value.equipment?.equipmentName || ''}`)

const qrcodes = ref([])
const selected = ref([])

async function load() {
  loading.value = true
  try {
    const data = await equipmentApi.list({
      keyword: query.keyword || undefined,
      department: query.department || undefined,
      enabled: query.enabled === null || query.enabled === '' ? undefined : query.enabled,
      page: query.page,
      size: query.size
    })
    rows.value = data.items
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function reset() {
  query.keyword = ''
  query.department = ''
  query.enabled = null
  query.page = 1
  load()
}

function openCreate() {
  Object.assign(form, {
    equipmentId: null, equipmentCode: '', equipmentName: '', category: '', model: '', department: '', location: ''
  })
  formVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    equipmentId: row.equipmentId,
    equipmentCode: row.equipmentCode,
    equipmentName: row.equipmentName,
    category: row.category || '',
    model: row.model || '',
    department: row.department,
    location: row.location
  })
  formVisible.value = true
}

async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.equipmentId) {
      await equipmentApi.update(form.equipmentId, { ...form })
      ElMessage.success('设备信息已更新')
    } else {
      await equipmentApi.create({ ...form })
      ElMessage.success('设备已新增')
    }
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function toggle(row, enable) {
  try {
    await ElMessageBox.confirm(
      enable ? `确认启用设备 ${row.equipmentCode}？` : `确认停用设备 ${row.equipmentCode}？停用后不可新报修或安排保养。`,
      enable ? '启用设备' : '停用设备',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  if (enable) {
    await equipmentApi.enable(row.equipmentId)
    ElMessage.success('设备已启用')
  } else {
    await equipmentApi.disable(row.equipmentId)
    ElMessage.success('设备已停用')
  }
  load()
}

async function openHistory(row) {
  historyVisible.value = true
  historyLoading.value = true
  try {
    history.value = await equipmentApi.history(row.equipmentId)
  } finally {
    historyLoading.value = false
  }
}

function onSelectionChange(selection) {
  selected.value = selection
}

function selectAll() {
  selected.value = [...rows.value]
}

function clearQr() {
  qrcodes.value = []
}

async function generateSelected() {
  if (!selected.value.length) {
    ElMessage.warning('请先在列表中勾选设备')
    return
  }
  if (selected.value.length > 50) {
    ElMessage.warning('批量生成单次不超过 50 台')
    return
  }
  const items = await equipmentApi.batchQrcode(selected.value.map((row) => row.equipmentId))
  qrcodes.value = items
  ElMessage.success(`已生成 ${items.length} 台设备二维码`)
}

async function generateOne(row) {
  const items = await equipmentApi.batchQrcode([row.equipmentId])
  const exists = qrcodes.value.some((item) => item.equipmentId === row.equipmentId)
  if (!exists) {
    qrcodes.value = [...qrcodes.value, ...items]
  }
  activeTab.value = 'qrcode'
  ElMessage.success(`已生成 ${row.equipmentCode} 的二维码`)
}

async function download(item) {
  const blobUrl = await fetchImageBlob(`/api/v1/equipments/${item.equipmentId}/qrcode`)
  const link = document.createElement('a')
  link.href = blobUrl
  link.download = `二维码-${item.equipmentCode}.png`
  link.click()
  URL.revokeObjectURL(blobUrl)
}

async function printAll() {
  const urls = []
  for (const item of qrcodes.value) {
    urls.push({
      url: await fetchImageBlob(`/api/v1/equipments/${item.equipmentId}/qrcode`),
      code: item.equipmentCode,
      name: item.equipmentName,
      location: `${item.department} · ${item.location}`
    })
  }
  const html = `<!DOCTYPE html><html><head><meta charset="utf-8"><title>设备二维码标签</title>
    <style>
      body { font-family: "Microsoft YaHei", sans-serif; margin: 12px; }
      .label { display: inline-block; width: 180px; text-align: center; border: 1px dashed #999;
               padding: 8px; margin: 6px; page-break-inside: avoid; }
      .label img { width: 150px; height: 150px; }
      .code { font-weight: 700; margin-top: 4px; }
      .name { font-size: 12px; }
      .loc { font-size: 11px; color: #666; }
    </style></head><body>
    ${urls.map((u) => `<div class="label"><img src="${u.url}"><div class="code">${u.code}</div>
      <div class="name">${u.name}</div><div class="loc">${u.loc}</div></div>`).join('')}
    </body></html>`
  const win = window.open('', '_blank')
  win.document.write(html)
  win.document.close()
  win.focus()
  setTimeout(() => win.print(), 400)
}

onMounted(load)
</script>

<style scoped>
.qr-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 16px;
}

.qr-card {
  width: 186px;
  background: #fff;
  border-radius: 10px;
  padding: 12px;
  text-align: center;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
}

.qr-code {
  font-weight: 600;
  margin-top: 8px;
}

.qr-name {
  font-size: 13px;
}

.qr-loc {
  font-size: 12px;
  color: var(--sems-muted);
  min-height: 18px;
}
</style>
