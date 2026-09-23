import request from '@/utils/request'

// 学期管理（znxsgl：/semester/**）
export const getCurrentSemester = () => request.get('/semester/current')
export const getSemesterList = () => request.get('/semester/list')
export const createSemester = (data) => request.post('/semester', data)
export const updateSemester = (id, data) => request.put(`/semester/${id}`, data)
export const switchSemester = (id) => request.put(`/semester/switch/${id}`)
export const deleteSemester = (id) => request.delete(`/semester/${id}`)