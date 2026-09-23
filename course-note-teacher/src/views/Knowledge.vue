<template>
  <div class="page-container">
    <div class="page-header kb-header">
      <div>
        <h2>知识库管理</h2>
        <p>管理各课程 AI 知识库文档，查看索引与统计</p>
      </div>
      <t-button theme="primary" @click="openUpload">
        <template #icon><t-icon name="upload" /></template>
        上传文档
      </t-button>
    </div>

    <!-- 统计概览 -->
    <div class="stat-cards" v-if="currentKbId">
      <div class="stat-card animated-stat-card" :class="{ active: statusFilter === 'all' }" @click="statusFilter = 'all'" style="--index:0">
        <div class="stat-icon" style="background:#e8f4fd;color:#0071E3"><t-icon name="file" size="24px" /></div>
        <div class="stat-info"><h3>{{ kbStats.total }}</h3><p>文档总数</p></div>
      </div>
      <div class="stat-card animated-stat-card" :class="{ active: statusFilter === 'indexed' }" @click="statusFilter = 'indexed'" style="--index:1">
        <div class="stat-icon" style="background:#e8f8f0;color:#34C759"><t-icon name="check-circle" size="24px" /></div>
        <div class="stat-info"><h3>{{ kbStats.indexed }}</h3><p>已索引</p></div>
      </div>
      <div class="stat-card animated-stat-card" :class="{ active: statusFilter === 'pending' }" @click="statusFilter = 'pending'" style="--index:2">
        <div class="stat-icon" style="background:#fff5e8;color:#FF9500"><t-icon name="time" size="24px" /></div>
        <div class="stat-info"><h3>{{ kbStats.pending }}</h3><p>待索引</p></div>
      </div>
      <div class="stat-card animated-stat-card" :class="{ active: statusFilter === 'failed' }" @click="statusFilter = 'failed'" style="--index:3">
        <div class="stat-icon" style="background:#fde8e8;color:#FF3B30"><t-icon name="close-circle" size="24px" /></div>
        <div class="stat-info"><h3>{{ kbStats.failed }}</h3><p>索引失败</p></div>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="table-toolbar">
      <t-space>
        <t-select v-model="currentKbId" placeholder="选择课程知识库" style="width:300px" clearable @change="loadDocs">
          <t-option v-for="kb in knowledgeBases" :key="kb.id" :value="kb.id" :label="kb.name + (kb.className ? '（' + kb.className + '）' : '')" />
        </t-select>
        <t-input v-if="currentKbId" v-model="docSearch" placeholder="搜索文档标题 / 摘要" clearable style="width:240px">
          <template #prefix-icon><t-icon name="search" /></template>
        </t-input>
      </t-space>
      <t-space>
        <t-radio-group v-if="currentKbId" v-model="statusFilter" variant="default-filled">
          <t-radio-button value="all">全部</t-radio-button>
          <t-radio-button value="indexed">已索引</t-radio-button>
          <t-radio-button value="pending">待索引</t-radio-button>
          <t-radio-button value="failed">失败</t-radio-button>
        </t-radio-group>
        <t-radio-group v-if="currentKbId" v-model="viewMode" variant="default-filled">
          <t-radio-button value="table"><t-icon name="view-module" /></t-radio-button>
          <t-radio-button value="card"><t-icon name="view-grid" /></t-radio-button>
        </t-radio-group>
      </t-space>
    </div>

    <!-- 文档列表 -->
    <t-skeleton :loading="loading" animation="gradient" theme="article">
      <template v-if="currentKbId && documents.length > 0">
        <!-- 表格视图 -->
        <t-card :bordered="false" v-if="viewMode === 'table'">
          <t-table :data="filteredDocuments" :columns="docColumns" row-key="id" :pagination="{ defaultPageSize: 10 }" stripe hover>
            <template #title="{ row }">
              <div class="doc-title">
                <t-icon name="file" style="color:#0071E3;flex-shrink:0" />
                <span>{{ row.title }}</span>
              </div>
            </template>
            <template #indexStatus="{ row }">
              <t-tag :theme="statusTheme(row.indexStatus)" variant="light" size="small">
                {{ statusText(row.indexStatus) }}
              </t-tag>
            </template>
            <template #summary="{ row }">
              <span class="doc-summary">{{ row.summary || '暂无摘要' }}</span>
            </template>
            <template #operation="{ row }">
              <t-space>
                <t-link theme="primary" @click="editDoc(row)">编辑</t-link>
                <t-link v-if="row.indexStatus !== 2" theme="primary" @click="reindexDoc(row)">重新索引</t-link>
                <t-popconfirm content="确认删除？" @confirm="deleteDoc(row)">
                  <t-link theme="danger">删除</t-link>
                </t-popconfirm>
              </t-space>
            </template>
          </t-table>
        </t-card>

        <!-- 卡片视图 -->
        <div v-else class="doc-card-grid">
          <div v-for="(row, idx) in filteredDocuments" :key="row.id" class="doc-card animated-card-item" :style="{ '--index': idx }">
            <div class="doc-card-header">
              <div class="doc-title">
                <t-icon name="file" style="color:#0071E3;flex-shrink:0" />
                <span>{{ row.title }}</span>
              </div>
              <t-tag :theme="statusTheme(row.indexStatus)" variant="light" size="small">
                {{ statusText(row.indexStatus) }}
              </t-tag>
            </div>
            <p class="doc-card-summary">{{ row.summary || '暂无摘要' }}</p>
            <div class="doc-card-meta">
              <span>{{ row.userName || '未知' }}</span>
              <span>{{ row.createdAt }}</span>
            </div>
            <div class="doc-card-actions">
              <t-link theme="primary" @click="editDoc(row)">编辑</t-link>
              <t-link v-if="row.indexStatus !== 2" theme="primary" @click="reindexDoc(row)">重新索引</t-link>
              <t-popconfirm content="确认删除？" @confirm="deleteDoc(row)">
                <t-link theme="danger">删除</t-link>
              </t-popconfirm>
            </div>
          </div>
        </div>
      </template>

      <!-- 已选知识库但无文档 -->
      <t-card :bordered="false" v-if="currentKbId && !loading && documents.length === 0" class="kb-empty-card">
        <t-icon name="folder-open" size="48px" style="color:#E5E7EB;margin-bottom:16px" />
        <p style="color:#1D1D1F;font-size:16px;font-weight:500;margin-bottom:8px">该课程知识库暂无文档</p>
        <p style="color:#86868B;font-size:14px;margin-bottom:20px">点击右上角“上传文档”添加学习资料</p>
        <t-button theme="primary" @click="openUpload">上传文档</t-button>
      </t-card>
    </t-skeleton>

    <!-- 未选择课程时：知识库数据可视化看板 -->
    <template v-if="!currentKbId">
      <!-- 核心指标 -->
      <div class="stat-cards">
        <div v-for="(kpi, idx) in kpiList" :key="kpi.label" class="stat-card kb-kpi-card animated-card-item" :style="{ '--index': idx }">
          <div class="stat-icon" :style="{ background: kpi.color + '14', color: kpi.color }">
            <t-icon :name="kpi.icon" size="24px" />
          </div>
          <div class="stat-info">
            <h3>{{ kpi.value }}</h3>
            <p>{{ kpi.label }}</p>
          </div>
        </div>
      </div>

      <!-- 图表区 -->
      <div class="chart-row">
        <t-card :bordered="false" title="班级知识贡献 Top 10" class="chart-card">
          <div ref="rankingChart" class="chart-container" />
        </t-card>
        <t-card :bordered="false" title="班级资料分布" class="chart-card">
          <div ref="distributionChart" class="chart-container" />
        </t-card>
      </div>

      <t-card :bordered="false" title="近 30 天上传趋势" class="trend-card">
        <div ref="trendChart" class="trend-chart-container" />
      </t-card>

      <!-- 班级明细表格 -->
      <t-card :bordered="false" title="班级数据明细" style="margin-top: 16px;">
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
        </t-table>
        <div v-if="!loadingStudents && classSummaries.filter(c => c.name !== '全部班级').length === 0" class="empty-tip">
          <t-icon name="user" size="48px" style="color:#E5E7EB;margin-bottom:12px" />
          <div>暂无班级数据</div>
        </div>
      </t-card>
    </template>

    <!-- 编辑文档弹窗 -->
    <t-dialog v-model:visible="showEdit" header="编辑文档" width="600px" @confirm="saveEdit">
      <t-form label-width="80px">
        <t-form-item label="标题"><t-input v-model="editForm.title" /></t-form-item>
        <t-form-item label="摘要"><t-textarea v-model="editForm.summary" :maxlength="200" :autosize="{ minRows: 3, maxRows: 6 }" /></t-form-item>
      </t-form>
    </t-dialog>

    <!-- 上传文档弹窗 -->
    <t-dialog v-model:visible="showUpload" header="上传文档到知识库" width="520px" @confirm="handleUpload" @close="resetUpload">
      <t-form label-width="100px">
        <t-form-item label="选择课程">
          <t-select v-model="uploadForm.kbId" placeholder="选择要上传到的知识库">
            <t-option v-for="kb in uploadCourseOptions" :key="kb.id" :value="kb.id" :label="kb.name + (kb.className ? '（' + kb.className + '）' : '')" />
          </t-select>
        </t-form-item>
        <t-form-item label="文档标题">
          <t-input v-model="uploadForm.title" placeholder="可选，不填将自动识别" />
        </t-form-item>
        <t-form-item label="文档文件">
          <t-upload
            v-model="uploadForm.files"
            theme="file-input"
            accept=".pdf,.doc,.docx,.txt,.md,.ppt,.pptx"
            tips="支持 PDF、Word、PPT、TXT、Markdown"
            :auto-upload="false"
            :multiple="false"
            :max-size="52428800"
          />
        </t-form-item>
      </t-form>
    </t-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { MessagePlugin } from 'tdesign-vue-next'
import * as echarts from 'echarts'
import request from '@/utils/request'

const currentKbId = ref('')
const knowledgeBases = ref([])
const teacherCourses = ref([])
const documents = ref([])
const showEdit = ref(false)
const showUpload = ref(false)
const loading = ref(false)
const docSearch = ref('')
const statusFilter = ref('all')
const viewMode = ref('table')

// ===== 未选择课程时的知识库数据可视化看板 =====
const allStudents = ref([])
const loadingStudents = ref(false)
const trendDates = ref([])
const rankingChart = ref(null)
const distributionChart = ref(null)
const trendChart = ref(null)
let rankingInstance = null
let distributionInstance = null
let trendInstance = null
let resizeObserver = null
const classColorPalette = ['#0A84FF', '#34C759', '#FF9500', '#AF52DE', '#5856D6', '#FF3B30', '#FFCC00', '#5AC8FA']

const classColumns = [
  { colKey: 'className', title: '班级', width: 140 },
  { colKey: 'studentCount', title: '学生人数', width: 100, align: 'center' },
  { colKey: 'totalKbDocCount', title: '知识总量', width: 100, align: 'center' },
  { colKey: 'avgKbDocCount', title: '人均知识量', width: 110, align: 'center' },
  { colKey: 'totalMaterialCount', title: '资料总量', width: 100, align: 'center' },
  { colKey: 'avgMaterialCount', title: '人均资料量', width: 110, align: 'center' },
  { colKey: 'activeScore', title: '班级活跃度', width: 160 }
]

function classNameOf(student) {
  return student?.className || student?.grade || '-'
}

const classColorMap = new Map()
function classColorOf(className) {
  if (!className || className === '-') return '#86868B'
  if (!classColorMap.has(className)) {
    const idx = classColorMap.size % classColorPalette.length
    classColorMap.set(className, classColorPalette[idx])
  }
  return classColorMap.get(className)
}

function classSummary(name, color, students) {
  const count = students.length
  const totalKb = students.reduce((s, r) => s + (r.kbDocCount || 0), 0)
  const totalMat = students.reduce((s, r) => s + (r.materialCount || 0), 0)
  const avgKb = count ? (totalKb / count).toFixed(1) : '0.0'
  const avgMat = count ? (totalMat / count).toFixed(1) : '0.0'
  const score = Math.min(100, Math.round((totalKb + totalMat) / (count || 1) * 2))
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
  const groupMap = new Map()
  allStudents.value.forEach(s => {
    const name = classNameOf(s)
    if (!groupMap.has(name)) groupMap.set(name, [])
    groupMap.get(name).push(s)
  })
  groupMap.forEach((_, name) => classColorOf(name))

  const summaries = Array.from(groupMap.entries())
    .sort((a, b) => a[0].localeCompare(b[0], 'zh-CN'))
    .map(([name, students]) => classSummary(name, classColorOf(name), students))

  const allSummary = classSummary('全部班级', '#0071e3', allStudents.value)
  return [allSummary, ...summaries]
})

/** 看板核心指标 */
const kpiList = computed(() => {
  const totalKb = allStudents.value.reduce((s, r) => s + (r.kbDocCount || 0), 0)
  const totalMat = allStudents.value.reduce((s, r) => s + (r.materialCount || 0), 0)
  const classCount = classSummaries.value.filter(c => c.name !== '全部班级').length
  const activeStudents = allStudents.value.filter(s => (s.kbDocCount || 0) > 0 || (s.materialCount || 0) > 0).length
  return [
    { label: '知识库文档', value: totalKb, color: '#AF52DE', icon: 'file' },
    { label: '学习资料', value: totalMat, color: '#0A84FF', icon: 'folder-open' },
    { label: '覆盖班级', value: classCount, color: '#34C759', icon: 'user' },
    { label: '活跃学生', value: activeStudents, color: '#FF9500', icon: 'user' }
  ]
})

/** 调整图表尺寸 */
function resizeCharts() {
  rankingInstance?.resize()
  distributionInstance?.resize()
  trendInstance?.resize()
}

/** 构建班级知识贡献排名图 */
function buildRankingChart() {
  if (!rankingChart.value) return
  if (rankingInstance) rankingInstance.dispose()
  rankingInstance = echarts.init(rankingChart.value)
  const list = classSummaries.value.filter(c => c.name !== '全部班级').sort((a, b) => b.totalKbDocCount - a.totalKbDocCount).slice(0, 10).reverse()
  if (list.length === 0) {
    rankingInstance.setOption({ title: { text: '暂无班级数据', left: 'center', top: 'center', textStyle: { color: '#86868B', fontSize: 14 } }, series: [] })
    return
  }
  rankingInstance.setOption({
    grid: { left: 72, right: 32, top: 8, bottom: 8 },
    xAxis: { type: 'value', show: false },
    yAxis: { type: 'category', data: list.map(c => c.name), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: '#1D1D1F', fontSize: 12, margin: 12, width: 60, overflow: 'truncate' } },
    series: [{ type: 'bar', data: list.map(c => c.totalKbDocCount), barWidth: 16, itemStyle: { borderRadius: [0, 8, 8, 0], color: '#AF52DE' }, label: { show: true, position: 'right', color: '#86868B', fontSize: 12 }, showBackground: true, backgroundStyle: { color: '#F5F5F7', borderRadius: [0, 8, 8, 0] } }]
  })
}

/** 构建班级资料分布图 */
function buildDistributionChart() {
  if (!distributionChart.value) return
  if (distributionInstance) distributionInstance.dispose()
  distributionInstance = echarts.init(distributionChart.value)
  const list = classSummaries.value.filter(c => c.name !== '全部班级').filter(c => c.totalMaterialCount > 0).sort((a, b) => b.totalMaterialCount - a.totalMaterialCount).slice(0, 8)
  if (list.length === 0) {
    distributionInstance.setOption({ title: { text: '暂无班级资料数据', left: 'center', top: 'center', textStyle: { color: '#86868B', fontSize: 14 } }, series: [] })
    return
  }
  distributionInstance.setOption({
    color: classColorPalette,
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { type: 'scroll', orient: 'vertical', right: 0, top: 'center', textStyle: { color: '#1D1D1F', fontSize: 12 }, itemWidth: 10, itemHeight: 10, icon: 'circle' },
    series: [{ type: 'pie', radius: ['44%', '72%'], center: ['34%', '50%'], data: list.map(c => ({ name: c.name, value: c.totalMaterialCount })), label: { show: false }, itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 } }]
  })
}

/** 构建近30天上传趋势图 */
function buildTrendChart() {
  if (!trendChart.value) return
  if (trendInstance) trendInstance.dispose()
  trendInstance = echarts.init(trendChart.value)
  if (trendDates.value.length === 0) {
    trendInstance.setOption({ title: { text: '暂无趋势数据', left: 'center', top: 'center', textStyle: { color: '#86868B', fontSize: 14 } }, series: [] })
    return
  }
  const maxValue = Math.max(1, ...trendDates.value.flatMap(d => [d.materials || 0, d.kbDocs || 0]))
  const yMax = Math.max(5, maxValue + 2)
  trendInstance.setOption({
    color: ['#0A84FF', '#AF52DE'],
    grid: { left: 16, right: 16, top: 32, bottom: 24 },
    tooltip: { trigger: 'axis' },
    legend: { data: ['学习资料', '知识库文档'], top: 0, right: 0, textStyle: { fontSize: 12 } },
    xAxis: { type: 'category', data: trendDates.value.map(d => d.date), axisLine: { lineStyle: { color: '#E5E7EB' } }, axisTick: { show: false }, axisLabel: { color: '#86868B', fontSize: 11 } },
    yAxis: { type: 'value', min: 0, max: yMax, minInterval: 1, splitLine: { lineStyle: { color: '#F5F5F7' } }, axisLabel: { color: '#86868B', fontSize: 11 } },
    series: [
      { name: '学习资料', type: 'line', smooth: true, data: trendDates.value.map(d => d.materials || 0), lineStyle: { width: 3 }, symbol: 'circle', symbolSize: 6, areaStyle: { opacity: 0.12 } },
      { name: '知识库文档', type: 'line', smooth: true, data: trendDates.value.map(d => d.kbDocs || 0), lineStyle: { width: 3 }, symbol: 'circle', symbolSize: 6, areaStyle: { opacity: 0.12 } }
    ]
  })
}

/** 上传课程选项：已有知识库的课程 ∪ 教师所教课程（按课程名去重） */
const uploadCourseOptions = computed(() => {
  const seen = new Set()
  const options = []
  for (const kb of knowledgeBases.value) {
    if (kb.name && !seen.has(kb.name)) {
      seen.add(kb.name)
      options.push(kb)
    }
  }
  for (const c of teacherCourses.value) {
    if (c.name && !seen.has(c.name)) {
      seen.add(c.name)
      options.push({ id: c.name, name: c.name, className: '' })
    }
  }
  return options
})

const editForm = reactive({ id: null, title: '', summary: '', content: '' })
const uploadForm = reactive({ kbId: '', title: '', files: [] })

/** 文档表格列配置 */
const docColumns = [
  { colKey: 'title', title: '文档标题', width: 240 },
  { colKey: 'summary', title: '摘要', ellipsis: true },
  { colKey: 'indexStatus', title: '状态', width: 100 },
  { colKey: 'userName', title: '上传学生', width: 120 },
  { colKey: 'createdAt', title: '创建时间', width: 160 },
  { colKey: 'operation', title: '操作', width: 160 }
]

/** 知识库统计数据 */
const kbStats = computed(() => {
  const total = documents.value.length
  const indexed = documents.value.filter(d => d.indexStatus === 2).length
  const pending = documents.value.filter(d => d.indexStatus === 0 || d.indexStatus === 1).length
  const failed = documents.value.filter(d => d.indexStatus === 3).length
  return { total, indexed, pending, failed }
})

/** 搜索和状态筛选后的文档列表 */
const filteredDocuments = computed(() => {
  let list = documents.value
  if (docSearch.value) {
    const kw = docSearch.value.trim().toLowerCase()
    list = list.filter(d => (d.title || '').toLowerCase().includes(kw) || (d.summary || '').toLowerCase().includes(kw))
  }
  if (statusFilter.value === 'indexed') list = list.filter(d => d.indexStatus === 2)
  else if (statusFilter.value === 'pending') list = list.filter(d => d.indexStatus === 0 || d.indexStatus === 1)
  else if (statusFilter.value === 'failed') list = list.filter(d => d.indexStatus === 3)
  return list
})

/** 获取状态对应的主题样式 */
function statusTheme(status) {
  if (status === 2) return 'success'
  if (status === 3) return 'danger'
  return 'default'
}

/** 获取状态显示文本 */
function statusText(status) {
  if (status === 2) return '已索引'
  if (status === 3) return '失败'
  if (status === 1) return '索引中'
  return '待索引'
}

/** 切换回未选择课程状态时重新渲染图表；进入课程详情时释放图表实例 */
watch(currentKbId, async (val) => {
  if (!val) {
    await nextTick()
    setTimeout(() => { buildRankingChart(); buildDistributionChart(); buildTrendChart(); resizeCharts() }, 100)
  } else {
    rankingInstance?.dispose(); rankingInstance = null
    distributionInstance?.dispose(); distributionInstance = null
    trendInstance?.dispose(); trendInstance = null
  }
})

/** 页面加载时获取知识库列表、教师课程、学生与趋势数据 */
onMounted(async () => {
  try {
    loading.value = true
    loadingStudents.value = true
    await Promise.all([loadKnowledgeBases(), loadTeacherCourses(), loadStudents(), loadTrend()])
    await nextTick()
    setTimeout(() => { buildRankingChart(); buildDistributionChart(); buildTrendChart(); resizeCharts() }, 100)
    window.addEventListener('resize', resizeCharts)
    resizeObserver = new ResizeObserver(resizeCharts)
    if (rankingChart.value) resizeObserver.observe(rankingChart.value)
    if (distributionChart.value) resizeObserver.observe(distributionChart.value)
    if (trendChart.value) resizeObserver.observe(trendChart.value)
  } catch (e) {
    MessagePlugin.error('知识库列表加载失败')
  } finally {
    loading.value = false
    loadingStudents.value = false
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', resizeCharts)
  resizeObserver?.disconnect()
  rankingInstance?.dispose()
  distributionInstance?.dispose()
  trendInstance?.dispose()
})

/** 加载学生数据并按班级聚合：后端已按角色过滤（教师=所教班级，管理员=全部） */
async function loadStudents() {
  try {
    const data = await request.get('/teacher/students')
    allStudents.value = data || []
  } catch (e) {
    console.warn('学生数据加载失败', e)
  }
}

/** 加载近30天趋势数据 */
async function loadTrend() {
  try {
    trendDates.value = await request.get('/teacher/stats/trend') || []
  } catch (e) {
    console.warn('趋势数据加载失败', e)
  }
}

/** 加载当前知识库的文档列表（currentKbId 为课程名，后端按课程名聚合可见学生文档） */
async function loadDocs() {
  if (!currentKbId.value) { documents.value = []; return }
  loading.value = true
  try {
    documents.value = await request.get('/teacher/knowledge/documents', {
      params: { courseName: currentKbId.value }
    }) || []
  } catch (e) {
    MessagePlugin.error('文档加载失败')
  } finally {
    loading.value = false
  }
}

/** 打开上传弹窗，默认选中当前知识库 */
function openUpload() {
  uploadForm.kbId = currentKbId.value || ''
  showUpload.value = true
}

/** 编辑文档 */
function editDoc(row) {
  Object.assign(editForm, { id: row.id, title: row.title, summary: row.summary || '' })
  showEdit.value = true
}

/** 保存编辑 */
async function saveEdit() {
  try {
    await request.put(`/ai/knowledge/document/${editForm.id}`, { title: editForm.title, summary: editForm.summary })
    MessagePlugin.success('已更新')
    showEdit.value = false
    loadDocs()
  } catch (e) {
    MessagePlugin.error('更新失败')
  }
}

/** 删除文档 */
async function deleteDoc(row) {
  try {
    await request.delete(`/ai/knowledge/document/${row.id}`)
    MessagePlugin.success('已删除')
    loadDocs()
    // 刷新知识库列表，更新文档计数
    await loadKnowledgeBases()
  } catch (e) {
    MessagePlugin.error('删除失败')
  }
}

/** 加载知识库列表（供删除后刷新统计计数） */
async function loadKnowledgeBases() {
  try {
    knowledgeBases.value = await request.get('/teacher/knowledge') || []
  } catch (e) {
    // 静默失败，避免打扰用户
  }
}

/** 加载教师所教课程（用于上传弹窗的课程选项） */
async function loadTeacherCourses() {
  try {
    teacherCourses.value = await request.get('/teacher/courses') || []
  } catch (e) {
    // 静默失败，避免打扰用户
  }
}

/** 重新索引文档 */
async function reindexDoc(row) {
  try {
    await request.put(`/ai/knowledge/document/${row.id}`, { title: row.title, summary: row.summary || '' })
    MessagePlugin.success('已触发重新索引')
    loadDocs()
  } catch (e) {
    MessagePlugin.error('操作失败')
  }
}

/** 重置上传表单 */
function resetUpload() {
  uploadForm.kbId = ''
  uploadForm.title = ''
  uploadForm.files = []
}

/** 处理文档上传：先按课程名换取教师个人课程ID，再上传文件 */
async function handleUpload() {
  if (!uploadForm.kbId) { MessagePlugin.warning('请选择要上传到的知识库'); return }
  const file = uploadForm.files?.[0]
  if (!file) { MessagePlugin.warning('请选择文件'); return }
  try {
    // 课程名 → 教师个人课程ID（不存在时后端自动创建）
    const courseId = await request.get('/teacher/knowledge/course-id', {
      params: { courseName: uploadForm.kbId }
    })
    if (!courseId) throw new Error('课程ID获取失败')

    const form = new FormData()
    form.append('file', file.raw || file)
    form.append('courseId', courseId)
    if (uploadForm.title) form.append('title', uploadForm.title)
    await request.post('/ai/knowledge/upload', form, { headers: { 'Content-Type': 'multipart/form-data' } })
    MessagePlugin.success('文档上传成功')
    showUpload.value = false
    const uploadedKb = uploadForm.kbId
    resetUpload()
    await loadKnowledgeBases()
    if (currentKbId.value === uploadedKb) loadDocs()
    else { currentKbId.value = uploadedKb; loadDocs() }
  } catch (e) {
    MessagePlugin.error((e && e.message) || '上传失败')
  }
}
</script>

<style scoped>
.kb-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
}

.doc-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
  color: #1D1D1F;
}

.doc-summary {
  color: #86868B;
  font-size: 13px;
}

:deep(.t-upload__single-input) {
  width: 100%;
}

.stat-card {
  cursor: pointer;
  transition: all 0.2s ease;
  border: 2px solid transparent;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
}

.stat-card.active {
  border-color: #0071E3;
  background: #f5f9ff;
}

.kb-empty-card {
  text-align: center;
  padding: 80px 40px;
}

/* ===== 知识库看板（未选择课程时） ===== */
.kb-kpi-card {
  cursor: default;
}

.chart-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.chart-card {
  min-height: 360px;
}

.chart-container {
  width: 100%;
  height: 300px;
}

.trend-card {
  min-height: 320px;
}

.trend-chart-container {
  width: 100%;
  height: 260px;
}

.class-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.class-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.active-bar {
  display: flex;
  align-items: center;
  gap: 10px;
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
  font-size: 13px;
  color: #86868B;
  min-width: 32px;
  text-align: right;
}

.doc-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}

.doc-card {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  transition: box-shadow 0.2s ease;
}

.doc-card:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

.doc-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.doc-card-header .doc-title {
  flex: 1;
  min-width: 0;
}

.doc-card-header .doc-title span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-card-summary {
  color: #86868B;
  font-size: 14px;
  line-height: 1.6;
  margin: 0 0 16px 0;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.doc-card-meta {
  color: #AEAEB2;
  font-size: 13px;
  margin-bottom: 16px;
}

.doc-card-actions {
  display: flex;
  gap: 16px;
}

@media (max-width: 768px) {
  .kb-header {
    flex-direction: column;
    align-items: flex-start;
  }
  .table-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }
  .doc-card-grid {
    grid-template-columns: 1fr;
  }
  .chart-row {
    grid-template-columns: 1fr;
  }
  .chart-container {
    height: 260px;
  }
  .trend-chart-container {
    height: 220px;
  }
}
</style>
