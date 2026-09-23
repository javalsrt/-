<template>
  <div class="page-container">
    <div class="page-header course-list-header">
      <div>
        <h2>课程管理</h2>
        <p>{{ isAdmin ? '管理所有课程信息' : '查看并管理您教授的课程' }}</p>
      </div>
      <t-button v-if="isAdmin" theme="primary" @click="showAddDialog = true">
        <template #icon><t-icon name="add" /></template>
        添加课程
      </t-button>
    </div>


    <t-card :title="isAdmin ? '全部课程' : '我的课程'" :bordered="false">
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

const showAddDialog = ref(false)
const editingCourse = ref(null)
const formRef = ref(null)
const courseList = ref([])

const currentSemester = computed(() => {
  const now = new Date()
  const year = now.getFullYear()
  const month = now.getMonth() + 1
  const startYear = month >= 9 ? year : year - 1
  const endYear = startYear + 1
  const term = month >= 3 && month <= 7 ? '2' : '1'
  return `${startYear}-${endYear}-${term}`
})

onMounted(() => loadCourses())

async function loadCourses() {
  try {
    const data = await request.get('/teacher/courses')
    courseList.value = data || []
  } catch (e) { console.warn('课程加载失败', e) }
}

const courseForm = reactive({
  name: '',
  teacher: '',
  classroom: '',
  dayOfWeek: 1,
  startTime: '08:10',
  endTime: '09:40',
  weeks: '1-16',
  color: '#0052d9',
  description: ''
})

const courseRules = {
  name: [{ required: true, message: '请输入课程名称' }],
  dayOfWeek: [{ required: true, message: '请选择星期' }]
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
    weeks: '1-16', color: '#0052d9', description: ''
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
      await request.post('/teacher/courses', { ...payload, semester: currentSemester.value })
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
</script>

<style scoped>
.course-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
