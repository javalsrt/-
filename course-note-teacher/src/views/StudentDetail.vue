<template>
  <div class="page-container">
    <t-button variant="text" @click="$router.back()" style="margin-bottom:16px">
      <template #icon><t-icon name="chevron-left" /></template>返回学生列表
    </t-button>

    <!-- 加载失败 / 越权访问提示：后端返回403时给出明确页面级反馈，而非空框架 -->
    <div v-if="loadError" class="error-block">
      <div class="error-icon"><t-icon name="lock-on" size="40px" /></div>
      <h3>无法查看该学生</h3>
      <p>{{ loadError }}</p>
      <t-button theme="primary" variant="outline" @click="$router.push('/students')">返回学生列表</t-button>
    </div>

    <div class="student-header" v-else-if="student.id">
      <t-avatar size="56px">{{ displayName.charAt(0) }}</t-avatar>
      <div class="student-info">
        <h2>{{ displayName }}</h2>
        <p>{{ student.className || student.grade }} · {{ student.studentNo }} · {{ student.major || '未设置专业' }}</p>
      </div>
    </div>

    <template v-if="!loadError">
    <!-- 统计卡片 -->
    <div class="stat-cards">
      <div class="stat-card" v-for="s in statItems" :key="s.key">
        <div class="stat-icon" :style="{ background: s.bg, color: s.color }">
          <t-icon :name="s.icon" size="28px" />
        </div>
        <div class="stat-info">
          <h3>{{ s.value }}</h3>
          <p>{{ s.label }}</p>
        </div>
      </div>
    </div>

    <!-- 知识图谱 · 个人知识看板 -->
    <t-card title="知识图谱 · 个人知识看板" :bordered="false" style="margin-bottom:16px">
      <t-tabs v-model="activeTab">
        <t-tab-panel value="cards" label="课程卡片">
          <div class="knowledge-toolbar">
            <t-input v-model="searchQuery" placeholder="搜索课程名称" clearable size="small" class="kb-search">
              <template #suffix-icon><t-icon name="search" /></template>
            </t-input>
            <div class="kb-legend">
              <span class="legend-title">图例</span>
              <span class="legend-item"><span class="legend-dot" style="background:#6B7280"></span>资料</span>
              <span class="legend-item"><span class="legend-dot" style="background:#9CA3AF"></span>知识库</span>
              <span class="legend-item"><span class="legend-dot" style="background:#D1D5DB"></span>AI提问</span>
              <span class="legend-item"><span class="legend-dot" style="background:#E5E7EB"></span>笔记</span>
            </div>
          </div>

          <!-- 知识概览：课程活跃度分布 -->
          <div class="knowledge-overview" v-if="topActiveCourses.length > 0">
            <div class="overview-title">课程活跃度分布</div>
            <div class="overview-bars">
              <div v-for="c in topActiveCourses" :key="c.courseId" class="overview-bar-item">
                <div class="bar-label" :title="c.courseName">{{ c.courseName }}</div>
                <div class="bar-track">
                  <div class="bar-fill" :style="{ width: getActivityPercent(c) + '%' }"></div>
                </div>
                <div class="bar-value">{{ c.total }}</div>
              </div>
            </div>
          </div>

          <!-- 课程知识卡片网格 -->
          <div v-if="filteredCourseDetails.length===0" class="empty-tip">未找到匹配的课程数据</div>
          <div v-else class="course-card-grid">
            <div
              v-for="c in filteredCourseDetails"
              :key="c.courseId"
              class="course-knowledge-card"
              @click="openCourseDetail(c)"
            >
              <div class="course-card-header">
                <div class="course-card-name">{{ c.courseName }}</div>
                <div class="course-card-score">{{ getActivityPercent(c) }}%</div>
              </div>
              <div class="course-card-progress">
                <div class="progress-track">
                  <div class="progress-fill" :style="{ width: getActivityPercent(c) + '%' }"></div>
                </div>
              </div>
              <div class="course-card-stats">
                <div class="course-card-stat" v-for="r in resourceDefs" :key="r.key" :style="{ color: (c[r.key] || 0) > 0 ? r.color : '#C7C7CC' }">
                  <span class="stat-icon" v-html="getLeafIcon(r.type)"></span>
                  <span class="stat-value">{{ c[r.key] || 0 }}</span>
                  <span class="stat-label">{{ r.label }}</span>
                </div>
              </div>
              <div class="course-card-footer">
                <span v-if="c.total > 0">已积累 {{ c.total }} 项知识资源</span>
                <span v-else>暂无知识资源</span>
              </div>
            </div>
          </div>
        </t-tab-panel>

        <t-tab-panel value="graph" label="关系图谱">
          <div ref="knowledgeGraphRef" class="knowledge-graph"></div>
          <div class="graph-legend">
            <span class="legend-item"><span class="legend-dot" style="background:#1D1D1F"></span>学生</span>
            <span class="legend-item"><span class="legend-dot" style="background:#0071E3"></span>课程</span>
            <span class="legend-item"><span class="legend-dot" style="background:#3B82F6"></span>资料</span>
            <span class="legend-item"><span class="legend-dot" style="background:#60A5FA"></span>知识库</span>
            <span class="legend-item"><span class="legend-dot" style="background:#93C5FD"></span>AI提问</span>
            <span class="legend-item"><span class="legend-dot" style="background:#BFDBFE"></span>笔记</span>
          </div>
        </t-tab-panel>

        <t-tab-panel value="timeline" label="时间轴">
          <div ref="timelineChartRef" class="timeline-chart"></div>
        </t-tab-panel>
      </t-tabs>
    </t-card>

    <!-- 知识库文档列表 -->
    <t-card title="知识库文档" :bordered="false" style="margin-bottom:16px">
      <div class="kb-doc-toolbar">
        <t-input v-model="docSearch" placeholder="搜索文档标题" clearable size="small" style="width:240px">
          <template #prefix-icon><t-icon name="search" /></template>
        </t-input>
        <t-select v-model="docCourseFilter" placeholder="按课程筛选" clearable style="width:180px">
          <t-option v-for="c in courseDetails" :key="c.courseId" :value="c.courseId" :label="c.courseName" />
        </t-select>
      </div>
      <t-table :data="filteredKbDocs" :columns="kbDocColumns" row-key="id" :pagination="{ defaultPageSize: 8 }" stripe hover>
        <template #courseName="{ row }">
          <t-tag variant="light" size="small">{{ row.courseName }}</t-tag>
        </template>
        <template #indexStatus="{ row }">
          <t-tag :theme="row.indexStatus === 2 ? 'success' : 'default'" variant="light" size="small">
            {{ row.indexStatus === 2 ? '已索引' : '待索引' }}
          </t-tag>
        </template>
      </t-table>
    </t-card>


    <!-- 课程详情弹窗 -->
    <t-dialog v-model:visible="showCourseDetail" :header="selectedCourse?.courseName || '课程详情'" width="420px" @close="closeCourseDetail">
      <div v-if="selectedCourse" class="course-detail">
        <div class="detail-row">
          <span class="detail-label">课程名称</span>
          <span class="detail-value">{{ selectedCourse.courseName }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">活跃度</span>
          <span class="detail-value" :style="{ color: selectedCourse.color, fontSize: '20px', fontWeight: 700 }">{{ getActivityPercent(selectedCourse) }}%</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">资源统计</span>
          <div class="detail-stats">
            <div class="detail-stat" v-for="r in resourceDefs" :key="r.key">
              <span>{{ selectedCourse[r.key] || 0 }}</span>
              <span>{{ r.label }}</span>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <t-button theme="default" @click="closeCourseDetail">关闭</t-button>
      </template>
    </t-dialog>

    <!-- 最近上传 -->
    <t-card title="最近上传" :bordered="false">
      <div v-if="recentMaterials.length===0" class="empty-tip">暂无资料</div>
      <div v-for="m in recentMaterials" :key="m.id" class="mat-item">
        <t-icon :name="m.type==='PHOTO'?'image':'file'" style="color:#0052d9;font-size:20px" />
        <div class="mat-info">
          <div class="mat-title">{{ m.title }}</div>
          <div class="mat-meta">{{ m.createdAt }} · {{ m.courseName }}</div>
        </div>
      </div>
    </t-card>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts'
import request from '@/utils/request'

const route = useRoute()

const studentId = computed(() => route.params.id)
const student = ref({})
const courseDetails = ref([])
const recentMaterials = ref([])
const kbDocs = ref([])
const loadError = ref('')

// 学生显示名：优先真实姓名，历史数据回退昵称
const displayName = computed(() => student.value.realName || student.value.nickname || '学生')

// 搜索
const searchQuery = ref('')
const activeTab = ref('cards')
const docSearch = ref('')
const docCourseFilter = ref('')

// 图表
const knowledgeGraphRef = ref(null)
const timelineChartRef = ref(null)
let graphChart = null
let timelineChart = null

// 课程详情弹窗
const showCourseDetail = ref(false)
const selectedCourse = ref(null)

const kbDocColumns = [
  { colKey: 'title', title: '文档标题', width: 240 },
  { colKey: 'courseName', title: '所属课程', width: 150 },
  { colKey: 'summary', title: '摘要', ellipsis: true },
  { colKey: 'indexStatus', title: '索引状态', width: 100 },
  { colKey: 'createdAt', title: '创建时间', width: 160 }
]

const filteredKbDocs = computed(() => {
  let list = kbDocs.value
  if (docCourseFilter.value) {
    list = list.filter(d => d.courseId === docCourseFilter.value)
  }
  if (docSearch.value) {
    const kw = docSearch.value.trim().toLowerCase()
    list = list.filter(d => (d.title || '').toLowerCase().includes(kw) || (d.summary || '').toLowerCase().includes(kw))
  }
  return list
})

const statItems = computed(() => [
  { key: 'courseCount', label: '课程数', icon: 'books', bg: '#e8f4fd', color: '#0071E3', value: student.value.courseCount || 0 },
  { key: 'materialCount', label: '上传资料', icon: 'folder', bg: '#e8f4fd', color: '#0071E3', value: student.value.materialCount || 0 },
  { key: 'chatCount', label: 'AI提问次数', icon: 'chat', bg: '#e8f4fd', color: '#0071E3', value: student.value.chatCount || 0 },
  { key: 'kbDocCount', label: '知识库文档', icon: 'root-list', bg: '#e8f4fd', color: '#0071E3', value: student.value.kbDocCount || 0 },
  { key: 'noteCount', label: '笔记数', icon: 'edit', bg: '#e8f4fd', color: '#0071E3', value: student.value.noteCount || 0 }
])

const courseColors = [
  { bg: '#F5F5F7', border: '#E5E7EB' },
]

const resourceDefs = [
  { key: 'materialCount', label: '资料', color: '#1D1D1F', type: 'mat' },
  { key: 'kbDocCount', label: '知识库', color: '#1D1D1F', type: 'kb' },
  { key: 'chatCount', label: 'AI提问', color: '#1D1D1F', type: 'ai' },
  { key: 'noteCount', label: '笔记', color: '#1D1D1F', type: 'note' },
]

const leafIcons = {
  mat: `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/></svg>`,
  kb: `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H19a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1H6.5a1 1 0 0 1 0-5H20"/></svg>`,
  ai: `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M7.9 20A9 9 0 1 0 4 16.1L2 22Z"/></svg>`,
  note: `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/></svg>`,
}

function getLeafIcon(type) {
  return leafIcons[type] || leafIcons.mat
}

const filteredCourseDetails = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return courseDetails.value
  return courseDetails.value.filter(c => c.courseName.toLowerCase().includes(q))
})

const topActiveCourses = computed(() => {
  return [...filteredCourseDetails.value].sort((a, b) => b.total - a.total).slice(0, 8)
})

const maxTotal = computed(() => {
  const max = Math.max(...courseDetails.value.map(c => c.total), 1)
  return max
})

function getActivityPercent(course) {
  if (!course.total) return 0
  return Math.round((course.total / maxTotal.value) * 100)
}

function openCourseDetail(course) {
  selectedCourse.value = course
  showCourseDetail.value = true
}

function closeCourseDetail() {
  showCourseDetail.value = false
  selectedCourse.value = null
}

function buildKnowledgeGraph() {
  if (!knowledgeGraphRef.value) return
  if (graphChart) graphChart.dispose()
  graphChart = echarts.init(knowledgeGraphRef.value)

  const nodes = [
    {
      id: 'student',
      name: student.value.realName || student.value.nickname || '学生',
      symbolSize: 64,
      itemStyle: { color: '#1D1D1F' },
      label: { fontSize: 14, fontWeight: 'bold' }
    }
  ]
  const links = []
  const resourceColors = { material: '#3B82F6', kb: '#60A5FA', ai: '#93C5FD', note: '#BFDBFE' }
  const resourceLabels = { material: '资料', kb: '知识库', ai: 'AI提问', note: '笔记' }

  courseDetails.value.forEach((c, idx) => {
    nodes.push({
      id: `course-${c.courseId}`,
      name: c.courseName,
      symbolSize: 36,
      itemStyle: { color: c.color || '#0071E3' },
      category: 'course'
    })
    links.push({ source: 'student', target: `course-${c.courseId}` })

    ;[['materialCount', 'material'], ['kbDocCount', 'kb'], ['chatCount', 'ai'], ['noteCount', 'note']].forEach(([key, type]) => {
      const count = c[key] || 0
      if (count > 0) {
        const nodeId = `${type}-${c.courseId}`
        nodes.push({
          id: nodeId,
          name: `${resourceLabels[type]} ${count}`,
          symbolSize: Math.min(28, 10 + count * 3),
          itemStyle: { color: resourceColors[type] },
          category: type
        })
        links.push({ source: `course-${c.courseId}`, target: nodeId })
      }
    })
  })

  graphChart.setOption({
    tooltip: { formatter: (p) => p.data.name },
    animationDuration: 1500,
    animationEasingUpdate: 'quinticInOut',
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      data: nodes,
      links: links,
      categories: [
        { name: '学生', itemStyle: { color: '#1D1D1F' } },
        { name: '课程', itemStyle: { color: '#0071E3' } },
        { name: '资料', itemStyle: { color: '#3B82F6' } },
        { name: '知识库', itemStyle: { color: '#60A5FA' } },
        { name: 'AI提问', itemStyle: { color: '#93C5FD' } },
        { name: '笔记', itemStyle: { color: '#BFDBFE' } }
      ],
      label: { show: true, position: 'bottom', color: '#1D1D1F', fontSize: 11 },
      force: { repulsion: 280, edgeLength: 90, gravity: 0.05 },
      lineStyle: { color: 'source', curveness: 0.2, opacity: 0.6 },
      emphasis: { focus: 'adjacency', lineStyle: { width: 4 } }
    }]
  })
}

function buildTimelineChart() {
  if (!timelineChartRef.value) return
  if (timelineChart) timelineChart.dispose()
  timelineChart = echarts.init(timelineChartRef.value)

  const dates = []
  const matData = []
  const kbData = []
  const aiData = []
  const stats = student.value?.dailyStats || []
  const statMap = new Map(stats.map(s => [s.date, s]))

  for (let i = 29; i >= 0; i--) {
    const d = new Date()
    d.setDate(d.getDate() - i)
    const month = d.getMonth() + 1
    const day = d.getDate()
    dates.push(`${month}/${day}`)
    const dateFull = d.toISOString().split('T')[0]
    const s = statMap.get(dateFull)
    matData.push(s?.materialCount ?? 0)
    kbData.push(s?.kbDocCount ?? 0)
    aiData.push(s?.chatCount ?? 0)
  }

  timelineChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0, textStyle: { color: '#86868B' } },
    grid: { left: '3%', right: '4%', bottom: '12%', top: '8%', containLabel: true },
    xAxis: { type: 'category', data: dates, axisLine: { lineStyle: { color: '#E5E7EB' } }, axisLabel: { color: '#86868B' } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#F5F5F7' } }, axisLabel: { color: '#86868B' } },
    series: [
      { name: '资料上传', type: 'line', smooth: true, data: matData, itemStyle: { color: '#2ecc71' }, areaStyle: { opacity: 0.1 } },
      { name: '知识库', type: 'line', smooth: true, data: kbData, itemStyle: { color: '#5A9FD4' }, areaStyle: { opacity: 0.1 } },
      { name: 'AI 提问', type: 'line', smooth: true, data: aiData, itemStyle: { color: '#f39c12' }, areaStyle: { opacity: 0.1 } }
    ]
  })
}

function renderCharts() {
  if (activeTab.value === 'graph') {
    nextTick(() => buildKnowledgeGraph())
  } else if (activeTab.value === 'timeline') {
    nextTick(() => buildTimelineChart())
  }
}

function handleResize() {
  graphChart && graphChart.resize()
  timelineChart && timelineChart.resize()
}

onMounted(async () => {
  try {
    const detail = await request.get(`/teacher/students/${studentId.value}`)
    if (detail) {
      student.value = detail
      recentMaterials.value = detail.materials || []
      kbDocs.value = (detail.kbDocuments || []).map(d => ({
        ...d,
        courseName: d.courseName || (courseDetails.value.find(c => c.courseId === d.courseId)?.courseName || '未知课程')
      }))

      const details = (detail.courseDetails || []).map((c, i) => {
        const palette = courseColors[i % courseColors.length]
        return {
          ...c,
          color: palette.border,
          bg: palette.bg,
          total: (c.kbDocCount || 0) + (c.chatCount || 0) + (c.noteCount || 0) + (c.materialCount || 0)
        }
      })
      details.sort((a, b) => b.total - a.total)
      courseDetails.value = details

      renderCharts()
    }
  } catch (e) {
    console.warn('学生详情加载失败', e)
    // 后端返回403（非所教班级）等错误时，在页面持续展示错误信息而非空框架
    loadError.value = e?.message || '学生详情加载失败，请稍后重试'
  }

  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  graphChart && graphChart.dispose()
  timelineChart && timelineChart.dispose()
})

watch(activeTab, () => renderCharts())
watch(courseDetails, () => renderCharts(), { deep: true })
</script>

<style scoped>
.student-header { display:flex; align-items:center; gap:20px; margin-bottom:24px; }
.student-header h2 { font-size:24px; font-weight:600; margin-bottom:4px; color:#1D1D1F; }
.student-header p { font-size:14px; color:#86868B; }
.empty-tip { text-align:center; color:#AEAEB2; padding:32px; font-size:14px; }

/* 越权/加载失败错误提示块 */
.error-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 72px 24px;
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 12px;
  text-align: center;
}
.error-icon {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: #FEF3F2;
  color: #D92D20;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 4px;
}
.error-block h3 { font-size:18px; font-weight:600; color:#1D1D1F; margin:0; }
.error-block p { font-size:14px; color:#86868B; margin:0 0 8px; max-width: 420px; }

/* 工具栏 */
.knowledge-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 18px;
  padding: 0 4px;
}

.kb-search {
  width: 240px;
}

.kb-legend {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.legend-title {
  font-size: 12px;
  color: #86868B;
  font-weight: 500;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #1D1D1F;
  font-weight: 500;
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

/* 知识概览 */
.knowledge-overview {
  background: #FAFAFA;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 12px;
  padding: 16px 18px;
  margin-bottom: 18px;
}

.overview-title {
  font-size: 13px;
  font-weight: 600;
  color: #1D1D1F;
  margin-bottom: 12px;
}

.overview-bars {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.overview-bar-item {
  display: grid;
  grid-template-columns: 120px 1fr 32px;
  align-items: center;
  gap: 12px;
}

.bar-label {
  font-size: 12px;
  color: #1D1D1F;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.bar-track {
  height: 8px;
  background: #E5E7EB;
  border-radius: 4px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  background: #0071E3;
  border-radius: 4px;
  transition: width 0.6s ease;
}

.bar-value {
  font-size: 12px;
  color: #86868B;
  font-weight: 600;
  text-align: right;
}

/* 课程知识卡片网格 */
.course-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.course-knowledge-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  border: 1px solid #E5E7EB;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  cursor: pointer;
  transition: all 0.25s ease;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.course-knowledge-card:hover {
  border-color: #0071E3;
  box-shadow: 0 4px 12px rgba(0, 113, 227, 0.1);
}

.course-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.course-card-name {
  font-size: 15px;
  font-weight: 600;
  color: #1D1D1F;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}

.course-card-score {
  font-size: 16px;
  font-weight: 700;
  flex-shrink: 0;
  color: #0071E3;
}

.course-card-progress {
  height: 6px;
  background: #F5F5F7;
  border-radius: 3px;
  overflow: hidden;
}

.progress-track {
  height: 100%;
  background: #F5F5F7;
  border-radius: 3px;
}

.progress-fill {
  height: 100%;
  background: #0071E3;
  border-radius: 3px;
  transition: width 0.6s ease;
}

.course-card-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.course-card-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 8px 4px;
  background: #FAFAFA;
  border-radius: 10px;
  gap: 2px;
  transition: all 0.2s;
}

.course-card-stat:hover {
  background: #F5F5F7;
}

.stat-icon {
  width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-icon svg {
  width: 14px;
  height: 14px;
}

.stat-value {
  font-size: 15px;
  font-weight: 700;
  line-height: 1.2;
}

.stat-label {
  font-size: 11px;
  font-weight: 500;
  line-height: 1.2;
}

.course-card-footer {
  font-size: 12px;
  color: #86868B;
  text-align: center;
  padding-top: 4px;
  border-top: 1px solid rgba(0, 0, 0, 0.04);
}

/* 课程详情弹窗 */
.course-detail {
  padding: 8px 4px;
}

.course-detail .detail-row {
  display: flex;
  padding: 14px 0;
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
  align-items: flex-start;
}

.course-detail .detail-row:last-child {
  border-bottom: none;
}

.course-detail .detail-label {
  width: 80px;
  flex-shrink: 0;
  font-size: 14px;
  color: #86868B;
  font-weight: 400;
  line-height: 1.5;
}

.course-detail .detail-value {
  flex: 1;
  font-size: 14px;
  color: #1D1D1F;
  font-weight: 500;
  line-height: 1.5;
}

.detail-stats {
  display: flex;
  gap: 8px;
  flex: 1;
  flex-wrap: wrap;
}

.detail-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 64px;
  padding: 8px 10px;
  background: #F5F5F7;
  border-radius: 10px;
}

.detail-stat span:first-child {
  font-size: 18px;
  font-weight: 700;
  color: #1D1D1F;
}

.detail-stat span:last-child {
  font-size: 11px;
  color: #86868B;
  margin-top: 2px;
  font-weight: 500;
}

/* 资料列表 */
.mat-item { display:flex; align-items:center; gap:12px; padding:10px 0; border-bottom:1px solid rgba(0,0,0,0.04); }
.mat-item:last-child { border-bottom:none; }
.mat-title { font-size:14px; font-weight:500; color:#1D1D1F; }
.mat-meta { font-size:12px; color:#86868B; margin-top:2px; }

/* 知识图谱 */
.knowledge-graph {
  width: 100%;
  height: 420px;
}

.graph-legend {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding-top: 8px;
  border-top: 1px solid rgba(0,0,0,0.04);
  flex-wrap: wrap;
}

.timeline-chart {
  width: 100%;
  height: 360px;
}

.kb-doc-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

:deep(.t-tabs__nav-item-text) {
  font-weight: 500;
}

/* 响应式 */
@media (max-width: 768px) {
  .knowledge-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }
  .kb-search {
    width: 100%;
  }
  .course-card-grid {
    grid-template-columns: 1fr;
  }
  .overview-bar-item {
    grid-template-columns: 90px 1fr 28px;
  }
  .knowledge-graph { height: 320px; }
  .timeline-chart { height: 280px; }
  .kb-doc-toolbar { flex-direction: column; align-items: flex-start; }
}

</style>
