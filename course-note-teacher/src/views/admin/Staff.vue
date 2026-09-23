<template>
  <div class="page-container">
    <!-- 页面头部 -->
    <div class="page-header staff-header">
      <div>
        <h2>人员管理</h2>
        <p>管理学生、教师和管理员账号信息</p>
      </div>
      <div class="header-actions">
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
          新增用户
        </t-button>
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
              <div v-for="(err, i) in importResult.errors" :key="i" class="import-error-line">
                <span>第{{ err.row }}行</span>
                <span>· {{ err.realName || err.studentNo }}</span>
                <span class="danger-text">· {{ err.errors.join('；') }}</span>
              </div>
            </div>
          </details>
        </template>
      </div>
      <t-icon name="close" class="import-result-close" @click="clearImportResult" />
    </div>

    <!-- 角色 Tabs -->
    <t-radio-group v-model="activeTab" variant="default-filled" class="role-tabs">
      <t-radio-button value="all">全部</t-radio-button>
      <t-radio-button value="1">学生</t-radio-button>
      <t-radio-button value="2">教师</t-radio-button>
      <t-radio-button value="3">管理员</t-radio-button>
    </t-radio-group>

    <!-- 筛选区 -->
    <t-card :bordered="false" class="filter-card">
      <div class="filter-row">
        <div class="filter-left">
          <t-select
            v-model="searchField"
            :options="searchFieldOptions"
            class="field-select"
            @change="onSearchFieldChange"
          />
          <t-input
            v-model="searchInput"
            :placeholder="searchPlaceholder"
            clearable
            class="keyword-input"
          >
            <template #prefix-icon><t-icon name="search" /></template>
          </t-input>
          <t-select
            v-model="statusFilter"
            :options="statusOptions"
            class="status-select"
          />
        </div>
        <div class="filter-total">共 {{ total }} 人</div>
      </div>
    </t-card>

    <!-- 人员概览统计卡片 -->
    <t-card v-if="overview" :bordered="false" class="overview-card" title="人员概览">
      <div class="overview-stats">
        <div class="overview-stat">
          <div class="overview-num">{{ overview.total }}</div>
          <div class="overview-label">总人数</div>
        </div>
        <div class="overview-stat">
          <div class="overview-num enabled">{{ overview.enabled }}</div>
          <div class="overview-label">启用</div>
        </div>
        <div class="overview-stat">
          <div class="overview-num disabled">{{ overview.disabled }}</div>
          <div class="overview-label">禁用</div>
        </div>
      </div>
      <div class="overview-section">
        <div class="overview-section-title">{{ majorLabel }}分布</div>
        <div v-if="overview.majors?.length" class="overview-tags">
          <div v-for="m in overview.majors" :key="m.name" class="overview-tag">
            <span class="overview-tag-count">{{ m.count }}</span>
            <span class="overview-tag-name" :title="m.name">{{ m.name }}</span>
          </div>
        </div>
        <div v-else class="overview-empty">暂无{{ majorLabel }}分布数据</div>
      </div>
      <div class="overview-section">
        <div class="overview-section-title">班级分布</div>
        <div v-if="overview.classes?.length" class="overview-tags">
          <div v-for="c in overview.classes" :key="c.id" class="overview-tag">
            <span class="overview-tag-count">{{ c.count }}</span>
            <span class="overview-tag-name" :title="c.name">{{ c.name }}</span>
          </div>
        </div>
        <div v-else class="overview-empty">暂无班级分布数据</div>
      </div>
    </t-card>

    <!-- 用户列表 -->
    <t-card :bordered="false" class="table-card">
      <t-table
        :data="list"
        :columns="columns"
        row-key="id"
        :loading="loading"
        stripe
        hover
        class="staff-table"
      >
        <template #realName="{ row }">
          <div class="user-cell">
            <div class="user-avatar">{{ (row.realName || row.username || '?').charAt(0) }}</div>
            <div>
              <div class="user-name">{{ row.realName || '-' }}</div>
            </div>
          </div>
        </template>
        <template #studentNo="{ row }">
          <span class="mono">{{ row.studentNo || '-' }}</span>
        </template>
        <template #role="{ row }">
          <t-tag :theme="roleTheme(row.role)" variant="light" size="small">{{ roleLabel(row.role) }}</t-tag>
        </template>
        <template #className="{ row }">
          <span>{{ classNameById(row.classId) }}</span>
        </template>
        <template #status="{ row }">
          <t-tag :theme="row.status === 1 ? 'success' : 'warning'" variant="light" size="small">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </t-tag>
        </template>
        <template #operation="{ row }">
          <t-space size="4">
            <t-button theme="primary" variant="text" size="small" @click="openEdit(row)">编辑</t-button>
            <t-button theme="default" variant="text" size="small" @click="openReset(row)">重置密码</t-button>
            <t-popconfirm
              :content="`确定要删除「${row.realName || row.username}」吗？该操作为软删除，可在数据库中恢复。`"
              theme="danger"
              @confirm="confirmDelete(row)"
            >
              <t-button theme="danger" variant="text" size="small">删除</t-button>
            </t-popconfirm>
          </t-space>
        </template>
      </t-table>

      <!-- 分页 -->
      <div v-if="!loading && list.length > 0" class="pagination-row">
        <div class="pagination-info">第 {{ pageNum }} / {{ totalPages }} 页 · 共 {{ total }} 条</div>
        <div class="pagination-actions">
          <t-button theme="default" variant="outline" size="small" :disabled="pageNum <= 1" @click="goPrev">
            <template #icon><t-icon name="chevron-left" /></template>
            上一页
          </t-button>
          <t-button theme="default" variant="outline" size="small" :disabled="pageNum >= totalPages" @click="goNext">
            下一页
            <template #suffix-icon><t-icon name="chevron-right" /></template>
          </t-button>
        </div>
      </div>
    </t-card>

    <!-- 新增 / 编辑用户 -->
    <t-dialog
      v-model:visible="formVisible"
      :header="editingUser ? '编辑用户' : '新增用户'"
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
      v-model:visible="resetVisible"
      header="重置密码"
      width="420px"
      :confirm-btn="{ content: resetting ? '重置中...' : '确认重置', loading: resetting }"
      :cancel-btn="resetting ? null : undefined"
      :on-confirm="confirmResetPassword"
    >
      <div class="reset-tip">
        将「{{ resettingUser?.realName || resettingUser?.username }}」的密码重置为以下新密码：
      </div>
      <t-input v-model="newPassword" placeholder="请输入新密码" type="password" />
    </t-dialog>
  </div>
</template>

<script setup>
import { ref, computed, reactive, watch, onMounted } from 'vue'
import { MessagePlugin } from 'tdesign-vue-next'
import {
  getUserList,
  getUserOverview,
  createUser,
  updateUser,
  resetPassword,
  deleteUser,
  getClasses,
  importStudents
} from '@/api/staff'

const IMPORT_RESULT_KEY = 'admin_staff_import_result'
const PAGE_SIZE = 10
const ROLE_LABEL = { 1: '学生', 2: '教师', 3: '管理员' }
const ROLE_THEME = { 1: 'primary', 2: 'success', 3: 'warning' }

const STUDENT_SEARCH_FIELDS = [
  { value: 'all', label: '全部' },
  { value: 'realName', label: '姓名' },
  { value: 'username', label: '账号' },
  { value: 'studentNo', label: '学号' },
  { value: 'phone', label: '手机号' },
  { value: 'major', label: '专业' },
  { value: 'grade', label: '年级' },
  { value: 'className', label: '班级' }
]
const TEACHER_SEARCH_FIELDS = [
  { value: 'all', label: '全部' },
  { value: 'realName', label: '姓名' },
  { value: 'username', label: '账号' },
  { value: 'phone', label: '手机号' }
]
const SEARCH_PLACEHOLDER = {
  all: '搜索姓名/账号/学号/手机号...',
  realName: '搜索姓名...',
  username: '搜索账号...',
  studentNo: '搜索学号...',
  phone: '搜索手机号...',
  major: '搜索专业...',
  grade: '搜索年级...',
  className: '搜索班级名称...'
}
const roleOptions = [
  { value: 1, label: '学生' },
  { value: 2, label: '教师' },
  { value: 3, label: '管理员' }
]
const statusOptions = [
  { value: -1, label: '全部状态' },
  { value: 1, label: '启用' },
  { value: 0, label: '禁用' }
]

// 列表状态
const activeTab = ref('all') // all / 1 / 2 / 3
const searchInput = ref('')
const keyword = ref('')
const searchField = ref('all')
const statusFilter = ref(-1)
const pageNum = ref(1)
const pageSize = ref(PAGE_SIZE)
const total = ref(0)
const list = ref([])
const loading = ref(false)
const classes = ref([])

// 概览
const overview = ref(null)

// 表单
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

// 重置密码
const resetVisible = ref(false)
const resettingUser = ref(null)
const newPassword = ref('123456')
const resetting = ref(false)

// 导入
const importInput = ref(null)
const importing = ref(false)
const importResult = ref(null)

const roleNum = computed(() => (activeTab.value === 'all' ? null : Number(activeTab.value)))
const currentRole = computed(() => roleNum.value ?? 1)
const majorLabel = computed(() => (activeTab.value === '2' ? '院系' : '专业'))
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

const searchFieldOptions = computed(() => {
  if (activeTab.value === '2' || activeTab.value === '3') return TEACHER_SEARCH_FIELDS
  return STUDENT_SEARCH_FIELDS
})
const searchPlaceholder = computed(() => SEARCH_PLACEHOLDER[searchField.value] || SEARCH_PLACEHOLDER.all)
const classOptions = computed(() =>
  classes.value.map((c) => ({ value: Number(c.id), label: c.className }))
)

const columns = [
  { colKey: 'realName', title: '姓名', width: 180, fixed: 'left' },
  { colKey: 'username', title: '用户名', width: 140 },
  { colKey: 'studentNo', title: '学号', width: 130 },
  { colKey: 'role', title: '角色', width: 90, align: 'center' },
  { colKey: 'className', title: '班级', width: 150 },
  { colKey: 'major', title: '专业', width: 140, ellipsis: true },
  { colKey: 'grade', title: '年级', width: 100 },
  { colKey: 'status', title: '状态', width: 90, align: 'center' },
  { colKey: 'operation', title: '操作', width: 220, align: 'center' }
]

const roleLabel = (r) => ROLE_LABEL[r] || '-'
const roleTheme = (r) => ROLE_THEME[r] || 'default'
const classNameById = (id) => {
  if (!id) return '-'
  const c = classes.value.find((item) => Number(item.id) === Number(id))
  return c ? c.className : '-'
}

// 加载班级列表
onMounted(async () => {
  try {
    classes.value = (await getClasses()) || []
  } catch (e) {
    classes.value = []
  }
  restoreImportResult()
})

// 查询列表（角色 + 搜索 + 状态 + 分页）
async function fetchList() {
  loading.value = true
  const params = { pageNum: pageNum.value, pageSize: pageSize.value }
  if (keyword.value) {
    params.keyword = keyword.value.trim()
    params.searchField = searchField.value || 'all'
  }
  params.status = statusFilter.value
  try {
    if (roleNum.value == null) {
      // 全部：并行查询三种角色后合并（后端 /list 的 role 为必填）
      const results = await Promise.all(
        [1, 2, 3].map((r) => getUserList({ ...params, role: r }).catch(() => null))
      )
      const valid = results.filter(Boolean)
      list.value = valid.flatMap((r) => r.list || [])
      total.value = valid.reduce((s, r) => s + (r.total || 0), 0)
    } else {
      const res = await getUserList({ ...params, role: roleNum.value })
      list.value = res?.list || []
      total.value = res?.total || 0
    }
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// 查询概览（后端 /overview 的 role 为必填）
async function fetchOverview() {
  const params = { status: -1 }
  try {
    if (roleNum.value == null) {
      const results = await Promise.all(
        [1, 2, 3].map((r) => getUserOverview({ ...params, role: r }).catch(() => null))
      )
      const valid = results.filter(Boolean)
      const merged = {
        total: valid.reduce((s, r) => s + (r.total || 0), 0),
        enabled: valid.reduce((s, r) => s + (r.enabled || 0), 0),
        disabled: valid.reduce((s, r) => s + (r.disabled || 0), 0),
        majors: mergeBy(valid, 'majors', 'name'),
        classes: mergeClasses(valid)
      }
      overview.value = merged
    } else {
      overview.value = await getUserOverview({ ...params, role: roleNum.value })
    }
  } catch (e) {
    overview.value = null
  }
}

function mergeBy(results, key, nameField) {
  const map = new Map()
  results.forEach((r) => {
    (r[key] || []).forEach((item) => {
      const k = item[nameField]
      map.set(k, { ...item, count: (map.get(k)?.count || 0) + (item.count || 0) })
    })
  })
  return Array.from(map.values()).sort((a, b) => b.count - a.count)
}
function mergeClasses(results) {
  const map = new Map()
  results.forEach((r) => {
    (r.classes || []).forEach((item) => {
      const k = Number(item.id)
      const prev = map.get(k)
      if (prev) prev.count += item.count || 0
      else map.set(k, { ...item })
    })
  })
  return Array.from(map.values()).sort((a, b) => b.count - a.count)
}

watch([activeTab, keyword, pageNum, pageSize, searchField, statusFilter], () => {
  fetchList()
}, { immediate: true })

// 概览随角色变化
watch(roleNum, fetchOverview, { immediate: true })

const emptyForm = () => ({
  username: '',
  password: '123456',
  realName: '',
  role: currentRole.value,
  studentNo: '',
  phone: '',
  email: '',
  classId: undefined,
  major: '',
  grade: ''
})

// 搜索防抖
let searchTimer = null
watch(searchInput, (val) => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    keyword.value = val
    pageNum.value = 1
  }, 400)
})

function onSearchFieldChange() {
  pageNum.value = 1
}

function goPrev() {
  pageNum.value = Math.max(1, pageNum.value - 1)
}
function goNext() {
  pageNum.value = Math.min(totalPages.value, pageNum.value + 1)
}

// 新增
function openCreate() {
  Object.assign(form, emptyForm())
  editingUser.value = null
  formVisible.value = true
}

// 编辑
function openEdit(user) {
  editingUser.value = user
  Object.assign(form, {
    username: user.username || '',
    password: '',
    realName: user.realName || '',
    role: Number(user.role),
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
    await fetchList()
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
    if (list.value.length === 1 && pageNum.value > 1) {
      pageNum.value -= 1
    } else {
      await fetchList()
    }
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
    if (res?.imported > 0) {
      if (activeTab.value === '1' || roleNum.value == null) {
        await fetchList()
      } else {
        activeTab.value = '1'
        pageNum.value = 1
        searchInput.value = ''
        keyword.value = ''
      }
    }
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
.staff-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.import-input {
  display: none;
}
.role-tabs {
  margin-bottom: 16px;
  display: block;
}

.filter-card {
  margin-bottom: 16px;
  background: #fff;
}
.filter-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.filter-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}
.field-select {
  width: 130px;
  flex-shrink: 0;
}
.keyword-input {
  width: 280px;
}
.status-select {
  width: 130px;
  flex-shrink: 0;
}
.filter-total {
  color: #86868b;
  font-size: 14px;
  flex-shrink: 0;
}

.overview-card {
  margin-bottom: 16px;
  background: #fff;
}
.overview-stats {
  display: flex;
  gap: 40px;
  margin-bottom: 20px;
}
.overview-stat {
  text-align: left;
}
.overview-num {
  font-size: 26px;
  font-weight: 700;
  color: #1d1d1f;
  line-height: 1.2;
}
.overview-num.enabled {
  color: #2ba471;
}
.overview-num.disabled {
  color: #d54941;
}
.overview-label {
  font-size: 13px;
  color: #86868b;
  margin-top: 4px;
}
.overview-section {
  margin-bottom: 16px;
}
.overview-section:last-child {
  margin-bottom: 0;
}
.overview-section-title {
  font-size: 13px;
  color: #86868b;
  margin-bottom: 10px;
}
.overview-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.overview-tag {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 8px;
  background: rgba(0, 113, 227, 0.03);
}
.overview-tag-count {
  font-size: 16px;
  font-weight: 700;
  color: #0071e3;
}
.overview-tag-name {
  font-size: 12px;
  color: #606078;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.overview-empty {
  font-size: 13px;
  color: #bfbfbf;
  padding: 8px 0;
}

.table-card {
  background: #fff;
}
.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: #0071e3;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.user-name {
  font-weight: 600;
  color: #1d1d1f;
}
.mono {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 13px;
}

.pagination-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 0 0;
  margin-top: 8px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
}
.pagination-info {
  font-size: 13px;
  color: #86868b;
}
.pagination-actions {
  display: flex;
  gap: 8px;
}

/* 导入遮罩 */
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

/* 导入结果 */
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
  .staff-header {
    flex-direction: column;
    align-items: flex-start;
  }
  .header-actions {
    flex-wrap: wrap;
  }
  .keyword-input {
    width: 100%;
  }
  .filter-left {
    flex-wrap: wrap;
  }
}
</style>