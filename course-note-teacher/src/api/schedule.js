import request from '@/utils/request'

// 排课管理（znxsgl：/admin/schedule/**）
export const getClassrooms = (type) =>
  request.get('/admin/schedule/classrooms', { params: type ? { type } : undefined })
export const addClassroom = (data) => request.post('/admin/schedule/classroom', data)
export const updateClassroom = (id, data) => request.put(`/admin/schedule/classroom/${id}`, data)
export const deleteClassroom = (id) => request.delete(`/admin/schedule/classroom/${id}`)

export const getTeachingTasks = (params) => request.get('/admin/schedule/tasks', { params })
export const batchImportTasks = (tasks) => request.post('/admin/schedule/tasks/batch', tasks)
export const deleteTeachingTask = (id) => request.delete(`/admin/schedule/task/${id}`)
export const clearTeachingTasks = (semester) =>
  request.delete('/admin/schedule/tasks/clear', { params: semester ? { semester } : undefined })

export const autoGenerateSchedule = (semester, clearExisting = true, teacherId) =>
  request.post('/admin/schedule/auto-generate', null, {
    params: { semester, clearExisting, ...(teacherId != null ? { teacherId } : {}) }
  })

export const getScheduleStats = (semester, teacherId) =>
  request.get('/admin/schedule/stats', {
    params: {
      ...(semester ? { semester } : {}),
      ...(teacherId != null ? { teacherId } : {})
    }
  })