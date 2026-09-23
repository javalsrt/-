const api = require('../../utils/api');

Page({
  data: {
    sessionId: '',
    courseId: '',
    photoPreview: '',
    ocrText: '',
    photos: []
  },

  onLoad(options) {
    this.setData({
      sessionId: options.sessionId,
      courseId: options.courseId
    });
    this.loadPhotos();
  },

  takePhoto() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['camera'],
      success: (res) => {
        const tempFile = res.tempFiles[0];
        this.uploadAndOcr(tempFile.tempFilePath);
      }
    });
  },

  chooseFromAlbum() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['album'],
      success: (res) => {
        const tempFile = res.tempFiles[0];
        this.uploadAndOcr(tempFile.tempFilePath);
      }
    });
  },

  uploadAndOcr(tempFilePath) {
    wx.showLoading({ title: '上传中...' });

    api.upload('/materials/photo', tempFilePath, {
      sessionId: this.data.sessionId,
      courseId: this.data.courseId
    }).then(res => {
      wx.hideLoading();
      if (res.code === 200) {
        const material = res.data;
        this.setData({
          photoPreview: material.fileUrl,
          ocrText: material.content || 'OCR识别中...'
        });
        wx.showToast({ title: '上传成功', icon: 'success' });
        this.loadPhotos();
      } else {
        wx.showToast({ title: res.message || '上传失败', icon: 'none' });
      }
    }).catch(() => {
      wx.hideLoading();
      wx.showToast({ title: '上传失败', icon: 'none' });
    });
  },

  // 照片在拍摄/选择时已经通过 uploadAndOcr 保存到后端，这里仅清空预览态
  clearPhoto() {
    this.setData({ ocrText: '', photoPreview: '' });
  },

  copyOcrText() {
    if (this.data.ocrText) {
      wx.setClipboardData({
        data: this.data.ocrText,
        success: () => wx.showToast({ title: '已复制', icon: 'success' })
      });
    }
  },

  loadPhotos() {
    api.get(`/materials/session/${this.data.sessionId}`, {})
      .then(res => {
        if (res.code === 200) {
          const photos = (res.data || []).filter(m => m.type === 'PHOTO');
          this.setData({ photos });
        }
      });
  }
});
