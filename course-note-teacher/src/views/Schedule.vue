<template>
  <div class="page-container">
    <div class="page-header">
      <h2>课表管理</h2>
      <p>{{ isAdmin ? '全校课表一览' : '我的授课课表' }} · {{ currentSemesterLabel }}</p>
    </div>

    <!-- 工具栏 -->
    <div class="table-toolbar">
      <t-space>
        <t-select v-model="currentClass" placeholder="选择班级" style="width: 160px" clearable>
          <t-option value="all" label="全部班级" />
          <t-option v-for="cls in classOptions" :key="cls.value" :value="cls.value" :label="cls.label" />
        </t-select>
        <t-select v-model="currentWeek" placeholder="选择周次" style="width: 120px">
          <t-option v-for="w in 16" :key="w" :value="w" :label="`第${w}周`" />
        </t-select>
      </t-space>
      <t-space>
        <t-button theme="default" @click="exportSchedule">
          <template #icon><t-icon name="download" /></template>
          导出课表
        </t-button>
        <t-button v-if="isAdmin" theme="primary" @click="showAddDialog = true">
          <template #icon><t-icon name="add" /></template>
          添加课程
        </t-button>
      </t-space>
    </div>

    <!-- 课表视图 -->
    <t-card :bordered="false">
      <div class="schedule-grid">
        <div class="schedule-header">节次</div>
        <div class="schedule-header" v-for="day in weekDays" :key="day">{{ day }}</div>

        <template v-for="period in timeSlots" :key="period.key">
          <div class="schedule-cell time-cell">
            <div class="time-section">{{ period.section }}</div>
            <div class="time-range">{{ period.time }}</div>
          </div>
          <div v-for="day in 7" :key="day" class="schedule-cell" :class="{ empty: !getCourses(day, period.key).length }">
            <div v-if="!getCourses(day, period.key).length" class="empty-cell">暂无课程</div>
            <div
              v-for="course in getCourses(day, period.key)"
              :key="course.id"
              class="course-block"
              :style="getCourseStyle(course)"
              @click="openCourseDetail(course)"
            >
              <div class="course-name">{{ course.name }}</div>
              <div class="course-info">{{ course.teacher }} · {{ course.classroom }}</div>
              <div class="course-time">{{ course.startTime }}-{{ course.endTime }}</div>
            </div>
          </div>
        </template>
      </div>
    </t-card>

    <!-- 课程列表 -->
    <t-card title="课程列表" :bordered="false" style="margin-top: 16px;">
      <CourseTable :courseList="courseList" @edit="editCourse" @delete="deleteCourse" />
    </t-card>

    <!-- 添加/编辑课程对话框 -->
    <t-dialog
      v-model:visible="showAddDialog"
      class="course-dialog"
      :header="editingCourse ? '编辑课程' : '添加课程'"
      width="680px"
      @confirm="saveCourse"
      @close="resetForm"
    >
      <t-form ref="formRef" :data="courseForm" :rules="courseRules" layout="vertical">
        <t-row :gutter="24">
          <t-col :span="6">
            <t-form-item label="课程名称" name="name">
              <t-input v-model="courseForm.name" placeholder="如：数据结构与算法" />
            </t-form-item>
          </t-col>
          <t-col :span="6">
            <t-form-item label="授课教师" name="teacher">
              <t-input v-model="courseForm.teacher" placeholder="教师姓名" />
            </t-form-item>
          </t-col>
        </t-row>
        <t-row :gutter="24">
          <t-col :span="4">
            <t-form-item label="星期" name="dayOfWeek">
              <t-select v-model="courseForm.dayOfWeek" placeholder="选择">
                <t-option v-for="(day, i) in weekDays" :key="i+1" :value="i+1" :label="day" />
              </t-select>
            </t-form-item>
          </t-col>
          <t-col :span="4">
            <t-form-item label="开始时间" name="startTime">
              <t-time-picker v-model="courseForm.startTime" format="HH:mm" />
            </t-form-item>
          </t-col>
          <t-col :span="4">
            <t-form-item label="结束时间" name="endTime">
              <t-time-picker v-model="courseForm.endTime" format="HH:mm" />
            </t-form-item>
          </t-col>
        </t-row>
        <t-row :gutter="24">
          <t-col :span="6">
            <t-form-item label="上课教室" name="classroom">
              <t-input v-model="courseForm.classroom" placeholder="如：教学楼A201" />
            </t-form-item>
          </t-col>
          <t-col :span="6">
            <t-form-item label="周次" name="weeks">
              <t-input v-model="courseForm.weeks" placeholder="如：1-16" />
            </t-form-item>
          </t-col>
        </t-row>
        <t-form-item label="课程颜色" name="color">
          <t-color-picker v-model="courseForm.color" />
        </t-form-item>
        <t-form-item label="课程简介" name="description">
          <t-textarea v-model="courseForm.description" placeholder="简要描述课程内容" :maxlength="200" />
        </t-form-item>
      </t-form>
    </t-dialog>

    <!-- 课程详情弹窗 -->
    <t-dialog
      v-model:visible="showDetailDialog"
      :header="selectedCourse?.name || '课程详情'"
      width="480px"
      @close="selectedCourse = null"
    >
      <div v-if="selectedCourse" class="course-detail">
        <div class="detail-row">
          <span class="detail-label">授课教师</span>
          <span class="detail-value">{{ selectedCourse.teacher }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">上课教室</span>
          <span class="detail-value">{{ selectedCourse.classroom }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">上课时间</span>
          <span class="detail-value">{{ weekDays[selectedCourse.dayOfWeek - 1] }} {{ selectedCourse.startTime }} - {{ selectedCourse.endTime }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">上课周次</span>
          <span class="detail-value">{{ selectedCourse.weeks }}</span>
        </div>
        <div class="detail-row" v-if="selectedCourse.description">
          <span class="detail-label">课程简介</span>
          <span class="detail-value">{{ selectedCourse.description }}</span>
        </div>
      </div>
      <template #footer>
        <t-button theme="default" @click="showDetailDialog = false">关闭</t-button>
        <t-button theme="primary" @click="onEditFromDetail">编辑</t-button>
      </template>
    </t-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { MessagePlugin } from 'tdesign-vue-next'
import request from '@/utils/request'
import CourseTable from '@/components/CourseTable.vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const isAdmin = computed(() => userStore.isAdmin)

const weekDays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

const timeSlots = [
  { key: 'morning1', section: '1-2节', time: '08:10-09:40' },
  { key: 'morning2', section: '3-4节', time: '10:00-11:30' },
  { key: 'afternoon1', section: '5-6节', time: '14:00-15:30' },
  { key: 'afternoon2', section: '7-8节', time: '15:40-17:10' },
  { key: 'evening', section: '9-10节', time: '19:00-20:30' }
]

const currentClass = ref('all')
const currentWeek = ref(1)
const classOptions = ref([])
const teacherInfo = ref({})
const currentSemesterLabel = ref('')
const showAddDialog = ref(false)
const showDetailDialog = ref(false)
const editingCourse = ref(null)
const selectedCourse = ref(null)
const formRef = ref(null)

const courseList = ref([])

onMounted(() => loadCourses())

async function loadCourses() {
  try {
    const data = await request.get('/teacher/courses')
    courseList.value = data || []
    buildClassOptions()
  } catch (e) { console.warn('课程加载失败', e) }
}

function buildClassOptions() {
  const set = new Set()
  courseList.value.forEach(c => {
    const desc = c.description || ''
    if (desc) set.add(desc)
  })
  classOptions.value = Array.from(set).sort((a, b) => a.localeCompare(b, 'zh-CN')).map(cls => ({
    value: cls,
    label: cls
  }))
}

const deptLabel = computed(() => teacherInfo.value?.school || '计算机科学与技术学院')

async function loadTeacherInfo() {
  try {
    const info = await request.get('/teacher/profile')
    if (info) teacherInfo.value = info
  } catch (e) { console.warn('教师信息加载失败', e) }
}

function getCurrentSemester() {
  const now = new Date()
  const year = now.getFullYear()
  const month = now.getMonth() + 1
  const startYear = month >= 9 ? year : year - 1
  const endYear = startYear + 1
  const term = month >= 3 && month <= 7 ? '2' : '1'
  return `${startYear}-${endYear}-${term}`
}

onMounted(() => {
  currentSemesterLabel.value = getCurrentSemester()
  loadTeacherInfo()
  loadCourses()
})



const courseForm = reactive({
  name: '',
  teacher: '',
  classroom: '',
  dayOfWeek: 1,
  startTime: '08:10',
  endTime: '09:40',
  weeks: '1-16',
  color: '#6366F1',
  description: ''
})

const courseRules = {
  name: [{ required: true, message: '请输入课程名称' }],
  dayOfWeek: [{ required: true, message: '请选择星期' }]
}

function getPeriodKey(time) {
  if (!time) return 'morning1'
  const toMin = t => { const [hh, mm] = t.split(':').map(Number); return hh * 60 + mm }
  const m = toMin(time)
  if (m < 570) return 'morning1'    // < 09:30 → 1-2节
  if (m < 780) return 'morning2'    // < 13:00 → 3-4节
  if (m < 940) return 'afternoon1'  // < 15:40 → 5-6节
  if (m < 1140) return 'afternoon2' // < 19:00 → 7-8节
  return 'evening'                  // ≥ 19:00 → 9-10节
}

function getCourses(day, periodKey) {
  return courseList.value.filter(c => {
    // 班级筛选：如果选了具体班级，只显示该班级的课程
    if (currentClass.value && currentClass.value !== 'all') {
      const classDesc = c.description || ''
      // 班级标识匹配（如 description="计科2201" 对应 class="2201"）
      if (!classDesc.includes(currentClass.value)) return false
    }
    // 周次筛选：解析 weeks 字段（JSON数组格式如 [1,2,3,4,5,6]），检查当前周是否在范围内
    if (currentWeek.value && c.weeks) {
      try {
        const weekList = typeof c.weeks === 'string' ? JSON.parse(c.weeks) : c.weeks
        if (Array.isArray(weekList) && !weekList.includes(currentWeek.value)) return false
      } catch (e) {
        // 解析失败则不按周次过滤（兼容旧格式）
      }
    }
    return c.dayOfWeek === day && getPeriodKey(c.startTime) === periodKey
  })
}

function editCourse(course) {
  editingCourse.value = course
  Object.assign(courseForm, {
    name: course.name,
    teacher: course.teacher,
    classroom: course.classroom,
    dayOfWeek: course.dayOfWeek,
    startTime: course.startTime,
    endTime: course.endTime,
    weeks: course.weeks,
    color: course.color,
    description: course.description || ''
  })
  showAddDialog.value = true
}

function resetForm() {
  editingCourse.value = null
  Object.assign(courseForm, {
    name: '', teacher: '', classroom: '',
    dayOfWeek: 1, startTime: '08:10', endTime: '09:40',
    weeks: '1-16', color: '#6366F1', description: ''
  })
}

async function saveCourse() {
  if (!courseForm.name) {
    MessagePlugin.warning('请输入课程名称')
    return
  }
  const payload = { ...courseForm, color: toHexColor(courseForm.color) }
  try {
    if (editingCourse.value) {
      await request.put(`/teacher/courses/${editingCourse.value.id}`, payload)
      MessagePlugin.success('课程更新成功')
    } else {
      await request.post('/teacher/courses', { ...payload, semester: currentSemesterLabel.value })
      MessagePlugin.success('课程添加成功')
    }
    showAddDialog.value = false
    resetForm()
    loadCourses()
  } catch (e) { MessagePlugin.error('操作失败') }
}

function toHexColor(color) {
  if (!color || typeof color !== 'string') return color
  color = color.trim()
  if (/^#[0-9a-fA-F]{6}$/.test(color)) return color
  if (/^#[0-9a-fA-F]{3}$/.test(color)) {
    return '#' + color[1] + color[1] + color[2] + color[2] + color[3] + color[3]
  }
  const m = color.match(/^rgba?\((\d+),\s*(\d+),\s*(\d+)/)
  if (m) {
    const hex = (n) => ('0' + parseInt(n).toString(16)).slice(-2)
    return '#' + hex(m[1]) + hex(m[2]) + hex(m[3])
  }
  return color
}

async function deleteCourse(course) {
  try {
    await request.del(`/teacher/courses/${course.id}`)
    MessagePlugin.success('课程已删除')
    loadCourses()
  } catch (e) { MessagePlugin.error('删除失败') }
}

function hexToRgba(hex, alpha = 1) {
  const sanitized = hex.replace('#', '')
  const bigint = parseInt(sanitized, 16)
  const r = (bigint >> 16) & 255
  const g = (bigint >> 8) & 255
  const b = bigint & 255
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

function getCourseStyle(course) {
  let color = course.color || '#6366F1'
  // 旧默认蓝色替换为新主题色，避免历史数据仍显示丑蓝
  if (color.toLowerCase() === '#0052d9') color = '#6366F1'
  return {
    backgroundColor: hexToRgba(color, 0.08)
  }
}

function openCourseDetail(course) {
  selectedCourse.value = course
  showDetailDialog.value = true
}

function onEditFromDetail() {
  showDetailDialog.value = false
  if (selectedCourse.value) {
    editCourse(selectedCourse.value)
  }
}

function exportSchedule() {
  MessagePlugin.info('导出功能开发中，可先使用浏览器打印为 PDF')
}
</script>

<style scoped>
</style>
