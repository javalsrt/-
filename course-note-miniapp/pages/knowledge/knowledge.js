const api = require('../../utils/api');
const util = require('../../utils/util');
const app = getApp();

Page({
  data: {
    courses: [],
    selectedCourseId: null,
    kbDetail: null,
    documents: [],
    filteredDocuments: [],
    docSearch: '',
    activeTab: 'chat',
    chatMessages: [
      {
        role: 'assistant',
        content: '你好！我是你的课程学习助手。选择一个课程后，我可以帮你解答知识点、总结重点或出题练习。'
      }
    ],
    chatInput: '',
    hasInput: false,
    scrollToView: '',
    quickQuestions: ['总结一下本章重点', '解释核心概念', '出几道练习题'],
    isStreaming: false,
    isLoadingKb: false
  },

  // 非响应式属性
  _ws: null,
  _wsConnected: false,
  _reconnectTimer: null,
  _reconnectCount: 0,
  _maxReconnectCount: 5,

  onLoad() {
    this.loadCourses();
    this._connectWs();
  },

  onUnload() {
    this._closeWs();
  },

  onShow() {
    // 设置自定义 tabBar 选中状态
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 1 });
    }
    // 之前未登录导致课程列表为空时，登录后返回本页自动重新加载
    const token = wx.getStorageSync('token');
    if (token && (!this.data.courses || this.data.courses.length === 0)) {
      this.loadCourses();
    }
    if (this.data.selectedCourseId) {
      this.loadKnowledgeBase(this.data.selectedCourseId);
    }
  },

  // 下拉刷新：重新加载课程列表和当前知识库
  onPullDownRefresh() {
    this.loadCourses();
    if (this.data.selectedCourseId) {
      this.loadKnowledgeBase(this.data.selectedCourseId);
    }
  },

  // 建立WebSocket连接（流式对话），带自动重连
  _connectWs() {
    if (this._ws) return;
    if (this._reconnectTimer) {
      clearTimeout(this._reconnectTimer);
      this._reconnectTimer = null;
    }
    const token = wx.getStorageSync('token') || '';
    this._ws = wx.connectSocket({
      url: `${api.WS_BASE}/ws/ai/chat?token=${token}`,
      success: () => { this._wsConnected = true; }
    });
    this._ws.onOpen(() => {
      this._wsConnected = true;
      this._reconnectCount = 0;
    });
    this._ws.onMessage((res) => {
      if (!this.data.isStreaming) return;
      const data = res.data || '';
      if (data === '[DONE]') {
        this.setData({ isStreaming: false });
        this.scrollToBottom();
        return;
      }
      if (data.startsWith('[ERROR]')) {
        this.setData({ isStreaming: false });
        const errMsg = data.replace('[ERROR]', '');
        const msgs = this.data.chatMessages;
        const lastIdx = msgs.length - 1;
        if (lastIdx >= 0) {
          const key = `chatMessages[${lastIdx}].content`;
          this.setData({
            [key]: this._cleanAiText(msgs[lastIdx].content || '抱歉，AI服务暂时不可用。' + (errMsg ? '(' + errMsg + ')' : ''))
          });
        }
        return;
      }
      // 追加流式文本到最后一条助手消息，并清理多余特殊符号
      const msgs = this.data.chatMessages;
      const lastIdx = msgs.length - 1;
      if (lastIdx >= 0) {
        const rawContent = (msgs[lastIdx].rawContent || '') + data;
        const key = `chatMessages[${lastIdx}].content`;
        this.setData({
          ['chatMessages[' + lastIdx + '].rawContent']: rawContent,
          [key]: this._cleanAiText(rawContent)
        }, () => {
          this.scrollToBottom();
        });
      }
    });
    this._ws.onError(() => {
      this._wsConnected = false;
      this._scheduleReconnect();
    });
    this._ws.onClose(() => {
      this._wsConnected = false;
      this._ws = null;
      this._scheduleReconnect();
    });
  },

  // WebSocket 自动重连（指数退避，最多 5 次）
  _scheduleReconnect() {
    if (this._reconnectCount >= this._maxReconnectCount) return;
    const delay = Math.min(1000 * Math.pow(2, this._reconnectCount), 30000);
    this._reconnectTimer = setTimeout(() => {
      this._reconnectCount++;
      this._connectWs();
    }, delay);
  },

  _closeWs() {
    if (this._reconnectTimer) {
      clearTimeout(this._reconnectTimer);
      this._reconnectTimer = null;
    }
    if (this._ws) {
      this._ws.close();
      this._ws = null;
      this._wsConnected = false;
    }
  },

  // 清理 AI 输出中的多余 markdown 特殊符号
  _cleanAiText(text) {
    if (!text) return text;
    return text
      .replace(/\*\*\s*\*\*/g, '') // 空粗体标记
      .replace(/\*\*/g, '') // 粗体标记
      .replace(/_{2,}/g, '') // 下划线强调
      .replace(/`([^`]*)`/g, '$1') // 行内代码
      .replace(/#{1,6}\s/g, '') // 标题标记
      .replace(/\n{3,}/g, '\n\n'); // 压缩连续空行
  },

  loadCourses() {
    const token = wx.getStorageSync('token');
    if (!token) {
      // 未登录：显示空态，登录后从「我的」页返回时 onShow 会重新加载
      this.setData({ courses: [], loading: false });
      return;
    }

    api.get('/courses/schedule')
      .then(res => {
        if (res.code === 200) {
          const schedule = res.data || {};
          const seen = new Set();
          const courses = [];
          for (let day = 1; day <= 7; day++) {
            const dayCourses = schedule[day] || [];
            dayCourses.forEach(c => {
              // 按课程名去重，同一门课只保留第一个
              if (!seen.has(c.name)) {
                seen.add(c.name);
                c._dayOfWeek = day;
                c._dayOfWeekText = util.getWeekDay(day);
                courses.push(c);
              }
            });
          }
          // 按课程名称排序（中文拼音排序）
          courses.sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'));
          this.setData({ courses });
          // 进入页面自动选中第一个课程，避免下方一片空白
          if (courses.length > 0 && !this.data.selectedCourseId) {
            this.selectCourse({ currentTarget: { dataset: { id: courses[0].id } } });
          }
        }
        wx.stopPullDownRefresh();
      })
      .catch(() => { wx.stopPullDownRefresh(); });
  },

  // 选择课程：切换知识库并生成课程相关的欢迎语和快捷问题
  selectCourse(e) {
    var courseId = parseInt(e.currentTarget.dataset.id);
    var course = this.data.courses.find(c => c.id === courseId) || {};
    var courseName = course.name || '本课程';
    this.setData({
      selectedCourseId: courseId,
      activeTab: 'chat',
      isLoadingKb: true,
      chatMessages: [
        {
          role: 'assistant',
          content: this._getWelcomeMessage(courseName)
        }
      ],
      quickQuestions: this._getQuickQuestions(courseName)
    });
    this.loadKnowledgeBase(courseId, () => {
      this.setData({ isLoadingKb: false });
    });
  },

  // 根据课程名生成欢迎语
  _getWelcomeMessage(courseName) {
    return '你好！我是《' + courseName + '》学习助手。关于这门课的概念、例题、重点总结，都可以问我。';
  },

  // 根据课程名生成相关快捷问题
  _getQuickQuestions(courseName) {
    var name = (courseName || '').toLowerCase();
    if (name.indexOf('编译') !== -1 || name.indexOf('compiler') !== -1) {
      return ['解释LR分析法', '什么是语法制导翻译', '词法分析和语法分析的区别'];
    }
    if (name.indexOf('数据结构') !== -1 || name.indexOf('算法') !== -1) {
      return ['栈和队列有什么区别', '什么是二叉树', '哈希表原理是什么'];
    }
    if (name.indexOf('操作系统') !== -1) {
      return ['进程和线程的区别', '页面置换算法有哪些', '死锁产生的四个条件'];
    }
    if (name.indexOf('计算机网络') !== -1 || name.indexOf('网络') !== -1) {
      return ['TCP和UDP的区别', '简述OSI七层模型', 'HTTP常见状态码含义'];
    }
    if (name.indexOf('数据库') !== -1) {
      return ['SQL基础查询怎么写', '事务的ACID特性', '索引的作用和优缺点'];
    }
    if (name.indexOf('软件工程') !== -1) {
      return ['敏捷开发是什么', 'UML图有哪些类型', '软件测试的常见方法'];
    }
    if (name.indexOf('人工智能') !== -1 || name.indexOf('机器学习') !== -1) {
      return ['机器学习常见算法', '神经网络基本原理', '深度学习与传统学习区别'];
    }
    if (name.indexOf('web') !== -1 || name.indexOf('前端') !== -1) {
      return ['HTML/CSS/JS各自作用', '什么是响应式布局', 'Vue和React的区别'];
    }
    if (name.indexOf('计算机组成') !== -1 || name.indexOf('组成原理') !== -1) {
      return ['CPU执行指令的过程', 'Cache的作用和工作原理', '指令流水线是什么'];
    }
    if (name.indexOf('离散数学') !== -1) {
      return ['命题逻辑基础', '什么是等价关系', '图论的基本概念'];
    }
    return ['总结一下本章重点', '解释核心概念', '出几道练习题'];
  },

  switchTab(e) {
    this.setData({ activeTab: e.currentTarget.dataset.tab });
  },

  // 点击文档卡片：图片预览 / 文档打开
  onDocTap(e) {
    const item = e.currentTarget.dataset;
    const type = this.inferDocType(item.url);
    const dataset = { materialId: item.materialId, url: item.url, title: item.title };
    if (type === 'image') {
      this.previewImage({ currentTarget: { dataset } });
    } else {
      this.openFile({ currentTarget: { dataset } });
    }
  },

  // 悬浮上传按钮：弹出操作菜单
  showUploadMenu() {
    wx.showActionSheet({
      itemList: ['上传图片', '上传文档'],
      success: (res) => {
        if (res.tapIndex === 0) this.uploadFromChat({ currentTarget: { dataset: { type: 'image' } } });
        else if (res.tapIndex === 1) this.uploadFromChat({ currentTarget: { dataset: { type: 'file' } } });
      }
    });
  },

  // 从文件名推断文件类型
  inferDocType(fileUrl) {
    if (!fileUrl) return 'file';
    const ext = fileUrl.split('.').pop().toLowerCase();
    const imgTypes = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp'];
    const docTypes = ['doc', 'docx', 'pdf', 'xls', 'xlsx', 'ppt', 'pptx', 'txt'];
    if (imgTypes.includes(ext)) return 'image';
    if (docTypes.includes(ext)) return 'file';
    return 'file';
  },

  // 加载指定课程的知识库详情和文档列表
  loadKnowledgeBase(courseId, callback) {
    let finished = 0;
    const done = () => {
      finished++;
      if (finished >= 2 && typeof callback === 'function') callback();
    };

    api.get(`/ai/knowledge/${courseId}`)
      .then(res => {
        if (res.code === 200) {
          const kbDetail = res.data;
          this.setData({ kbDetail });
        }
      })
      .catch(() => {})
      .finally(done);

    api.get(`/ai/knowledge/${courseId}/documents`)
      .then(res => {
        if (res.code === 200) {
          const docs = (res.data || []).map(d => ({
            ...d,
            _type: d._type || this.inferDocType(d.fileUrl)
          }));
          this.setData({ documents: docs }, () => {
            this.filterDocuments();
          });
        }
        wx.stopPullDownRefresh();
      })
      .catch(() => { wx.stopPullDownRefresh(); })
      .finally(done);
  },

  // 根据搜索关键字过滤文档列表
  filterDocuments() {
    const kw = (this.data.docSearch || '').trim().toLowerCase();
    let list = this.data.documents || [];
    if (kw) {
      list = list.filter(d =>
        (d.title || '').toLowerCase().includes(kw) ||
        (d.summary || '').toLowerCase().includes(kw)
      );
    }
    this.setData({ filteredDocuments: list });
  },

  // 文档搜索输入
  onDocSearchInput(e) {
    this.setData({ docSearch: e.detail.value }, () => {
      this.filterDocuments();
    });
  },

  onChatInput(e) {
    const value = e.detail.value || '';
    this.setData({ chatInput: value, hasInput: value.trim().length > 0 });
  },

  // 点击快捷问题直接发送
  sendQuickQuestion(e) {
    const question = e.currentTarget.dataset.question;
    if (!question) return;
    this.setData({ chatInput: question }, () => {
      this.sendMessage();
    });
  },

  sendMessage() {
    const message = this.data.chatInput.trim();
    if (!message || !this.data.selectedCourseId) return;
    if (this.data.isStreaming) {
      wx.showToast({ title: '请等待回复完成', icon: 'none' });
      return;
    }

    // 合并用户消息与空占位符一次 setData，避免竞态
    const userMsg = { role: 'user', content: message };
    const allMsgs = [...this.data.chatMessages, userMsg, { role: 'assistant', content: '', rawContent: '' }];
    this.setData({
      chatMessages: allMsgs,
      chatInput: '',
      hasInput: false,
      isStreaming: true
    }, () => {
      this.scrollToBottom();
    });

    // 构建历史记录（不包括最新的用户消息和空占位符）
    const historyMsgs = allMsgs.slice(0, -2).map(m => ({ role: m.role, content: m.content }));

    // 通过WebSocket发送（流式）
    if (this._ws && this._wsConnected) {
      try {
        wx.sendSocketMessage({
          data: JSON.stringify({
            courseId: this.data.selectedCourseId,
            message: message,
            useKnowledgeBase: true,
            history: historyMsgs
          })
        });
      } catch (e) {
        this.setData({ isStreaming: false });
        this._fallbackHttp(message, historyMsgs);
      }
    } else {
      // WebSocket未连接，尝试重新连接后降级到HTTP
      this.setData({ isStreaming: false });
      this._fallbackHttp(message, historyMsgs);
    }
  },

  // HTTP降级（非流式，但仍清理特殊符号）
  _fallbackHttp(message, history) {
    api.post('/ai/chat', {
      courseId: this.data.selectedCourseId,
      message: message,
      useKnowledgeBase: true,
      history: history
    }).then(res => {
      if (res.code === 200) {
        const raw = res.data || '';
        const msgs = this.data.chatMessages;
        msgs[msgs.length - 1] = { role: 'assistant', content: this._cleanAiText(raw), rawContent: raw };
        this.setData({ chatMessages: msgs });
      }
    }).catch(() => {
      const msgs = this.data.chatMessages;
      msgs[msgs.length - 1] = { role: 'assistant', content: '抱歉，AI服务暂时不可用，请稍后重试。' };
      this.setData({ chatMessages: msgs });
    }).finally(() => {
      this.setData({ isStreaming: false });
      this.scrollToBottom();
    });
  },

  scrollToBottom() {
    setTimeout(() => {
      this.setData({ scrollToView: 'msgBottom' }, () => {
        // 滚动到目标后重置，避免后续消息不触发
        setTimeout(() => {
          this.setData({ scrollToView: '' });
        }, 200);
      });
    }, 100);
  },

  // 查看原图
  previewImage(e) {
    const data = e.currentTarget.dataset;
    const url = data.url;
    if (!url) return;
    wx.showLoading({ title: '加载图片...' });
    const fullUrl = data.materialId
      ? `${api.BASE_URL}/files/materials/${data.materialId}/preview`
      : `${api.BASE_URL}/files/preview/${url.replace('/uploads/', '').split('/').map(s => encodeURIComponent(s)).join('/')}`;
    wx.downloadFile({
      url: fullUrl,
      header: { 'Authorization': 'Bearer ' + wx.getStorageSync('token') },
      success: (res) => {
        wx.hideLoading();
        if (res.statusCode === 200) {
          wx.previewImage({
            current: res.tempFilePath,
            urls: [res.tempFilePath]
          });
        } else {
          wx.showToast({ title: '图片加载失败(' + res.statusCode + ')', icon: 'none' });
        }
      },
      fail: (err) => {
        wx.hideLoading();
        wx.showToast({ title: '图片加载失败: ' + (err.errMsg || ''), icon: 'none' });
      }
    });
  },

  // 文档类型标签（文字 + 颜色）
  docTypeInfo(item) {
    const ext = ((item.fileUrl || '').split('.').pop() || '').toLowerCase();
    if (item._type === 'image' || ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp'].includes(ext)) {
      return { text: '图片', color: '#34C759', bg: 'rgba(52, 199, 89, 0.1)' };
    }
    if (ext === 'pdf') return { text: 'PDF', color: '#FF3B30', bg: 'rgba(255, 59, 48, 0.1)' };
    if (['doc', 'docx'].includes(ext)) return { text: 'DOC', color: '#0A84FF', bg: 'rgba(10, 132, 255, 0.1)' };
    if (['xls', 'xlsx'].includes(ext)) return { text: 'XLS', color: '#34C759', bg: 'rgba(52, 199, 89, 0.1)' };
    if (['ppt', 'pptx'].includes(ext)) return { text: 'PPT', color: '#FF9500', bg: 'rgba(255, 149, 0, 0.1)' };
    if (['txt', 'md'].includes(ext)) return { text: 'TXT', color: '#8E8E93', bg: 'rgba(142, 142, 147, 0.1)' };
    return { text: '文件', color: '#0A84FF', bg: 'rgba(10, 132, 255, 0.1)' };
  },

  // 文档索引状态标签
  docStatusInfo(item) {
    const status = item.indexStatus;
    if (status === 2) return { text: '已索引', color: '#34C759', bg: 'rgba(52, 199, 89, 0.1)' };
    if (status === 3) return { text: '失败', color: '#FF3B30', bg: 'rgba(255, 59, 48, 0.1)' };
    if (status === 1) return { text: '索引中', color: '#FF9500', bg: 'rgba(255, 149, 0, 0.1)' };
    return { text: '待索引', color: '#8E8E93', bg: 'rgba(142, 142, 147, 0.1)' };
  },

  // 格式化文件大小
  formatFileSize(size) {
    if (!size || size <= 0) return '';
    if (size < 1024) return size + 'B';
    if (size < 1024 * 1024) return (size / 1024).toFixed(1) + 'KB';
    return (size / (1024 * 1024)).toFixed(1) + 'MB';
  },

  // 打开文档（doc/docx/pdf/xls/xlsx）
  openFile(e) {
    const data = e.currentTarget.dataset;
    const url = data.url;
    const title = data.title || '资料';
    if (!url) return;
    const fullUrl = data.materialId
      ? `${api.BASE_URL}/files/materials/${data.materialId}/preview`
      : `${api.BASE_URL}/files/preview/${url.replace('/uploads/', '').split('/').map(s => encodeURIComponent(s)).join('/')}`;
    wx.showLoading({ title: '加载文档...' });
    wx.downloadFile({
      url: fullUrl,
      header: { 'Authorization': 'Bearer ' + wx.getStorageSync('token') },
      success: (res) => {
        wx.hideLoading();
        if (res.statusCode === 200) {
          wx.openDocument({
            filePath: res.tempFilePath,
            fileType: this._getFileType(url),
            success: () => {},
            fail: () => {
              wx.showToast({ title: '打开文档失败', icon: 'none' });
            }
          });
        } else {
          wx.showToast({ title: '文档下载失败(' + res.statusCode + ')', icon: 'none' });
        }
      },
      fail: (err) => {
        wx.hideLoading();
        wx.showToast({ title: '文档下载失败: ' + (err.errMsg || ''), icon: 'none' });
      }
    });
  },

  _getFileType(url) {
    const ext = (url || '').split('.').pop().toLowerCase();
    if (['doc', 'docx'].includes(ext)) return 'doc';
    if (ext === 'pdf') return 'pdf';
    if (['xls', 'xlsx'].includes(ext)) return 'xls';
    if (['ppt', 'pptx'].includes(ext)) return 'ppt';
    return ext;
  },

  // 长按显示操作菜单
  showDocActions(e) {
    const id = e.currentTarget.dataset.id;
    wx.showActionSheet({
      itemList: ['编辑', '删除'],
      success: (res) => {
        if (res.tapIndex === 0) {
          // 找到对应卡片数据
          const doc = this.data.documents.find(d => d.id === id);
          if (doc) {
            this.editDoc({
              currentTarget: { dataset: { id: doc.id, title: doc.title, summary: doc.summary } }
            });
          }
        } else if (res.tapIndex === 1) {
          this.deleteDoc({ currentTarget: { dataset: { id: id } } });
        }
      }
    });
  },

  // 编辑文档
  editDoc(e) {
    const id = e.currentTarget.dataset.id;
    const oldTitle = e.currentTarget.dataset.title || '';
    const oldSummary = e.currentTarget.dataset.summary || '';
    wx.showModal({
      title: '编辑文档',
      editable: true,
      placeholderText: '请输入标题',
      content: oldTitle,
      success: (res1) => {
        if (!res1.confirm) return;
        const newTitle = res1.content.trim() || oldTitle;
        wx.showModal({
          title: '编辑摘要',
          editable: true,
          placeholderText: '请输入摘要',
          content: oldSummary,
          success: (res2) => {
            if (!res2.confirm) return;
            const newSummary = res2.content.trim() || oldSummary;
            wx.showLoading({ title: '保存中...' });
            api.put('/ai/knowledge/document/' + id, {
              title: newTitle,
              summary: newSummary
            }).then(resp => {
              wx.hideLoading();
              if (resp.code === 200) {
                wx.showToast({ title: '修改成功', icon: 'success' });
                // 更新本地数据
                const docs = this.data.documents.map(d => {
                  if (d.id === id) {
                    return { ...d, title: newTitle, summary: newSummary };
                  }
                  return d;
                });
                this.setData({ documents: docs }, () => {
                  this.filterDocuments();
                });
              } else {
                wx.showToast({ title: '修改失败', icon: 'none' });
              }
            });
          }
        });
      }
    });
  },

  // 删除按钮点击
  onDeleteTap(e) {
    this.deleteDoc({ currentTarget: { dataset: { id: e.currentTarget.dataset.id } } });
  },

  // 删除文档
  deleteDoc(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '确认删除',
      content: '确定要删除这条知识库文档吗？',
      success: (res) => {
        if (!res.confirm) return;
        wx.showLoading({ title: '删除中...' });
        api.del('/ai/knowledge/document/' + id)
          .then(resp => {
            wx.hideLoading();
            if (resp.code === 200) {
              wx.showToast({ title: '删除成功', icon: 'success' });
              const docs = this.data.documents.filter(d => d.id !== id);
              this.setData({ documents: docs }, () => {
                this.filterDocuments();
              });
            } else {
              wx.showToast({ title: resp.message || '删除失败', icon: 'none' });
            }
          })
          .catch(() => {
            wx.hideLoading();
            wx.showToast({ title: '删除失败', icon: 'none' });
          });
      }
    });
  },

  // 从微信聊天记录选择文件上传到知识库
  uploadFromChat(e) {
    const type = e.currentTarget.dataset.type;
    const courseId = this.data.selectedCourseId;
    if (!courseId) {
      wx.showToast({ title: '请先选择课程', icon: 'none' });
      return;
    }

    // 根据类型设置文件过滤
    let chooseType = 'all';
    if (type === 'image') chooseType = 'image';
    else if (type === 'file') chooseType = 'file';

    wx.chooseMessageFile({
      count: 9,
      type: chooseType,
      success: (res) => {
        const files = res.tempFiles;
        if (!files || files.length === 0) return;
        wx.showLoading({ title: '正在处理中...' });
        let uploaded = 0;
        let failed = 0;
        const total = files.length;

        const checkDone = () => {
          if (uploaded + failed >= total) {
            wx.hideLoading();
            wx.showToast({
              title: `上传完成：成功${uploaded}个${failed > 0 ? '，失败' + failed + '个' : ''}`,
              icon: 'none',
              duration: 2000
            });
          }
        };

        files.forEach((file) => {
          const formData = {
            courseId: courseId,
            fileType: type,
            fileName: file.name
          };

          api.upload('/ai/knowledge/upload', file.path, formData)
            .then(resp => {
              if (resp.code === 200 && resp.data) {
                uploaded++;
                // 将新卡片插入到文档列表最前面
                const newDoc = {
                  id: resp.data.id,
                  title: resp.data.title,
                  summary: resp.data.summary || '',
                  fileUrl: resp.data.fileUrl || '',
                  indexStatus: resp.data.indexStatus || 2,
                  createdAt: resp.data.createdAt || '',
                  _type: type
                };
                const docs = this.data.documents || [];
                docs.unshift(newDoc);
                this.setData({ documents: docs }, () => {
                  this.filterDocuments();
                });
              } else {
                failed++;
              }
            })
            .catch(() => { failed++; })
            .finally(() => { checkDone(); });
        });
      }
    });
  }
});
