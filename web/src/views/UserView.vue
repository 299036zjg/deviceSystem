<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">账号管理</h2>
        <div class="page-subtitle">
          账号只能停用不能删除；存在未完成工单的维修人员、最后一个启用的管理员不可停用；账号创建后不再修改角色
        </div>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增账号</el-button>
    </div>

    <div class="filter-bar">
      <el-input v-model="query.keyword" placeholder="账号 / 姓名" clearable style="width: 200px"
                @keyup.enter="load" @clear="load" />
      <el-select v-model="query.role" placeholder="角色" clearable style="width: 140px" @change="load">
        <el-option label="管理员" value="ADMIN" />
        <el-option label="报修人" value="REPORTER" />
        <el-option label="维修人员" value="TECHNICIAN" />
      </el-select>
      <el-select v-model="query.enabled" placeholder="状态" clearable style="width: 120px" @change="load">
        <el-option label="启用" :value="true" />
        <el-option label="停用" :value="false" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <div class="table-card">
      <el-table v-loading="loading" :data="rows" size="small" stripe>
        <el-table-column prop="account" label="账号" width="140" />
        <el-table-column prop="name" label="姓名" width="110" />
        <el-table-column prop="department" label="部门" width="120" />
        <el-table-column label="角色" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="roleType(row.role)" effect="plain">{{ row.roleLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openReset(row)">重置密码</el-button>
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

    <el-dialog v-model="createVisible" title="新增账号" width="520px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="96px">
        <el-form-item label="账号" prop="account">
          <el-input v-model="createForm.account" placeholder="3~32 位字母、数字或下划线" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="createForm.name" maxlength="50" />
        </el-form-item>
        <el-form-item label="部门" prop="department">
          <el-select v-model="createForm.department" filterable allow-create placeholder="请选择或输入部门"
                     style="width: 100%">
            <el-option v-for="d in departments" :key="d" :label="d" :value="d" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="createForm.role" placeholder="请选择角色" style="width: 100%">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="报修人" value="REPORTER" />
            <el-option label="维修人员" value="TECHNICIAN" />
          </el-select>
        </el-form-item>
        <el-form-item label="初始密码" prop="password">
          <el-input v-model="createForm.password" type="password" show-password
                    placeholder="8~64 位，须同时包含字母和数字" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetVisible" title="重置密码" width="460px">
      <div class="muted tip">重置后该用户既有会话立即失效，需使用新密码重新登录并修改。</div>
      <el-form ref="resetFormRef" :model="resetForm" :rules="resetRules" label-width="96px">
        <el-form-item label="新密码" prop="password">
          <el-input v-model="resetForm.password" type="password" show-password
                    placeholder="8~64 位，须同时包含字母和数字" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReset">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { userApi } from '@/api/modules'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const departments = ['设备科', '生产一部', '生产二部', '质检部', '动力车间', '维修部']
const query = reactive({ keyword: '', role: '', enabled: null, page: 1, size: 10 })

const createVisible = ref(false)
const resetVisible = ref(false)
const submitting = ref(false)
const createFormRef = ref()
const resetFormRef = ref()
const current = ref(null)

const createForm = reactive({ account: '', name: '', department: '', role: 'REPORTER', password: '' })
const resetForm = reactive({ password: '' })

const passwordPattern = /^(?=.*[A-Za-z])(?=.*\d).{8,64}$/

const createRules = {
  account: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{3,32}$/, message: '账号只能由 3~32 位字母、数字或下划线组成', trigger: 'blur' }
  ],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  department: [{ required: true, message: '请选择部门', trigger: 'change' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { pattern: passwordPattern, message: '密码须为 8~64 位且同时包含字母和数字', trigger: 'blur' }
  ]
}

const resetRules = {
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { pattern: passwordPattern, message: '密码须为 8~64 位且同时包含字母和数字', trigger: 'blur' }
  ]
}

function roleType(role) {
  return role === 'ADMIN' ? 'danger' : role === 'TECHNICIAN' ? 'warning' : 'primary'
}

async function load() {
  loading.value = true
  try {
    const data = await userApi.list({
      keyword: query.keyword || undefined,
      role: query.role || undefined,
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
  query.role = ''
  query.enabled = null
  query.page = 1
  load()
}

function openCreate() {
  createForm.account = ''
  createForm.name = ''
  createForm.department = ''
  createForm.role = 'REPORTER'
  createForm.password = ''
  createVisible.value = true
}

async function submitCreate() {
  await createFormRef.value.validate()
  submitting.value = true
  try {
    await userApi.create({ ...createForm })
    ElMessage.success('账号已创建')
    createVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

function openReset(row) {
  current.value = row
  resetForm.password = ''
  resetVisible.value = true
}

async function submitReset() {
  await resetFormRef.value.validate()
  submitting.value = true
  try {
    await userApi.resetPassword(current.value.userId, resetForm.password)
    ElMessage.success(`已重置 ${current.value.name} 的密码`)
    resetVisible.value = false
  } finally {
    submitting.value = false
  }
}

async function toggle(row, enable) {
  try {
    await ElMessageBox.confirm(
      enable ? `确认启用账号 ${row.account}？` : `确认停用账号 ${row.account}？停用后该用户立即掉线。`,
      enable ? '启用账号' : '停用账号',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  if (enable) {
    await userApi.enable(row.userId)
    ElMessage.success('账号已启用')
  } else {
    await userApi.disable(row.userId)
    ElMessage.success('账号已停用')
  }
  load()
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
