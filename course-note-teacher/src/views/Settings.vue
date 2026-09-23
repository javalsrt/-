<template>
  <div class="page-container">
    <div class="page-header">
      <h2>系统设置</h2>
      <p>{{ userStore.isAdmin ? '管理个人信息、账号安全与学期配置' : '管理个人信息、账号安全与偏好设置' }}</p>
    </div>

    <t-row :gutter="20">
      <t-col :span="12">
        <t-card title="个人资料" :bordered="false">
          <div class="profile-section">
            <t-avatar size="64px">{{ (userStore.userInfo?.realName || '教').charAt(0) }}</t-avatar>
            <div class="profile-info">
              <div class="profile-name">{{ userStore.userInfo?.realName || '教师' }}</div>
              <div class="profile-meta">{{ userStore.userInfo?.username || '-' }}</div>
            </div>
          </div>
          <t-form :data="profileForm" label-width="100px" class="setting-form">
            <t-form-item label="姓名">
              <t-input v-model="profileForm.realName" placeholder="请输入姓名" />
            </t-form-item>
            <t-form-item label="手机号">
              <t-input v-model="profileForm.phone" placeholder="请输入手机号" />
            </t-form-item>
            <t-form-item label="学校">
              <t-input v-model="profileForm.school" placeholder="请输入学校" />
            </t-form-item>
            <t-form-item label="学院">
              <t-input v-model="profileForm.college" placeholder="请输入学院" />
            </t-form-item>
            <t-form-item>
              <t-button theme="primary" :loading="savingProfile" @click="saveProfile">保存资料</t-button>
            </t-form-item>
          </t-form>
        </t-card>
      </t-col>

      <t-col :span="12">
        <t-card title="账号安全" :bordered="false">
          <t-form :data="passwordForm" :rules="passwordRules" label-width="120px" class="setting-form">
            <t-form-item label="原密码" name="oldPassword">
              <t-input v-model="passwordForm.oldPassword" type="password" placeholder="请输入原密码" />
            </t-form-item>
            <t-form-item label="新密码" name="newPassword">
              <t-input v-model="passwordForm.newPassword" type="password" placeholder="请输入新密码" />
            </t-form-item>
            <t-form-item label="确认新密码" name="confirmPassword">
              <t-input v-model="passwordForm.confirmPassword" type="password" placeholder="请再次输入新密码" />
            </t-form-item>
            <t-form-item>
              <t-button theme="primary" :loading="savingPassword" @click="changePassword">修改密码</t-button>
            </t-form-item>
          </t-form>
        </t-card>
      </t-col>
    </t-row>

    <t-row :gutter="20" style="margin-top:20px">
      <!-- 学期配置仅管理员可见（教师无学期管理权限） -->
      <t-col :span="12" v-if="userStore.isAdmin">
        <t-card title="学期配置" :bordered="false">
          <t-form :data="semesterForm" label-width="100px" class="setting-form">
            <t-form-item label="当前学期">
              <t-select v-model="semesterForm.currentSemester" placeholder="选择当前学期">
                <t-option v-for="s in semesterOptions" :key="s" :value="s" :label="s" />
              </t-select>
            </t-form-item>
            <t-form-item label="开学日期">
              <t-date-picker v-model="semesterForm.startDate" />
            </t-form-item>
            <t-form-item label="周数">
              <t-input-number v-model="semesterForm.weeks" :min="1" :max="30" />
            </t-form-item>
            <t-form-item>
              <t-button theme="primary" :loading="savingSemester" @click="saveSemester">保存学期</t-button>
            </t-form-item>
          </t-form>
        </t-card>
      </t-col>

      <t-col :span="userStore.isAdmin ? 12 : 24">
        <t-card title="偏好设置" :bordered="false">
          <t-form label-width="140px" class="setting-form">
            <t-form-item label="侧边栏收起">
              <t-switch v-model="prefForm.sidebarCollapsed" />
            </t-form-item>
            <t-form-item label="表格默认页数">
              <t-radio-group v-model="prefForm.pageSize">
                <t-radio-button :value="10">10</t-radio-button>
                <t-radio-button :value="20">20</t-radio-button>
                <t-radio-button :value="50">50</t-radio-button>
              </t-radio-group>
            </t-form-item>
            <t-form-item label="通知提醒">
              <t-switch v-model="prefForm.notification" />
            </t-form-item>
            <t-form-item>
              <t-button theme="primary" @click="savePreference">保存偏好</t-button>
            </t-form-item>
          </t-form>
        </t-card>
      </t-col>
    </t-row>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { MessagePlugin } from 'tdesign-vue-next'
import { useUserStore } from '@/stores/user'
import request from '@/utils/request'

const userStore = useUserStore()

const savingProfile = ref(false)
const savingPassword = ref(false)
const savingSemester = ref(false)

const profileForm = reactive({
  realName: '',
  phone: '',
  school: '',
  college: ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (val) => {
        if (!val) return { result: false, message: '请确认新密码', trigger: 'blur' }
        return val === passwordForm.newPassword ? { result: true } : { result: false, message: '两次输入密码不一致', trigger: 'blur' }
      }
    }
  ]
}

function generateSemesterOptions() {
  const options = []
  const now = new Date()
  const baseYear = now.getFullYear()
  for (let y = baseYear - 2; y <= baseYear + 1; y++) {
    options.push(`${y}-${y + 1}-1`)
    options.push(`${y}-${y + 1}-2`)
  }
  return options
}

const semesterOptions = ref(generateSemesterOptions())

const semesterForm = reactive({
  currentSemester: '',
  startDate: '',
  weeks: 16
})

const prefForm = reactive({
  sidebarCollapsed: false,
  pageSize: 10,
  notification: true
})

onMounted(() => {
  const info = userStore.userInfo || {}
  profileForm.realName = info.realName || ''
  profileForm.phone = info.phone || ''
  profileForm.school = info.school || ''
  profileForm.college = info.college || info.major || ''

  // 学期配置仅管理员加载
  if (userStore.isAdmin) loadSemester()

  const pref = localStorage.getItem('teacher_pref')
  if (pref) {
    try {
      Object.assign(prefForm, JSON.parse(pref))
    } catch (e) {}
  }
})

async function loadSemester() {
  try {
    const data = await request.get('/teacher/semester')
    if (data && data.currentSemester) {
      Object.assign(semesterForm, {
        currentSemester: data.currentSemester,
        startDate: data.semesterStartDate || '',
        weeks: data.semesterWeeks || 16
      })
      return
    }
  } catch (e) { console.warn('学期配置加载失败，使用本地/默认配置', e) }

  const saved = localStorage.getItem('teacher_semester')
  if (saved) {
    try {
      Object.assign(semesterForm, JSON.parse(saved))
    } catch (e) {}
  }
  // 默认当前学期
  if (!semesterForm.currentSemester) {
    const now = new Date()
    const year = now.getFullYear()
    const month = now.getMonth() + 1
    const startYear = month >= 9 ? year : year - 1
    const endYear = startYear + 1
    const term = month >= 3 && month <= 7 ? '2' : '1'
    semesterForm.currentSemester = `${startYear}-${endYear}-${term}`
  }
}

async function saveProfile() {
  savingProfile.value = true
  try {
    await request.put('/teacher/profile', profileForm)
    userStore.userInfo = { ...userStore.userInfo, ...profileForm }
    localStorage.setItem('teacher_info', JSON.stringify(userStore.userInfo))
    MessagePlugin.success('资料已保存')
  } catch (e) {
    // 后端接口可能不存在，降级到本地存储
    userStore.userInfo = { ...userStore.userInfo, ...profileForm }
    localStorage.setItem('teacher_info', JSON.stringify(userStore.userInfo))
    MessagePlugin.success('资料已保存（本地）')
  } finally {
    savingProfile.value = false
  }
}

async function changePassword() {
  if (!passwordForm.oldPassword || !passwordForm.newPassword || passwordForm.newPassword !== passwordForm.confirmPassword) {
    MessagePlugin.warning('请检查密码输入')
    return
  }
  savingPassword.value = true
  try {
    await request.put('/teacher/password', {
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    MessagePlugin.success('密码已修改，请重新登录')
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
  } catch (e) {
    MessagePlugin.error('密码修改失败：' + (e.message || '服务端未支持'))
  } finally {
    savingPassword.value = false
  }
}

async function saveSemester() {
  savingSemester.value = true
  try {
    await request.put('/teacher/profile', {
      semester: semesterForm.currentSemester,
      semesterStartDate: semesterForm.startDate,
      semesterWeeks: String(semesterForm.weeks)
    })
    localStorage.setItem('teacher_semester', JSON.stringify(semesterForm))
    MessagePlugin.success('学期配置已保存')
  } catch (e) {
    // 后端未支持时降级到本地
    localStorage.setItem('teacher_semester', JSON.stringify(semesterForm))
    MessagePlugin.success('学期配置已保存（本地）')
  } finally {
    savingSemester.value = false
  }
}

function savePreference() {
  localStorage.setItem('teacher_pref', JSON.stringify(prefForm))
  MessagePlugin.success('偏好设置已保存')
}
</script>

<style scoped>
.profile-section {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
  padding-bottom: 24px;
  border-bottom: 1px solid rgba(0,0,0,0.06);
}

.profile-name {
  font-size: 18px;
  font-weight: 600;
  color: #1D1D1F;
  margin-bottom: 4px;
}

.profile-meta {
  font-size: 13px;
  color: #86868B;
}

.setting-form :deep(.t-form__item) {
  margin-bottom: 18px;
}

.setting-form :deep(.t-form__item:last-child) {
  margin-bottom: 0;
}

@media (max-width: 900px) {
  .profile-section {
    margin-bottom: 20px;
    padding-bottom: 20px;
  }
}
</style>
