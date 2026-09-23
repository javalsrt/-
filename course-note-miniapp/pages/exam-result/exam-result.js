// pages/exam-result/exam-result.js
// 作答结果页：成绩概览 + 逐题明细（我的作答/参考答案/AI点评/教师调分）
const api = require('../../utils/api');

const TYPE_LABELS = {
  single_choice: '单选题',
  multiple_choice: '多选题',
  true_false: '判断题',
  fill_blank: '填空题',
  short_answer: '简答题'
};

Page({
  data: {
    examId: null,
    loading: true,
    result: null,
    answers: [],
    typeLabels: TYPE_LABELS
  },

  onLoad(options) {
    const id = options.id;
    if (!id) {
      wx.showToast({ title: '参数错误', icon: 'none' });
      setTimeout(() => wx.navigateBack(), 800);
      return;
    }
    this.setData({ examId: id });
    this.loadResult(id);
  },

  onPullDownRefresh() {
    // 教师调分后可下拉刷新最新成绩
    this.loadResult(this.data.examId, () => wx.stopPullDownRefresh());
  },

  loadResult(id, done) {
    api.get(`/student/exam/${id}/result`).then(res => {
      if (res && res.error) {
        wx.showToast({ title: res.error, icon: 'none' });
        setTimeout(() => wx.navigateBack(), 1000);
        if (done) done();
        return;
      }
      if (!res || !Array.isArray(res.answers)) {
        wx.showToast({ title: '加载失败', icon: 'none' });
        if (done) done();
        return;
      }
      const answers = res.answers.map((a, idx) => ({
        ...a,
        index: idx + 1,
        score: a.finalScore != null ? a.finalScore : a.autoScore,
        // 客观题：是否答对；主观题：按得分率判断
        correct: (a.finalScore != null ? a.finalScore : a.autoScore) != null
          && (a.finalScore != null ? a.finalScore : a.autoScore) === a.maxScore
      }));
      wx.setNavigationBarTitle({ title: res.title || '作答结果' });
      this.setData({ result: res, answers, loading: false });
      if (done) done();
    }).catch(() => {
      wx.showToast({ title: '加载失败', icon: 'none' });
      if (done) done();
    });
  },

  goBackToList() {
    wx.navigateBack();
  }
});
