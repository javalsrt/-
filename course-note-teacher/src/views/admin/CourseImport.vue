<template>
  <div class="page-container">
    <div class="page-header import-header">
      <div>
        <h2>课程导入</h2>
        <p>通过文件批量导入课程和排课信息，AI 自动识别解析</p>
      </div>
    </div>

    <t-tabs v-model="activeTab">
      <t-tab-panel value="upload" label="文件导入">
        <!-- 错误提示 -->
        <t-alert
          v-if="error"
          theme="danger"
          :message="error"
          title="操作失败"
          close
          @close="error = null"
          style="margin-bottom: 20px"
        />

        <!-- 导入结果 -->
        <t-card v-if="confirmResult" :bordered="false" class="result-card">
          <template #title>
            <div class="result-head">
              <t-icon name="check-circle" size="22px" style="color:#00A870" />
              <span class="result-title">导入完成</span>
              <t-button variant="text" theme="default" @click="resetState">
                <template #icon><t-icon name="refresh" /></template>
                继续导入
              </t-button>
            </div>
          </template>
          <div class="result-badges">
            <t-tag theme="success" variant="light">成功导入 {{ confirmResult.imported }} 条</t-tag>
            <t-tag v-if="(confirmResult.autoFilled ?? 0) > 0" theme="primary" variant="light">自动补充 {{ confirmResult.autoFilled }} 条</t-tag>
            <t-tag v-if="confirmResult.skipped > 0" theme="warning" variant="light">跳过 {{ confirmResult.skipped }} 条</t-tag>
          </div>
          <ul v-if="confirmResult.messages && confirmResult.messages.length" class="result-messages">
            <li v-for="(msg, idx) in confirmResult.messages" :key="idx">{{ msg }}</li>
          </ul>
        </t-card>

        <!-- 上传文件阶段 -->
        <t-card v-if="!file" :bordered="false">
          <div
            class="dropzone"
            :class="{ dragging: isDragging }"
            @dragover.prevent="onDragOver"
            @dragleave.prevent="isDragging = false"
            @drop.prevent="onDrop"
            @click="fileInputRef.click()"
          >
            <input
              ref="fileInputRef"
              type="file"
              :accept="ACCEPTED_FORMATS"
              style="display:none"
              @change="onInputChange"
            />
            <div class="dropzone-icon"><t-icon name="upload" size="40px" /></div>
            <div class="dropzone-title">拖拽文件到此处，或点击上传</div>
            <div class="dropzone-desc">支持 .xlsx、.docx、.pdf、.jpg、.png 格式，单文件不超过 10MB</div>
            <t-button theme="primary" size="medium">选择文件</t-button>
          </div>

          <div class="import-tips">
            <div class="tips-title"><t-icon name="data-array" /> 导入说明</div>
            <ul class="tips-list">
              <li>文件需包含：课程名称、授课教师、班级、星期、节次、教室、周次等字段</li>
              <li>AI 将自动识别文件内容并匹配系统中的教师和班级</li>
              <li>导入前可预览数据，确认无误后再提交</li>
              <li>导入后会按课程总课时自动补充剩余课时，确保「要求上多少课时就排多少课时」</li>
            </ul>
            <div class="template-links">
              <t-button theme="default" variant="outline" size="small" @click="downloadTemplate('schedule-import-template.xlsx')">
                <template #icon><t-icon name="download" /></template>
                下载空白模板
              </t-button>
              <t-button theme="primary" variant="outline" size="small" @click="downloadTemplate('summer-training-schedule.xlsx')">
                <template #icon><t-icon name="file-excel" /></template>
                下载暑假培训样例
              </t-button>
            </div>
          </div>
        </t-card>

        <!-- 已选文件 + 预览阶段 -->
        <template v-else>
          <t-card :bordered="false" class="file-card">
            <div class="file-row">
              <div class="file-ico">
                <t-icon :name="fileIcon" size="28px" :style="{ color: fileIconColor }" />
              </div>
              <div class="file-meta">
                <div class="file-name-line">
                  <span class="file-name" :title="file.name">{{ file.name }}</span>
                  <t-tag
                    :theme="uploading ? 'warning' : preview ? 'success' : 'danger'"
                    variant="light"
                    size="small"
                  >
                    {{ uploading ? 'AI 解析中...' : preview ? '解析完成' : '解析失败' }}
                  </t-tag>
                </div>
                <div class="file-desc">
                  {{ uploading
                    ? '正在上传并由 AI 智能识别文件内容...'
                    : preview
                    ? `共识别 ${preview.total} 条课程数据，${preview.success} 条成功，${errorCount} 条异常`
                    : '请重新选择文件' }}
                </div>
              </div>
              <t-button variant="text" theme="default" color="danger" :disabled="uploading || confirming" @click="resetState">
                <template #icon><t-icon name="refresh" /></template>
              </t-button>
            </div>
            <div v-if="uploading" class="progress-bar"><div class="progress-inner"></div></div>
          </t-card>

          <!-- 预览表格 -->
          <t-card v-if="showPreview" :bordered="false" class="preview-card">
            <template #title>
              <div class="preview-head">
                <div>
                  <div class="preview-title">导入预览</div>
                  <div class="preview-sub">请核对导入数据，异常数据请修正后再确认导入</div>
                </div>
                <div class="preview-stats">
                  <t-tag variant="outline">总计 {{ preview.total }} 条</t-tag>
                  <t-tag theme="success" variant="light">成功 {{ preview.success }} 条</t-tag>
                  <t-tag v-if="errorCount > 0" theme="danger" variant="light">异常 {{ errorCount }} 条</t-tag>
                </div>
              </div>
            </template>

            <t-table
              row-key="rowIndex"
              :data="preview.preview"
              :columns="previewColumns"
              :selected-row-keys="selectedRowKeys"
              :select-on-row-click="false"
              :hover="true"
              :stripe="true"
              size="medium"
              @select-change="onSelectChange"
            >
              <template #teacher="{ row, rowIndex }">
                <template v-if="row.teacherMatchStatus === 'fuzzy' && (row.teacherSuggestions || []).length">
                  <div class="teacher-fuzzy">
                    <span class="txt-warning">{{ row.teacherName || '-' }}</span>
                    <t-select
                      size="small"
                      :value="row.teacherId"
                      style="width:180px;margin-left:8px"
                      :options="suggestionOptions(row)"
                      @change="v => selectTeacher(rowIndex, v)"
                    />
                  </div>
                </template>
                <template v-else-if="row.teacherMatchStatus === 'matched'">
                  <span class="ok-inline">
                    <t-icon name="check-circle" style="color:#00A870" />
                    {{ row.matchedTeacherName || row.teacherName || '-' }}
                  </span>
                </template>
                <template v-else>{{ row.teacherName || '-' }}</template>
              </template>
              <template #weekday="{ row }">
                <div>周{{ row.dayOfWeek }}</div>
                <div v-if="row.startTime && row.endTime" class="time-cell">{{ row.startTime }} - {{ row.endTime }}</div>
              </template>
              <template #classroom="{ row }">{{ row.classroom || '-' }}</template>
              <template #weeks="{ row }">{{ row.weeks || '-' }}</template>
              <template #credit="{ row }">{{ row.credit ?? '-' }}</template>
              <template #status="{ row }">
                <span v-if="row.teacherMatchStatus === 'fuzzy'" class="txt-warning">
                  <t-icon name="error-circle" /> 教师待确认
                </span>
                <span v-else class="txt-success">
                  <t-icon name="check-circle" /> 正常
                </span>
              </template>
            </t-table>

            <!-- 解析错误列表 -->
            <t-alert
              v-if="preview.errors && preview.errors.length"
              theme="warning"
              class="parse-errors"
              :close="false"
            >
              <template #title>解析错误信息（{{ preview.errors.length }}）</template>
              <ul class="parse-error-list">
                <li v-for="(err, idx) in preview.errors" :key="idx">
                  第 {{ err.row }} 行{{ err.courseName ? `「${err.courseName}」` : '' }}：{{ (err.errors || []).join('；') }}
                </li>
              </ul>
            </t-alert>

            <div class="preview-footer">
              <div class="footer-tip">将导入 <b>{{ selectedRowKeys.length }}</b> 条课程数据</div>
              <t-space>
                <t-button theme="default" variant="outline" :disabled="confirming" @click="resetState">取消</t-button>
                <t-button
                  theme="primary"
                  :disabled="confirming || selectedRowKeys.length === 0"
                  :loading="confirming"
                  @click="confirmDialogVisible = true"
                >确认导入</t-button>
              </t-space>
            </div>
          </t-card>
        </template>
      </t-tab-panel>

      <t-tab-panel value="history" label="导入记录">
        <t-card :bordered="false">
          <template #title>
            <div class="history-head">
              <div>
                <div class="preview-title">导入记录</div>
                <div class="preview-sub">查看历史课程导入操作及结果详情</div>
              </div>
              <t-button theme="default" variant="outline" size="small" :loading="recordsLoading" @click="fetchRecords">
                <template #icon><t-icon name="refresh" /></template>
                刷新
              </t-button>
            </div>
          </template>

          <t-alert
            v-if="recordsError"
            theme="danger"
            :message="recordsError"
            title="加载失败"
            close
            @close="recordsError = null"
            style="margin-bottom:16px"
          />
          <div>
            <t-button v-if="recordsError" variant="text" @click="fetchRecords">重试</t-button>
          </div>

          <t-skeleton v-if="recordsLoading && records.length === 0" animation="gradient" theme="article" />
          <div v-else-if="records.length === 0" class="empty-tip">
            <t-icon name="file" size="48px" style="color:#E5E7EB;margin-bottom:12px" />
            <div>暂无导入记录</div>
            <div class="empty-sub">在「文件导入」标签页上传并确认导入后将显示在此</div>
          </div>
          <t-table
            v-else
            row-key="id"
            :data="records"
            :columns="recordColumns"
            :expanded-row-keys="expandedRecords"
            :hover="true"
            :stripe="true"
          >
            <template #fileName="{ row }">
              <span class="record-file">{{ row.fileName || '未命名文件' }}</span>
            </template>
            <template #createdAt="{ row }">
              <span class="nowrap">{{ formatTime(row.createdAt) }}</span>
            </template>
            <template #successCount="{ row }">
              <span style="color:#00A870;font-weight:500">{{ row.successCount ?? '-' }}</span>
            </template>
            <template #skipCount="{ row }">
              <span style="color:#ED7B2F">{{ row.skipCount ?? 0 }}</span>
            </template>
            <template #operator="{ row }">
              {{ row.importedByName || `用户 ${row.importedBy}` || '-' }}
            </template>
            <template #detail="{ row }">
              <t-button variant="text" theme="primary" size="small" @click="toggleRecord(row.id)">
                {{ expandedRecords.includes(row.id) ? '收起' : '展开' }}
              </t-button>
            </template>
            <template #expandedRow="{ row }">
              <div class="expanded-head">导入结果详情</div>
              <ul v-if="parseMessages(row).length" class="expanded-list">
                <li v-for="(msg, idx) in parseMessages(row)" :key="idx">{{ msg }}</li>
              </ul>
              <div v-else class="muted">无详细消息</div>
            </template>
          </t-table>
        </t-card>
      </t-tab-panel>
    </t-tabs>

    <!-- 确认导入对话框 -->
    <t-dialog
      v-model:visible="confirmDialogVisible"
      header="确认导入"
      width="480px"
      :footer="false"
    >
      <p>确定要导入这 {{ selectedRowKeys.length }} 条课程数据吗？
        <template v-if="errorCount > 0">其中 {{ errorCount }} 条异常数据将被跳过。</template>
      </p>
      <p style="color:#0071E3;margin-top:8px">导入后将自动按课程总课时补充剩余课时，确保排满。</p>
      <div class="dialog-actions">
        <t-space>
          <t-button theme="default" :disabled="confirming" @click="confirmDialogVisible = false">取消</t-button>
          <t-button theme="primary" :loading="confirming" @click="handleConfirm">确认导入</t-button>
        </t-space>
      </div>
    </t-dialog>

    <!-- 阻塞式全屏遮罩：上传 / AI 识别阶段 -->
    <div v-if="uploading" class="fullscreen-loading">
      <t-loading size="large" text="正在上传并由 AI 智能识别文件内容，请稍候..."></t-loading>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { MessagePlugin } from 'tdesign-vue-next'
import { previewImport, confirmImport, getImportRecords } from '@/api/course-import'

const ACCEPTED_FORMATS = '.xlsx,.docx,.pdf,.jpg,.png'
const ALLOWED_EXTS = ['xlsx', 'docx', 'pdf', 'jpg', 'jpeg', 'png']
const MAX_FILE_SIZE = 10 * 1024 * 1024 // 10MB

const activeTab = ref('upload')

// ---- 文件上传 / AI 提取 ----
const fileInputRef = ref(null)
const isDragging = ref(false)
const file = ref(null)
const uploading = ref(false)
const preview = ref(null)
const error = ref(null)

// ---- 确认导入 ----
const confirming = ref(false)
const confirmResult = ref(null)
const confirmDialogVisible = ref(false)

// ---- 导入记录 ----
const records = ref([])
const recordsLoading = ref(false)
const recordsError = ref(null)
const expandedRecords = ref([])
let rowSeq = 0

function resetState() {
  file.value = null
  preview.value = null
  confirmResult.value = null
  error.value = null
  uploading.value = false
  confirming.value = false
  confirmDialogVisible.value = false
}

const errorCount = computed(() => (preview.value ? preview.value.total - preview.value.success : 0))
const showPreview = computed(() => preview.value && !confirmResult.value)

// 供表格 row-key 使用的稳定索引
function ensureRowIndex(item) {
  if (item.rowIndex === undefined) item.rowIndex = rowSeq++
  return item.rowIndex
}

// ---- 教师建议 ----
function suggestionOptions(item) {
  return (item.teacherSuggestions || []).map(s => ({
    label: `${s.teacherName}${s.distance === 1 ? '（疑似错别字）' : `（差异 ${s.distance}）`}`,
    value: s.teacherId,
  }))
}

function selectTeacher(index, teacherId) {
  const item = preview.value.preview[index]
  const sug = (item.teacherSuggestions || []).find(s => s.teacherId === teacherId)
  item.teacherId = teacherId
  item.matchedTeacherName = sug ? sug.teacherName : item.matchedTeacherName
  item.teacherMatchStatus = 'matched'
}

// ---- 预览表格勾选 ----
const selectedRowKeys = ref([])

const previewColumns = [
  { colKey: 'row-select', type: 'multiple', width: 46 },
  { colKey: 'courseName', title: '课程名称', minWidth: 160 },
  { colKey: 'teacher', title: '授课教师', minWidth: 220 },
  { colKey: 'className', title: '班级', minWidth: 120 },
  { colKey: 'weekday', title: '星期/时间', minWidth: 120 },
  { colKey: 'classroom', title: '教室', minWidth: 100 },
  { colKey: 'weeks', title: '周次', minWidth: 90 },
  { colKey: 'credit', title: '学分', minWidth: 80 },
  { colKey: 'status', title: '状态', minWidth: 110 },
]

function onSelectChange(keys) {
  selectedRowKeys.value = keys
}

const recordColumns = [
  { colKey: 'fileName', title: '文件名', minWidth: 200 },
  { colKey: 'createdAt', title: '导入时间', minWidth: 170 },
  { colKey: 'operator', title: '导入人', minWidth: 110 },
  { colKey: 'semester', title: '学期', minWidth: 120 },
  { colKey: 'totalCount', title: '总数', minWidth: 70, align: 'right' },
  { colKey: 'successCount', title: '成功', minWidth: 70, align: 'right' },
  { colKey: 'skipCount', title: '跳过', minWidth: 70, align: 'right' },
  { colKey: 'detail', title: '详情', minWidth: 80, align: 'center' },
  { colKey: 'expandedRow', type: 'multiple', minWidth: 10 },
]

function toggleRecord(id) {
  const idx = expandedRecords.value.indexOf(id)
  if (idx >= 0) expandedRecords.value.splice(idx, 1)
  else expandedRecords.value.push(id)
}

function parseMessages(record) {
  if (!record.messages) return []
  try {
    const parsed = JSON.parse(record.messages)
    return Array.isArray(parsed) ? parsed : [record.messages]
  } catch (e) {
    return [record.messages]
  }
}

function formatTime(t) {
  if (!t) return '-'
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return t
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

// ---- 文件处理 ----
function onDragOver(e) {
  isDragging.value = true
}
function onDrop(e) {
  isDragging.value = false
  const f = e.dataTransfer.files ? e.dataTransfer.files[0] : null
  handleFile(f)
}
function onInputChange(e) {
  const f = e.target.files ? e.target.files[0] : null
  handleFile(f)
  e.target.value = ''
}

async function handleFile(selectedFile) {
  if (!selectedFile) return
  const ext = selectedFile.name.split('.').pop()?.toLowerCase()
  if (!ext || !ALLOWED_EXTS.includes(ext)) {
    error.value = `不支持的文件格式：.${ext || '未知'}，仅支持 .xlsx、.docx、.pdf、.jpg、.png`
    MessagePlugin.error(error.value)
    return
  }
  if (selectedFile.size > MAX_FILE_SIZE) {
    error.value = '文件大小超过 10MB 限制'
    MessagePlugin.error(error.value)
    return
  }

  error.value = null
  confirmResult.value = null
  preview.value = null
  file.value = selectedFile
  uploading.value = true

  try {
    const result = await previewImport(selectedFile)
    // 后端返回裸数据体，若有 { data } 包裹则解包
    const data = result && typeof result === 'object' && 'data' in result && !('preview' in result)
      ? result.data
      : result
    preview.value = data
    ;(data.preview || []).forEach(ensureRowIndex)
    selectedRowKeys.value = (data.preview || []).filter(i => !i.errorMsg).map(i => i.rowIndex)
    MessagePlugin.success(`识别完成，共 ${data.total} 条课程数据`)
  } catch (err) {
    const msg = err?.message || '文件解析失败，请稍后重试'
    error.value = msg
  } finally {
    uploading.value = false
  }
}

// ---- 确认导入 ----
async function handleConfirm() {
  if (!preview.value) return
  confirming.value = true
  error.value = null
  try {
    const items = preview.value.preview.filter(i => selectedRowKeys.value.includes(i.rowIndex))
    const result = await confirmImport(items, file.value?.name)
    const data = result && typeof result === 'object' && 'data' in result && !('imported' in result)
      ? result.data
      : result
    confirmResult.value = data
    confirmDialogVisible.value = false
    MessagePlugin.success(`导入完成，成功 ${data.imported} 条`)
    fetchRecords()
  } catch (err) {
    const msg = err?.message || '导入失败，请稍后重试'
    error.value = msg
    MessagePlugin.error(msg)
  } finally {
    confirming.value = false
  }
}

// ---- 导入记录 ----
async function fetchRecords() {
  recordsLoading.value = true
  recordsError.value = null
  try {
    const data = await getImportRecords()
    records.value = data || []
  } catch (err) {
    recordsError.value = err?.message || '导入记录加载失败'
  } finally {
    recordsLoading.value = false
  }
}

// ---- 文件图标 / 模板下载 ----
const fileIcon = computed(() => {
  const ext = (file.value?.name || '').split('.').pop()?.toLowerCase()
  if (ext === 'xlsx' || ext === 'xls') return 'file-excel'
  if (ext === 'docx' || ext === 'doc' || ext === 'pdf') return 'file-paste'
  if (ext === 'jpg' || ext === 'jpeg' || ext === 'png') return 'image'
  return 'file'
})
const fileIconColor = computed(() => {
  const ext = (file.value?.name || '').split('.').pop()?.toLowerCase()
  if (ext === 'xlsx' || ext === 'xls') return '#00B42A'
  if (ext === 'docx' || ext === 'doc') return '#3370FF'
  if (ext === 'pdf') return '#F43146'
  if (ext === 'jpg' || ext === 'jpeg' || ext === 'png') return '#722ED1'
  return '#86909C'
})

function downloadTemplate(name) {
  window.location.href = `/template/${name}`
}

onMounted(() => {
  fetchRecords()
})
</script>

<style scoped>
.import-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  margin-bottom: 20px;
}

/* 白色卡片风格统一 */
:deep(.t-card) {
  background: #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  border-radius: 10px;
}

/* 拖拽上传区 */
.dropzone {
  border: 2px dashed #D0D5E0;
  border-radius: 16px;
  padding: 48px 24px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
}
.dropzone:hover,
.dropzone.dragging {
  border-color: #0071E3;
  background: rgba(0, 113, 227, 0.04);
}
.dropzone-icon {
  width: 80px;
  height: 80px;
  margin: 0 auto 20px;
  border-radius: 12px;
  background: rgba(0, 113, 227, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #0071E3;
}
.dropzone-title {
  font-size: 16px;
  font-weight: 600;
  color: #1D1D1F;
  margin-bottom: 8px;
}
.dropzone-desc {
  font-size: 13px;
  color: #86868B;
  margin-bottom: 24px;
}

/* 导入说明 */
.import-tips {
  margin-top: 28px;
  padding: 20px 24px;
  background: #F7F8FA;
  border-radius: 12px;
}
.tips-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 14px;
  color: #1D1D1F;
  margin-bottom: 12px;
}
.tips-list {
  list-style: none;
  margin: 0;
  padding: 0;
  font-size: 13px;
  color: #4E5969;
}
.tips-list li {
  position: relative;
  padding-left: 14px;
  margin-bottom: 8px;
}
.tips-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 7px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #C9CDD4;
}
.template-links {
  margin-top: 16px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

/* 文件卡片 */
.file-card {
  margin-bottom: 16px;
}
.file-row {
  display: flex;
  align-items: center;
  gap: 16px;
}
.file-ico {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: rgba(0, 180, 42, 0.06);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.file-meta {
  flex: 1;
  min-width: 0;
}
.file-name-line {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
}
.file-name {
  font-weight: 600;
  color: #1D1D1F;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-desc {
  font-size: 13px;
  color: #86909C;
}
.progress-bar {
  margin-top: 16px;
  height: 6px;
  background: #F0F1F4;
  border-radius: 8px;
  overflow: hidden;
}
.progress-inner {
  height: 100%;
  width: 60%;
  background: #0071E3;
  border-radius: 8px;
  animation: pulse 1.2s ease-in-out infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 0.4; }
  50% { opacity: 1; }
}

/* 结果卡片 */
.result-card {
  margin-bottom: 16px;
}
.result-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.result-title {
  font-size: 16px;
  font-weight: 700;
  color: #1D1D1F;
}
.result-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}
.result-messages {
  list-style: none;
  margin: 12px 0 0;
  padding: 0;
}
.result-messages li {
  position: relative;
  padding-left: 14px;
  font-size: 13px;
  color: #4E5969;
  margin-bottom: 6px;
}
.result-messages li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 7px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #C9CDD4;
}

/* 预览 */
.preview-card .preview-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}
.preview-title {
  font-size: 16px;
  font-weight: 600;
  color: #1D1D1F;
}
.preview-sub {
  margin-top: 4px;
  font-size: 13px;
  color: #86909C;
}
.preview-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.teacher-fuzzy {
  display: flex;
  align-items: center;
}
.ok-inline {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #00B42A;
}
.txt-warning { color: #ED7B2F; }
.txt-success { color: #00B42A; }
.time-cell { font-size: 12px; color: #86909C; }
.parse-errors { margin: 16px 0 0; }
.parse-error-list {
  list-style: none;
  margin: 8px 0 0;
  padding: 0;
  font-size: 13px;
  color: #4E5969;
}
.parse-error-list li { margin-bottom: 4px; }
.preview-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 20px 24px;
  border-top: 1px solid #F0F1F4;
  margin: 16px -24px -24px;
}
.footer-tip { font-size: 13px; color: #86909C; }
.footer-tip b { color: #1D1D1F; }

/* 记录 */
.history-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}
.record-file {
  font-weight: 500;
  color: #1D1D1F;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: inline-block;
  max-width: 220px;
  vertical-align: bottom;
}
.nowrap { white-space: nowrap; }
.expanded-head {
  font-weight: 600;
  color: #1D1D1F;
  margin-bottom: 10px;
  font-size: 14px;
}
.expanded-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 260px;
  overflow-y: auto;
}
.expanded-list li {
  position: relative;
  padding-left: 14px;
  font-size: 13px;
  color: #4E5969;
  margin-bottom: 6px;
}
.expanded-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 7px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #C9CDD4;
}
.muted { color: #AEAEB2; font-size: 13px; }
.empty-tip {
  text-align: center;
  padding: 48px 0;
  color: #AEAEB2;
  font-size: 14px;
}
.empty-sub { font-size: 13px; margin-top: 6px; }
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 24px;
}

/* 全屏遮罩 loading */
.fullscreen-loading {
  position: fixed;
  inset: 0;
  z-index: 3000;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(2px);
  display: flex;
  align-items: center;
  justify-content: center;
}

@media (max-width: 768px) {
  .import-header,
  .preview-head,
  .history-head,
  .preview-footer {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>