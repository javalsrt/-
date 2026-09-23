<template>
  <div class="page-container">
    <div class="page-header semester-header">
      <div>
        <h2>学期管理</h2>
        <p>管理学期信息，包括正常学期和假期培训</p>
      </div>
      <t-button theme="primary" @click="openCreate">
        <template #icon><t-icon name="add" /></template>
        新建学期
      </t-button>
    </div>

    <!-- 当前学期信息卡片 -->
    <t-card :bordered="false" v-if="current" class="current-card">
      <template #title>
        <div class="current-head">
          <span class="current-label">当前学期</span>
          <t-tag :theme="currentStatusTheme" variant="light" size="small">{{ currentStatusLabel }}</t-tag>
        </div>
      </template>
      <div class="current-name">{{ current.name }}</div>
      <div class="current-date"><t-icon name="time" /> 开始日期：{{ current.startDate }}</div>
      <div v-if="current.notice" class="current-notice">
        <t-icon name="info-circle" />
        <span>{{ current.notice }}</span>
      </div>
    </t-card>

    <!-- 学期列表 -->
    <t-skeleton :loading="loading" animation="gradient" theme="article">
      <t-card :bordered="false" title="学期列表">
        <t-table :data="list" :columns="columns" row-key="id" stripe hover>
          <template #name="{ row }">
            <div class="name-cell">
              <span>{{ row.name }}</span>
              <t-tag v-if="row.isCurrent" theme="primary" variant="light" size="small">当前</t-tag>
            </div>
          </template>
          <template #dateRange="{ row }">
            <span>{{ row.startDate }} ~ {{ row.endDate || '未设置' }}</span>
          </template>
          <template #weekCount="{ row }">
            <span>{{ row.weekCount ? `${row.weekCount} 周` : '-' }}</span>
          </template>
          <template #semesterType="{ row }">
            <t-tag :theme="row.semesterType === 'EXTRA' ? 'warning' : 'primary'" variant="light" size="small">
              {{ row.semesterType === 'EXTRA' ? '假期培训' : '正常学期' }}
            </t-tag>
          </template>
          <template #classNames="{ row }">
            <template v-if="row.semesterType === 'EXTRA'">
              <t-tag v-if="row.classList?.length" variant="light" size="small" style="margin-right:4px" v-for="c in row.classList" :key="c">
                {{ c }}
              </t-tag>
              <span v-else class="muted">未指定班级</span>
            </template>
            <span v-else class="muted">所有班级</span>
          </template>
          <template #status="{ row }">
            <t-tag :theme="row.isCurrent ? 'success' : 'default'" variant="light" size="small">
              {{ row.isCurrent ? '进行中' : '历史' }}
            </t-tag>
          </template>
          <template #operation="{ row }">
            <t-space>
              <t-popconfirm
                v-if="!row.isCurrent"
                content="确定将本学期设为当前学期吗？切换后全校师生看到的「当前学期」都会改变，原当前学期将变为历史学期。"
                :visible="switchVisible && switchTarget?.id === row.id"
                @visible-change="v => onSwitchPopVisible(v, row)"
                @confirm="handleSwitch"
              >
                <t-button theme="primary" variant="text" size="small">设为当前</t-button>
              </t-popconfirm>
              <t-button theme="default" variant="text" size="small" @click="openEdit(row)">编辑</t-button>
              <t-tooltip v-if="!row.isCurrent" content="删除本学期">
                <t-button theme="danger" variant="text" size="small" @click="openDelete(row)">删除</t-button>
              </t-tooltip>
            </t-space>
          </template>
        </t-table>
        <div v-if="!loading && list.length === 0" class="empty-tip">
          <t-icon name="calendar" size="48px" style="color:#E5E7EB;margin-bottom:12px" />
          <div>暂无学期数据</div>
        </div>
      </t-card>
    </t-skeleton>

    <!-- 新建 / 编辑学期 -->
    <t-dialog v-model:visible="formVisible" :header="editingId ? '编辑学期' : '新建学期'" width="560px" :footer="false" @close="resetForm">
      <t-form :label-width="90" :data="form" @submit="handleSubmit">
        <t-form-item label="学期名称">
          <t-input v-model="form.name" placeholder="例如：2024-2025学年第一学期" />
        </t-form-item>
        <t-form-item label="起止日期" help="开始与结束日期均必填，周数将根据起止日期自动计算">
          <div class="date-range">
            <t-date-picker v-model="form.startDate" placeholder="开始日期" clearable />
            <span class="range-sep">~</span>
            <t-date-picker v-model="form.endDate" placeholder="结束日期" clearable />
          </div>
        </t-form-item>
        <t-form-item label="总周数">
          <t-input v-model.number="form.weekCount" disabled>
            <template #suffix>周</template>
          </t-input>
          <div class="week-hint">
            根据起止日期自动计算：{{ computedWeekCount ? `${computedWeekCount} 周` : '请选择开始和结束日期' }}
          </div>
        </t-form-item>
        <t-form-item label="学期类型">
          <t-radio-group v-model="form.semesterType">
            <t-radio value="NORMAL">正常学期（所有班级通用）</t-radio>
            <t-radio value="EXTRA">假期培训（指定班级）</t-radio>
          </t-radio-group>
        </t-form-item>
        <t-form-item v-if="form.semesterType === 'EXTRA'" label="关联班级">
          <t-select v-model="form.classIds" multiple placeholder="请选择班级" :options="classOptions" style="width:100%" />
        </t-form-item>
        <div class="dialog-actions">
          <t-space>
            <t-button theme="default" @click="formVisible = false">取消</t-button>
            <t-button theme="primary" type="submit" :loading="submitting">{{ editingId ? '保存' : '创建' }}</t-button>
          </t-space>
        </div>
      </t-form>
    </t-dialog>

    <!-- 删除学期 -->
    <t-dialog v-model:visible="deleteVisible" :header="'删除学期'" width="480px" :footer="false">
      <p>确定要删除学期「{{ deleteTarget?.name }}」吗？该操作不可恢复，相关数据将被永久移除。</p>
      <div class="dialog-actions">
        <t-space>
          <t-button theme="default" @click="deleteVisible = false">取消</t-button>
          <t-button theme="danger" :loading="deleting" @click="handleDelete">确认删除</t-button>
        </t-space>
      </div>
    </t-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { MessagePlugin } from 'tdesign-vue-next'
import {
  getCurrentSemester,
  getSemesterList,
  createSemester,
  updateSemester,
  switchSemester,
  deleteSemester,
} from '@/api/semester'
import { getClasses } from '@/api/staff'

const current = ref(null)
const list = ref([])
const classes = ref([])
const loading = ref(false)

const emptyForm = {
  name: '',
  startDate: '',
  endDate: '',
  weekCount: 20,
  semesterType: 'NORMAL',
  classIds: [],
}
const form = ref({ ...emptyForm })
const editingId = ref(null)
const formVisible = ref(false)
const submitting = ref(false)

const switchVisible = ref(false)
const switchTarget = ref(null)
const switching = ref(false)

function onSwitchPopVisible(visible, row) {
  if (visible) {
    switchTarget.value = row
    switchVisible.value = true
  } else {
    switchVisible.value = false
    switchTarget.value = null
  }
}

const deleteVisible = ref(false)
const deleteTarget = ref(null)
const deleting = ref(false)

const columns = [
  { colKey: 'name', title: '学期名称', minWidth: 200 },
  { colKey: 'dateRange', title: '起止日期', minWidth: 200 },
  { colKey: 'weekCount', title: '周数', width: 90 },
  { colKey: 'semesterType', title: '学期类型', width: 110 },
  { colKey: 'classNames', title: '分配班级', minWidth: 160 },
  { colKey: 'status', title: '状态', width: 90 },
  { colKey: 'operation', title: '操作', width: 170, align: 'center' },
]

const currentStatusMeta = {
  before: { label: '未开始', theme: 'warning' },
  ongoing: { label: '进行中', theme: 'success' },
  ended: { label: '已结束', theme: 'default' },
}
const currentStatusLabel = computed(() => (current.value && currentStatusMeta[current.value.status]?.label) || '')
const currentStatusTheme = computed(() => (current.value && currentStatusMeta[current.value.status]?.theme) || 'default')

const classOptions = computed(() => classes.value.map(c => ({ label: c.className, value: c.id })))

const computedWeekCount = computed(() => {
  const { startDate, endDate } = form.value
  if (startDate && endDate) {
    return Math.max(1, Math.floor((new Date(endDate).getTime() - new Date(startDate).getTime()) / (1000 * 60 * 60 * 24 * 7)) + 1)
  }
  return 0
})

async function loadAll() {
  loading.value = true
  try {
    const [cur, lst, cls] = await Promise.all([
      getCurrentSemester(),
      getSemesterList(),
      getClasses().catch(() => []),
    ])
    current.value = cur
    list.value = lst || []
    classes.value = cls || []
  } catch (e) {
    MessagePlugin.error('学期数据加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => loadAll())

function resetForm() {
  form.value = { ...emptyForm }
  editingId.value = null
}

function openCreate() {
  resetForm()
  formVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id || null
  form.value = {
    name: row.name,
    startDate: row.startDate,
    endDate: row.endDate || '',
    weekCount: row.weekCount || 20,
    semesterType: row.semesterType || 'NORMAL',
    classIds: row.classIds || [],
  }
  formVisible.value = true
}

async function handleSubmit() {
  if (!form.value.name.trim() || !form.value.startDate) {
    MessagePlugin.warning('请填写学期名称和开始日期')
    return
  }
  submitting.value = true
  try {
    const payload = {
      name: form.value.name.trim(),
      startDate: form.value.startDate,
      endDate: form.value.endDate || '',
      weekCount: computedWeekCount.value || form.value.weekCount,
      semesterType: form.value.semesterType,
      classIds:
        form.value.semesterType === 'EXTRA' && form.value.classIds.length > 0
          ? form.value.classIds
          : [],
    }
    if (editingId.value) {
      await updateSemester(editingId.value, payload)
    } else {
      await createSemester(payload)
    }
    MessagePlugin.success(editingId.value ? '学期更新成功' : '学期创建成功')
    formVisible.value = false
    resetForm()
    await loadAll()
  } catch (e) {
    MessagePlugin.error('保存失败，请检查填写内容')
  } finally {
    submitting.value = false
  }
}

async function handleSwitch() {
  if (!switchTarget.value?.id) return
  switching.value = true
  try {
    await switchSemester(switchTarget.value.id)
    MessagePlugin.success('当前学期切换成功')
    switchVisible.value = false
    switchTarget.value = null
    await loadAll()
  } catch (e) {
    MessagePlugin.error('切换失败')
  } finally {
    switching.value = false
  }
}

function openDelete(row) {
  deleteTarget.value = row
  deleteVisible.value = true
}

async function handleDelete() {
  if (!deleteTarget.value?.id) return
  deleting.value = true
  try {
    await deleteSemester(deleteTarget.value.id)
    MessagePlugin.success('学期删除成功')
    deleteVisible.value = false
    deleteTarget.value = null
    await loadAll()
  } catch (e) {
    MessagePlugin.error('删除失败')
  } finally {
    deleting.value = false
  }
}
</script>

<style scoped>
.semester-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  margin-bottom: 20px;
}

.current-card {
  margin-bottom: 20px;
}

.current-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.current-label {
  color: #86868B;
  font-size: 13px;
}

.current-name {
  font-size: 18px;
  font-weight: 700;
  color: #1D1D1F;
  margin-bottom: 8px;
}

.current-date {
  font-size: 13px;
  color: #86868B;
  display: flex;
  align-items: center;
  gap: 6px;
}

.current-notice {
  margin-top: 12px;
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 10px 12px;
  background: rgba(0, 113, 227, 0.05);
  border-radius: 8px;
  color: #0071E3;
  font-size: 13px;
}

.name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
  color: #1D1D1F;
}

.muted {
  color: #AEAEB2;
  font-size: 13px;
}

.date-range {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.range-sep {
  color: #C7C7CC;
}

.week-hint {
  width: 100%;
  margin-top: 6px;
  font-size: 12px;
  color: #AEAEB2;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 24px;
}

.desc-text {
  margin-top: 8px;
  color: #86868B;
  font-size: 13px;
  line-height: 1.6;
}

.empty-tip {
  text-align: center;
  padding: 48px 0;
  color: #AEAEB2;
  font-size: 14px;
}

@media (max-width: 768px) {
  .semester-header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>