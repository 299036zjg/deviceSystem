<template>
  <div class="login-page">
    <div class="login-card">
      <div class="intro">
        <div class="intro-title">设备维修保养系统</div>
        <div class="intro-sub">设备台账 · 故障报修 · 派工维修 · 保养验收</div>
        <ul class="intro-list">
          <li>报修人提交故障并上传现场照片</li>
          <li>管理员受理派工、对照两类照片验收</li>
          <li>维修人员开工、提交处理结果与完工照片</li>
          <li>小程序扫码报修，与 Web 后台共用同一套状态</li>
        </ul>
        <div class="intro-foot">软件工程课程设计 · 第 16 组</div>
      </div>

      <div class="form-side">
        <div class="form-title">账号登录</div>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="submit">
          <el-form-item label="账号" prop="account">
            <el-input v-model="form.account" placeholder="请输入账号" autocomplete="username" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" show-password
                      placeholder="请输入密码" autocomplete="current-password" />
          </el-form-item>
          <el-button type="primary" class="submit" :loading="loading" @click="submit">登 录</el-button>
        </el-form>

        <div class="hint">
          <div class="hint-title">演示账号</div>
          <div>管理员 admin / admin123</div>
          <div>维修人员 tech1 / tech123</div>
          <div>报修人 rep1 / rep12345</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const formRef = ref()
const loading = ref(false)
const form = reactive({ account: '', password: '' })

const rules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    await auth.login(form.account.trim(), form.password)
    ElMessage.success(`欢迎回来，${auth.displayName}`)
    const redirect = route.query.redirect
    router.replace(typeof redirect === 'string' && redirect ? redirect : '/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #eef3ff 0%, #f7f9fc 55%, #eaf1ff 100%);
  padding: 24px;
}

.login-card {
  width: 920px;
  max-width: 100%;
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 18px 40px rgba(31, 45, 61, 0.14);
}

.intro {
  padding: 40px 36px;
  background: linear-gradient(160deg, #2f6fed, #1f4fc4);
  color: #fff;
  display: flex;
  flex-direction: column;
}

.intro-title {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 1px;
}

.intro-sub {
  margin-top: 8px;
  font-size: 13px;
  opacity: 0.88;
}

.intro-list {
  margin: 26px 0 0;
  padding-left: 18px;
  line-height: 2;
  font-size: 13px;
  opacity: 0.95;
}

.intro-foot {
  margin-top: auto;
  font-size: 12px;
  opacity: 0.75;
}

.form-side {
  padding: 44px 40px 32px;
}

.form-title {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 20px;
}

.submit {
  width: 100%;
  margin-top: 6px;
}

.hint {
  margin-top: 26px;
  padding: 12px 14px;
  background: #f7f9fc;
  border: 1px dashed var(--sems-border);
  border-radius: 8px;
  color: var(--sems-muted);
  font-size: 12px;
  line-height: 1.9;
}

.hint-title {
  color: var(--sems-text);
  font-weight: 600;
  margin-bottom: 2px;
}

@media (max-width: 820px) {
  .login-card {
    grid-template-columns: 1fr;
  }

  .intro {
    display: none;
  }
}
</style>
