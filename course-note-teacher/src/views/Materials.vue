<template>
  <div class="page-container">
    <div class="page-header materials-header">
      <div>
        <h2>资料管理</h2>
        <p>查看和管理学生上传的课堂资料，共 {{ filteredMaterials.length }} 条记录</p>
      </div>
      <t-button theme="primary" @click="exportMaterials">
        <template #icon><t-icon name="download" /></template>
        导出资料
      </t-button>
    </div>

    <div class="table-toolbar">
      <t-space>
        <t-input v-model="searchKeyword" placeholder="搜索标题 / 内容" clearable style="width:220px" @enter="loadData">
          <template #prefix-icon><t-icon name="search" /></template>
        </t-input>
        <t-select v-model="typeFilter" placeholder="资料类型" style="width:130px" clearable>
          <t-option value="NOTE" label="笔记" /><t-option value="PHOTO" label="拍照" />
          <t-option value="RECORDING" label="录音" /><t-option value="THIRD_PARTY" label="导入" />
        </t-select>
        <t-select v-model="courseFilter" placeholder="按课程" style="width:180px" clearable>
          <t-option v-for="c in courses" :key="c.id" :value="c.id" :label="c.name" />
        </t-select>
        <t-select v-model="aiFilter" placeholder="AI处理状态" style="width:130px" clearable>
          <t-option :value="true" label="已处理" />
          <t-option :value="false" label="未处理" />
        </t-select>
      </t-space>
      <t-button theme="default" @click="loadData">
        <template #icon><t-icon name="refresh" /></template>
        刷新
      </t-button>
    </div>

    <t-skeleton :loading="loading" animation="gradient" theme="article">
      <t-card :bordered="false">
        <t-table :data="filteredMaterials" :columns="columns" row-key="id" :pagination="{ defaultPageSize: prefPageSize }" stripe hover>
          <template #type="{ row }">
            <t-tag variant="light" :theme="typeTheme(row.type)" size="small">{{ typeLabel(row.type) }}</t-tag>
          </template>
          <template #aiProcessed="{ row }">
            <t-tag :theme="row.aiProcessed ? 'success' : 'default'" variant="light" size="small">{{ row.aiProcessed ? '已处理' : '未处理' }}</t-tag>
          </template>
          <template #operation="{ row }">
            <t-space>
              <t-link theme="primary" @click="viewContent(row)">查看</t-link>
              <t-link v-if="!row.aiProcessed" theme="primary" @click="aiProcess(row)">AI处理</t-link>
            </t-space>
          </template>
        </t-table>
        <div v-if="!loading && filteredMaterials.length === 0" class="empty-tip">
          <t-icon name="file" size="48px" style="color:#E5E7EB;margin-bottom:12px" />
          <div>暂无符合条件的资料</div>
        </div>
      </t-card>
    </t-skeleton>

    <t-dialog v-model:visible="showContent" header="资料详情" width="700px">
      <t-descriptions bordered v-if="currentMaterial">
        <t-descriptions-item label="标题">{{ currentMaterial.title }}</t-descriptions-item>
        <t-descriptions-item label="类型">{{ typeLabel(currentMaterial.type) }}</t-descriptions-item>
        <t-descriptions-item label="学生">{{ currentMaterial.studentName }}</t-descriptions-item>
        <t-descriptions-item label="课程">{{ currentMaterial.courseName }}</t-descriptions-item>
        <t-descriptions-item label="时间">{{ currentMaterial.createdAt }}</t-descriptions-item>
        <t-descriptions-item label="AI标签">{{ currentMaterial.aiTags || '无' }}</t-descriptions-item>
        <t-descriptions-item label="AI摘要">{{ currentMaterial.aiSummary || '无' }}</t-descriptions-item>
      </t-descriptions>
      <div v-if="currentMaterial?.content" class="material-content">{{ currentMaterial.content }}</div>
      <div v-if="currentMaterial?.fileUrl" style="margin-top:16px">
        <t-link theme="primary" hover="color" @click="previewFile(currentMaterial)">
          <template #prefix-icon><t-icon name="browse" /></template>查看文件
        </t-link>
      </div>
    </t-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { MessagePlugin } from 'tdesign-vue-next'
import request from '@/utils/request'

const searchKeyword = ref('')
const typeFilter = ref('')
const courseFilter = ref('')
const aiFilter = ref(null)
const showContent = ref(false)
const currentMaterial = ref(null)
const courses = ref([])
const materials = ref([])
const loading = ref(false)

const prefPageSize = computed(() => {
  try {
    const p = JSON.parse(localStorage.getItem('teacher_pref') || '{}')
    return p.pageSize || 10
  } catch (e) { return 10 }
})

const typeLabel = t => ({ NOTE: '笔记', PHOTO: '拍照', RECORDING: '录音', THIRD_PARTY: '导入' }[t] || t)
const typeTheme = t => ({ NOTE: 'primary', PHOTO: 'success', RECORDING: 'warning', THIRD_PARTY: 'default' }[t] || 'default')

const columns = [
  { colKey: 'title', title: '标题', width: 220 },
  { colKey: 'type', title: '类型', width: 80 },
  { colKey: 'studentName', title: '学生', width: 100 },
  { colKey: 'courseName', title: '课程', width: 150 },
  { colKey: 'aiTags', title: 'AI标签', width: 160 },
  { colKey: 'aiProcessed', title: '处理', width: 80 },
  { colKey: 'createdAt', title: '时间', width: 110 },
  { colKey: 'operation', title: '操作', width: 120 }
]

const filteredMaterials = computed(() => materials.value.filter(m => {
  if (searchKeyword.value) {
    const kw = searchKeyword.value.trim().toLowerCase()
    if (!((m.title || '').toLowerCase().includes(kw) || (m.content || '').toLowerCase().includes(kw))) return false
  }
  if (typeFilter.value && m.type !== typeFilter.value) return false
  if (courseFilter.value && m.courseId !== courseFilter.value) return false
  if (aiFilter.value !== null && aiFilter.value !== '' && m.aiProcessed !== aiFilter.value) return false
  return true
}))

async function loadData() {
  loading.value = true
  try {
    const [courseData, matData] = await Promise.all([
      request.get('/teacher/courses').catch(() => []),
      request.get('/teacher/materials').catch(() => [])
    ])
    courses.value = [...new Map((courseData || []).map(c => [c.name, { id: c.id, name: c.name }])).values()]
    materials.value = matData || []
  } catch (e) {
    MessagePlugin.error('资料加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => loadData())

function viewContent(row) {
  currentMaterial.value = row
  showContent.value = true
}

async function previewFile(material) {
  try {
    const response = await request.get(`/files/materials/${material.id}/preview`, { responseType: 'blob' })
    const url = URL.createObjectURL(new Blob([response]))
    window.open(url, '_blank', 'noopener,noreferrer')
    window.setTimeout(() => URL.revokeObjectURL(url), 60000)
  } catch (e) {
    MessagePlugin.error('文件预览失败')
  }
}

async function aiProcess(row) {
  try {
    await request.post(`/materials/${row.id}/ai-process`)
    MessagePlugin.success('AI 处理已触发')
    loadData()
  } catch (e) {
    MessagePlugin.error('AI 处理失败')
  }
}

function exportMaterials() {
  const headers = ['标题', '类型', '学生', '课程', 'AI处理', 'AI标签', '时间']
  const rows = filteredMaterials.value.map(m => [
    m.title, typeLabel(m.type), m.studentName, m.courseName, m.aiProcessed ? '已处理' : '未处理', m.aiTags || '', m.createdAt
  ])
  const csv = [headers, ...rows].map(r => r.join(',')).join('\n')
  const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `资料列表_${new Date().toISOString().split('T')[0]}.csv`
  link.click()
  MessagePlugin.success('导出成功')
}
</script>

<style scoped>
.materials-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
}

.material-content {
  margin-top: 16px;
  white-space: pre-wrap;
  padding: 12px;
  background: #f9f9f9;
  border-radius: 8px;
  max-height: 300px;
  overflow-y: auto;
  font-size: 14px;
  line-height: 1.6;
}

.empty-tip {
  text-align: center;
  padding: 48px 0;
  color: #AEAEB2;
  font-size: 14px;
}

@media (max-width: 768px) {
  .materials-header {
    flex-direction: column;
    align-items: flex-start;
  }
  .table-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
