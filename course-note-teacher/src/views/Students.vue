<template>
  <div class="page-container">
    <div class="page-header student-header">
      <div>
        <h2>学生管理</h2>
        <p>{{ isAdmin ? '计算机科学与技术学院 · 共 ' + totalStudents + ' 名学生' : '我教授的班级 · 共 ' + totalStudents + ' 名学生' }}</p>
      </div>
      <div class="header-actions">
        <t-input
          v-model="searchKeyword"
          placeholder="搜索姓名 / 学号"
          clearable
          class="student-search"
        >
          <template #prefix-icon><t-icon name="search" /></template>
        </t-input>
        <template v-if="isAdmin">
          <input
            ref="importInput"
            type="file"
            accept=".xlsx,.xls"
            class="import-input"
            @change="handleImportFile"
          />
          <t-button
            theme="default"
            variant="outline"
            :loading="importing"
            title="请使用标准模板上传 .xlsx/.xls 文件，仅支持导入学生名单"
            @click="triggerImport"
          >
            <template #icon><t-icon name="upload" /></template>
            {{ importing ? '导入中...' : '批量导入' }}
          </t-button>
          <t-button theme="primary" @click="openCreate">
            <template #icon><t-icon name="add" /></template>
            新增学生
          </t-button>
        </template>
      </div>
    </div>

    <!-- 导入期间全屏遮罩：锁定页面操作 -->
    <div v-if="importing" class="import-mask">
      <div class="import-mask-card">
        <t-loading size="large" theme="primary" text="正在导入，请稍候..." />
        <div class="import-mask-tip">导入期间已锁定页面操作，请勿刷新或关闭页面</div>
      </div>
    </div>

    <!-- 导入结果提示（从 sessionStorage 恢复，30 分钟内有效） -->
    <div v-if="importResult" class="import-result" :class="importResult.success ? 'is-success' : 'is-warning'">
      <t-icon :name="importResult.success ? 'check-circle-filled' : 'info-circle-filled'" size="20px" />
      <div class="import-result-body">
        <div class="import-result-msg">{{ importResult.message }}</div>
        <template v-if="importResult.errors?.length">
          <details class="import-result-details">
            <summary>{{ importResult.errors.length }} 条数据异常，点击查看详情</summary>
            <div class="import-result-errors">
              <div v-for="(e, i) in importResult.errors" :key="i" class="import-error-line">
                <span class="danger-text">第 {{ e.row || '—' }} 行</span>
                <span>{{ e.reason || e.message || e }}</span>
              </div>
            </div>
          </details>
        </template>
      </div>
      <span class="import-result-close" @click="clearImportResult">
        <t-icon name="close" size="16px" />
      </span>
    </div>


    <t-skeleton :loading="loading" animation="gradient" theme="article">
      <!-- 班级卡片：默认以班级为单位展示整体数据 -->
      <div class="class-cards">
        <div
          v-for="(cls, idx) in classSummaries"
          :key="cls.name"
          class="class-card animated-card-item"
          :class="{ active: activeClass === cls.name }"
          :style="{ '--accent': cls.color, '--index': idx }"
          @click="selectClass(cls)"
        >
          <div class="class-avatar" :style="{ background: cls.color, opacity: 0.9 }">
            {{ cls.name === '全部班级' ? '全' : cls.name.slice(-2) }}
          </div>
          <div class="class-info">
            <div class="class-name">{{ cls.name }}</div>
            <div class="class-count">{{ cls.studentCount }} 名学生 · 人均 {{ cls.avgKbDocCount }} 知识量 · {{ cls.avgMaterialCount }} 资料</div>
          </div>
          <t-icon :name="activeClass === cls.name ? 'chevron-up' : 'chevron-down'" size="20px" style="color:#606078" />
        </div>
      </div>

      <!-- 班级整体平均数据表格（默认视图） -->
      <t-card v-if="viewMode === 'class'" :bordered="false" title="班级整体数据概览">
        <t-table :data="classSummaries.filter(c => c.name !== '全部班级')" :columns="classColumns" row-key="name" stripe hover>
          <template #className="{ row }">
            <div class="class-cell">
              <span class="class-dot" :style="{ background: row.color }"></span>
              <span>{{ row.name }}</span>
            </div>
          </template>
          <template #avgKbDocCount="{ row }">
            <t-tag :theme="row.avgKbDocCount > 0 ? 'primary' : 'default'" variant="light" size="small">{{ row.avgKbDocCount }}</t-tag>
          </template>
          <template #avgMaterialCount="{ row }">
            <t-tag :theme="row.avgMaterialCount > 0 ? 'success' : 'default'" variant="light" size="small">{{ row.avgMaterialCount }}</t-tag>
          </template>
          <template #activeScore="{ row }">
            <div class="active-bar">
              <div class="active-track">
                <div class="active-fill" :style="{ width: row.activeScore + '%', background: row.color }"></div>
              </div>
              <span class="active-text">{{ row.activeScore }}</span>
            </div>
          </template>
          <template #operation="{ row }">
            <t-link theme="primary" @click.stop="selectClass(row)">查看学生</t-link>
          </template>
        </t-table>
        <div v-if="!loading && classSummaries.filter(c => c.name !== '全部班级').length === 0" class="empty-tip">
          <t-icon name="user" size="48px" style="color:#E5E7EB;margin-bottom:12px" />
          <div>暂无班级数据</div>
        </div>
      </t-card>

      <!-- 学生明细列表（点击班级后切换显示） -->
      <t-card v-else :bordered="false" :title="studentTableTitle">
        <template #actions>
          <t-button theme="default" size="small" @click="backToClassView">
            <template #icon><t-icon name="rollback" /></template>
            返回班级视图
          </t-button>
        </template>
        <t-table :data="displayStudents" :columns="studentColumns" row-key="id" :pagination="{ defaultPageSize: 10 }" stripe hover @row-click="goDetail">
          <template #className="{ row }">
            <t-tag variant="light" size="small" :style="{ background: studentClassColorOf(row) + '14', color: studentClassColorOf(row) }">
              {{ classNameOf(row) }}
            </t-tag>
          </template>
          <template #kbDocCount="{ row }">
            <t-tag :theme="row.kbDocCount > 0 ? 'primary' : 'default'" variant="light" size="small">{{ row.kbDocCount || 0 }}</t-tag>
          </template>
          <template #materialCount="{ row }">
            <t-tag :theme="row.materialCount > 0 ? 'success' : 'default'" variant="light" size="small">{{ row.materialCount || 0 }}</t-tag>
          </template>
          <template #operation="{ row }">
            <t-space size="4">
              <t-link theme="primary" @click.stop="goDetail({ row })">知识图谱</t-link>
              <template v-if="isAdmin">
                <t-button theme="default" variant="text" size="small" @click.stop="openEdit(row)">编辑</t-button>
                <t-button theme="default" variant="text" size="small" @click.stop="openReset(row)">重置密码</t-button>
                <t-popconfirm
                  :content="`确定要删除「${row.nickname || row.realName || row.username}」吗？该操作为软删除，可在数据库中恢复。`"
                  theme="danger"
                  @confirm="confirmDelete(row)"
                >
                  <t-button theme="danger" variant="text" size="small">删除</t-button>
                </t-popconfirm>
              </template>
            </t-space>
          </template>
        </t-table>
        <div v-if="!loading && displayStudents.length === 0" class="empty-tip">
          <t-icon name="user" size="48px" style="color:#E5E7EB;margin-bottom:12px" />
          <div>暂无学生数据</div>
        </div>
      </t-card>
    </t-skeleton>

    <!-- 新增 / 编辑学生（管理员） -->
    <t-dialog
      v-if="isAdmin"
      v-model:visible="formVisible"
      :header="editingUser ? '编辑学生' : '新增学生'"
      width="560px"
      :confirm-btn="{ content: submitting ? '提交中...' : '确定', loading: submitting }"
      :cancel-btn="submitting ? null : undefined"
      :on-confirm="handleSubmit"
      @close="resetForm"
    >
      <t-form :label-width="90" :data="form" class="staff-form">
        <t-form-item label="姓名">
          <t-input v-model="form.realName" placeholder="请输入姓名" />
        </t-form-item>
        <t-form-item label="账号">
          <t-input v-model="form.username" placeholder="请输入登录账号" :disabled="!!editingUser" />
        </t-form-item>
        <t-form-item :label="editingUser ? '重置密码' : '初始密码'" :help="editingUser ? '编辑时留空则不改动密码' : '默认 123456'">
          <t-input v-model="form.password" :placeholder="editingUser ? '留空则不修改' : '默认 123456'" type="password" />
        </t-form-item>
        <t-form-item label="角色">
          <t-select v-model="form.role" :options="roleOptions" class="form-select" />
        </t-form-item>
        <t-form-item v-if="form.role === 1" label="学号">
          <t-input v-model="form.studentNo" placeholder="请输入学号" />
        </t-form-item>
        <t-form-item label="手机号">
          <t-input v-model="form.phone" placeholder="请输入手机号" />
        </t-form-item>
        <t-form-item label="邮箱">
          <t-input v-model="form.email" placeholder="请输入邮箱" />
        </t-form-item>
        <template v-if="form.role === 1">
          <t-form-item label="所属班级">
            <t-select v-model="form.classId" :options="classOptions" placeholder="请选择班级" allow-clear class="form-select" />
          </t-form-item>
          <t-form-item label="专业">
            <t-input v-model="form.major" placeholder="请输入专业" />
          </t-form-item>
          <t-form-item label="年级">
            <t-input v-model="form.grade" placeholder="如 2024级" />
          </t-form-item>
        </template>
      </t-form>
    </t-dialog>

    <!-- 重置密码 -->
    <t-dialog
      v-if="isAdmin"
      v-model:visible="resetVisible"
      header="重置密码"
      width="420px"
      :confirm-btn="{ content: resetting ? '重置中...' : '确认重置', loading: resetting }"
      :cancel-btn="resetting ? null : undefined"
      :on-confirm="confirmResetPassword"
    >
      <div class="reset-tip">
        将「{{ resettingUser?.realName || resettingUser?.username || resettingUser?.nickname }}」的密码重置为以下新密码：
      </div>
      <t-input v-model="newPassword" placeholder="请输入新密码" type="password" />
    </t-dialog>
  </div>
</template>

<script setup>
import { ref, computed, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { MessagePlugin } from 'tdesign-vue-next'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import {
  createUser,
  updateUser,
  resetPassword,
  deleteUser,
  getClasses,
  importStudents
} from '@/api/staff'

const router = useRouter()
const userStore = useUserStore()
const isAdmin = computed(() => userStore.isAdmin)
const allStudents = ref([])
const activeClass = ref('')
const viewMode = ref('class')
const searchKeyword = ref('')
const loading = ref(false)

// ===== 用户/账号管理（管理员）=====
const IMPORT_RESULT_KEY = 'admin_staff_import_result'
const ROLE_LABEL = { 1: '学生', 2: '教师', 3: '管理员' }
const ROLE_THEME = { 1: 'primary', 2: 'success', 3: 'warning' }
const roleOptions = [
  { value: 1, label: '学生' },
  { value: 2, label: '教师' },
  { value: 3, label: '管理员' }
]

const classes = ref([])
const formVisible = ref(false)
const editingUser = ref(null)
const submitting = ref(false)
const form = reactive({
  username: '',
  password: '123456',
  realName: '',
  role: 1,
  studentNo: '',
  phone: '',
  email: '',
  classId: undefined,
  major: '',
  grade: ''
})
const resetVisible = ref(false)
const resettingUser = ref(null)
const newPassword = ref('123456')
const resetting = ref(false)
const importInput = ref(null)
const importing = ref(false)
const importResult = ref(null)

const classOptions = computed(() =>
  classes.value.map((c) => ({ value: Number(c.id), label: c.className }))
)
const roleLabel = (r) => ROLE_LABEL[r] || '-'
const roleTheme = (r) => ROLE_THEME[r] || 'default'



const classColorPalette = ['#6366f1', '#34d399', '#fb923c', '#f43f5e', '#8b5cf6', '#06b6d4', '#84cc16', '#f59e0b']

const classColumns = [
  { colKey: 'className', title: '班级', width: 140 },
  { colKey: 'studentCount', title: '学生人数', width: 100, align: 'center' },
  { colKey: 'totalKbDocCount', title: '知识总量', width: 100, align: 'center' },
  { colKey: 'avgKbDocCount', title: '人均知识量', width: 110, align: 'center' },
  { colKey: 'totalMaterialCount', title: '资料总量', width: 100, align: 'center' },
  { colKey: 'avgMaterialCount', title: '人均资料量', width: 110, align: 'center' },
  { colKey: 'activeScore', title: '班级活跃度', width: 160 },
  { colKey: 'operation', title: '操作', width: 100 }
]

const studentColumns = [
  { colKey: 'className', title: '班级', width: 100 },
  { colKey: 'nickname', title: '姓名', width: 100 },
  { colKey: 'studentNo', title: '学号', width: 120 },
  { colKey: 'major', title: '专业', width: 150 },
  { colKey: 'grade', title: '年级', width: 80 },
  { colKey: 'kbDocCount', title: '知识量', width: 80, align: 'center' },
  { colKey: 'materialCount', title: '资料', width: 70, align: 'center' },
  { colKey: 'operation', title: '操作', width: 280 }
]

/** 班级名称：优先后端返回的真实班级名（className），历史数据回退 grade */
function classNameOf(student) {
  return student?.className || student?.grade || '-'
}

/** 班级颜色映射：按班级名稳定分配 */
const classColorMap = new Map()
function classColorOf(className) {
  if (!className || className === '-') return '#86868B'
  if (!classColorMap.has(className)) {
    const idx = classColorMap.size % classColorPalette.length
    classColorMap.set(className, classColorPalette[idx])
  }
  return classColorMap.get(className)
}

function studentClassColorOf(student) {
  return classColorOf(classNameOf(student))
}

function classSummary(name, color, students, idx) {
  const count = students.length
  const totalKb = students.reduce((s, r) => s + (r.kbDocCount || 0), 0)
  const totalMat = students.reduce((s, r) => s + (r.materialCount || 0), 0)
  const avgKb = count ? (totalKb / count).toFixed(1) : '0.0'
  const avgMat = count ? (totalMat / count).toFixed(1) : '0.0'
  const maxScore = 100
  const score = Math.min(maxScore, Math.round((totalKb + totalMat) / (count || 1) * 2))
  return {
    name,
    color,
    students,
    studentCount: count,
    totalKbDocCount: totalKb,
    totalMaterialCount: totalMat,
    avgKbDocCount: avgKb,
    avgMaterialCount: avgMat,
    activeScore: score
  }
}

const classSummaries = computed(() => {
  // 按 grade 字段动态分组
  const groupMap = new Map()
  allStudents.value.forEach(s => {
    const name = classNameOf(s)
    if (!groupMap.has(name)) groupMap.set(name, [])
    groupMap.get(name).push(s)
  })
  // 保持颜色稳定
  groupMap.forEach((_, name) => classColorOf(name))

  const summaries = Array.from(groupMap.entries())
    .sort((a, b) => a[0].localeCompare(b[0], 'zh-CN'))
    .map(([name, students], i) => classSummary(name, classColorOf(name), students, i))

  const allSummary = classSummary('全部班级', '#0071e3', allStudents.value, -1)
  return [allSummary, ...summaries]
})

const totalStudents = computed(() => allStudents.value.length)
const displayStudents = computed(() => {
  const cls = classSummaries.value.find(c => c.name === activeClass.value)
  const students = cls ? cls.students : []
  if (!searchKeyword.value) return students
  const kw = searchKeyword.value.trim().toLowerCase()
  return students.filter(s =>
    (s.nickname || '').toLowerCase().includes(kw) ||
    (s.studentNo || '').toLowerCase().includes(kw)
  )
})

const studentTableTitle = computed(() => {
  const cls = classSummaries.value.find(c => c.name === activeClass.value)
  return cls ? `${cls.name} 学生明细 · ${cls.studentCount} 人` : '学生明细'
})

async function reloadStudents() {
  try {
    const data = await request.get('/teacher/students')
    // 使用后端返回的 studentNo，不伪造
    allStudents.value = data || []
  } catch (e) {
    console.warn('学生加载失败', e)
    MessagePlugin.error('学生数据加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  loading.value = true
  if (isAdmin.value) {
    try {
      classes.value = (await getClasses()) || []
    } catch (e) {
      classes.value = []
    }
    restoreImportResult()
  }
  await reloadStudents()
})

function selectClass(cls) {
  activeClass.value = cls.name === '全部班级' ? '' : cls.name
  viewMode.value = 'student'
}

function backToClassView() {
  activeClass.value = ''
  viewMode.value = 'class'
}

function goDetail({ row }) {
  router.push(`/students/${row.id}`)
}

// ===== 用户/账号管理（管理员）=====
const emptyForm = () => ({
  username: '',
  password: '123456',
  realName: '',
  role: 1,
  studentNo: '',
  phone: '',
  email: '',
  classId: undefined,
  major: '',
  grade: ''
})

function openCreate() {
  Object.assign(form, emptyForm())
  editingUser.value = null
  formVisible.value = true
}

function openEdit(user) {
  editingUser.value = user
  Object.assign(form, {
    username: user.username || '',
    password: '',
    realName: user.realName || user.nickname || '',
    role: Number(user.role) || 1,
    studentNo: user.studentNo || '',
    phone: user.phone || '',
    email: user.email || '',
    classId: user.classId ? Number(user.classId) : undefined,
    major: user.major || '',
    grade: user.grade || ''
  })
  formVisible.value = true
}

function resetForm() {
  formVisible.value = false
  editingUser.value = null
}

async function handleSubmit() {
  if (!form.username.trim() || !form.realName.trim()) {
    MessagePlugin.warning('账号和姓名为必填项')
    return
  }
  if (!editingUser.value && !form.password.trim()) {
    MessagePlugin.warning('请填写初始密码')
    return
  }
  submitting.value = true
  try {
    const base = {
      username: form.username.trim(),
      realName: form.realName.trim(),
      phone: form.phone.trim() || undefined,
      email: form.email.trim() || undefined,
      role: Number(form.role)
    }
    const studentFields =
      Number(form.role) === 1
        ? {
            studentNo: form.studentNo.trim() || undefined,
            classId: form.classId ? Number(form.classId) : undefined,
            major: form.major.trim() || undefined,
            grade: form.grade.trim() || undefined
          }
        : {}
    if (editingUser.value) {
      const payload = { ...base, ...studentFields }
      delete payload.username // 编辑时账号不可改，避免触发唯一性校验
      if (form.password) payload.password = form.password
      await updateUser(editingUser.value.id, payload)
      MessagePlugin.success('用户更新成功')
    } else {
      await createUser({
        ...base,
        ...studentFields,
        password: form.password.trim()
      })
      MessagePlugin.success('用户创建成功')
    }
    formVisible.value = false
    editingUser.value = null
    await reloadStudents()
  } catch (e) {
    const msg = e?.response?.data?.error || e?.response?.data?.message || '操作失败'
    MessagePlugin.error(msg)
  } finally {
    submitting.value = false
  }
}

function openReset(user) {
  resettingUser.value = user
  newPassword.value = '123456'
  resetVisible.value = true
}

async function confirmResetPassword() {
  if (!resettingUser.value) return
  if (!newPassword.value.trim()) {
    MessagePlugin.warning('请输入新密码')
    return
  }
  resetting.value = true
  try {
    await resetPassword(resettingUser.value.id, newPassword.value.trim())
    resetVisible.value = false
    resettingUser.value = null
    MessagePlugin.success('密码重置成功')
  } catch (e) {
    MessagePlugin.error(e?.response?.data?.error || e?.response?.data?.message || '重置密码失败')
  } finally {
    resetting.value = false
  }
}

async function confirmDelete(user) {
  try {
    await deleteUser(user.id)
    MessagePlugin.success('删除成功')
    await reloadStudents()
  } catch (e) {
    MessagePlugin.error(e?.response?.data?.error || e?.response?.data?.message || '删除失败')
  }
}

// ===== 批量导入 =====
function triggerImport() {
  importInput.value?.click()
}

function saveImportResult(result) {
  importResult.value = result
  try {
    sessionStorage.setItem(IMPORT_RESULT_KEY, JSON.stringify({ ...result, ts: Date.now() }))
  } catch (e) {
    // 忽略存储失败（如隐私模式）
  }
}

function clearImportResult() {
  importResult.value = null
  try {
    sessionStorage.removeItem(IMPORT_RESULT_KEY)
  } catch (e) {
    // 忽略
  }
}

function restoreImportResult() {
  try {
    const raw = sessionStorage.getItem(IMPORT_RESULT_KEY)
    if (!raw) return
    const saved = JSON.parse(raw)
    if (
      saved &&
      typeof saved.success === 'boolean' &&
      Date.now() - (saved.ts || 0) < 30 * 60 * 1000
    ) {
      importResult.value = saved
    } else {
      sessionStorage.removeItem(IMPORT_RESULT_KEY)
    }
  } catch (e) {
    sessionStorage.removeItem(IMPORT_RESULT_KEY)
  }
}

async function handleImportFile(e) {
  const file = e.target.files?.[0]
  if (!file) return
  e.target.value = ''
  importing.value = true
  clearImportResult()
  try {
    const res = await importStudents(file)
    // 后端返回 { total, imported, skipped, errors, message }
    const success = (res?.imported || 0) > 0
    saveImportResult({
      success,
      message:
        res?.message ||
        `导入完成：共${res?.total || 0}条，成功${res?.imported || 0}条，跳过${res?.skipped || 0}条`,
      total: res?.total,
      imported: res?.imported,
      skipped: res?.skipped,
      errors: res?.errors
    })
    if (res?.imported > 0) await reloadStudents()
  } catch (err) {
    const msg =
      err?.response?.data?.error ||
      err?.response?.data?.message ||
      '导入失败，请检查文件后重试'
    saveImportResult({ success: false, message: msg })
  } finally {
    importing.value = false
  }
}
</script>

<style scoped>
/* ===== 班级卡片（苹果极简） ===== */
.student-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
}

.student-search {
  width: 240px;
  flex-shrink: 0;
}

.class-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 20px;
}


.class-card {
  flex: 1 1 220px;
  min-width: 220px;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 22px;
  background: #FFFFFF;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
}

.class-card:hover {
  border-color: rgba(0, 0, 0, 0.1);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
}

.class-card.active {
  border-color: rgba(0, 113, 227, 0.3);
  box-shadow: 0 0 0 1px rgba(0, 113, 227, 0.15);
  background: rgba(0, 113, 227, 0.03);
}

.class-avatar {
  width: 46px; height: 46px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 17px;
  font-weight: 700;
  flex-shrink: 0;
}

.class-info { flex: 1; }

.class-name {
  font-size: 15px;
  font-weight: 600;
  color: #1D1D1F;
}

.class-count {
  font-size: 13px;
  color: #86868B;
  margin-top: 4px;
}

/* 表格内班级样式 */
.class-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.class-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

/* 活跃度进度条 */
.active-bar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.active-track {
  flex: 1;
  height: 6px;
  background: rgba(0, 0, 0, 0.06);
  border-radius: 3px;
  overflow: hidden;
}

.active-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.4s ease;
}

.active-text {
  font-size: 12px;
  color: #86868B;
  width: 28px;
  text-align: right;
}

@media (max-width: 900px) {
  .student-header {
    flex-direction: column;
    align-items: flex-start;
  }
  .student-search {
    width: 100%;
  }
  .class-cards {
    flex-wrap: wrap;
  }
  .class-card {
    flex: 1 1 calc(50% - 8px);
    min-width: 200px;
  }
}


@media (max-width: 600px) {
  .class-card {
    flex: 1 1 100%;
  }
}

/* ===== 用户/账号管理（管理员）===== */
.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.import-input {
  display: none;
}
.import-mask {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(2px);
}
.import-mask-card {
  background: #fff;
  border-radius: 12px;
  padding: 28px 36px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.18);
  text-align: center;
}
.import-mask-tip {
  font-size: 13px;
  color: #86868b;
  margin-top: 12px;
}
.import-result {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 8px;
  margin-bottom: 16px;
  font-size: 14px;
}
.import-result.is-success {
  background: rgba(43, 164, 113, 0.06);
  border: 1px solid rgba(43, 164, 113, 0.3);
  color: #1aa06d;
}
.import-result.is-warning {
  background: rgba(230, 162, 60, 0.08);
  border: 1px solid rgba(230, 162, 60, 0.3);
  color: #c8811f;
}
.import-result-body {
  flex: 1;
  min-width: 0;
}
.import-result-msg {
  font-weight: 500;
}
.import-result-details summary {
  font-size: 13px;
  cursor: pointer;
  opacity: 0.7;
  margin-top: 8px;
}
.import-result-details summary:hover {
  opacity: 1;
}
.import-result-errors {
  max-height: 180px;
  overflow-y: auto;
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.import-error-line {
  font-size: 12px;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 4px;
  padding: 4px 8px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.danger-text {
  color: #d54941;
}
.import-result-close {
  cursor: pointer;
  opacity: 0.6;
  flex-shrink: 0;
}
.import-result-close:hover {
  opacity: 1;
}
.reset-tip {
  margin-bottom: 12px;
  color: #86868b;
  font-size: 14px;
}
.staff-form .form-select {
  width: 100%;
}

@media (max-width: 900px) {
  .student-header {
    flex-direction: column;
    align-items: flex-start;
  }
  .header-actions {
    flex-wrap: wrap;
  }
}
</style>
