<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2>数据统计</h2>
        <p>关键指标一眼看清，学生差异一目了然</p>
      </div>
      <t-space>
        <t-select
          v-model="selectedCourse"
          :options="courseOptions"
          clearable
          placeholder="全部课程"
          style="width: 200px"
          @change="refresh"
        />
        <t-button theme="default" @click="refresh">
          <template #icon><t-icon name="refresh" /></template>
          刷新数据
        </t-button>
      </t-space>
    </div>

    <t-skeleton :loading="loading" animation="gradient" theme="article">
      <!-- KPI 卡片 -->
      <div class="kpi-cards">
        <div class="kpi-card" v-for="k in kpiList" :key="k.label">
          <div class="kpi-meta">
            <span class="kpi-label">{{ k.label }}</span>
            <span class="kpi-trend" :class="k.trend >= 0 ? 'up' : 'down'" v-if="k.trend !== undefined">
              {{ k.trend >= 0 ? '↑' : '↓' }} {{ Math.abs(k.trend) }}% <span class="trend-text">较上周期</span>
            </span>
          </div>
          <div class="kpi-value" :style="{ color: k.color }">{{ k.value }}</div>
        </div>
      </div>
    </t-skeleton>

    <t-skeleton :loading="loading" animation="gradient" theme="article">
      <div class="stats-grid">
        <!-- 学生知识库贡献 Top 10 -->
        <div class="stat-card">
          <div class="stat-card-header">
            <div class="stat-card-title">学生知识库贡献 Top 10</div>
            <div class="stat-card-sub">按上传文档数排名</div>
          </div>
          <div ref="rankingChart" class="stat-chart"></div>
          <div class="stat-empty" v-if="!loading && topStudents.length === 0">暂无数据</div>
        </div>

        <!-- 课程资料分布 / 单课程资料类型分布 -->
        <div class="stat-card">
          <div class="stat-card-header">
            <div class="stat-card-title">{{ selectedCourse ? '资料类型分布' : '课程资料分布' }}</div>
            <div class="stat-card-sub">{{ selectedCourse ? '当前课程资料类型占比' : '各课程资料数量占比' }}</div>
          </div>
          <div ref="courseChart" class="stat-chart"></div>
          <div class="stat-empty" v-if="!loading && courseMaterials.length === 0">暂无数据</div>
        </div>
      </div>
    </t-skeleton>

    <t-skeleton :loading="loading" animation="gradient" theme="article">
      <div class="stats-grid">
        <!-- 活跃 vs 待激励 -->
        <div class="stat-card">
          <div class="stat-card-header">
            <div class="stat-card-title">学生投入度对比</div>
            <div class="stat-card-sub">资料上传数 Top 5 与待激励学生</div>
          </div>
          <div class="contrast-summary">
            班级平均 <strong>{{ averageUploads }}</strong> 份 ·
            <strong>{{ participatedStudents.length }}</strong> 人参与 ·
            <strong style="color: #FF3B30">{{ zeroUploadStudents.length }}</strong> 人未上传
          </div>
          <div class="contrast-list">
            <div class="contrast-group">
              <div class="contrast-label">积极参与</div>
              <div class="contrast-item" v-for="(s, i) in topActive" :key="'top-'+s.id">
                <span class="contrast-rank" :style="{ background: '#0A84FF20', color: '#0A84FF' }">{{ i + 1 }}</span>
                <span class="contrast-name">{{ s.nickname }}</span>
                <span class="contrast-value" style="color: #0A84FF">{{ s.materialCount }} 份</span>
              </div>
              <div class="stat-empty" v-if="!loading && topActive.length === 0" style="min-height: 80px">暂无数据</div>
            </div>
            <div class="contrast-divider"></div>
            <div class="contrast-group">
              <div class="contrast-label">
                待激励
                <span class="contrast-hint">（0 上传优先，其次 1-2 份）</span>
              </div>
              <div class="contrast-item" v-for="(s, i) in lowActiveStudents" :key="'btm-'+s.id">
                <span class="contrast-rank" :class="s.materialCount === 0 ? 'zero' : 'low'">{{ i + 1 }}</span>
                <span class="contrast-name">{{ s.nickname }}</span>
                <span class="contrast-value" :class="s.materialCount === 0 ? 'zero' : 'low'">{{ s.materialCount === 0 ? '未上传' : s.materialCount + ' 份' }}</span>
              </div>
              <div class="stat-empty" v-if="!loading && lowActiveStudents.length === 0" style="min-height: 80px">暂无待激励学生</div>
            </div>
          </div>
          <div class="contrast-footer">
            <t-link theme="primary" hover="color" @click="router.push('/students')">查看全部学生</t-link>
          </div>
        </div>

        <!-- 近 30 天趋势 -->
        <div class="stat-card">
          <div class="stat-card-header">
            <div class="stat-card-title">近 30 天学习趋势</div>
            <div class="stat-card-sub">资料与知识库文档新增数量</div>
          </div>
          <div ref="trendChart" class="stat-chart"></div>
          <div class="stat-empty" v-if="!loading && trendDates.length === 0">暂无数据</div>
        </div>
      </div>
    </t-skeleton>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import request from '@/utils/request'

const router = useRouter()

const loading = ref(false)
const rankingChart = ref(null)
const courseChart = ref(null)
const trendChart = ref(null)

let rankingInstance = null
let courseInstance = null
let trendInstance = null
let resizeObserver = null

const selectedCourse = ref('')
const courseOptions = ref([])

const kpiList = ref([
  { label: '学生总数', value: 0, color: '#0A84FF' },
  { label: '课程总数', value: 0, color: '#34C759' },
  { label: '资料总数', value: 0, color: '#FF9500', trend: 0 },
  { label: '知识库文档', value: 0, color: '#AF52DE', trend: 0 }
])

const allStudents = ref([])
const allMaterials = ref([])
const trendDates = ref([])

/** 当前筛选下的学生ID（按资料归属课程名去重：教务课程名与学生个人课程同名互认） */
const filteredStudentIds = computed(() => {
  if (!selectedCourse.value) return new Set(allStudents.value.map(s => s.id))
  return new Set(allMaterials.value
    .filter(m => m.courseName === selectedCourse.value)
    .map(m => m.studentId))
})

/** 当前筛选下的学生列表 */
const filteredStudents = computed(() => {
  return allStudents.value.filter(s => filteredStudentIds.value.has(s.id))
})

/** 知识库贡献Top10学生 */
const topStudents = computed(() => {
  return [...filteredStudents.value]
    .sort((a, b) => (b.kbDocCount || 0) - (a.kbDocCount || 0))
    .slice(0, 10)
})

/** 课程/资料类型分布数据 */
const courseMaterials = computed(() => {
  if (selectedCourse.value) {
    const typeMap = {}
    const typeNameMap = { PHOTO: '拍照', NOTE: '笔记', THIRD_PARTY: '导入' }
    allMaterials.value
      .filter(m => m.courseName === selectedCourse.value)
      .forEach(m => {
        const name = typeNameMap[m.type] || m.type || '其他'
        typeMap[name] = (typeMap[name] || 0) + 1
      })
    return Object.entries(typeMap).map(([name, value]) => ({ name, value })).sort((a, b) => b.value - a.value)
  }
  const courseMap = {}
  allMaterials.value.forEach(m => {
    const name = m.courseName?.trim() || (m.courseId ? `课程 ${m.courseId}` : '未分类')
    courseMap[name] = (courseMap[name] || 0) + 1
  })
  return Object.entries(courseMap).map(([name, value]) => ({ name, value })).sort((a, b) => b.value - a.value)
})

/** 有上传记录的学生 */
const participatedStudents = computed(() => {
  return filteredStudents.value.filter(s => (s.materialCount || 0) > 0)
})

/** 班级平均上传数 */
const averageUploads = computed(() => {
  const list = participatedStudents.value
  if (list.length === 0) return 0
  return (list.reduce((sum, s) => sum + (s.materialCount || 0), 0) / list.length).toFixed(1)
})

/** 未上传学生 */
const zeroUploadStudents = computed(() => {
  return filteredStudents.value.filter(s => (s.materialCount || 0) === 0)
})

/** 积极参与Top5学生 */
const topActive = computed(() => {
  return [...participatedStudents.value]
    .sort((a, b) => (b.materialCount || 0) - (a.materialCount || 0))
    .slice(0, 5)
})

/** 待激励学生（0上传优先，其次1-2份） */
const lowActiveStudents = computed(() => {
  const zeros = zeroUploadStudents.value
  const lows = participatedStudents.value
    .filter(s => (s.materialCount || 0) > 0 && (s.materialCount || 0) <= 2)
    .sort((a, b) => (a.materialCount || 0) - (b.materialCount || 0))
  return [...zeros, ...lows].slice(0, 5)
})

/** 计算趋势增长率 */
function computeTrend(series) {
  if (!series || series.length < 2) return 0
  const half = Math.floor(series.length / 2)
  const prev = series.slice(0, half).reduce((a, b) => a + b, 0)
  const curr = series.slice(half).reduce((a, b) => a + b, 0)
  if (prev === 0) return curr > 0 ? 100 : 0
  return Math.round(((curr - prev) / prev) * 100)
}

/** 调整图表尺寸 */
function resizeCharts() {
  rankingInstance?.resize()
  courseInstance?.resize()
  trendInstance?.resize()
}

/** 刷新统计数据 */
async function refresh() {
  loading.value = true
  try {
    const courseName = selectedCourse.value || undefined
    const [courses, dashboard, students, materials, trend] = await Promise.all([
      request.get('/teacher/courses'),
      request.get('/teacher/dashboard'),
      request.get('/teacher/students'),
      request.get('/teacher/materials', { params: courseName ? { courseName } : {} }),
      request.get('/teacher/stats/trend', { params: courseName ? { courseName } : {} })
    ])

    const seen = new Set()
    courseOptions.value = [
      { label: '全部课程', value: '' },
      ...(courses || [])
        .filter(c => c.name && !seen.has(c.name) && seen.add(c.name))
        .map(c => ({ label: c.name, value: c.name }))
    ]
    allStudents.value = students || []
    allMaterials.value = materials || []
    trendDates.value = trend || []

    const stats = dashboard?.stats || {}
    const totalKbDocs = trendDates.value.reduce((sum, d) => sum + (d.kbDocs || 0), 0)

    let studentCount, courseCount, materialCount
    if (selectedCourse.value) {
      courseCount = 1
      materialCount = allMaterials.value.length
      studentCount = new Set(allMaterials.value.map(m => m.studentId)).size
    } else {
      courseCount = stats.courseCount || 0
      materialCount = stats.totalMaterials || 0
      studentCount = stats.studentCount || 0
    }

    const materialSeries = trendDates.value.map(d => d.materials || 0)
    const kbDocSeries = trendDates.value.map(d => d.kbDocs || 0)

    kpiList.value = [
      { label: '学生总数', value: studentCount, color: '#0A84FF' },
      { label: '课程总数', value: courseCount, color: '#34C759' },
      { label: '资料总数', value: materialCount, color: '#FF9500', trend: computeTrend(materialSeries) },
      { label: '知识库文档', value: totalKbDocs, color: '#AF52DE', trend: computeTrend(kbDocSeries) }
    ]

    await nextTick()
    setTimeout(() => { buildRankingChart(); buildCourseChart(); buildTrendChart(); resizeCharts() }, 100)
  } catch (e) {
    console.warn('统计数据加载失败', e)
  } finally {
    loading.value = false
  }
}

/** 构建学生贡献排名图 */
function buildRankingChart() {
  if (!rankingChart.value || topStudents.value.length === 0) return
  if (rankingInstance) rankingInstance.dispose()
  rankingInstance = echarts.init(rankingChart.value)

  const data = [...topStudents.value].reverse()
  rankingInstance.setOption({
    grid: { left: 64, right: 32, top: 8, bottom: 8 },
    xAxis: { type: 'value', show: false },
    yAxis: { type: 'category', data: data.map(s => s.nickname), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: '#1D1D1F', fontSize: 12, margin: 12, width: 52, overflow: 'truncate' } },
    series: [{ type: 'bar', data: data.map(s => s.kbDocCount || 0), barWidth: 14, itemStyle: { borderRadius: [0, 7, 7, 0], color: '#0A84FF' }, label: { show: true, position: 'right', color: '#86868B', fontSize: 12 }, showBackground: true, backgroundStyle: { color: '#F5F5F7', borderRadius: [0, 7, 7, 0] } }]
  })
}

/** 构建课程/资料类型分布图 */
function buildCourseChart() {
  if (!courseChart.value || courseMaterials.value.length === 0) return
  if (courseInstance) courseInstance.dispose()
  courseInstance = echarts.init(courseChart.value)

  const colors = ['#0A84FF', '#34C759', '#FF9500', '#AF52DE', '#5856D6', '#FF3B30', '#FFCC00', '#5AC8FA']
  courseInstance.setOption({
    color: colors,
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { type: 'scroll', orient: 'vertical', right: 0, top: 'center', textStyle: { color: '#1D1D1F', fontSize: 12 }, itemWidth: 10, itemHeight: 10, icon: 'circle' },
    series: [{ type: 'pie', radius: selectedCourse.value ? ['40%', '70%'] : ['48%', '72%'], center: ['35%', '50%'], data: courseMaterials.value, label: { show: false }, itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 } }]
  })
}

/** 构建近30天趋势图 */
function buildTrendChart() {
  if (!trendChart.value || trendDates.value.length === 0) return
  if (trendInstance) trendInstance.dispose()
  trendInstance = echarts.init(trendChart.value)

  const maxValue = Math.max(1, ...trendDates.value.flatMap(d => [d.materials || 0, d.kbDocs || 0]))
  const yMax = Math.max(5, maxValue + 2)

  trendInstance.setOption({
    color: ['#0A84FF', '#AF52DE'],
    grid: { left: 16, right: 16, top: 32, bottom: 24 },
    tooltip: { trigger: 'axis' },
    legend: { data: ['资料', '知识库文档'], top: 0, right: 0, textStyle: { fontSize: 12 } },
    xAxis: { type: 'category', data: trendDates.value.map(d => d.date), axisLine: { lineStyle: { color: '#E5E7EB' } }, axisTick: { show: false }, axisLabel: { color: '#86868B', fontSize: 11 } },
    yAxis: { type: 'value', min: 0, max: yMax, minInterval: 1, splitLine: { lineStyle: { color: '#F5F5F7' } }, axisLabel: { color: '#86868B', fontSize: 11 } },
    series: [
      { name: '资料', type: 'bar', data: trendDates.value.map(d => d.materials), barGap: '20%', barWidth: 10, itemStyle: { borderRadius: [4, 4, 0, 0], color: '#0A84FF' } },
      { name: '知识库文档', type: 'bar', data: trendDates.value.map(d => d.kbDocs), barWidth: 10, itemStyle: { borderRadius: [4, 4, 0, 0], color: '#AF52DE' } }
    ]
  })
}

onMounted(() => {
  refresh()
  window.addEventListener('resize', resizeCharts)
  resizeObserver = new ResizeObserver(resizeCharts)
  if (rankingChart.value) resizeObserver.observe(rankingChart.value)
  if (courseChart.value) resizeObserver.observe(courseChart.value)
  if (trendChart.value) resizeObserver.observe(trendChart.value)
})

onUnmounted(() => {
  window.removeEventListener('resize', resizeCharts)
  resizeObserver?.disconnect()
  rankingInstance?.dispose()
  courseInstance?.dispose()
  trendInstance?.dispose()
})
</script>

<style scoped>
.page-container {
  padding: 28px;
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 22px;
  font-weight: 600;
  color: #1D1D1F;
  letter-spacing: -0.02em;
  margin: 0 0 4px;
}

.page-header p {
  font-size: 13px;
  color: #86868B;
  margin: 0;
}

/* KPI 卡片 */
.kpi-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.kpi-card {
  background: #FFFFFF;
  border-radius: 14px;
  padding: 20px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  transition: transform 0.2s;
}

.kpi-card:hover {
  transform: translateY(-2px);
}

.kpi-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.kpi-label {
  font-size: 13px;
  color: #86868B;
  font-weight: 500;
}

.kpi-trend {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 6px;
}

.kpi-trend.up {
  color: #34C759;
  background: rgba(52, 199, 89, 0.1);
}

.kpi-trend.down {
  color: #FF3B30;
  background: rgba(255, 59, 48, 0.1);
}

.trend-text {
  font-weight: 400;
  opacity: 0.8;
}

.kpi-value {
  font-size: 32px;
  font-weight: 700;
  letter-spacing: -0.03em;
}

/* 统计网格 */
.stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 20px;
}

.stat-card {
  background: #FFFFFF;
  border-radius: 14px;
  padding: 20px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  min-height: 360px;
  display: flex;
  flex-direction: column;
}

.stat-card-header {
  margin-bottom: 16px;
}

.stat-card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1D1D1F;
  letter-spacing: -0.01em;
}

.stat-card-sub {
  font-size: 12px;
  color: #86868B;
  margin-top: 2px;
}

.stat-chart {
  flex: 1;
  min-height: 260px;
  width: 100%;
  min-width: 0;
}

.stat-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #86868B;
  font-size: 13px;
}

/* 对比列表 */
.contrast-list {
  flex: 1;
  display: flex;
  flex-direction: row;
  gap: 32px;
  padding: 0 8px;
}

.contrast-group {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.contrast-label {
  font-size: 12px;
  font-weight: 600;
  color: #86868B;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.contrast-hint {
  text-transform: none;
  font-weight: 400;
  color: #B0B0B0;
  letter-spacing: 0;
}

.contrast-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  background: #F5F5F7;
  border-radius: 10px;
}

.contrast-rank {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}

.contrast-name {
  flex: 1;
  font-size: 14px;
  color: #1D1D1F;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.contrast-value {
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}

.contrast-divider {
  width: 1px;
  background: #E5E7EB;
  margin: 0;
  flex-shrink: 0;
}

.contrast-summary {
  font-size: 13px;
  color: #86868B;
  margin-bottom: 16px;
  padding: 10px 12px;
  background: #F5F5F7;
  border-radius: 10px;
}

.contrast-summary strong {
  color: #1D1D1F;
  font-weight: 600;
}

.contrast-rank.zero {
  background: rgba(255, 59, 48, 0.12);
  color: #FF3B30;
}

.contrast-rank.low {
  background: rgba(255, 149, 0, 0.12);
  color: #FF9500;
}

.contrast-value.zero {
  color: #FF3B30;
}

.contrast-value.low {
  color: #FF9500;
}

.contrast-footer {
  margin-top: auto;
  padding-top: 12px;
  text-align: center;
}

@media (max-width: 900px) {
  .page-container {
    padding: 16px;
  }

  .kpi-cards {
    grid-template-columns: repeat(2, 1fr);
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }

  .stat-card {
    min-height: 320px;
  }
}

@media (max-width: 600px) {
  .contrast-list {
    flex-direction: column;
  }

  .contrast-divider {
    width: auto;
    height: 1px;
    margin: 0;
  }
}

@media (max-width: 480px) {
  .kpi-cards {
    grid-template-columns: 1fr;
  }

  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}
</style>
