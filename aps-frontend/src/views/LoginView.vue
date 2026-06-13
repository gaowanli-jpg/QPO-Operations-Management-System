<template>
  <div class="login-page">
    <section class="login-panel">
      <div class="login-brand">
        <div class="login-brand-mark">APS</div>
        <div>
          <h1>生产准备中心</h1>
          <p>使用管理员账号登录后进入系统</p>
        </div>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="handleLogin">
        <el-form-item label="用户名" prop="username">
          <el-input v-model.trim="form.username" size="large" placeholder="admin" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" size="large" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-button type="primary" size="large" class="login-button" :loading="loading" @click="handleLogin">
          登录
        </el-button>
      </el-form>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { request } from '../api'
import { setAuth } from '../auth'

const router = useRouter()
const route = useRoute()
const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  await formRef.value?.validate()
  loading.value = true
  try {
    const response = await request('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify(form)
    })
    setAuth(response.token, response.user)
    ElMessage.success(response.message || '登录成功')
    router.replace(route.query.redirect || '/sync')
  } catch (err) {
    ElMessage.error(err.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: #eef2f7;
  padding: 24px;
}

.login-panel {
  width: min(440px, 100%);
  background: #ffffff;
  border: 1px solid #d8e0e8;
  border-radius: 8px;
  padding: 32px;
  box-shadow: 0 18px 45px rgba(38, 57, 89, 0.12);
}

.login-brand {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 28px;
}

.login-brand-mark {
  width: 52px;
  height: 52px;
  border-radius: 8px;
  background: #f3c44f;
  color: #172331;
  display: grid;
  place-items: center;
  font-weight: 700;
}

h1 {
  font-size: 22px;
  margin: 0 0 6px;
  color: #202a35;
}

p {
  margin: 0;
  color: #667789;
  font-size: 13px;
}

.login-button {
  width: 100%;
  margin-top: 8px;
}
</style>
