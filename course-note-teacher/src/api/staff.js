import request from '@/utils/request'

// 人员管理（znxsgl：/admin/user/**）
export const getUserList = (params) => request.get('/admin/user/list', { params })
export const createUser = (data) => request.post('/admin/user', data)
export const updateUser = (id, data) => request.put(`/admin/user/${id}`, data)
export const resetPassword = (id, password) => request.put(`/admin/user/${id}/reset-password`, { password })
export const deleteUser = (id) => request.delete(`/admin/user/${id}`)
export const getClasses = () => request.get('/admin/user/classes')
export const getUserOverview = (params) => request.get('/admin/user/overview', { params })
export const importStudents = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/admin/user/import-students', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
export const getTeacherCourses = (params) => request.get('/admin/user/teacher-courses', { params })