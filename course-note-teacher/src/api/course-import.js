import request from '@/utils/request'

// 课程导入（znxsgl：/schedule/import/**，上传→AI 提取→确认）
export const previewImport = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/schedule/import/preview', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
export const confirmImport = (items, fileName) =>
  request.post('/schedule/import/confirm', { items, fileName })
export const getImportRecords = () => request.get('/schedule/import/records')