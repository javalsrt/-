// pages/exam-list/exam-list.js
// 作业和考试列表（学生端）
const api = require('../../utils/api');

Page({
  data: {
    loading: true,
    isLoggedIn: true,
    activeTab: 'all', // all | exam | homework
    tabs: [
      { key: 'all', label: '全部' },
      { key: 'exam', label: '考试' },
      { key: 'homework', label: '作业' }
    ],
    allList: [],
    showList: []
  },

  onLoad() {
    this.loadList();
  },

  onShow() {
    // 从作答页/结果页返回时刷新列表（提交状态可能已变化）
    if (!this.data.loading) this.loadList();
  },

  onPullDownRefresh() {
    this.loadList(() => wx.stopPullDownRefresh());
  },

  loadList(done) {
    const token = wx.getStorageSync('token');
    if (!token) {
      this.setData({ isLoggedIn: false, loading: false, allList: [], showList: [] });
      if (done) done();
      return;
    }
    // 仅学生可访问班级作业/考试列表，其他角色直接空态（避免 403 触发登录态清理）
    const role = (wx.getStorageSync('userInfo') || {}).role;
    if (role != null && role !== 1) {
      this.setData({ isLoggedIn: false, loading: false, allList: [], showList: [] });
      wx.showToast({ title: '作业和考试仅面向学生账号', icon: 'none' });
      if (done) done();
      return;
    }
    api.get('/student/exam/list').then(res => {
      if (Array.isArray(res)) {
        this.setData({ allList: res, isLoggedIn: true, loading: false });
        this.applyFilter();
      } else if (res && res.error) {
        wx.showToast({ title: res.error, icon: 'none' });
        this.setData({ loading: false });
      } else {
        this.setData({ loading: false });
      }
      if (done) done();
    }).catch(() => {
      this.setData({ loading: false });
      if (done) done();
    });
  },

  // 切换筛选标签
  switchTab(e) {
    const key = e.currentTarget.dataset.tab;
    this.setData({ activeTab: key });
    this.applyFilter();
  },

  applyFilter() {
    const tab = this.data.activeTab;
    const list = tab === 'all'
      ? this.data.allList
      : this.data.allList.filter(item => item.type === tab);
    this.setData({ showList: list });
  },

  // 点击列表项：已提交 → 查看结果；进行中 → 去作答；已结束未提交 → 提示
  openExam(e) {
    const { id, submitted, examstatus } = e.currentTarget.dataset;
    if (submitted) {
      wx.navigateTo({ url: `/pages/exam-result/exam-result?id=${id}` });
    } else if (examstatus === 'ended') {
      wx.showToast({ title: '该考试/作业已结束', icon: 'none' });
    } else {
      wx.navigateTo({ url: `/pages/exam-take/exam-take?id=${id}` });
    }
  }
});
