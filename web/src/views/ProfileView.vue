<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">个人中心</h2>
        <div class="page-subtitle">账号由管理员创建，角色不可自行修改；修改密码后需要重新登录</div>
      </div>
    </div>

    <el-row :gutter="14">
      <el-col :md="10">
        <div class="table-card">
          <div class="section-title" style="margin-top: 0">账号信息</div>
          <div class="detail-grid">
            <div class="detail-item"><span class="label">姓名</span>{{ profile.name || '-' }}</div>
            <div class="detail-item"><span class="label">账号</span>{{ profile.account || '-' }}</div>
            <div class="detail-item"><span class="label">部门</span>{{ profile.department || '-' }}</div>
            <div class="detail-item"><span class="label">角色</span>{{ profile.roleLabel || '-' }}</div>
            <div class="detail-item">
              <span class="label">状态</span>
              <el-tag size="small" :type="profile.enabled ? 'success' : 'info'">
                {{ profile.enabled ? '启用' : '停用' }}
              </el-tag>
            </div>
          </div>
          <div class="section-title">操作</div>
          <el-button @click="load">刷新信息</el-button>
          <el-button v-if="auth.isReporter" type="primary" @click="router.push('/repairs/create')">提交报修</el-button>
          <el-button @click="router.push('/dashboard')">返回首页</el-button>
        </div>
      </el-col>

      <el-col :md="14">
        <div class="table-card">
          <div class="section-title" style="margin-top: 0">修改密码</div>
          <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" style="max-width: 460px">
            <el-form-item label="原密码" prop="oldPassword">
              <el-input v-model="form.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="form.newPassword" type="password" show-password
                        placeholder="8~64 位，须同时包含字母和数字" />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="form.confirmPassword" type="password" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="submit">修改密码</el-button>
              <el-button @click="formRef.resetFields()">重置</el-button>
            </el-form-item>
          </el-form>
          <div class="muted">
            密码规则：8~64 位字符，必须同时包含字母和数字；密码以带盐摘要方式保存，服务端不保存明文。
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api/modules'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const profile = ref({})
const saving = ref(false)
const formRef = ref()
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const validateConfirm = (rule, value, callback) => {
  if (value !== form.newPassword) {
    callback(new Error('两次输入的新密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d).{8,64}$/, message: '密码须为 8~64 位且同时包含字母和数字', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ]
}

async function load() {
  profile.value = await authApi.profile()
}

async function submit() {
  await formRef.value.validate()
  saving.value = true
  try {
    await authApi.changePassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    ElMessage.success('密码修改成功，请重新登录')
    auth.clear()
    router.replace('/login')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
