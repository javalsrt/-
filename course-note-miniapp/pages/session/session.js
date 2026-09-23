const api = require('../../utils/api');
const util = require('../../utils/util');

Page({
  data: {
    sessionId: '',
    courseId: '',
    courseName: '',
    courseColor: '#4A90D9',
    sessionDate: '',
    startTime: '',
    endTime: '',
    summary: '',
    showSummary: false,
    photos: [],
    notes: [],
    thirdParty: [],
    totalCount: 0
  },

  onLoad(options) {
    this.setData({
      sessionId: options.sessionId,
      courseId: options.courseId
    });
    this.loadSessionDetail();
  },

  loadSessionDetail() {
    api.get(`/sessions/${this.data.sessionId}/detail`)
      .then(res => {
        if (res.code === 200) {
          const data = res.data;
          const session = data.session || {};
          const course = data.course || {};

          this.setData({
            courseName: course.name || '',
            courseColor: course.color || '#4A90D9',
            sessionDate: util.formatDate(session.sessionDate),
            startTime: util.timeToString(session.actualStartTime),
            endTime: util.timeToString(session.actualEndTime),
            summary: data.session.summary || '',
            photos: data.photos || [],
            notes: data.notes || [],
            thirdParty: data.thirdParty || [],
            totalCount: data.totalCount || 0
          });
        }
      });
  },

  toggleSummary() {
    this.setData({ showSummary: !this.data.showSummary });
  },

  goBack() {
    wx.navigateBack();
  },

  goToPhoto() {
    wx.navigateTo({
      url: `/pages/photo/photo?sessionId=${this.data.sessionId}&courseId=${this.data.courseId}`
    });
  },

  goToNote() {
    wx.navigateTo({
      url: `/pages/note/note?sessionId=${this.data.sessionId}&courseId=${this.data.courseId}`
    });
  },

  goToImport() {
    wx.showActionSheet({
      itemList: ['从微信文件导入', '从链接导入'],
      success: (res) => {
        if (res.tapIndex === 0) {
          wx.chooseMessageFile({
            count: 1,
            type: 'file',
            success: (fileRes) => {
              this.importFile(fileRes.tempFiles[0]);
            }
          });
        } else {
          wx.showModal({
            title: '导入资料',
            content: '请输入资料链接',
            editable: true,
            success: (modalRes) => {
              if (modalRes.confirm && modalRes.content) {
                this.importLink(modalRes.content);
              }
            }
          });
        }
      }
    });
  },

  importFile(file) {
    wx.showLoading({ title: '导入中...' });
    api.upload('/files/upload', file.path, {})
      .then(res => {
        wx.hideLoading();
        if (res.code === 200) {
          api.post('/materials/import', {
            sessionId: this.data.sessionId,
            courseId: this.data.courseId,
            title: file.name,
            fileUrl: res.data,
            source: 'wechat'
          }).then(() => {
            wx.showToast({ title: '导入成功', icon: 'success' });
            this.loadSessionDetail();
          });
        }
      });
  },

  importLink(link) {
    api.post('/materials/import', {
      sessionId: this.data.sessionId,
      courseId: this.data.courseId,
      title: '导入资料',
      fileUrl: link,
      description: link,
      source: 'external'
    }).then(() => {
      wx.showToast({ title: '导入成功', icon: 'success' });
      this.loadSessionDetail();
    });
  },

  goToMaterialDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({
      url: `/pages/material-detail/material-detail?id=${id}&sessionId=${this.data.sessionId}`
    });
  }
});
