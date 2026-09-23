//app.js
const api = require('./utils/api');

App({
  onLaunch() {
    const token = wx.getStorageSync('token');
    if (token) {
      this.globalData.token = token;
      this.globalData.userInfo = wx.getStorageSync('userInfo') || {};
    }
    // 未登录不自动登录，由用户在「我的」页进行账号密码登录（教务统一认证）
  },

  // 账号密码登录（教务系统统一登录：username+password → /auth/login）
  accountLogin(username, password) {
    return new Promise((resolve, reject) => {
      api.post('/auth/login', { username, password })
        .then(response => {
          // 后端不可达时 api.js 降级返回离线演示数据（mock_token）：
          // 假登录会让后续所有请求 401，这里明确报错提示用户，而不是假成功。
          if (response && response.token === 'mock_token') {
            reject(new Error('无法连接教务服务器，请确认后端服务已启动'));
            return;
          }
          if (response && (response.code === 200 || response.token)) {
            // 教务接口直接返回 {token, realName, username, role, userId, ...}
            var token = response.token;
            var userId = response.userId;
            var n = response.realName || response.username || '';
            var a = response.avatarUrl || '';
            var role = response.role; // 1学生 2教师 3管理员
            wx.setStorageSync('token', token);
            wx.setStorageSync('userInfo', { userId: userId, nickname: n, avatarUrl: a, role: role });
            wx.setStorageSync('wxNickname', n || '');
            this.globalData.token = token;
            this.globalData.userInfo = { userId: userId, nickname: n, avatarUrl: a, role: role };
            this.globalData.offlineMode = false;
            resolve(response);
          } else {
            // 兼容旧 {code, message} 失败结构
            reject(new Error((response && response.message) || '账号或密码错误'));
          }
        })
        .catch((err) => {
          reject(err);
        });
    });
  },

  globalData: {
    token: '',
    userInfo: {},
    offlineMode: false,
    currentSemester: '',
    currentSession: null
  }
});
