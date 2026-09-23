import request from '@/utils/request'

// 考试/作业（/exam-homework/**、/teacher/**、/schedule/teacher/**）
export const getTeacherClasses = () => request.get('/teacher/class/list')
export const getTeacherCoursesForSelect = () => request.get('/schedule/teacher/courses')
export const getExamHomeworkList = (params) => request.get('/exam-homework/list', { params })
export const publishExamHomework = (data) => request.post('/exam-homework/publish', data)
export const deleteExamHomework = (id) => request.delete(`/exam-homework/${id}`)
export const toggleExamHomeworkStatus = (id, status) => request.put(`/exam-homework/${id}/status`, { status })

// AI 按范围生成题目预览
export const generateQuestionsByRange = async (data) => {
  const res = await request.post('/exam-homework/generate-by-range', data)
  if (res.error && (!res.questions || res.questions.length === 0)) {
    throw new Error(res.error)
  }
  return res
}

// AI 识别文档生成题目预览
export const generateQuestionsByDocument = async (data) => {
  const res = await request.post('/exam-homework/generate-by-document', data, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  if (res.error && (!res.questions || res.questions.length === 0)) {
    throw new Error(res.error)
  }
  return res
}

// 提交与判分
export const getExamSubmissions = (examId) => request.get(`/exam-homework/${examId}/submissions`)
export const adjustSubmissionAnswerScore = (answerId, data) =>
  request.post(`/exam-homework/submission-answer/${answerId}/adjust-score`, data)