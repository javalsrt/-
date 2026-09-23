<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2>仪表盘</h2>
        <p>欢迎回来，{{ userStore.userInfo?.realName || '教师' }}老师</p>
      </div>
      <t-space>
        <t-button theme="default" @click="refresh">
          <template #icon><t-icon name="refresh" /></template>
        </t-button>
      </t-space>
    </div>

    <t-skeleton :loading="loading" animation="gradient" theme="article">
      <div class="dashboard-grid">
        <div class="app-card">
          <div class="app-card-header">
            <div class="app-card-title">今日课程</div>
            <t-link theme="primary" size="small" @click="goSchedule">查看课表</t-link>
          </div>
          <div v-if="todayCourses.length===0" class="empty-tip">今日暂无课程</div>
          <div v-for="c in todayCourses" :key="c.id" class="list-item list-item-link" @click="goSchedule">
            <div class="course-dot" :style="{ background: c.color || '#0071E3' }"></div>
            <div style="flex:1;min-width:0">
              <div class="item-label">{{ c.name }}</div>
              <div class="item-meta">{{ c.startTime }} - {{ c.endTime }} · {{ c.classroom }}</div>
            </div>
            <t-icon name="chevron-right" style="color:#C4C4CC;flex-shrink:0" />
          </div>
        </div>

        <div class="app-card">
          <div class="app-card-header">
            <div class="app-card-title">知识库概况</div>
            <t-link theme="primary" size="small" @click="goKnowledge">管理知识库</t-link>
          </div>
          <div v-if="knowledgeBases.length===0" class="empty-tip">暂无知识库</div>
          <div v-for="kb in knowledgeBases" :key="kb.id" class="list-item list-item-link" @click="goKnowledge">
            <div style="flex:1;min-width:0">
              <div class="item-label">{{ kb.name }}</div>
              <div class="item-meta">{{ kb.documentCount }} 篇文档</div>
            </div>
            <t-icon name="chevron-right" style="color:#C4C4CC;flex-shrink:0" />
          </div>
        </div>

        <div class="app-card">
          <div class="app-card-header">
            <div class="app-card-title">快捷入口</div>
          </div>
          <div class="quick-grid">
            <div v-for="q in quickLinks" :key="q.path" class="quick-item" @click="router.push(q.path)">
              <div class="quick-icon" :style="{ background: q.bg, color: q.color }">
                <t-icon :name="q.icon" size="20px" />
              </div>
              <span class="quick-label">{{ q.label }}</span>
            </div>
          </div>
        </div>
      </div>
    </t-skeleton>

  </div>
</template>


<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import request from '@/utils/request'

const router = useRouter()
const userStore = useUserStore()
const todayCourses = ref([])
const knowledgeBases = ref([])
const loading = ref(false)

/** 跳转课表页 */
function goSchedule() { router.push('/schedule') }

/** 跳转知识库页 */
function goKnowledge() { router.push('/knowledge') }

/** 快捷入口 */
const quickLinks = [
  { path: '/courses', label: '课程管理', icon: 'view-list', bg: 'rgba(0,113,227,0.08)', color: '#0071E3' },
  { path: '/schedule', label: '课表管理', icon: 'calendar', bg: 'rgba(52,199,89,0.10)', color: '#34C759' },
  { path: '/students', label: '学生管理', icon: 'user', bg: 'rgba(255,149,0,0.10)', color: '#FF9500' },
  { path: '/materials', label: '资料管理', icon: 'folder', bg: 'rgba(88,86,214,0.10)', color: '#5856D6' },
  { path: '/knowledge', label: '知识库管理', icon: 'book', bg: 'rgba(175,82,222,0.10)', color: '#AF52DE' },
  { path: '/statistics', label: '数据统计', icon: 'chart-bar', bg: 'rgba(6,182,212,0.10)', color: '#06B6D4' }
]

/** 刷新仪表盘数据 */
async function refresh() {
  loading.value = true
  try {
    const data = await request.get('/teacher/dashboard')
    if (data) {
      todayCourses.value = data.todayCourses || []
      knowledgeBases.value = data.knowledgeBases || []
    }
  } catch (e) {
    console.warn('刷新失败', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => refresh())
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 20px;
}

.app-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.app-card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1D1D1F;
}

.list-item-link {
  cursor: pointer;
  transition: background 0.15s;
  border-radius: 8px;
  margin: 0 -12px;
  padding: 12px;
}

.list-item-link:hover {
  background: #F5F5F7;
}

.course-dot {
  width: 8px; height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

/* 快捷入口 */
.quick-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 14px 8px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.quick-item:hover {
  background: #F5F5F7;
}

.quick-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.quick-label {
  font-size: 13px;
  color: #1D1D1F;
  font-weight: 500;
}

@media (max-width: 900px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }
  .dashboard-grid { grid-template-columns: 1fr; }
}

</style>
