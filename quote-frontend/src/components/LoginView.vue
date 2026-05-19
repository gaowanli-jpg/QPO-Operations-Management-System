<template>
  <div class="login-page">
    <section class="login-panel">
      <div class="brand">
        <div class="brand-mark">ERP</div>
        <div>
          <h1>智能运营管理系统</h1>
          <p>按部门账号登录后进入对应业务模块</p>
        </div>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="handleLogin">
        <el-form-item label="用户名" prop="username">
          <el-input v-model.trim="form.username" size="large" placeholder="admin / market / engineer / warehouse" />
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
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import { API_BASE_URL } from '../config'
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

const roleHome = {
  ADMIN: '/market',
  MARKET: '/market',
  ENGINEER: '/engineering',
  WAREHOUSE: '/warehouse'
}

const handleLogin = async () => {
  await formRef.value?.validate()
  loading.value = true
  try {
    const response = await axios.post(`${API_BASE_URL}/api/auth/login`, form)
    if (!response.data?.success) {
      throw new Error(response.data?.message || '登录失败')
    }
    setAuth(response.data.token, response.data.user)
    ElMessage.success('登录成功')
    const redirect = route.query.redirect || roleHome[response.data.user?.role] || '/market'
    router.replace(redirect)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '登录失败')
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
  background: #fff;
  border: 1px solid #d8dee8;
  border-radius: 8px;
  padding: 32px;
  box-shadow: 0 18px 45px rgba(38, 57, 89, 0.12);
}

.brand {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 28px;
}

.brand-mark {
  width: 52px;
  height: 52px;
  border-radius: 8px;
  background: #1f4e79;
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 700;
}

h1 {
  font-size: 22px;
  margin: 0 0 6px;
  color: #1f2d3d;
}

p {
  margin: 0;
  color: #6b778c;
  font-size: 13px;
}

.login-button {
  width: 100%;
  margin-top: 8px;
}

</style>
