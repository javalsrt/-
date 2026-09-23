/**
 * API 请求封装
 * 统一处理请求拦截、token注入、错误处理
 * 后端不可用时自动返回模拟数据（离线演示模式）
 */

// 后端服务地址，部署到线上时修改此处即可
const BASE_URL = 'http://localhost:8080/api';
const WS_BASE = 'ws://localhost:8080/api';


// ===== 离线演示模拟数据 =====
const MOCK_DATA = {
  '/auth/login': () => ({
    code: 200,
    token: 'mock_token',
    userId: 1,
    realName: '演示学生',
    username: 'demo',
    role: 1,
    roles: ['student'],
    permissions: []
  }),
  '/schedule/student/my-with-semester': () => ({
    code: 200,
    semester: { name: '2025-2026-2', status: 'ongoing' },
    schedules: [
      { scheduleId: 101, courseName: '软件工程', teacherName: '张教授', classroom: '教学楼A201', dayOfWeek: 1, startTime: '08:10', endTime: '09:40', weeks: '[1,2,3,4,5,6]' },
      { scheduleId: 102, courseName: '数据结构', teacherName: '李教授', classroom: '教学楼B302', dayOfWeek: 2, startTime: '10:00', endTime: '11:30', weeks: '[1,2,3,4,5,6]' },
      { scheduleId: 103, courseName: '数据库原理', teacherName: '赵教授', classroom: '教学楼A101', dayOfWeek: 3, startTime: '08:10', endTime: '09:40', weeks: '[1,2,3,4,5,6]' },
      { scheduleId: 104, courseName: '计算机网络', teacherName: '陈教授', classroom: '教学楼B201', dayOfWeek: 4, startTime: '10:00', endTime: '11:30', weeks: '[1,2,3,4,5,6]' },
      { scheduleId: 105, courseName: '编译原理', teacherName: '刘教授', classroom: '教学楼C301', dayOfWeek: 5, startTime: '14:00', endTime: '15:30', weeks: '[1,2,3,4,5,6]' }
    ]
  }),
  '/courses/schedule': () => ({
    code: 200,
    data: {
      1: [{ id: 1, name: '软件工程', teacher: '张教授', classroom: '教学楼A201', startTime: '08:10', endTime: '09:40', color: '#4A90D9', dayOfWeek: 1, weeks: '[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16]', status: 1 }],
      2: [{ id: 2, name: '数据结构', teacher: '李教授', classroom: '教学楼B302', startTime: '10:00', endTime: '11:30', color: '#E8833A', dayOfWeek: 2, weeks: '[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16]', status: 1 },
          { id: 3, name: '操作系统', teacher: '王教授', classroom: '教学楼C405', startTime: '14:00', endTime: '15:30', color: '#6BCB77', dayOfWeek: 2, weeks: '[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16]', status: 1 }],
      3: [{ id: 4, name: '数据库原理', teacher: '赵教授', classroom: '教学楼A101', startTime: '08:10', endTime: '09:40', color: '#4D96FF', dayOfWeek: 3, weeks: '[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16]', status: 1 }],
      4: [{ id: 5, name: '计算机网络', teacher: '陈教授', classroom: '教学楼B201', startTime: '10:00', endTime: '11:30', color: '#FF6B6B', dayOfWeek: 4, weeks: '[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16]', status: 1 }],
      5: [{ id: 6, name: '编译原理', teacher: '刘教授', classroom: '教学楼C301', startTime: '14:00', endTime: '15:30', color: '#845EC2', dayOfWeek: 5, weeks: '[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16]', status: 1 }]
    }
  }),
  '/user/info': () => ({ code: 200, data: { id: 1, nickname: '演示用户', avatarUrl: '', school: '示例大学', major: '计算机科学' } }),
  '/user/stats': () => ({ code: 200, data: { courseCount: 5, knowledgeCount: 3, noteCount: 2 } }),
  '/sessions/date': () => ({ code: 200, data: [] }),
  '/sessions/current': () => ({ code: 200, data: null }),
  '/ai/knowledge': () => ({ code: 200, data: { knowledgeBase: { id: 1, name: '数据结构知识库' }, course: { name: '数据结构' }, documents: [], totalDocs: 3, indexedDocs: 3 } }),
  default: () => ({ code: 200, data: [] })
};

function getMockData(url) {
  const mockFn = MOCK_DATA[url] || MOCK_DATA.default;
  const result = mockFn();
  return new Promise((resolve) => setTimeout(() => resolve(result), 100));
}

/**
 * 执行真实的HTTP请求
 * 说明：登录统一为账号密码（/auth/login），token 失效不再自动登录，
 *       清除本地凭据后降级为离线演示数据，由用户在「我的」页重新登录。
 */
function doRequest(method, url, data, token, retryCount) {
  return new Promise((resolve) => {
    const header = { 'Content-Type': 'application/json' };
    if (token) header['Authorization'] = 'Bearer ' + token;

    wx.request({
      url: BASE_URL + url,
      method: method,
      data: data,
      header: header,
      success: (res) => {
        var isUnauthorized = res.statusCode === 401
          || (res.data && (res.data.code === 401 || res.data.code === 403));
        if (isUnauthorized && retryCount < 1) {
          // token失效 → 清除本地登录态，降级演示数据
          console.warn('[API] ' + url + ' 401/403，登录态已失效');
          // 竞态保护：仅当本地 token 仍等于本次请求携带的 token 时才清除。
          // 否则说明期间用户已重新登录（新 token），旧请求的 401 不应清掉新登录态。
          if (token && wx.getStorageSync('token') === token) {
            wx.removeStorageSync('token');
          }
          getMockData(url).then(resolve);
        } else {
          resolve(res.data);
        }
      },
      fail: (err) => {
        console.warn(`[API] ${url} 网络失败，使用模拟数据: ${err.errMsg}`);
        getMockData(url).then(resolve);
      }
    });
  });
}

function request(method, url, data) {
  const token = wx.getStorageSync('token');
  return doRequest(method, url, data, token, 0);
}

module.exports = {
  BASE_URL,
  WS_BASE,
  get: (url, data) => request('GET', url, data),
  post: (url, data) => request('POST', url, data),
  put: (url, data) => request('PUT', url, data),
  del: (url) => request('DELETE', url),
  upload: (url, filePath, formData) => {
    return new Promise((resolve) => {
      const token = wx.getStorageSync('token');
      wx.uploadFile({
        url: BASE_URL + url,
        filePath: filePath,
        name: 'file',
        formData: formData,
        header: { 'Authorization': 'Bearer ' + token },
        success: (res) => {
          try { resolve(JSON.parse(res.data)); } catch (e) { resolve(res.data); }
        },
        fail: () => {
          resolve({
            code: 200,
            data: { id: Date.now(), type: 'PHOTO', title: '模拟图片', content: '离线演示模式，OCR识别结果示例', fileUrl: '', aiProcessed: 1, createdAt: new Date().toISOString() }
          });
        }
      });
    });
  }
};
