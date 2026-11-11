<template>
  <el-container class="layout">
    <el-aside width="212px" class="aside">
      <div class="brand">
        <div class="brand-mark">设</div>
        <div>
          <div class="brand-title">设备维修保养系统</div>
          <div class="brand-sub">课程设计第 16 组</div>
        </div>
      </div>
      <el-menu :default-active="activeMenu" router class="menu" background-color="#1f2d3d"
               text-color="#c7d0dd" active-text-color="#ffffff">
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <span class="header-title">{{ currentTitle }}</span>
          <el-tag size="small" type="info" effect="plain">{{ auth.roleLabel }}</el-tag>
        </div>
        <div class="header-right">
          <el-dropdown @command="onCommand">
            <span class="user">
              <el-icon><UserFilled /></el-icon>
              {{ auth.displayName }}（{{ auth.user?.account }}）
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <el-alert v-if="auth.user?.mustChangePassword" type="warning" show-icon :closable="false"
                  title="密码已被管理员重置，请先修改本人密码"
                  class="pwd-alert">
          <template #default>
            为保护账号安全，请尽快到「个人中心」修改密码后再继续处理业务。
          </template>
        </el-alert>
        <router-view v-slot="{ Component }">
          <component :is="Component" :key="$route.fullPath" />
        </router-view>
      </el-main>
    </el-container>

    <el-dialog v-model="passwordVisible" title="修改密码" width="420px">
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="96px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password
                    placeholder="8~64 位，须同时包含字母和数字" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordSaving" @click="submitPassword">确定</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/api/modules'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const allMenus = [
  { path: '/dashboard', title: '首页', icon: 'HomeFilled', roles: ['ADMIN', 'REPORTER', 'TECHNICIAN'] },
  { path: '/equipments', title: '设备台账', icon: 'Monitor', roles: ['ADMIN'] },
  { path: '/repairs', title: '报修管理', icon: 'Tickets', roles: ['ADMIN', 'REPORTER'] },
  { path: '/repairs/create', title: '提交报修', icon: 'EditPen', roles: ['REPORTER'] },
  { path: '/orders', title: '工单管理', icon: 'Tools', roles: ['ADMIN', 'TECHNICIAN'] },
  { path: '/acceptances', title: '待验收', icon: 'CircleCheck', roles: ['ADMIN'] },
  { path: '/statistics', title: '查询统计', icon: 'DataAnalysis', roles: ['ADMIN'] },
  { path: '/users', title: '账号管理', icon: 'User', roles: ['ADMIN'] }
]

const menus = computed(() => allMenus.filter((m) => m.roles.includes(auth.role)))
const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/repairs/') && path !== '/repairs/create') return '/repairs'
  if (path.startsWith('/orders/')) return '/orders'
  return path
})
const currentTitle = computed(() => route.meta.title || '首页')

const passwordVisible = ref(false)
const passwordSaving = ref(false)
const passwordFormRef = ref()
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const validateConfirm = (rule, value, callback) => {
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的新密码不一致'))
  } else {
    callback()
  }
}

const passwordRules = {
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

async function submitPassword() {
  await passwordFormRef.value.validate()
  passwordSaving.value = true
  try {
    await authApi.changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    ElMessage.success('密码修改成功，请重新登录')
    auth.clear()
    router.replace('/login')
  } finally {
    passwordSaving.value = false
  }
}

async function onCommand(command) {
  if (command === 'profile') {
    router.push('/profile')
    return
  }
  if (command === 'password') {
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    passwordVisible.value = true
    return
  }
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确认退出登录？', '退出', { type: 'warning' })
    } catch (e) {
      return
    }
    await auth.logout()
    ElMessage.success('已退出登录')
    router.replace('/login')
  }
}
</script>

<style scoped>
.layout {
  height: 100vh;
}

.aside {
  background: #1f2d3d;
  color: #fff;
  display: flex;
  flex-direction: column;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.brand-mark {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: var(--sems-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
}

.brand-title {
  font-size: 14px;
  font-weight: 600;
}

.brand-sub {
  font-size: 11px;
  color: #8b9bb4;
  margin-top: 2px;
}

.menu {
  border-right: none;
  flex: 1;
}

.menu :deep(.el-menu-item.is-active) {
  background: var(--sems-primary) !important;
}

.header {
  background: #fff;
  border-bottom: 1px solid var(--sems-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
}

.user {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: var(--sems-text);
  outline: none;
}

.main {
  background: var(--sems-bg);
  padding: 0;
  overflow: auto;
}

.pwd-alert {
  margin: 12px 20px 0;
  width: auto;
}
</style>
