import axios from 'axios'
import { MessagePlugin } from 'tdesign-vue-next'

const request = axios.create({
  baseURL: '/api',
  timeout: 300000 // 5 分钟：AI 出题 / 导入识别等接口可能需要较长时间
})

// 统一清理登录态（兼容两套登录取名，兼容迁移期）
function clearAuth() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  localStorage.removeItem('roles')
  localStorage.removeItem('permissions')
  localStorage.removeItem('teacher_token')
  localStorage.removeItem('teacher_info')
}

export function isAuthenticated() {
  return !!localStorage.getItem('token') || !!localStorage.getItem('teacher_token')
}

export function getToken() {
  return localStorage.getItem('token') || localStorage.getItem('teacher_token') || ''
}

// 请求拦截器：附加 token
request.interceptors.request.use(
  config => {
    const token = getToken()
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

// 响应拦截器：兼容后端两种返回约定
//  1) znxsgl 教务接口：直接返回裸数据体（数组/对象），HTTP 状态即成功与否；
//  2) coursennote 既有接口：返回 { code, message, data } 包裹（code===200 为成功）。
//  统一收口：若返回体带 code 字段则按包裹解包，否则视为裸数据原样透传。
request.interceptors.response.use(
  response => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 200) {
        return body.data
      }
      if (body.code === 401) {
        clearAuth()
        window.location.href = '/login'
        return Promise.reject(new Error(body.message || '未登录'))
      }
      MessagePlugin.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  error => {
    const status = error.response?.status
    // 401 或 403：未认证/无权限，清理并回登录页（403 无权限不弹错，避免打扰）
    if (status === 401) {
      clearAuth()
      window.location.href = '/login'
      return Promise.reject(error)
    }
    if (status === 403) {
      MessagePlugin.warning('无权限访问该资源')
      return Promise.reject(error)
    }
    const msg = error.response?.data?.message || error.message || '网络错误'
    MessagePlugin.error(msg)
    return Promise.reject(error)
  }
)

export default request