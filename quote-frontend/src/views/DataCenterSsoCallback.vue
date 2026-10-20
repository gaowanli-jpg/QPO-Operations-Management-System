<template>
  <div class="data-center-sso-callback-page">
    <section class="data-center-sso-callback-panel" aria-live="polite">
      <div class="data-center-sso-callback-mark">QPO</div>
      <h1>数据中心登录</h1>
      <p>{{ statusText }}</p>
      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        show-icon
        :closable="false"
      />
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/authStore'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const loading = ref(true)
const errorMessage = ref('')

const statusText = computed(() => (
  errorMessage.value || (loading.value ? '正在验证数据中心登录票据，请稍候。' : '登录成功，正在进入 QPO。')
))

const queryString = (value) => Array.isArray(value) ? value[0] || '' : value || ''

const safeRedirectPath = (candidate) => {
  const value = String(candidate || '/dashboard').trim() || '/dashboard'
  if (!value.startsWith('/') || value.startsWith('//') || value.startsWith('http://') || value.startsWith('https://')) {
    return '/dashboard'
  }
  return value
}

const buildPayload = () => ({
  appCode: queryString(route.query.appCode),
  username: queryString(route.query.username),
  displayName: queryString(route.query.displayName),
  timestamp: Number(queryString(route.query.timestamp)),
  nonce: queryString(route.query.nonce),
  redirect: queryString(route.query.redirect) || '/dashboard',
  signature: queryString(route.query.signature)
})

const exchangeTicket = async () => {
  const payload = buildPayload()
  try {
    const response = await authStore.loginWithDataCenterSso(payload)
    const target = safeRedirectPath(response?.redirect || payload.redirect || '/dashboard')
    loading.value = false
    await router.replace(target)
  } catch (error) {
    const message = error?.message || '数据中心登录失败，请重新从数据中心进入 QPO。'
    errorMessage.value = message
    loading.value = false
    authStore.clearAuth()
    router.replace({ path: '/login', query: { error: message } })
  }
}

onMounted(exchangeTicket)
</script>

<style scoped>
.data-center-sso-callback-page {
  background: #eef2f7;
  display: grid;
  min-height: 100vh;
  padding: 24px;
  place-items: center;
}

.data-center-sso-callback-panel {
  background: #fff;
  border: 1px solid #d8dee8;
  border-radius: 8px;
  box-shadow: 0 18px 45px rgba(38, 57, 89, 0.12);
  display: grid;
  gap: 14px;
  max-width: 420px;
  padding: 32px;
  text-align: center;
  width: min(420px, 100%);
}

.data-center-sso-callback-mark {
  align-items: center;
  background: #1f4e79;
  border-radius: 8px;
  color: #fff;
  display: inline-grid;
  font-weight: 700;
  height: 52px;
  justify-self: center;
  width: 52px;
}

.data-center-sso-callback-panel h1 {
  color: #1f2d3d;
  font-size: 22px;
  margin: 0;
}

.data-center-sso-callback-panel p {
  color: #6b778c;
  font-size: 14px;
  line-height: 1.6;
  margin: 0;
}
</style>
