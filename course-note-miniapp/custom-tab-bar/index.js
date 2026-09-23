Component({
  data: {
    selected: 0,
    list: [
      {
        pagePath: '/pages/index/index',
        text: '课表',
        icon: '课'
      },
      {
        pagePath: '/pages/knowledge/knowledge',
        text: '知识库',
        icon: '知'
      },
      {
        pagePath: '/pages/profile/profile',
        text: '我的',
        icon: '我'
      }
    ]
  },
  methods: {
    switchTab(e) {
      const data = e.currentTarget.dataset;
      const url = data.path;
      wx.switchTab({
        url,
        success: () => {
          this.setData({ selected: data.index });
        },
        fail: () => {
          // 兼容路径无前导斜杠
          wx.switchTab({
            url: url.replace(/^\//, ''),
            success: () => {
              this.setData({ selected: data.index });
            }
          });
        }
      });
    }
  }
});
