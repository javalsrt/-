// pages/exam-take/exam-take.js
// 在线作答页：支持单选/多选/判断/填空/简答，考试带倒计时
const api = require('../../utils/api');

const TYPE_LABELS = {
  single_choice: '单选题',
  multiple_choice: '多选题',
  true_false: '判断题',
  fill_blank: '填空题',
  short_answer: '简答题'
};

/** 从选项文本提取字母键："A.选项" → "A"；无字母前缀则用全文 */
function optionKey(text) {
  const m = /^([A-Ha-h])\s*[.、．:：)]?\s*/.exec(text);
  return m ? m[1].toUpperCase() : text;
}

Page({
  data: {
    examId: null,
    loading: true,
    submitting: false,
    exam: null,
    questions: [],
    typeLabels: TYPE_LABELS,
    // 倒计时
    timerText: '',
    timeUp: false,
    timeWarning: false,
    // 答题状态
    answeredCount: 0
  },

  _timer: null,
  _remainSeconds: 0,

  onLoad(options) {
    const id = options.id;
    if (!id) {
      wx.showToast({ title: '参数错误', icon: 'none' });
      setTimeout(() => wx.navigateBack(), 800);
      return;
    }
    this.setData({ examId: id });
    this.loadDetail(id);
  },

  onUnload() {
    this.stopTimer();
  },

  loadDetail(id) {
    api.get(`/student/exam/${id}/detail`).then(res => {
      if (res && res.error) {
        wx.showToast({ title: res.error, icon: 'none' });
        setTimeout(() => wx.navigateBack(), 1000);
        return;
      }
      if (!res || !Array.isArray(res.questions)) {
        wx.showToast({ title: '加载失败', icon: 'none' });
        return;
      }
      // 已提交 → 直接看结果
      if (res.submitted) {
        wx.redirectTo({ url: `/pages/exam-result/exam-result?id=${id}` });
        return;
      }
      if (res.examStatus === 'not_started') {
        wx.showToast({ title: '考试尚未开始', icon: 'none' });
        setTimeout(() => wx.navigateBack(), 1000);
        return;
      }
      if (res.examStatus === 'ended') {
        wx.showToast({ title: '该考试/作业已结束', icon: 'none' });
        setTimeout(() => wx.navigateBack(), 1000);
        return;
      }

      // 初始化每题作答状态：选项转为 {key, text} 结构
      const questions = res.questions.map((q, idx) => ({
        ...q,
        index: idx + 1,
        options: (q.options || []).map(opt => ({ key: optionKey(opt), text: opt })),
        selected: '',      // 单选/判断选中的 key
        checked: {},       // 多选：{ 选项key: true }
        textAnswer: ''     // 填空/简答
      }));

      wx.setNavigationBarTitle({ title: res.title || '在线作答' });
      this.setData({ exam: res, questions, loading: false });

      // 考试类型且有限时 → 启动倒计时
      if (res.type === 'exam' && res.timeLimit > 0) {
        this.startTimer(res.timeLimit * 60);
      }
    }).catch(() => {
      wx.showToast({ title: '加载失败，请重试', icon: 'none' });
    });
  },

  // ==================== 倒计时 ====================

  startTimer(seconds) {
    this._remainSeconds = seconds;
    this.updateTimerText();
    this._timer = setInterval(() => {
      this._remainSeconds--;
      if (this._remainSeconds <= 0) {
        this.stopTimer();
        this.setData({ timeUp: true, timerText: '00:00' });
        wx.showModal({
          title: '时间到',
          content: '答题时间已结束，将自动提交答卷',
          showCancel: false,
          success: () => this.doSubmit(true)
        });
        return;
      }
      this.updateTimerText();
    }, 1000);
  },

  updateTimerText() {
    const m = Math.floor(this._remainSeconds / 60);
    const s = this._remainSeconds % 60;
    this.setData({
      timerText: `${m < 10 ? '0' + m : m}:${s < 10 ? '0' + s : s}`,
      timeWarning: this._remainSeconds <= 300 && this._remainSeconds > 0
    });
  },

  stopTimer() {
    if (this._timer) {
      clearInterval(this._timer);
      this._timer = null;
    }
  },

  // ==================== 作答交互 ====================

  // 单选题：选中选项
  selectOption(e) {
    const { qindex, key } = e.currentTarget.dataset;
    this.setData({ [`questions[${qindex}].selected`]: key }, () => this.refreshAnswered());
  },

  // 多选题：切换选项
  toggleOption(e) {
    const { qindex, key } = e.currentTarget.dataset;
    const q = this.data.questions[qindex];
    const checked = { ...q.checked };
    if (checked[key]) {
      delete checked[key];
    } else {
      checked[key] = true;
    }
    this.setData({ [`questions[${qindex}].checked`]: checked }, () => this.refreshAnswered());
  },

  // 判断题：选 对/错
  selectJudge(e) {
    const { qindex, value } = e.currentTarget.dataset;
    this.setData({ [`questions[${qindex}].selected`]: value }, () => this.refreshAnswered());
  },

  // 填空/简答：文本输入
  onTextInput(e) {
    const qindex = e.currentTarget.dataset.qindex;
    this.setData({ [`questions[${qindex}].textAnswer`]: e.detail.value }, () => this.refreshAnswered());
  },

  // 统计已答题数
  refreshAnswered() {
    const count = this.data.questions.filter(q => {
      if (q.type === 'single_choice' || q.type === 'true_false') return !!q.selected;
      if (q.type === 'multiple_choice') return Object.keys(q.checked).length > 0;
      return !!(q.textAnswer && q.textAnswer.trim());
    }).length;
    this.setData({ answeredCount: count });
  },

  // ==================== 提交 ====================

  handleSubmit() {
    if (this.data.submitting) return;
    const total = this.data.questions.length;
    const unanswered = total - this.data.answeredCount;
    const msg = unanswered > 0
      ? `还有 ${unanswered} 道题未作答，确定提交吗？`
      : '确定提交答卷吗？提交后将自动评分，不能修改。';
    wx.showModal({
      title: '确认提交',
      content: msg,
      confirmText: '提交',
      cancelText: '再想想',
      success: (res) => {
        if (res.confirm) this.doSubmit(false);
      }
    });
  },

  doSubmit(auto) {
    if (this.data.submitting) return;
    this.stopTimer();
    this.setData({ submitting: true });
    if (!auto) wx.showLoading({ title: '提交评分中...', mask: true });

    const answers = this.data.questions.map(q => {
      let answer = '';
      if (q.type === 'single_choice' || q.type === 'true_false') {
        answer = q.selected || '';
      } else if (q.type === 'multiple_choice') {
        answer = Object.keys(q.checked).join(',');
      } else {
        answer = q.textAnswer || '';
      }
      return { questionId: q.id, answer };
    });

    api.post(`/student/exam/${this.data.examId}/submit`, { answers }).then(res => {
      wx.hideLoading();
      if (res && res.error) {
        this.setData({ submitting: false });
        wx.showModal({ title: '提交失败', content: res.error, showCancel: false });
        return;
      }
      wx.showToast({ title: '提交成功', icon: 'success' });
      setTimeout(() => {
        wx.redirectTo({ url: `/pages/exam-result/exam-result?id=${this.data.examId}` });
      }, 600);
    }).catch(() => {
      wx.hideLoading();
      this.setData({ submitting: false });
      wx.showModal({ title: '提交失败', content: '网络异常，请重试', showCancel: false });
    });
  }
});
