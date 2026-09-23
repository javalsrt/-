<template>
  <div class="page-container">
    <!-- 标题与操作区 -->
    <div class="page-header schedule-admin-header">
      <div>
        <h2>排课管理</h2>
        <p>{{ isAdmin ? '管理员录入教学任务，系统自动排课' : '查看并管理您本人的教学任务与排课结果' }}</p>
      </div>
      <div class="header-actions">
        <t-select v-model="semester" style="width: 200px" placeholder="选择学期">
          <t-option v-for="s in semesterOptions" :key="s" :value="s" :label="s" />
        </t-select>
        <t-button theme="default" :loading="loading" @click="refreshAll">
          <template #icon><t-icon name="refresh" /></template>
          刷新
        </t-button>
        <t-button theme="primary" :loading="loading" :disabled="!semester" @click="handleAutoSchedule">
          <template #icon><t-icon name="play-circle" /></template>
          一键自动排课
        </t-button>
      </div>
    </div>

    <!-- 排课统计 -->
    <div class="stat-cards">
      <div class="stat-card animated-stat-card" style="--index:0">
        <div class="stat-icon"><t-icon name="calendar" size="20px" /></div>
        <div class="stat-info">
          <h3>{{ stats?.total ?? '-' }}</h3>
          <p>教学任务总数</p>
        </div>
      </div>
      <div class="stat-card animated-stat-card" style="--index:1">
        <div class="stat-icon" style="background:rgba(52,199,89,0.12);color:#34C759"><t-icon name="check-circle" size="20px" /></div>
        <div class="stat-info">
          <h3>{{ stats?.scheduled ?? '-' }}</h3>
          <p>已排课</p>
        </div>
      </div>
      <div class="stat-card animated-stat-card" style="--index:2">
        <div class="stat-icon" style="background:rgba(255,149,0,0.12);color:#FF9500"><t-icon name="time" size="20px" /></div>
        <div class="stat-info">
          <h3>{{ stats?.pending ?? '-' }}</h3>
          <p>待排课</p>
        </div>
      </div>
      <div class="stat-card animated-stat-card" style="--index:3">
        <div class="stat-icon" style="background:rgba(255,59,48,0.12);color:#FF3B30"><t-icon name="close-circle" size="20px" /></div>
        <div class="stat-info">
          <h3>{{ stats?.failed ?? '-' }}</h3>
          <p>排课失败</p>
        </div>
      </div>
      <div class="stat-card animated-stat-card" style="--index:4">
        <div class="stat-icon" style="background:rgba(88,86,214,0.12);color:#5856D6"><t-icon name="lock-on" size="20px" /></div>
        <div class="stat-info">
          <h3>{{ stats?.locked ?? '-' }}</h3>
          <p>已锁定</p>
        </div>
      </div>
    </div>

    <!-- 当前操作学期提示 -->
    <div class="semester-tip">
      <t-icon name="calendar" size="16px" />
      当前操作学期：<span class="semester-name">{{ semester || '未设置' }}</span>
    </div>

    <t-tabs v-model="activeTab">
      <t-tab-panel value="overview" label="任务与结果">
        <!-- 教学任务维护 -->
        <t-card :bordered="false" class="section-card">
          <template #title>教学任务维护</template>
          <div class="task-toolbar">
            <t-space>
              <t-button theme="default" :loading="loading" :disabled="!semester" @click="handleGenerateTasks">
                <template #icon><t-icon name="add" /></template>
                从课程生成任务
              </t-button>
              <t-button theme="default" :loading="loading" :disabled="tasks.length === 0" @click="handleClearTasks">
                <template #icon><t-icon name="delete" /></template>
                清空任务
              </t-button>
            </t-space>
          </div>
          <p class="tip-text">
            提示：点击"从课程生成任务"会读取当前学期已导入的课程，按默认规则生成教学任务；您也可以在后端直接维护 teaching_task 表后刷新查看。
          </p>
        </t-card>

        <!-- 最近一次排课结果 -->
        <t-card v-if="lastResult" :bordered="false" class="section-card result-card" :class="lastResult.failed > 0 ? 'result-warn' : 'result-ok'">
          <template #title>最近一次排课结果</template>
          <template #actions>
            <t-link theme="default" @click="lastResult = null">关闭</t-link>
          </template>
          <div class="result-summary">
            <span class="result-stat">成功 <b class="ok">{{ lastResult.scheduled ?? 0 }}</b></span>
            <span class="result-stat">失败 <b class="bad">{{ lastResult.failed ?? 0 }}</b></span>
            <span class="result-stat">部分成功 <b>{{ lastResult.partialScheduled ?? 0 }}</b></span>
            <span class="result-stat" v-if="lastResult.elapsedMs != null">耗时 <b>{{ lastResult.elapsedMs }}ms</b></span>
          </div>
          <p class="result-message">{{ lastResult.message }}</p>
          <div v-if="lastResult.failures && lastResult.failures.length">
            <div class="result-section-title">失败详情</div>
            <div class="result-failures">
              <div v-for="(f, i) in lastResult.failures" :key="i" class="result-failure">
                <div class="failure-course">{{ f.courseName || `任务 #${f.taskId ?? ''}` }}</div>
                <div class="failure-reason">{{ f.reason }}</div>
              </div>
            </div>
          </div>
        </t-card>

        <!-- 教学任务列表 -->
        <t-card :bordered="false" class="section-card">
          <template #title>教学任务列表 <span class="count-badge">{{ tasks.length }}</span></template>
          <t-skeleton :loading="tableLoading" animation="gradient" theme="article">
            <t-table :data="tasks" :columns="taskColumns" row-key="id" stripe hover>
              <template #classId="{ row }">
                <span>{{ classNameOf(row.classId) }}</span>
              </template>
              <template #preferredRoomType="{ row }">
                <span>{{ roomTypeLabel(row.preferredRoomType) }}</span>
              </template>
              <template #preferredPeriod="{ row }">
                <span>{{ periodLabel(row.preferredPeriod) }}</span>
              </template>
              <template #status="{ row }">
                <t-tag :theme="statusMeta(row.status).theme" variant="light" size="small">{{ statusMeta(row.status).text }}</t-tag>
              </template>
              <template #operation="{ row }">
                <t-link theme="danger" @click="deleteTask(row)">删除</t-link>
              </template>
            </t-table>
            <div v-if="!tableLoading && tasks.length === 0" class="empty-tip">
              <t-icon name="file" size="48px" style="color:#E5E7EB;margin-bottom:12px" />
              <div>暂无教学任务，点击上方"从课程生成任务"或手动录入</div>
            </div>
          </t-skeleton>
        </t-card>
      </t-tab-panel>

      <t-tab-panel value="classrooms" label="教室资源">
        <t-card :bordered="false" class="section-card">
          <template #title>
            <span class="card-title-with-icon"><t-icon name="building" />教室资源</span>
          </template>
          <template #actions>
            <t-button theme="default" size="small" @click="openAddClassroom">
              <template #icon><t-icon name="add" /></template>
              新增教室
            </t-button>
          </template>
          <t-skeleton :loading="tableLoading" animation="gradient" theme="article">
            <t-table :data="classrooms" :columns="classroomColumns" row-key="id" stripe hover>
              <template #type="{ row }"><span>{{ roomTypeLabel(row.type) }}</span></template>
              <template #operation="{ row }">
                <t-space>
                  <t-link theme="primary" @click="editClassroom(row)">编辑</t-link>
                  <t-link theme="danger" @click="handleDeleteClassroom(row)">删除</t-link>
                </t-space>
              </template>
            </t-table>
            <div v-if="!tableLoading && classrooms.length === 0" class="empty-tip">
              <t-icon name="building" size="48px" style="color:#E5E7EB;margin-bottom:12px" />
              <div>暂无教室数据，点击"新增教室"添加</div>
            </div>
          </t-skeleton>
        </t-card>
      </t-tab-panel>
    </t-tabs>

    <!-- 教室新增/编辑对话框 -->
    <t-dialog
      v-model:visible="classroomDialogVisible"
      :header="editingId ? '编辑教室' : '新增教室'"
      width="560px"
      :confirm-btn="{ content: '保存', loading: saving }"
      @confirm="saveClassroom"
      @close="resetClassroomForm"
    >
      <t-form ref="classroomFormRef" :data="classroomForm" :rules="classroomRules" layout="vertical">
        <t-form-item label="教室名称" name="name">
          <t-input v-model="classroomForm.name" placeholder="如：教学楼A201" />
        </t-form-item>
        <t-form-item label="教室类型" name="type">
          <t-select v-model="classroomForm.type" placeholder="请选择类型">
            <t-option v-for="(label, value) in ROOM_TYPE_LABELS" :key="value" :value="value" :label="label" />
          </t-select>
        </t-form-item>
        <t-form-item label="容量（人）" name="capacity">
          <t-input-number v-model="classroomForm.capacity" :min="1" style="width: 100%" />
        </t-form-item>
        <t-row :gutter="16">
          <t-col :span="12">
            <t-form-item label="楼宇" name="building">
              <t-input v-model="classroomForm.building" placeholder="如：教学楼主楼" />
            </t-form-item>
          </t-col>
          <t-col :span="12">
            <t-form-item label="楼层" name="floor">
              <t-input-number v-model="classroomForm.floor" :min="1" style="width: 100%" />
            </t-form-item>
          </t-col>
        </t-row>
        <t-form-item label="设备" name="equipment">
          <t-input v-model="classroomForm.equipment" placeholder="如：多媒体、空调" />
        </t-form-item>
      </t-form>
    </t-dialog>

    <!-- 通用确认对话框 -->
    <t-dialog
      v-model:visible="confirmState.visible"
      :header="confirmState.title"
      width="480px"
      :confirm-btn="{ content: confirmState.confirmText, theme: confirmState.danger ? 'danger' : 'primary' }"
      @confirm="handleConfirm"
    >
      <p class="confirm-content">{{ confirmState.content }}</p>
    </t-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch, computed } from 'vue'
import { MessagePlugin } from 'tdesign-vue-next'
import { useUserStore } from '@/stores/user'
import {
  getClassrooms,
  addClassroom,
  updateClassroom,
  deleteClassroom,
  getTeachingTasks,
  batchImportTasks,
  deleteTeachingTask,
  clearTeachingTasks,
  autoGenerateSchedule,
  getScheduleStats,
} from '@/api/schedule'
import { getCurrentSemester, getSemesterList } from '@/api/semester'
import { getClasses, getTeacherCourses } from '@/api/staff'
import request from '@/utils/request'

// ===== 常量映射 =====
const ROOM_TYPE_LABELS = {
  normal: '普通教室',
  lab: '专业实训室',
  computer: '计算机机房',
  music: '琴房',
  dance: '舞蹈室',
  art: '美术室',
  sports: '运动场',
}
const PERIOD_LABELS = { any: '不限', morning: '上午', afternoon: '下午' }
const STATUS_MAP = {
  pending: { text: '待排', theme: 'default' },
  scheduled: { text: '已排', theme: 'success' },
  failed: { text: '失败', theme: 'danger' },
  locked: { text: '锁定', theme: 'warning' },
}

const roomTypeLabel = (t) => ROOM_TYPE_LABELS[t || 'normal'] || t || '-'
const periodLabel = (t) => PERIOD_LABELS[t || 'any'] || t || '-'
const statusMeta = (s) => STATUS_MAP[s || 'pending'] || STATUS_MAP.pending

const userStore = useUserStore()
const isAdmin = computed(() => userStore.isAdmin)
const currentTeacherId = ref(null)

// ===== 状态 =====
const activeTab = ref('overview')
const loading = ref(false)
const tableLoading = ref(false)
const semester = ref('')
const semesterOptions = ref([])
const stats = ref(null)
const tasks = ref([])
const classrooms = ref([])
const classes = ref([])
const lastResult = ref(null)

// 班级名称映射
const classNameOf = (id) => {
  if (id === null || id === undefined || id === '') return '-'
  const found = classes.value.find((c) => String(c.id) === String(id))
  if (found) return found.name || found.className || found.label || id
  return id
}

// ===== 数据加载 =====
const effectiveTeacherId = computed(() => (isAdmin.value ? null : currentTeacherId.value))

const fetchStats = async () => {
  if (!semester.value) return
  try {
    stats.value = await getScheduleStats(semester.value, effectiveTeacherId.value)
  } catch (e) {
    console.warn('stats error', e)
    stats.value = null
  }
}

const fetchTasks = async () => {
  if (!semester.value) return
  tableLoading.value = true
  try {
    const params = { semester: semester.value }
    if (effectiveTeacherId.value != null) params.teacherId = effectiveTeacherId.value
    tasks.value = (await getTeachingTasks(params)) || []
  } catch (e) {
    console.warn('tasks error', e)
    tasks.value = []
  } finally {
    tableLoading.value = false
  }
}

const fetchClassrooms = async () => {
  try {
    classrooms.value = (await getClassrooms()) || []
  } catch (e) {
    console.warn('classrooms error', e)
    classrooms.value = []
  }
}

const refreshAll = async () => {
  await Promise.all([fetchStats(), fetchTasks(), fetchClassrooms()])
}

const loadSemester = async () => {
  try {
    const list = await getSemesterList()
    semesterOptions.value = (list || []).map((s) => s.name).filter(Boolean)
  } catch (e) {
    semesterOptions.value = []
  }
  try {
    const cur = await getCurrentSemester()
    if (cur?.name) semester.value = cur.name
    else if (semesterOptions.value.length) semester.value = semesterOptions.value[0]
  } catch (e) {
    if (semesterOptions.value.length) semester.value = semesterOptions.value[0]
  }
}

async function loadProfile() {
  if (isAdmin.value) return
  try {
    const info = await request.get('/teacher/profile')
    if (info?.teacherId) currentTeacherId.value = info.teacherId
  } catch (e) {
    console.warn('教师信息加载失败', e)
  }
}

onMounted(async () => {
  loading.value = true
  await loadProfile()
  await loadSemester()
  await fetchClassOptions()
  loading.value = false
})

watch(semester, (val) => {
  if (val) refreshAll()
})

async function fetchClassOptions() {
  try {
    classes.value = (await getClasses()) || []
  } catch (e) {
    classes.value = []
  }
}

const errMsg = (e) =>
  e?.response?.data?.message || e?.response?.data?.error || e?.message || '操作失败'

// ===== 一键自动排课 =====
async function generateTasksFromCourses() {
  let courses = []
  try {
    courses = ((await getTeacherCourses()) || []).filter((c) => c.semester === semester.value)
  } catch (e) {
    courses = []
  }
  const pending = []
  courses.forEach((c) => {
    const clsList = c.classes || []
    const baseHours = c.hours ?? (c.credit ? Number(c.credit) : 8)
    const weeklyHours = baseHours <= 4 ? baseHours : 2
    const consecutive = weeklyHours >= 2 ? 2 : 1
    const name = (c.courseName || '').toLowerCase()
    const preferredRoomType =
      name.includes('python') ||
      name.includes('计算机') ||
      name.includes('机器学习') ||
      name.includes('深度学习')
        ? 'computer'
        : 'normal'
    const build = (classId) => ({
      semester: c.semester || semester.value,
      classId: classId || 0,
      courseId: c.courseId || 0,
      teacherId: c.teacherId,
      weeklyHours,
      consecutive,
      preferredRoomType,
      preferredPeriod: 'any',
      priority: 5,
    })
    if (clsList.length > 0) clsList.forEach((cls) => pending.push(build(cls.classId || 0)))
    else if (c.classId) pending.push(build(c.classId))
  })
  if (pending.length === 0) {
    return { success: false, message: '当前学期没有可用课程，请先到课程导入页导入课程' }
  }
  const result = await batchImportTasks(pending)
  return {
    success: true,
    message: result.message + (result.errors?.length ? '\n失败：' + result.errors.join('；') : ''),
  }
}

function handleAutoSchedule() {
  if (!semester.value) {
    MessagePlugin.warning('请先选择学期')
    return
  }
  openConfirm({
    title: '一键自动排课',
    content: isAdmin.value
      ? `将对 ${semester.value} 执行自动排课，已锁定的课不会被覆盖。是否继续？`
      : `将对 ${semester.value} 您本人的教学任务执行自动排课，已锁定的课不会被覆盖。是否继续？`,
    confirmText: '开始排课',
    onConfirm: doAutoSchedule,
  })
}

async function doAutoSchedule() {
  loading.value = true
  lastResult.value = null
  try {
    const actionable = tasks.value.filter((t) => t.status === 'pending' || t.status === 'failed')
    if (actionable.length === 0) {
      const generated = await generateTasksFromCourses()
      if (!generated.success) {
        MessagePlugin.warning(generated.message)
        return
      }
      await fetchTasks()
    }
    const result = await autoGenerateSchedule(semester.value, true, effectiveTeacherId.value)
    lastResult.value = result
    await refreshAll()
    if (result.failed > 0) MessagePlugin.warning(result.message || '排课完成（含失败）')
    else if (result.partialScheduled > 0) MessagePlugin.success(result.message || '排课完成（部分成功）')
    else MessagePlugin.success(result.message || '排课成功')
  } catch (e) {
    MessagePlugin.error(errMsg(e))
  } finally {
    loading.value = false
  }
}

// 从课程生成任务
function handleGenerateTasks() {
  openConfirm({
    title: '从课程生成教学任务',
    content: `将根据 ${semester.value || '当前学期'} 的已导入课程自动生成教学任务。是否继续？`,
    confirmText: '生成',
    onConfirm: doGenerateTasks,
  })
}

async function doGenerateTasks() {
  loading.value = true
  try {
    const result = await generateTasksFromCourses()
    if (!result.success) {
      MessagePlugin.warning(result.message || '生成失败')
      return
    }
    MessagePlugin.success(result.message || '生成完成')
    await refreshAll()
  } catch (e) {
    MessagePlugin.error(errMsg(e))
  } finally {
    loading.value = false
  }
}

// 清空学期任务
function handleClearTasks() {
  openConfirm({
    title: '清空教学任务',
    content: isAdmin.value
      ? `确定清空 ${semester.value || '当前学期'} 的所有教学任务吗？此操作不可恢复。`
      : `确定清空 ${semester.value || '当前学期'} 您本人的教学任务吗？此操作不可恢复。`,
    confirmText: '清空',
    danger: true,
    onConfirm: doClearTasks,
  })
}

async function doClearTasks() {
  loading.value = true
  try {
    await clearTeachingTasks(semester.value)
    MessagePlugin.success('教学任务已清空')
    await refreshAll()
  } catch (e) {
    MessagePlugin.error(errMsg(e))
  } finally {
    loading.value = false
  }
}

// 删除单个任务
function deleteTask(task) {
  if (!task.id) return
  openConfirm({
    title: '删除教学任务',
    content: `确定删除「${task.courseName || ''}」的教学任务吗？`,
    confirmText: '删除',
    danger: true,
    onConfirm: async () => {
      loading.value = true
      try {
        await deleteTeachingTask(task.id)
        MessagePlugin.success('教学任务已删除')
        await refreshAll()
      } catch (e) {
        MessagePlugin.error(errMsg(e))
      } finally {
        loading.value = false
      }
    },
  })
}

// ===== 教室管理 =====
const classroomDialogVisible = ref(false)
const saving = ref(false)
const editingId = ref(null)
const classroomForm = reactive({
  name: '',
  type: 'normal',
  capacity: 50,
  building: '',
  floor: 1,
  equipment: '',
})
const classroomRules = {
  name: [{ required: true, message: '请输入教室名称' }],
  type: [{ required: true, message: '请选择教室类型' }],
  capacity: [{ required: true, message: '请输入容量' }],
}

function resetClassroomForm() {
  editingId.value = null
  Object.assign(classroomForm, {
    name: '', type: 'normal', capacity: 50, building: '', floor: 1, equipment: '',
  })
}

function openAddClassroom() {
  resetClassroomForm()
  classroomDialogVisible.value = true
}

function editClassroom(row) {
  editingId.value = row.id
  Object.assign(classroomForm, {
    name: row.name || '',
    type: row.type || 'normal',
    capacity: row.capacity ?? 50,
    building: row.building || '',
    floor: row.floor ?? 1,
    equipment: row.equipment || '',
  })
  classroomDialogVisible.value = true
}

async function saveClassroom() {
  if (!classroomForm.name) return MessagePlugin.warning('请输入教室名称')
  if (!classroomForm.type) return MessagePlugin.warning('请选择教室类型')
  if (!classroomForm.capacity) return MessagePlugin.warning('请输入容量')
  saving.value = true
  try {
    const payload = { ...classroomForm }
    if (editingId.value) {
      await updateClassroom(editingId.value, payload)
      MessagePlugin.success('教室已更新')
    } else {
      await addClassroom(payload)
      MessagePlugin.success('教室已添加')
    }
    classroomDialogVisible.value = false
    resetClassroomForm()
    await fetchClassrooms()
  } catch (e) {
    MessagePlugin.error(errMsg(e))
  } finally {
    saving.value = false
  }
}

function handleDeleteClassroom(row) {
  openConfirm({
    title: '删除教室',
    content: `确定删除教室「${row.name}」吗？`,
    confirmText: '删除',
    danger: true,
    onConfirm: async () => {
      loading.value = true
      try {
        await deleteClassroom(row.id)
        MessagePlugin.success('教室已删除')
        await fetchClassrooms()
      } catch (e) {
        MessagePlugin.error(errMsg(e))
      } finally {
        loading.value = false
      }
    },
  })
}

// ===== 通用确认对话框 =====
const confirmState = reactive({
  visible: false,
  title: '',
  content: '',
  confirmText: '确定',
  danger: false,
  onConfirm: null,
})
function openConfirm({ title, content, confirmText = '确定', danger = false, onConfirm }) {
  confirmState.title = title
  confirmState.content = content
  confirmState.confirmText = confirmText
  confirmState.danger = danger
  confirmState.onConfirm = onConfirm
  confirmState.visible = true
}
function handleConfirm() {
  confirmState.visible = false
  if (typeof confirmState.onConfirm === 'function') confirmState.onConfirm()
}

// ===== 表格列配置 =====
const taskColumns = [
  { colKey: 'courseName', title: '课程', width: 180 },
  { colKey: 'classId', title: '班级', width: 110 },
  { colKey: 'teacherName', title: '教师', width: 110 },
  { colKey: 'weeklyHours', title: '周课时', width: 80, align: 'center' },
  { colKey: 'consecutive', title: '连堂', width: 70, align: 'center' },
  { colKey: 'preferredRoomType', title: '教室偏好', width: 110 },
  { colKey: 'preferredPeriod', title: '时段偏好', width: 90 },
  { colKey: 'status', title: '状态', width: 90 },
  { colKey: 'failReason', title: '失败原因', width: 180 },
  { colKey: 'operation', title: '操作', width: 90 },
]

const classroomColumns = [
  { colKey: 'name', title: '教室名称', width: 160 },
  { colKey: 'type', title: '类型', width: 110 },
  { colKey: 'capacity', title: '容量', width: 90, align: 'center' },
  { colKey: 'building', title: '楼宇', width: 140 },
  { colKey: 'floor', title: '楼层', width: 80, align: 'center' },
  { colKey: 'equipment', title: '设备', width: 200 },
  { colKey: 'operation', title: '操作', width: 120 },
]


</script>

<style scoped>
.schedule-admin-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 20px;
  flex-wrap: wrap;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.semester-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #86868B;
  background: #FFFFFF;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 12px;
  padding: 12px 16px;
  margin-bottom: 24px;
}
.semester-tip .semester-name {
  font-weight: 600;
  color: #1D1D1F;
}
.section-card {
  margin-bottom: 20px;
}
.task-toolbar {
  margin-bottom: 12px;
}
.tip-text {
  font-size: 12px;
  color: #86868B;
  margin: 0;
}
.card-title-with-icon {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.count-badge {
  font-size: 13px;
  font-weight: 400;
  color: #86868B;
  margin-left: 8px;
}
/* 排课结果卡片 */
.result-card .result-summary {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}
.result-stat {
  font-size: 14px;
  color: #86868B;
}
.result-stat b {
  font-size: 18px;
  color: #1D1D1F;
  margin-left: 4px;
}
.result-stat b.ok { color: #34C759; }
.result-stat b.bad { color: #FF3B30; }
.result-message {
  font-size: 14px;
  color: #55555C;
  white-space: pre-wrap;
  margin: 0 0 12px;
}
.result-section-title {
  font-size: 13px;
  font-weight: 600;
  color: #1D1D1F;
  margin-bottom: 8px;
}
.result-failures {
  max-height: 220px;
  overflow-y: auto;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 8px;
  background: #FAFAFA;
  padding: 4px 0;
}
.result-failure {
  padding: 8px 12px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
}
.result-failure:last-child { border-bottom: none; }
.failure-course {
  font-size: 13px;
  font-weight: 600;
  color: #D11A1A;
}
.failure-reason {
  font-size: 12px;
  color: #86868B;
  margin-top: 2px;
}

.confirm-content {
  font-size: 14px;
  line-height: 1.6;
  color: #1D1D1F;
  margin: 0;
  white-space: pre-wrap;
}

@media (max-width: 768px) {
  .schedule-admin-header {
    flex-direction: column;
    align-items: flex-start;
  }
  .header-actions {
    width: 100%;
  }
}
</style>