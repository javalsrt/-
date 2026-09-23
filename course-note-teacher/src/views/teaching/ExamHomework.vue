<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <div class="page-header exam-header">
      <div>
        <h2>考试作业</h2>
        <p>发布和管理考试、作业，支持 AI 出题与文档识别</p>
      </div>
      <t-button theme="primary" @click="openPublish">
        <template #icon><t-icon name="add" /></template>
        发布考试/作业
      </t-button>
    </div>

    <!-- 筛选工具栏 -->
    <div class="table-toolbar">
      <t-space break-line>
        <t-select v-model="filterClassId" placeholder="全部班级" style="width:140px" clearable>
          <t-option v-for="c in classes" :key="c.id" :value="c.id" :label="c.className" />
        </t-select>
        <t-select v-model="filterType" placeholder="全部类型" style="width:120px" clearable>
          <t-option value="exam" label="考试" /><t-option value="homework" label="作业" />
        </t-select>
        <t-select v-model="filterStatus" placeholder="全部状态" style="width:130px" clearable>
          <t-option value="published" label="进行中" />
          <t-option value="draft" label="草稿" />
          <t-option value="ended" label="已结束" />
        </t-select>
        <t-input v-model="searchKeyword" placeholder="搜索标题 / 班级" clearable style="width:220px" @enter="loadList">
          <template #prefix-icon><t-icon name="search" /></template>
        </t-input>
        <t-button theme="default" :loading="loading" @click="loadList">
          <template #icon><t-icon name="refresh" /></template>
          刷新
        </t-button>
      </t-space>
    </div>

    <!-- 列表 -->
    <t-skeleton :loading="loading" animation="gradient" theme="article">
      <t-card :bordered="false">
        <t-table
          :data="filteredList"
          :columns="columns"
          row-key="id"
          :pagination="{ defaultPageSize: prefPageSize }"
          stripe
          hover
        >
          <template #type="{ row }">
            <t-tag variant="light" :theme="row.type === 'exam' ? 'warning' : 'primary'" size="small">
              {{ typeLabel(row.type) }}
            </t-tag>
          </template>
          <template #startTime="{ row }">
            <span class="time-range-text">{{ row.startTime }} ~ {{ row.endTime }}</span>
          </template>
          <template #status="{ row }">
            <t-tag variant="light" :theme="statusTheme(row.status)" size="small">{{ statusLabel(row) }}</t-tag>
          </template>
          <template #shelf="{ row }">
            <t-tag :theme="row.status === 1 ? 'success' : 'default'" variant="light" size="small">
              {{ row.status === 1 ? '已上架' : '未上架' }}
            </t-tag>
          </template>
          <template #operation="{ row }">
            <t-space>
              <t-link theme="primary" @click="openPublish">发布/编辑</t-link>
              <t-link theme="primary" @click="openSubmissions(row)">查看答题</t-link>
              <t-link theme="primary" @click="handleToggleStatus(row)">
                {{ row.status === 1 ? '下架' : '上架' }}
              </t-link>
              <t-link theme="danger" @click="handleDelete(row)">删除</t-link>
            </t-space>
          </template>
        </t-table>
        <div v-if="!loading && filteredList.length === 0" class="empty-tip">
          <t-icon name="file" size="48px" style="color:#E5E7EB;margin-bottom:12px" />
          <div>暂无符合条件的考试/作业</div>
        </div>
      </t-card>
    </t-skeleton>

    <!-- ===== 发布弹窗 ===== -->
    <t-dialog
      v-model:visible="publishOpen"
      :header="`发布${form.type === 'exam' ? '考试' : '作业'}`"
      width="720px"
      :footer="false"
      :close-on-overlay-click="!generating && !submitting"
      @close="onPublishClose"
    >
      <t-form :data="form" layout="vertical" class="publish-form">
        <!-- 步骤条 -->
        <div class="steps">
          <template v-for="(s, idx) in steps" :key="s">
            <div class="step-item">
              <div class="step-dot" :class="{ active: idx <= currentStep, done: idx < currentStep }">
                {{ idx < currentStep ? '' : idx + 1 }}
              </div>
              <span class="step-text" :class="{ active: idx <= currentStep }">{{ s }}</span>
            </div>
            <t-icon v-if="idx < steps.length - 1" name="chevron-right" class="step-arrow" />
          </template>
        </div>

        <div v-if="generationError" class="gen-error">
          <t-icon name="error-circle" />
          <span>{{ generationError }}</span>
        </div>

        <!-- 步骤 1：基础信息 -->
        <template v-if="currentStep === 0">
          <t-form-item label="发布类型">
            <div class="type-cards">
              <div
                v-for="t in typeOptions"
                :key="t.value"
                class="type-card"
                :class="{ active: form.type === t.value }"
                @click="form.type = t.value"
              >
                <div class="type-title">{{ t.label }}</div>
                <div class="type-desc">{{ t.desc }}</div>
              </div>
            </div>
          </t-form-item>

          <t-form-item label="选择班级">
            <t-select v-model="form.classId" placeholder="请选择班级" style="width:100%">
              <t-option v-for="c in classes" :key="c.id" :value="c.id" :label="c.className" />
            </t-select>
          </t-form-item>

          <t-form-item label="标题">
            <t-input v-model="form.title" placeholder="例如：Python 基础期中考试" />
          </t-form-item>

          <t-form-item label="描述">
            <t-textarea v-model="form.description" :rows="3" placeholder="请输入考试/作业说明" />
          </t-form-item>

          <div class="grid-2">
            <t-form-item label="开始时间">
              <input v-model="form.startTime" type="datetime-local" class="datetime-input" />
            </t-form-item>
            <t-form-item label="截止时间">
              <input v-model="form.endTime" type="datetime-local" class="datetime-input" />
            </t-form-item>
          </div>

          <div class="grid-3">
            <t-form-item label="限时（分钟）">
              <t-input-number v-model="form.timeLimit" :min="1" theme="column" style="width:100%" />
            </t-form-item>
            <t-form-item label="总分">
              <t-input-number v-model="form.totalScore" :min="1" theme="column" style="width:100%" />
            </t-form-item>
            <t-form-item label="及格分">
              <t-input-number v-model="form.passScore" :min="0" theme="column" style="width:100%" />
            </t-form-item>
          </div>

          <t-form-item label="发布方式">
            <t-radio-group v-model="form.publishMode">
              <t-radio value="immediate">立即发布</t-radio>
              <t-radio value="scheduled">定时发布</t-radio>
            </t-radio-group>
            <input
              v-if="form.publishMode === 'scheduled'"
              v-model="form.scheduledTime"
              type="datetime-local"
              class="datetime-input"
              style="margin-top:8px"
            />
          </t-form-item>
        </template>

        <!-- 步骤 2：出题方式 -->
        <template v-else-if="currentStep === 1">
          <t-form-item label="出题方式">
            <div class="mode-cards">
              <div
                v-for="m in modeOptions"
                :key="m.value"
                class="mode-card"
                :class="{ active: form.questionMode === m.value }"
                @click="form.questionMode = m.value"
              >
                <div class="mode-title">{{ m.label }}</div>
                <div class="mode-desc">{{ m.desc }}</div>
              </div>
            </div>
          </t-form-item>

          <template v-if="form.questionMode === 'ai-range'">
            <t-form-item label="选择课程">
              <t-select v-model="form.courseId" placeholder="请选择课程" style="width:100%">
                <t-option v-for="c in courses" :key="c.courseId" :value="c.courseId" :label="c.courseName" />
              </t-select>
            </t-form-item>
            <t-form-item label="章节范围">
              <t-radio-group v-model="form.chapterScope">
                <t-radio value="all">全部章节</t-radio>
                <t-radio value="custom">指定章节</t-radio>
              </t-radio-group>
              <div v-if="form.chapterScope === 'custom'" class="chapter-tip">
                暂无可选章节（需要后端提供章节列表接口）
              </div>
            </t-form-item>
          </template>

          <template v-else>
            <t-form-item label="上传文档">
              <div class="upload-area" @click="fileInputRef && fileInputRef.click()">
                <t-icon name="upload" size="32px" style="color:#BBBBBF;margin-bottom:8px" />
                <p>{{ form.documentName || '点击上传 Word / PDF / TXT 文档' }}</p>
                <p class="upload-sub">支持 .docx、.pdf、.txt</p>
              </div>
              <input
                ref="fileInputRef"
                type="file"
                accept=".docx,.pdf,.txt,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain"
                style="display:none"
                @change="handleFileChange"
              />
            </t-form-item>
          </template>

          <t-form-item label="题型设置">
            <div class="qtype-list">
              <label
                v-for="qt in questionTypeOptions"
                :key="qt.value"
                class="qtype-chip"
                :class="{ active: form.questionTypes.includes(qt.value) }"
              >
                <input
                  v-model="form.questionTypes"
                  type="checkbox"
                  :value="qt.value"
                  class="qtype-check"
                />
                {{ qt.label }}
              </label>
            </div>
          </t-form-item>

          <div class="grid-2">
            <t-form-item label="难度">
              <t-select v-model="form.difficulty" style="width:100%">
                <t-option v-for="d in difficultyOptions" :key="d.value" :value="d.value" :label="d.label" />
              </t-select>
            </t-form-item>
            <t-form-item label="题目数量">
              <t-input-number v-model="form.questionCount" :min="1" :max="100" theme="column" style="width:100%" />
            </t-form-item>
          </div>
        </template>

        <!-- 步骤 3：题目预览 -->
        <template v-else-if="currentStep === 2">
          <div class="preview-head">
            <h3 class="preview-title">题目预览（共 {{ previewQuestions.length }} 道）</h3>
            <t-button theme="default" size="small" @click="currentStep = 1">重新生成</t-button>
          </div>

          <div v-if="previewQuestions.length === 0" class="preview-empty">
            <t-icon name="error-circle" size="30px" style="color:#D8D8DC;margin-bottom:8px" />
            <div>暂无题目，请返回上一步生成</div>
          </div>
          <div v-else class="preview-list">
            <div v-for="(q, idx) in previewQuestions" :key="idx" class="question-card">
              <div class="question-head">
                <span class="q-index">{{ idx + 1 }}.</span>
                <div class="q-content">
                  <div v-if="editingIndex === idx">
                    <t-textarea v-model="q.content" :rows="2" placeholder="题干" />
                    <div v-if="q.options && q.options.length" class="q-options-editor">
                      <div v-for="(opt, oi) in q.options" :key="oi" class="q-option-line">
                        <span class="q-opt-letter">{{ letters[oi] }}.</span>
                        <t-input v-model="q.options[oi]" size="small" />
                      </div>
                    </div>
                    <div class="q-edit-grid">
                      <div>
                        <span class="q-edit-label">答案</span>
                        <t-input v-model="q.answer" size="small" />
                      </div>
                      <div>
                        <span class="q-edit-label">分值</span>
                        <t-input-number v-model="q.score" :min="0" size="small" theme="column" />
                      </div>
                      <div>
                        <span class="q-edit-label">难度</span>
                        <t-select v-model="q.difficulty" size="small">
                          <t-option v-for="d in difficultyOptions" :key="d.value" :value="d.value" :label="d.label" />
                        </t-select>
                      </div>
                    </div>
                  </div>
                  <div v-else class="q-body">
                    <p class="q-text">{{ q.content }}</p>
                    <div v-if="q.options && q.options.length" class="q-options">
                      <div v-for="(opt, oi) in q.options" :key="oi" class="q-option-line">
                        <span class="q-opt-letter">{{ letters[oi] }}.</span>
                        <span>{{ opt }}</span>
                      </div>
                    </div>
                    <div class="q-meta">
                      题型：{{ qtypeLabel(q.type) }} · 分值：{{ q.score }} 分
                      <t-link theme="primary" style="margin-left:8px" @click="editingIndex = editingIndex === idx ? -1 : idx">
                        {{ editingIndex === idx ? '收起' : '编辑' }}
                      </t-link>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- 步骤 4：发布确认 -->
        <template v-else>
          <h3 class="preview-title">发布信息确认</h3>
          <div class="confirm-list">
            <div class="confirm-row"><span>发布类型</span><b>{{ form.type === 'exam' ? '考试' : '作业' }}</b></div>
            <div class="confirm-row"><span>标题</span><b>{{ form.title }}</b></div>
            <div class="confirm-row"><span>班级</span><b>{{ classNameOf(form.classId) || '-' }}</b></div>
            <div class="confirm-row"><span>时间</span><b>{{ form.startTime }} ~ {{ form.endTime }}</b></div>
            <div class="confirm-row"><span>限时/总分/及格分</span><b>{{ form.timeLimit }}分钟 / {{ form.totalScore }}分 / {{ form.passScore }}分</b></div>
            <div class="confirm-row"><span>出题方式</span><b>{{ form.questionMode === 'ai-range' ? 'AI 按范围出题' : 'AI 识别文档出题' }}</b></div>
            <div class="confirm-row"><span>题目数量</span><b>{{ previewQuestions.length }} 道</b></div>
            <div class="confirm-row"><span>发布方式</span><b>{{ form.publishMode === 'immediate' ? '立即发布' : `定时发布（${form.scheduledTime}）` }}</b></div>
          </div>
        </template>
      </t-form>

      <!-- 底部按钮 -->
      <div class="publish-footer">
        <t-button theme="default" :disabled="submitting || generating" @click="publishOpen = false">取消</t-button>
        <div class="footer-actions">
          <t-button v-if="currentStep > 0" theme="default" :disabled="submitting || generating" @click="handlePrev">
            <template #icon><t-icon name="chevron-left" /></template>
            上一步
          </t-button>
          <t-button v-if="currentStep < steps.length - 1" theme="primary" :loading="generating" :disabled="submitting" @click="handleNext">
            {{ currentStep === 1 ? '生成题目' : '下一步' }}
            <template v-if="currentStep !== 1" #suffix><t-icon name="chevron-right" /></template>
          </t-button>
          <t-button v-else theme="primary" :loading="submitting" @click="handlePublish">确认发布</t-button>
        </div>
      </div>

      <!-- 生成题目遮罩 -->
      <div v-if="generating" class="gen-overlay">
        <t-icon name="loading" size="40px" class="gen-spin" />
        <p>AI 正在分批生成题目</p>
        <p class="gen-sub">请勿关闭窗口或点击其他区域</p>
      </div>
    </t-dialog>

    <!-- ===== 提交列表弹窗 ===== -->
    <t-dialog
      v-model:visible="submissionsOpen"
      width="960px"
      :footer="false"
      header="学生提交列表"
    >
      <div class="subs-header">
        <span v-if="submissionsExam" class="subs-title">
          {{ submissionsExam.title }} · {{ submissions.length }} 份提交
        </span>
      </div>

      <div v-if="submissionsLoading" class="subs-loading">
        <t-icon name="loading" class="gen-spin" /> 加载提交中...
      </div>
      <div v-else-if="submissions.length === 0" class="subs-empty">
        <t-icon name="file" size="40px" style="color:#E5E7EB;margin-bottom:8px" />
        <div>暂无学生提交</div>
      </div>
      <div v-else class="subs-list">
        <div v-for="sub in submissions" :key="sub.id" class="sub-card">
          <div class="sub-head" @click="sub.expanded = !sub.expanded">
            <div class="sub-student">
              <div class="sub-avatar">{{ (sub.studentName || '?').slice(0, 1) }}</div>
              <div>
                <div class="sub-name">
                  {{ sub.studentName || '未知学生' }}
                  <span class="sub-no">{{ sub.studentNo || '' }}</span>
                </div>
                <div class="sub-meta">
                  提交时间：{{ sub.submitTime || '-' }} ·
                  <t-tag :theme="sub.status === 'completed' ? 'success' : 'default'" variant="light" size="small">
                    {{ sub.status === 'completed' ? '已完成' : sub.status || '进行中' }}
                  </t-tag>
                </div>
              </div>
            </div>
            <div class="sub-score">
              <div class="sub-score-label">AI 评分 / 最终分</div>
              <div class="sub-score-val">
                <span class="sub-ai">{{ sub.autoScore ?? '-' }}</span>
                <span class="sub-slash">/</span>
                <span class="sub-final">{{ subTotalFinal(sub) }}</span>
              </div>
            </div>
            <t-icon :name="sub.expanded ? 'chevron-down' : 'chevron-right'" class="sub-chevron" />
          </div>

          <div v-if="sub.expanded" class="sub-answers">
            <div v-for="a in sub.answers || []" :key="a.id" class="answer-card">
              <div class="answer-head">
                <t-tag variant="outline" size="small" class="answer-type">{{ qtypeLabel(a.questionType) }}</t-tag>
                <div class="answer-question">
                  <div class="answer-content">{{ a.questionContent }}</div>
                  <div v-if="a.questionType && a.questionType.indexOf('choice') !== -1 && a.referenceAnswer" class="answer-ref">
                    参考答案：{{ a.referenceAnswer }}
                  </div>
                </div>
                <div class="answer-score">
                  <div class="ans-max">满分 {{ a.maxScore }}</div>
                  <div class="ans-val">
                    {{ a.finalScore ?? a.autoScore ?? 0 }} 分
                    <span v-if="a.questionType === 'short_answer' && a.adjustCount > 0" class="ans-adjust">
                      （已调整 {{ a.adjustCount }} 次）
                    </span>
                  </div>
                  <div v-if="a.autoScore != null && a.finalScore != null && a.finalScore !== a.autoScore" class="ans-ai-initial">
                    AI 初始：{{ a.autoScore }}
                  </div>
                </div>
              </div>

              <div class="answer-cols">
                <div class="answer-col">
                  <div class="col-label">学生答案</div>
                  <div class="col-body">{{ a.userAnswer || '（未作答）' }}</div>
                </div>
                <div class="answer-col">
                  <div class="col-head">
                    <span class="col-label">AI 评分{{ a.questionType === 'short_answer' ? ' / 教师评语' : '' }}</span>
                    <template v-if="a.questionType === 'short_answer'">
                      <t-button
                        v-if="adjustingAnswerId !== a.id"
                        size="small"
                        theme="default"
                        :disabled="a.adjustCount >= 2"
                        @click="openAdjustScore(a)"
                      >
                        调整分数
                      </t-button>
                      <t-space v-else>
                        <t-button size="small" theme="primary" @click="confirmAdjustScore(sub)">保存</t-button>
                        <t-button size="small" theme="default" @click="adjustingAnswerId = null">取消</t-button>
                      </t-space>
                    </template>
                  </div>
                  <div v-if="adjustingAnswerId === a.id && a.questionType === 'short_answer'" class="adjust-box">
                    <div class="adjust-row">
                      <span class="adjust-label">分数（0-{{ a.maxScore }}）</span>
                      <t-input-number v-model="adjustForm.score" :min="0" :max="a.maxScore" size="small" theme="column" style="width:110px" />
                    </div>
                    <div class="adjust-row">
                      <span class="adjust-label">教师评语</span>
                      <t-input v-model="adjustForm.comment" placeholder="可输入评语" size="small" style="flex:1" />
                    </div>
                    <div class="adjust-tip">提示：每题最多可调整 2 次（已调整 {{ a.adjustCount ?? 0 }} 次）</div>
                  </div>
                  <div v-else class="col-body col-comments">
                    <div v-if="a.aiComment" class="comment-line"><span class="c-label">AI 评语：</span>{{ a.aiComment }}</div>
                    <div v-if="a.teacherComment" class="comment-line"><span class="c-label">教师评语：</span>{{ a.teacherComment }}</div>
                    <span v-if="!a.aiComment && !a.teacherComment">-</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="subs-footer">
        <t-button theme="default" @click="submissionsOpen = false">关闭</t-button>
      </div>
    </t-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { MessagePlugin, DialogPlugin } from 'tdesign-vue-next'
import {
  getExamHomeworkList,
  getTeacherClasses,
  getTeacherCoursesForSelect,
  publishExamHomework,
  generateQuestionsByRange,
  generateQuestionsByDocument,
  deleteExamHomework,
  toggleExamHomeworkStatus,
  getExamSubmissions,
  adjustSubmissionAnswerScore
} from '@/api/exam-homework'

/* ===== 常量 ===== */
const questionTypeOptions = [
  { value: 'single_choice', label: '单选题' },
  { value: 'multiple_choice', label: '多选题' },
  { value: 'true_false', label: '判断题' },
  { value: 'fill_blank', label: '填空题' },
  { value: 'short_answer', label: '简答题' }
]
const difficultyOptions = [
  { value: 'easy', label: '简单' },
  { value: 'medium', label: '中等' },
  { value: 'hard', label: '困难' },
  { value: 'mixed', label: '混合' }
]
const typeOptions = [
  { value: 'exam', label: '考试', desc: '限时完成，计入成绩' },
  { value: 'homework', label: '作业', desc: '课后练习，巩固知识' }
]
const modeOptions = [
  { value: 'ai-range', label: 'AI 按范围出题', desc: '选择课程章节，AI 自动生成题目' },
  { value: 'ai-document', label: 'AI 识别文档出题', desc: '上传文档，AI 识别知识点出题' }
]
const steps = ['基础信息', '出题方式', '题目预览', '发布确认']
const letters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('')

const STATUS_LABEL = { 0: '草稿', 1: '进行中', 2: '已结束', 3: '已下架' }
const STATUS_THEME = { 0: 'default', 1: 'success', 2: 'warning', 3: 'default' }
const TYPE_LABEL = {
  single_choice: '单选题',
  multiple_choice: '多选题',
  true_false: '判断题',
  fill_blank: '填空题',
  short_answer: '简答题'
}

const typeLabel = t => ({ exam: '考试', homework: '作业' }[t] || t)
const qtypeLabel = t => TYPE_LABEL[t] || t
const statusLabel = row => row.statusLabel || STATUS_LABEL[row.status] || '未知'
const statusTheme = s => STATUS_THEME[s] || 'default'

/* ===== 列表数据 ===== */
const list = ref([])
const loading = ref(false)
const classes = ref([])
const courses = ref([])

const filterClassId = ref(undefined)
const filterType = ref(undefined)
const filterStatus = ref(undefined)
const searchKeyword = ref('')

const prefPageSize = computed(() => {
  try {
    const p = JSON.parse(localStorage.getItem('teacher_pref') || '{}')
    return p.pageSize || 10
  } catch (e) {
    return 10
  }
})

const columns = [
  { colKey: 'title', title: '标题', width: 200, ellipsis: true },
  { colKey: 'type', title: '类型', width: 80 },
  { colKey: 'className', title: '班级', width: 120, ellipsis: true },
  { colKey: 'courseName', title: '课程', width: 140, ellipsis: true },
  { colKey: 'startTime', title: '起止时间', width: 280 },
  { colKey: 'timeLimit', title: '时长', width: 80, align: 'center', cell: (h, { row }) => row.timeLimit + ' 分钟' },
  { colKey: 'totalScore', title: '总分', width: 70, align: 'center' },
  { colKey: 'passScore', title: '及格分', width: 80, align: 'center' },
  { colKey: 'questionCount', title: '题目数', width: 80, align: 'center' },
  { colKey: 'status', title: '状态', width: 90 },
  { colKey: 'shelf', title: '上架状态', width: 90 },
  { colKey: 'operation', title: '操作', width: 240 }
]

const filteredList = computed(() => {
  if (!searchKeyword.value) return list.value
  const kw = searchKeyword.value.trim().toLowerCase()
  return list.value.filter(item =>
    (item.title || '').toLowerCase().includes(kw) ||
    (item.className || '').toLowerCase().includes(kw)
  )
})

const classNameOf = id => {
  const c = classes.value.find(x => x.id === Number(id))
  return c ? c.className : ''
}

const loadList = async () => {
  loading.value = true
  try {
    const res = await getExamHomeworkList({
      classId: filterClassId.value || undefined,
      type: filterType.value || undefined,
      status: filterStatus.value || undefined,
      keyword: searchKeyword.value || undefined
    })
    // 后端 /exam-homework/list 返回裸数组，兼容 { list } 包裹结构
    list.value = Array.isArray(res) ? res : (res?.list || [])
  } catch (e) {
    MessagePlugin.error('加载失败')
  } finally {
    loading.value = false
  }
}

const loadClasses = async () => {
  try {
    const data = await getTeacherClasses()
    classes.value = data || []
  } catch {
    classes.value = []
  }
}

const loadCourses = async () => {
  try {
    const data = await getTeacherCoursesForSelect()
    courses.value = data || []
  } catch {
    courses.value = []
  }
}

onMounted(() => {
  loadClasses()
  loadCourses()
  loadList()
})

/* ===== 发布弹窗 ===== */
const publishOpen = ref(false)
const currentStep = ref(0)
const submitting = ref(false)
const generating = ref(false)
const generationError = ref('')
const previewQuestions = ref([])
const fileInputRef = ref(null)
const editingIndex = ref(-1)

const emptyForm = () => ({
  type: 'exam',
  classId: undefined,
  title: '',
  description: '',
  startTime: '',
  endTime: '',
  timeLimit: 60,
  totalScore: 100,
  passScore: 60,
  publishMode: 'immediate',
  scheduledTime: '',
  questionMode: 'ai-range',
  courseId: undefined,
  chapterScope: 'all',
  selectedChapters: [],
  questionTypes: ['single_choice', 'multiple_choice', 'true_false'],
  difficulty: 'medium',
  questionCount: 20,
  documentFile: null,
  documentName: ''
})

const form = ref(emptyForm())

const openPublish = () => {
  form.value = emptyForm()
  currentStep.value = 0
  previewQuestions.value = []
  generationError.value = ''
  generating.value = false
  submitting.value = false
  editingIndex.value = -1
  publishOpen.value = true
}

const onPublishClose = () => {
  if (generating.value || submitting.value) return
  publishOpen.value = false
}

const validateStep = step => {
  if (step === 0) {
    if (!form.value.classId) return '请选择班级'
    if (!form.value.title.trim()) return '请输入标题'
    if (!form.value.startTime) return '请选择开始时间'
    if (!form.value.endTime) return '请选择截止时间'
    if (new Date(form.value.endTime) <= new Date(form.value.startTime)) return '截止时间必须晚于开始时间'
    if (form.value.publishMode === 'scheduled') {
      if (!form.value.scheduledTime) return '请选择定时发布时间'
      if (new Date(form.value.scheduledTime) > new Date(form.value.startTime)) return '定时发布时间不能晚于开始时间'
    }
    if (form.value.timeLimit <= 0) return '限时必须大于 0'
    if (form.value.totalScore <= 0) return '总分必须大于 0'
    if (form.value.passScore < 0 || form.value.passScore > form.value.totalScore) return '及格分必须在 0 到总分之间'
  }
  if (step === 1) {
    if (form.value.questionMode === 'ai-range') {
      if (!form.value.courseId) return '请选择课程'
      if (form.value.questionTypes.length === 0) return '请至少选择一种题型'
      if (form.value.questionCount <= 0) return '题目数量必须大于 0'
    } else {
      if (!form.value.documentFile) return '请上传文档'
    }
  }
  if (step === 2) {
    if (previewQuestions.value.length === 0) return '请先生成题目预览'
  }
  return null
}

const handleGeneratePreview = async () => {
  if (generating.value) return
  const err = validateStep(1)
  if (err) {
    generationError.value = err
    return
  }
  generating.value = true
  generationError.value = ''
  try {
    let result
    if (form.value.questionMode === 'ai-range') {
      result = await generateQuestionsByRange({
        courseId: form.value.courseId || undefined,
        chapterIds: form.value.chapterScope === 'custom' ? form.value.selectedChapters : undefined,
        questionTypes: form.value.questionTypes,
        difficulty: form.value.difficulty,
        count: form.value.questionCount
      })
    } else {
      if (!form.value.documentFile) return
      const fd = new FormData()
      fd.append('file', form.value.documentFile)
      fd.append('questionTypes', form.value.questionTypes.join(','))
      fd.append('difficulty', form.value.difficulty)
      fd.append('count', String(form.value.questionCount))
      result = await generateQuestionsByDocument(fd)
    }
    const questions = (result && result.questions) || []
    if (!questions.length) {
      generationError.value = (result && result.error) || 'AI 未返回有效题目，请减少题目数量或重新生成'
      return
    }
    previewQuestions.value = questions
    if (result.error && result.failedTypes && result.failedTypes.length) {
      generationError.value = `${result.error}（已保留生成成功的题目，可继续发布或重新生成）`
    }
    currentStep.value = 2
  } catch (e) {
    generationError.value = (e && e.message) || '生成题目失败，请重试'
  } finally {
    generating.value = false
  }
}

const handleNext = async () => {
  const err = validateStep(currentStep.value)
  if (err) {
    generationError.value = err
    return
  }
  generationError.value = ''
  if (currentStep.value === 1) {
    await handleGeneratePreview()
  } else if (currentStep.value === 2) {
    currentStep.value = 3
  } else {
    currentStep.value = Math.min(currentStep.value + 1, steps.length - 1)
  }
}

const handlePrev = () => {
  generationError.value = ''
  currentStep.value = Math.max(currentStep.value - 1, 0)
}

const handlePublish = async () => {
  const err = validateStep(0) || validateStep(1) || validateStep(2)
  if (err) {
    generationError.value = err
    return
  }
  submitting.value = true
  try {
    await publishExamHomework({
      type: form.value.type,
      classId: Number(form.value.classId),
      title: form.value.title,
      description: form.value.description,
      startTime: form.value.startTime,
      endTime: form.value.endTime,
      timeLimit: form.value.timeLimit,
      totalScore: form.value.totalScore,
      passScore: form.value.passScore,
      publishMode: form.value.publishMode,
      scheduledTime: form.value.publishMode === 'scheduled' ? form.value.scheduledTime : undefined,
      questionMode: form.value.questionMode,
      courseId: form.value.questionMode === 'ai-range' ? Number(form.value.courseId) : undefined,
      chapterIds:
        form.value.questionMode === 'ai-range' && form.value.chapterScope === 'custom'
          ? form.value.selectedChapters
          : undefined,
      questionTypes: form.value.questionTypes,
      difficulty: form.value.difficulty,
      questionCount: form.value.questionCount,
      questions: previewQuestions.value
    })
    publishOpen.value = false
    MessagePlugin.success('发布成功')
    loadList()
  } catch (e) {
    generationError.value = (e && e.message) || '发布失败'
  } finally {
    submitting.value = false
  }
}

const handleFileChange = e => {
  const file = e.target.files && e.target.files[0]
  if (file) {
    form.value.documentFile = file
    form.value.documentName = file.name
  }
}

/* ===== 删除 / 上架下架 ===== */
const handleDelete = row => {
  const dlg = DialogPlugin.confirm({
    header: '删除确认',
    body: `确定删除「${row.title}」吗？`,
    theme: 'warning',
    onConfirm: async () => {
      try {
        await deleteExamHomework(row.id)
        MessagePlugin.success('删除成功')
        dlg.destroy()
        loadList()
      } catch (e) {
        MessagePlugin.error((e && e.message) || '删除失败')
      }
    }
  })
}

const handleToggleStatus = async row => {
  const newStatus = row.status === 1 ? 0 : 1
  const action = newStatus === 0 ? '下架' : '上架'
  const dlg = DialogPlugin.confirm({
    header: `${action}确认`,
    body: `确定${action}「${row.title}」吗？`,
    onConfirm: async () => {
      try {
        await toggleExamHomeworkStatus(row.id, newStatus)
        MessagePlugin.success(`${action}成功`)
        dlg.destroy()
        loadList()
      } catch (e) {
        MessagePlugin.error((e && e.message) || '操作失败')
      }
    }
  })
}

/* ===== 提交列表 ===== */
const submissionsOpen = ref(false)
const submissionsExam = ref(null)
const submissions = ref([])
const submissionsLoading = ref(false)
const adjustingAnswerId = ref(null)
const adjustForm = ref({ score: 0, comment: '' })
const adjustMax = ref(0)

const subTotalFinal = sub => {
  return (sub.answers || []).reduce((sum, a) => sum + Number(a.finalScore ?? a.autoScore ?? 0), 0)
}

const openSubmissions = async row => {
  submissionsExam.value = row
  submissionsOpen.value = true
  submissions.value = []
  adjustingAnswerId.value = null
  submissionsLoading.value = true
  try {
    const data = await getExamSubmissions(row.id)
    submissions.value = (data || []).map(s => ({ ...s, expanded: false }))
  } catch (e) {
    MessagePlugin.error((e && e.message) || '加载提交列表失败')
  } finally {
    submissionsLoading.value = false
  }
}

const openAdjustScore = a => {
  adjustingAnswerId.value = a.id
  adjustMax.value = a.maxScore ?? 0
  adjustForm.value = {
    score: Math.min(a.maxScore ?? 0, Math.max(0, Number(a.finalScore ?? a.autoScore ?? 0))),
    comment: a.teacherComment || a.aiComment || ''
  }
}

const confirmAdjustScore = async sub => {
  if (adjustingAnswerId.value == null) return
  try {
    const res = await adjustSubmissionAnswerScore(adjustingAnswerId.value, {
      finalScore: Number(adjustForm.value.score),
      teacherComment: adjustForm.value.comment
    })
    MessagePlugin.success(`调整成功！新分数：${res.newScore}分，剩余可调整次数：${res.remainingAdjust}`)
    adjustingAnswerId.value = null
    const data = await getExamSubmissions(submissionsExam.value.id)
    submissions.value = (data || []).map(s => ({ ...s, expanded: true }))
  } catch (e) {
    MessagePlugin.error((e && e.message) || '调整失败')
  }
}
</script>

<style scoped>
.exam-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20px;
}

.time-range-text {
  font-size: 13px;
  color: #55555C;
  white-space: nowrap;
}

/* ===== 发布弹窗 ===== */
.publish-form .t-form-item {
  margin-bottom: 16px;
}

.steps {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
  padding: 4px 8px;
}

.step-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.step-dot {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  background: #F5F5F7;
  color: #86868B;
  transition: all 0.25s ease;
}

.step-dot.active {
  background: #1D1D1F;
  color: #fff;
}

.step-dot.done {
  background: #0071E3;
  color: #fff;
}

.step-text {
  font-size: 13px;
  color: #AEAEB2;
  white-space: nowrap;
}

.step-text.active {
  color: #1D1D1F;
  font-weight: 500;
}

.step-arrow {
  color: #D1D1D6;
}

.gen-error {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  background: rgba(255, 59, 48, 0.06);
  color: #D32F2F;
  font-size: 13px;
  padding: 10px 12px;
  border-radius: 10px;
  margin-bottom: 16px;
  line-height: 1.5;
}

.type-cards,
.mode-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  width: 100%;
}

.type-card,
.mode-card {
  padding: 12px 14px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.type-card:hover,
.mode-card:hover {
  border-color: rgba(0, 113, 227, 0.3);
}

.type-card.active,
.mode-card.active {
  border-color: #0071E3;
  background: rgba(0, 113, 227, 0.04);
  box-shadow: 0 0 0 1px rgba(0, 113, 227, 0.15);
}

.type-title,
.mode-title {
  font-weight: 600;
  font-size: 14px;
  color: #1D1D1F;
}

.type-desc,
.mode-desc {
  font-size: 12px;
  color: #86868B;
  margin-top: 3px;
}

.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.datetime-input {
  width: 100%;
  height: 42px;
  padding: 0 12px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 10px;
  background: #FAFAFA;
  font-size: 15px;
  color: #1D1D1F;
  outline: none;
  transition: all 0.2s ease;
  font-family: inherit;
  box-sizing: border-box;
}

.datetime-input:hover {
  border-color: rgba(0, 0, 0, 0.12);
}

.datetime-input:focus {
  border-color: #0071E3;
  box-shadow: 0 0 0 3px rgba(0, 113, 227, 0.08);
  background: #FFFFFF;
}

.grid-3 {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.chapter-tip {
  margin-top: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #F5F5F7;
  color: #86868B;
  font-size: 13px;
}

.upload-area {
  border: 2px dashed rgba(0, 0, 0, 0.12);
  border-radius: 12px;
  padding: 28px;
  text-align: center;
  cursor: pointer;
  width: 100%;
  transition: border-color 0.2s, background 0.2s;
  font-size: 14px;
  color: #55555C;
}

.upload-area:hover {
  border-color: rgba(0, 113, 227, 0.4);
  background: rgba(0, 113, 227, 0.03);
}

.upload-sub {
  font-size: 12px;
  color: #AEAEB2;
  margin-top: 4px;
}

.qtype-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.qtype-chip {
  padding: 5px 14px;
  border-radius: 999px;
  font-size: 13px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  color: #55555C;
  cursor: pointer;
  user-select: none;
  transition: all 0.2s ease;
}

.qtype-chip.active {
  background: rgba(0, 113, 227, 0.08);
  border-color: rgba(0, 113, 227, 0.35);
  color: #0071E3;
}

.qtype-check {
  display: none;
}

/* ===== 题目预览 ===== */
.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.preview-title {
  font-weight: 600;
  font-size: 15px;
  color: #1D1D1F;
}

.preview-empty {
  text-align: center;
  padding: 48px 0;
  color: #AEAEB2;
  font-size: 14px;
  border: 1px dashed rgba(0, 0, 0, 0.1);
  border-radius: 12px;
}

.preview-list {
  max-height: 420px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-right: 4px;
}

.question-card {
  background: #FAFAFA;
  border: 1px solid rgba(0, 0, 0, 0.04);
  border-radius: 12px;
  padding: 14px;
}

.question-head {
  display: flex;
  gap: 10px;
}

.q-index {
  font-weight: 600;
  color: #0071E3;
  font-size: 14px;
  line-height: 1.5;
  flex-shrink: 0;
}

.q-content {
  flex: 1;
}

.q-text {
  font-size: 14px;
  color: #1D1D1F;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.q-options {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.q-options-editor {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.q-option-line {
  font-size: 13px;
  color: #55555C;
  display: flex;
  align-items: center;
  gap: 6px;
}

.q-opt-letter {
  color: #86868B;
  flex-shrink: 0;
}

.q-meta {
  margin-top: 8px;
  font-size: 12px;
  color: #AEAEB2;
  display: flex;
  align-items: center;
}

.q-edit-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 10px;
  margin-top: 8px;
}

.q-edit-label {
  display: block;
  font-size: 12px;
  color: #86868B;
  margin-bottom: 4px;
}

/* ===== 发布底部 ===== */
.publish-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 16px;
  margin-top: 20px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
}

.footer-actions {
  display: flex;
  gap: 10px;
}

.gen-overlay {
  position: absolute;
  inset: 0;
  z-index: 10;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(4px);
  border-radius: 12px;
  text-align: center;
}

.gen-overlay p {
  font-size: 14px;
  font-weight: 500;
  color: #1D1D1F;
  margin-top: 12px;
}

.gen-overlay .gen-sub {
  font-size: 12px;
  color: #86868B;
  font-weight: 400;
  margin-top: 4px;
}

.gen-spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ===== 确认列表 ===== */
.confirm-list {
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 12px;
  overflow: hidden;
}

.confirm-row {
  display: flex;
  justify-content: space-between;
  padding: 12px 14px;
  font-size: 14px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
}

.confirm-row:last-child {
  border-bottom: none;
}

.confirm-row span {
  color: #86868B;
}

.confirm-row b {
  color: #1D1D1F;
  font-weight: 500;
  text-align: right;
}

/* ===== 提交列表 ===== */
.subs-header {
  margin-bottom: 12px;
}

.subs-title {
  font-size: 14px;
  color: #86868B;
}

.subs-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
  margin-top: 16px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
}

.subs-loading,
.subs-empty {
  text-align: center;
  padding: 48px 0;
  color: #AEAEB2;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 14px;
}

.subs-list {
  max-height: 60vh;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-right: 4px;
}

.sub-card {
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 12px;
  overflow: hidden;
}

.sub-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px;
  cursor: pointer;
  transition: background 0.15s;
}

.sub-head:hover {
  background: rgba(0, 0, 0, 0.02);
}

.sub-student {
  display: flex;
  align-items: center;
  gap: 12px;
}

.sub-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: rgba(0, 113, 227, 0.1);
  color: #0071E3;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 15px;
  flex-shrink: 0;
}

.sub-name {
  font-weight: 600;
  color: #1D1D1F;
}

.sub-no {
  font-size: 12px;
  color: #AEAEB2;
  margin-left: 6px;
  font-weight: 400;
}

.sub-meta {
  font-size: 12px;
  color: #86868B;
  margin-top: 3px;
}
.sub-meta .t-tag {
  margin-left: 4px;
  vertical-align: middle;
}

.sub-score {
  text-align: right;
}

.sub-score-label {
  font-size: 12px;
  color: #AEAEB2;
}

.sub-score-val {
  font-weight: 600;
  margin-top: 2px;
}

.sub-ai { color: #86868B; }
.sub-slash { color: #D1D1D6; margin: 0 6px; }
.sub-final { color: #0071E3; }

.sub-chevron {
  color: #C7C7CC;
  flex-shrink: 0;
}

.sub-answers {
  border-top: 1px solid rgba(0, 0, 0, 0.04);
  background: #FAFAFA;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.answer-card {
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 12px;
  padding: 14px;
}

.answer-head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 12px;
}

.answer-type {
  flex-shrink: 0;
}

.answer-question {
  flex: 1;
}

.answer-content {
  font-size: 14px;
  font-weight: 500;
  color: #1D1D1F;
  white-space: pre-wrap;
  word-break: break-word;
}

.answer-ref {
  margin-top: 4px;
  font-size: 12px;
  color: #34C759;
  font-weight: 500;
}

.answer-score {
  text-align: right;
  min-width: 100px;
  flex-shrink: 0;
}

.ans-max {
  font-size: 12px;
  color: #86868B;
}

.ans-val {
  font-weight: 600;
  color: #1D1D1F;
  margin-top: 2px;
}

.ans-adjust {
  font-size: 12px;
  color: #FF9500;
}

.ans-ai-initial {
  font-size: 12px;
  color: #AEAEB2;
  margin-top: 2px;
}

.answer-cols {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  border-top: 1px solid rgba(0, 0, 0, 0.04);
  padding-top: 12px;
}

.answer-col {
  display: flex;
  flex-direction: column;
}

.col-label {
  font-size: 12px;
  color: #AEAEB2;
  margin-bottom: 6px;
}

.col-body {
  background: #FAFAFA;
  border-radius: 8px;
  padding: 10px;
  font-size: 13px;
  color: #55555C;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
  flex: 1;
}

.col-comments {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.comment-line {
  font-size: 13px;
  color: #55555C;
  line-height: 1.6;
}

.c-label {
  color: #AEAEB2;
}

.col-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.adjust-box {
  background: rgba(0, 113, 227, 0.05);
  border: 1px solid rgba(0, 113, 227, 0.2);
  border-radius: 8px;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.adjust-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.adjust-label {
  font-size: 12px;
  color: #86868B;
  white-space: nowrap;
}

.adjust-tip {
  font-size: 12px;
  color: #FF9500;
}

@media (max-width: 768px) {
  .exam-header {
    flex-direction: column;
    align-items: flex-start;
  }
  .answer-cols {
    grid-template-columns: 1fr;
  }
  .type-cards,
  .mode-cards {
    grid-template-columns: 1fr;
  }
  .grid-3 {
    grid-template-columns: 1fr;
  }
}
</style>