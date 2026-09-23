const api = require('../../utils/api');

Page({
  data: {
    sessionId: '',
    courseId: '',
    title: '',
    content: '',
    notes: []
  },

  onLoad(options) {
    this.setData({
      sessionId: options.sessionId,
      courseId: options.courseId
    });
    this.loadNotes();
  },

  onTitleInput(e) {
    this.setData({ title: e.detail.value });
  },

  onContentInput(e) {
    this.setData({ content: e.detail.value });
  },

  saveNote() {
    if (!this.data.content.trim()) {
      wx.showToast({ title: '请输入笔记内容', icon: 'none' });
      return;
    }

    wx.showLoading({ title: '保存中...' });

    api.post('/materials/note', {
      sessionId: this.data.sessionId,
      courseId: this.data.courseId,
      title: this.data.title || '课堂笔记',
      content: this.data.content
    }).then(res => {
      wx.hideLoading();
      if (res.code === 200) {
        wx.showToast({ title: '保存成功', icon: 'success' });
        this.setData({ title: '', content: '' });
        this.loadNotes();
      } else {
        wx.showToast({ title: res.message || '保存失败', icon: 'none' });
      }
    }).catch(() => {
      wx.hideLoading();
      wx.showToast({ title: '保存失败', icon: 'none' });
    });
  },

  clearNote() {
    this.setData({ title: '', content: '' });
  },

  loadNotes() {
    api.get(`/materials/session/${this.data.sessionId}`, {})
      .then(res => {
        if (res.code === 200) {
          const notes = (res.data || [])
            .filter(m => m.type === 'NOTE')
            .map(n => ({
              ...n,
              aiTags: n.aiTags ? (() => { try { return JSON.parse(n.aiTags); } catch { return []; } })() : []
            }));
          this.setData({ notes });
        }
      });
  }
});
