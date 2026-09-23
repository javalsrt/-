const api = require('../../utils/api');
const util = require('../../utils/util');

Page({
  data: {
    mode: 'add', // add/edit/list
    courseId: null,
    form: {
      name: '',
      teacher: '',
      classroom: '',
      dayOfWeek: 1,
      startTime: '08:10',
      endTime: '09:40',
      weeks: '1-16',
      color: '#4A90D9',
      colorIndex: 0,
      semester: ''
    },
    weekdayOptions: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
    colorOptions: util.COURSE_COLORS,
    courses: []
  },

  onLoad(options) {
    const mode = options.mode || 'add';
    const courseId = options.id;

    this.setData({
      mode,
      courseId: courseId ? parseInt(courseId) : null,
      'form.semester': util.getCurrentSemester()
    });

    if (mode === 'edit' && courseId) {
      this.loadCourse(courseId);
    }

    if (mode === 'list') {
      this.loadCourses();
    }
  },

  loadCourse(id) {
    api.get(`/courses/${id}`)
      .then(res => {
        if (res.code === 200) {
          const course = res.data;
          const colorIndex = util.COURSE_COLORS.indexOf(course.color);
          this.setData({
            form: {
              name: course.name,
              teacher: course.teacher || '',
              classroom: course.classroom || '',
              dayOfWeek: course.dayOfWeek,
              startTime: course.startTime?.substring(0, 5) || '08:10',
              endTime: course.endTime?.substring(0, 5) || '09:40',
              weeks: course.weeks || '',
              color: course.color,
              colorIndex: colorIndex >= 0 ? colorIndex : 0,
              semester: course.semester || ''
            }
          });
        }
      });
  },

  loadCourses() {
    const token = wx.getStorageSync('token');
    if (!token) return;

    api.get('/courses/schedule', { semester: '' })
      .then(res => {
        if (res.code === 200) {
          const schedule = res.data || {};
          const courses = [];
          for (let day = 1; day <= 7; day++) {
            const dayCourses = schedule[day] || [];
            dayCourses.forEach(c => {
              if (!courses.find(x => x.id === c.id)) {
                c.timeText = util.timeToString(c.startTime) + '-' + util.timeToString(c.endTime);
                courses.push(c);
              }
            });
          }
          this.setData({ courses });
        }
      });
  },

  onFormInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ [`form.${field}`]: e.detail.value });
  },

  onDayChange(e) {
    this.setData({ 'form.dayOfWeek': parseInt(e.detail.value) + 1 });
  },

  onColorChange(e) {
    const index = parseInt(e.detail.value);
    this.setData({
      'form.color': util.COURSE_COLORS[index],
      'form.colorIndex': index
    });
  },

  onTimeChange(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ [`form.${field}`]: e.detail.value });
  },

  saveCourse() {
    const form = this.data.form;
    if (!form.name) {
      wx.showToast({ title: '请输入课程名称', icon: 'none' });
      return;
    }

    wx.showLoading({ title: '保存中...' });

    const data = {
      name: form.name,
      teacher: form.teacher,
      classroom: form.classroom,
      dayOfWeek: form.dayOfWeek,
      startTime: form.startTime,
      endTime: form.endTime,
      weeks: form.weeks,
      color: form.color,
      semester: form.semester
    };

    const request = this.data.mode === 'edit'
      ? api.put(`/courses/${this.data.courseId}`, data)
      : api.post('/courses', data);

    request.then(res => {
      wx.hideLoading();
      if (res.code === 200) {
        wx.showToast({ title: '保存成功', icon: 'success' });
        setTimeout(() => wx.navigateBack(), 1500);
      } else {
        wx.showToast({ title: res.message || '保存失败', icon: 'none' });
      }
    }).catch(() => {
      wx.hideLoading();
      wx.showToast({ title: '保存失败', icon: 'none' });
    });
  },

  deleteCourse() {
    wx.showModal({
      title: '删除课程',
      content: '确定要删除该课程吗？所有相关课段和资料也会被删除。',
      success: (res) => {
        if (res.confirm) {
          api.del(`/courses/${this.data.courseId}`)
            .then(() => {
              wx.showToast({ title: '已删除', icon: 'success' });
              setTimeout(() => wx.navigateBack(), 1500);
            });
        }
      }
    });
  },

  goToAdd() {
    wx.navigateTo({ url: '/pages/course/course?mode=add' });
  },

  editCourse(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/course/course?mode=edit&id=${id}` });
  }
});
