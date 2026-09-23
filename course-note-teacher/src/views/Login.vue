<template>
  <div class="login-page">
    <div class="login-container">
      <!-- Logo & 标题 — 大留白，居中 -->
      <div class="login-brand">
        <div class="brand-icon">
          <svg width="36" height="36" viewBox="0 0 32 32" fill="none">
            <rect width="32" height="32" rx="8" fill="#1D1D1F"/>
            <path d="M10 16L14 20L22 12" stroke="white" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <h1 class="brand-title">课堂助手</h1>
        <p class="brand-subtitle">教师管理平台</p>
      </div>

      <!-- 登录表单 — 极简白色卡片 -->
      <t-form
        ref="formRef"
        :data="formData"
        :rules="rules"
        label-width="0"
        class="login-form"
        @submit="handleLogin"
      >
        <t-form-item name="username">
          <t-input
            v-model="formData.username"
            placeholder="用户名"
            size="large"
            clearable
          >
            <template #prefix-icon>
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                <circle cx="8" cy="5" r="3" stroke="#86868B" stroke-width="1.2"/>
                <path d="M2 14C2 11.2 4.7 9 8 9C11.3 9 14 11.2 14 14" stroke="#86868B" stroke-width="1.2" stroke-linecap="round"/>
              </svg>
            </template>
          </t-input>
        </t-form-item>
        <t-form-item name="password">
          <t-input
            v-model="formData.password"
            type="password"
            placeholder="密码"
            size="large"
            clearable
            @enter="handleLogin"
          >
            <template #prefix-icon>
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                <rect x="3" y="7" width="10" height="7" rx="1.5" stroke="#86868B" stroke-width="1.2"/>
                <path d="M5 7V4.5C5 2.8 6.3 1.5 8 1.5C9.7 1.5 11 2.8 11 4.5V7" stroke="#86868B" stroke-width="1.2" stroke-linecap="round"/>
              </svg>
            </template>
          </t-input>
        </t-form-item>
        <div v-if="errorMsg" class="login-error">{{ errorMsg }}</div>
        <t-form-item>
          <t-button
            theme="primary"
            type="submit"
            block
            size="large"
            :loading="loading"
            class="login-btn"
          >
            登录
          </t-button>
        </t-form-item>
      </t-form>

      <div class="login-footer">
        <span class="login-tip">管理员 / 教师账号 | 学生请使用移动端或学生端登录</span>
        <span class="login-accounts">管理员 admin · 教师账号由管理员在「人员管理」中创建</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { MessagePlugin } from 'tdesign-vue-next'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)
const loading = ref(false)

const errorMsg = ref('')

const formData = reactive({
  username: 'admin',
  password: 'admin123'
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin({ validateResult }) {
  if (validateResult !== true) return
  loading.value = true
  errorMsg.value = ''
  try {
    const user = await userStore.login(formData.username, formData.password)
    if (user.role === 'student') {
      errorMsg.value = '学生账号请使用移动端或学生端登录'
      userStore.logout()
      loading.value = false
      return
    }
    MessagePlugin.success('登录成功')
    router.push('/dashboard')
  } catch (e) {
    errorMsg.value = e.message || '登录失败'
    MessagePlugin.error(e.message || '登录失败')
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
  background: #FAFAFA;
  padding: 24px;
}

.login-container {
  width: 100%;
  max-width: 400px;
}

/* Brand — 大留白，居中 */
.login-brand {
  text-align: center;
  margin-bottom: 48px;
}

.brand-icon {
  display: flex;
  justify-content: center;
  margin-bottom: 20px;
}

.brand-title {
  font-size: 28px;
  font-weight: 700;
  color: #1D1D1F;
  letter-spacing: -0.025em;
  margin-bottom: 6px;
  line-height: 1.2;
}

.brand-subtitle {
  font-size: 15px;
  color: #86868B;
  font-weight: 400;
  line-height: 1.5;
}

/* 表单 — 白色卡片，轻微圆角，极淡边框 */
.login-form {
  background: #FFFFFF;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 16px;
  padding: 32px;
  transition: box-shadow 0.3s ease;
}
.login-form:hover {
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
}

.login-form :deep(.t-form-item) {
  margin-bottom: 20px;
}

.login-form :deep(.t-input) {
  height: 48px;
  border-radius: 10px;
  border-color: rgba(0, 0, 0, 0.08);
  background: #FAFAFA;
  font-size: 15px;
  padding-left: 12px;
  transition: all 0.2s ease;
}
.login-form :deep(.t-input:hover) {
  border-color: rgba(0, 0, 0, 0.12);
}
.login-form :deep(.t-input:focus),
.login-form :deep(.t-input.t-is-focused) {
  background: #FFFFFF;
  border-color: #0071E3;
  box-shadow: 0 0 0 3px rgba(0, 113, 227, 0.08);
}

.login-form :deep(.t-input__inner) {
  font-size: 15px;
}

.login-form :deep(.t-input__suffix-icon) {
  color: #86868B;
}

.login-btn {
  height: 48px !important;
  border-radius: 10px !important;
  font-size: 15px !important;
  font-weight: 500 !important;
  letter-spacing: 0.02em;
  background: #1D1D1F !important;
  border-color: #1D1D1F !important;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1) !important;
}
.login-btn:hover {
  opacity: 0.85 !important;
}

.login-error {
  background: rgba(255, 69, 58, 0.08);
  color: #E5484D;
  font-size: 13px;
  padding: 10px 14px;
  border-radius: 10px;
  margin-bottom: 18px;
  line-height: 1.5;
}

/* Footer */
.login-footer {
  text-align: center;
  margin-top: 24px;
}

.login-tip {
  font-size: 13px;
  color: #AEAEB2;
}

.login-accounts {
  display: block;
  font-size: 11px;
  color: #C7C7CC;
  margin-top: 4px;
  word-break: break-all;
  line-height: 1.6;
}

@media (max-width: 480px) {
  .login-form {
    padding: 24px;
  }
  .brand-title {
    font-size: 24px;
  }
}
</style>
