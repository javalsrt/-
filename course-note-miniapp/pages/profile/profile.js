// pages/profile/profile.js
const api = require('../../utils/api');
const app = getApp();

Page({
  data: {
    isLoggedIn: false,
    userInfo: {},
    stats: {
      courseCount: 0,
      knowledgeCount: 0
    },
    examTodoCount: 0,
    // 账号密码登录表单
    loginForm: {
      username: '',
      password: ''
    },
    loginError: ''
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 2 });
    }
    this.checkLoginStatus();
  },

  // 检查登录状态
  checkLoginStatus() {
    const token = wx.getStorageSync('token');
    const userInfo = wx.getStorageSync('userInfo');

    if (token && userInfo) {
      // 已登录
      this.setData({ isLoggedIn: true });
      this.loadData();
    } else {
      // 未登录
      this.setData({
        isLoggedIn: false,
        userInfo: {},
        stats: { courseCount: 0, knowledgeCount: 0 },
        examTodoCount: 0
      });
    }
  },

  // 加载用户数据
  loadData() {
    const cachedInfo = wx.getStorageSync('userInfo') || {};
    const localAvatar = wx.getStorageSync('localAvatar');
    if (localAvatar) cachedInfo.avatarUrl = localAvatar;
    this.setData({ userInfo: cachedInfo });

    api.get('/user/info')
      .then(res => {
        if (res.code === 200) {
          const info = res.data;
          const localAvatar = wx.getStorageSync('localAvatar');
          if (localAvatar) info.avatarUrl = localAvatar;
          this.setData({ userInfo: info });
          wx.setStorageSync('userInfo', info);
        }
      })
      .catch(() => {});

    api.get('/user/stats')
      .then(res => {
        if (res.code === 200 && res.data) {
          this.setData({ stats: res.data });
        }
      })
      .catch(() => {});

    // 待完成的作业/考试角标（仅学生角色，教师/管理员无班级考试列表）
    const cachedRole = (wx.getStorageSync('userInfo') || {}).role;
    if (wx.getStorageSync('token') && cachedRole === 1) {
      api.get('/student/exam/list').then(res => {
        if (Array.isArray(res)) {
          const todo = res.filter(e => !e.submitted && e.examStatus === 'ongoing').length;
          this.setData({ examTodoCount: todo });
        }
      }).catch(() => {});
    } else {
      this.setData({ examTodoCount: 0 });
    }
  },

  // 登录表单输入
  onLoginInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ [`loginForm.${field}`]: e.detail.value, loginError: '' });
  },

  // 账号密码登录（教务系统统一登录）
  handleAccountLogin() {
    const { username, password } = this.data.loginForm;
    if (!username || !password) {
      this.setData({ loginError: '请输入账号和密码' });
      wx.showToast({ title: '请输入账号和密码', icon: 'none' });
      return;
    }
    wx.showLoading({ title: '登录中...' });
    app.accountLogin(username.trim(), password).then((data) => {
      wx.hideLoading();
      wx.showToast({ title: '登录成功', icon: 'success' });
      this.setData({
        isLoggedIn: true,
        loginForm: { username: '', password: '' },
        loginError: ''
      });
      this.loadData();
      // 通知课表等其他页面刷新登录态
      const pages = getCurrentPages();
      pages.forEach(page => {
        if (page.checkLoginStatus) page.checkLoginStatus();
      });
    }).catch((err) => {
      wx.hideLoading();
      const msg = (err && err.message) || '账号或密码错误';
      this.setData({ loginError: msg });
      wx.showToast({ title: msg, icon: 'none' });
    });
  },

  // 退出登录
  handleLogout() {
    wx.showModal({
      title: '确认退出',
      content: '退出后需要重新登录，确定退出吗？',
      confirmText: '退出',
      confirmColor: '#e74c3c',
      success: (res) => {
        if (res.confirm) {
          // 清除所有登录状态
          wx.removeStorageSync('token');
          wx.removeStorageSync('userInfo');
          wx.removeStorageSync('localAvatar');
          app.globalData.token = '';
          app.globalData.userInfo = {};
          app.globalData.offlineMode = false;

          this.setData({
            isLoggedIn: false,
            userInfo: {},
            stats: { courseCount: 0, knowledgeCount: 0 },
            examTodoCount: 0
          });

          wx.showToast({ title: '已退出登录', icon: 'success' });

          // 通知课表页面刷新
          const pages = getCurrentPages();
          pages.forEach(page => {
            if (page.checkLoginStatus) page.checkLoginStatus();
          });
        }
      }
    });
  },

  // 切换头像
  changeAvatar() {
    if (!this.data.isLoggedIn) return;
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['album'],
      success: (res) => {
        const tempFilePath = res.tempFiles[0].tempFilePath;
        wx.setStorageSync('localAvatar', tempFilePath);
        const info = { ...this.data.userInfo, avatarUrl: tempFilePath };
        this.setData({ userInfo: info });
        wx.showToast({ title: '头像已更新' });
      }
    });
  },

  goToSchedule() {
    wx.switchTab({ url: '/pages/index/index' });
  },

  goToKnowledge() {
    wx.switchTab({ url: '/pages/knowledge/knowledge' });
  },

  // 作业和考试列表（仅学生角色）
  goToExams() {
    if (!this.data.isLoggedIn) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }
    const role = (this.data.userInfo || {}).role;
    if (role != null && role !== 1) {
      wx.showToast({ title: '作业和考试仅面向学生账号', icon: 'none' });
      return;
    }
    wx.navigateTo({ url: '/pages/exam-list/exam-list' });
  },

  // 关于我们
  showAbout() {
    wx.showModal({
      title: '课堂笔记助手',
      content: '版本：1.0.0\n\n一个基于AI的课堂笔记智能管理工具，支持拍照OCR识别、AI整理归纳和知识库问答。',
      showCancel: false,
      confirmText: '知道了'
    });
  }
});
