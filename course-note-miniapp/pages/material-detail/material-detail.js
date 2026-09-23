const api = require('../../utils/api');
const util = require('../../utils/util');

Page({
  data: {
    material: null,
    sessionId: ''
  },

  onLoad(options) {
    this.setData({ sessionId: options.sessionId || '' });
    this.loadMaterial(options.id);
  },

  loadMaterial(id) {
    // 从会话详情获取材料详情
    if (this.data.sessionId) {
      api.get(`/materials/session/${this.data.sessionId}`, {})
        .then(res => {
          if (res.code === 200) {
            const materials = res.data || [];
            const material = materials.find(m => m.id == id);
            this.setData({ material });
          }
        });
    }
  },

  parseTags(tags) {
    if (!tags) return [];
    try {
      const parsed = typeof tags === 'string' ? JSON.parse(tags) : tags;
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  },

  getTypeText(type) {
    return util.getMaterialTypeText(type);
  },

  getTypeClass(type) {
    return util.getMaterialTypeClass(type);
  },

  importToKB() {
    if (!this.data.material) return;
    wx.showLoading({ title: '导入中...' });
    api.post(`/ai/knowledge/import?courseId=${this.data.material.courseId}&materialId=${this.data.material.id}`)
      .then(res => {
        wx.hideLoading();
        if (res.code === 200) {
          wx.showToast({ title: '已导入知识库', icon: 'success' });
        }
      });
  },

  deleteMaterial() {
    wx.showModal({
      title: '删除确认',
      content: '确定删除此资料吗？',
      success: (res) => {
        if (res.confirm) {
          api.del(`/materials/${this.data.material.id}`)
            .then(() => {
              wx.showToast({ title: '已删除', icon: 'success' });
              setTimeout(() => wx.navigateBack(), 1000);
            });
        }
      }
    });
  },

  goBack() {
    wx.navigateBack();
  }
});
