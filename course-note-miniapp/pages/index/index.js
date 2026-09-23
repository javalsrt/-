const api = require('../../utils/api');
const util = require('../../utils/util');
const app = getApp();

Page({
  data: {
    weekDays: [],
    currentWeek: 1,
    weekLabel: '',
    weekPickerRange: [],
    weekPickerIndex: 0,
    swiperCourses: [[], [], [], [], [], [], []],
    swiperDays: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
    selectedDayIndex: 0,
    hasWeekendCourses: true,
    totalDays: 7,
    weekSchedule: [],
    weekGrid: [],
    weekGridDays: [],
    todayCol: -1,
    gridCols: [],
    weekScheduleEmpty: false,
    scheduleLoggedIn: false, // 教务体系登录态（区分"未登录"与"已登录无课表"两种空态）
    currentSession: null,
    selectedDate: '',
    swiperHeight: 300,
    // 学期状态：不影响课表展示(false) 或 需提示并隐藏课表(true)
    scheduleHidden: false,
    semStatus: 'ongoing', // before / ongoing / ended / none
    semNotice: ''
  },

  // 课表缓存，避免重复请求
  _scheduleCache: null,
  // 课表缓存对应的用户ID，用于账号切换时失效缓存
  _cacheUserId: null,
  // 课表加载中标记，防止 onLoad 与 onShow 重复请求
  _scheduleLoading: false,

  onLoad() {
    const now = new Date();
    const today = this.formatDateStr(now);
    const dayOfWeek = now.getDay() || 7;
    const totalDays = this.data.totalDays || 7;
    const weekPickerRange = [];
    for (let i = 1; i <= 18; i++) weekPickerRange.push('第' + i + '周');
    // 先按自然周估算当前周，不依赖 setData；学期真实 startDate 由 loadSchedule 返回后覆盖。
    const year = now.getFullYear();
    const semesterStart = new Date(year, 2, 9);
    const week = Math.floor((now - semesterStart) / (7 * 86400000)) + 1;
    const currentWeek = week > 0 ? week : 1;

    // 始终显示 7 天
    const selectedDayIndex = Math.min(dayOfWeek - 1, totalDays - 1);
    this.setData({
      selectedDate: today,
      selectedDayIndex,
      currentWeek,
      weekPickerIndex: currentWeek - 1,
      weekPickerRange
    }, () => {
      this.generateWeekDays(now, totalDays);
      this.loadSchedule();
    });
  },

  onShow() {
    // 设置自定义 tabBar 选中状态
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 0 });
    }
    // 登录态自愈：在「我的」页登录后切回本页时自动刷新课表。
    // tabBar 切换后 getCurrentPages() 不含其他 tab 页，profile 的跨页通知不可达，必须由 onShow 兜底。
    this.checkLoginStatus();
    this.checkCurrentSession();
  },

  // 供「我的」页登录/退出后调用，刷新课表
  checkLoginStatus() {
    const token = wx.getStorageSync('token');
    if (token) {
      // 账号切换（userId 变化）时失效旧缓存，强制重新加载课表
      const uid = (wx.getStorageSync('userInfo') || {}).userId;
      if (this._cacheUserId != null && this._cacheUserId !== uid) {
        this._scheduleCache = null;
        this._cacheUserId = null;
      }
      // 缓存未加载，或当前仍显示「去登录」空态（曾被 401 清除后重新登录）→ 重新加载课表
      if ((this._scheduleCache === null || !this.data.scheduleLoggedIn) && !this._scheduleLoading) {
        this.loadSchedule();
      }
    } else {
      // 退出登录：清空课表显示
      this._scheduleCache = null;
      this._cacheUserId = null;
      this.setData({ weekScheduleEmpty: true, scheduleLoggedIn: false, semNotice: '', semStatus: 'ongoing', swiperCourses: [[], [], [], [], [], [], []] });
    }
  },

  // 未登录空态 → 跳转「我的」页登录（教务统一认证）
  goToLogin() {
    wx.switchTab({ url: '/pages/profile/profile' });
  },

  // 生成日期导航数据，固定显示 7 天
  generateWeekDays(date, totalDays) {
    // 优先按「学期第 currentWeek 周」生成日期，确保日期导航与网格表头、周选择器完全对齐。
    // 若学期开始日期未就绪（登录前），则回退到自然周。
    totalDays = totalDays || this.data.totalDays || 7;
    const currentWeek = this.data.currentWeek || 1;
    const semStart = this._getSemesterStart();
    let monday;
    if (semStart && semStart.getTime && !isNaN(semStart.getTime())) {
      monday = new Date(semStart);
      monday.setDate(monday.getDate() + (currentWeek - 1) * 7);
    } else {
      const day = (date || new Date()).getDay() || 7;
      monday = new Date(date || new Date());
      monday.setDate(monday.getDate() - day + 1);
    }

    const weekDays = [];
    for (let i = 0; i < totalDays; i++) {
      const d = new Date(monday);
      d.setDate(monday.getDate() + i);
      const dateStr = this.formatDateStr(d);
      weekDays.push({
        date: dateStr,
        weekday: util.WEEKDAY[i + 1],
        day: d.getDate(),
        isToday: dateStr === this.formatDateStr(new Date()),
        isSelected: dateStr === this.data.selectedDate
      });
    }
    const endDate = new Date(monday.getTime() + (totalDays - 1) * 86400000);
    this.setData({ weekDays, weekLabel: `${monday.getMonth() + 1}月${monday.getDate()}日 - ${endDate.getMonth() + 1}月${endDate.getDate()}日` });
  },

  calcCurrentWeek(date) {
    // 基于后端返回的真实学期开始日期计算周次；未就绪时回退到自然周估算。
    const semStart = this._getSemesterStart();
    let diff;
    if (semStart && semStart.getTime && !isNaN(semStart.getTime())) {
      diff = Math.floor((date - semStart.getTime()) / (7 * 86400000)) + 1;
    } else {
      const year = date.getFullYear();
      const semesterStart = new Date(year, 2, 9);
      diff = Math.floor((date - semesterStart) / (7 * 86400000)) + 1;
    }
    const week = diff > 0 ? diff : 1;
    this.setData({ currentWeek: week, weekPickerIndex: week - 1 });
  },

  loadSchedule() {
    const token = wx.getStorageSync('token');
    if (!token) {
      // 未登录：不请求教务课表，显示登录引导空态（用户在「我的」页登录后自动刷新）
      this.setData({ weekScheduleEmpty: true, scheduleLoggedIn: false });
      return;
    }
    if (this._scheduleLoading) return; // 已在加载中，跳过重复请求
    this._scheduleLoading = true;
    this.setData({ scheduleLoggedIn: true });

    // 班级制教务：统一课表接口；week=0 取全部周，前端按 weeks 本地过滤以支持划周切换。
    // 不传 semester，由后端根据 is_current 自动判定当前学期，并返回其放假/开学状态。
    api.get('/schedule/student/my-with-semester', { week: 0 })
      .then(res => {
        this._scheduleLoading = false;
        // 401 被挤下线/登录过期：api.js 已清除本地 token → 显示去登录空态，不渲染演示数据
        // （后端不可达的网络故障场景 token 仍在，仍走离线演示数据）
        if (!wx.getStorageSync('token')) {
          this._scheduleCache = null;
          this._cacheUserId = null;
          this.setData({
            weekScheduleEmpty: true,
            scheduleLoggedIn: false,
            scheduleHidden: false,
            semNotice: '',
            semStatus: 'ongoing',
            swiperCourses: [[], [], [], [], [], [], []]
          });
          return;
        }
        if (!res || (!res.schedules && !res.semester)) {
          this.hideScheduleWith('none', '新学期安排待定');
          return;
        }
        const sem = res.semester || null;
        const schedules = res.schedules || [];

        // 参照 aiStudy 安卓端：无论假期/未开学，只要管理员导入了课程就正常显示课表；
        // 仅在学期与课表数据都为空时才显示空态卡片。
        if (!sem && schedules.length === 0) {
          this.hideScheduleWith('none', '新学期安排待定');
          return;
        }

        // 周次计算：用后端学期起始日 + 周数，超出学期范围（假期）时 clamp 到边界周
        const semStatus = (sem && sem.status) || 'none';
        const semWeekCount = (sem && sem.weekCount) || 18;
        let week = this.data.currentWeek;
        let outOfTerm = false; // 当前日期是否超出学期范围（假期/未开学）
        if (sem && sem.startDate && sem.weekCount) {
          const start = new Date((sem.startDate + '').replace(/-/g, '/'));
          const diff = Math.floor((Date.now() - start.getTime()) / (7 * 86400000)) + 1;
          outOfTerm = diff > sem.weekCount || diff < 1;
          week = Math.max(1, Math.min(sem.weekCount, diff));
        } else {
          outOfTerm = true;
          week = Math.max(1, Math.min(18, week));
        }

        // 未开学（before）：默认显示第一周，方便预览新学期安排
        if (outOfTerm && semStatus === 'before') {
          week = 1;
        } else if (outOfTerm && schedules.length > 0) {
          // 学期已结束（ended）：默认定位到有课程的最后一周，保证打开课表即可见课程
          let maxHasCourse = 0;
          schedules.forEach(c => {
            const ws = util.parseWeeks(c.weeks);
            if (ws) {
              ws.forEach(w => {
                if (w > maxHasCourse && w <= semWeekCount) maxHasCourse = w;
              });
            }
          });
          if (maxHasCourse > 0) week = maxHasCourse;
        }

        // 假期/未开学：不隐藏课表，仅在顶部显示提示条
        let semNotice = '';
        if (semStatus === 'before') {
          semNotice = sem.notice || '尚未开学';
        } else if (semStatus === 'ended') {
          semNotice = sem.notice || '本学期已结束';
        }
        // 缓存学期信息，供「今天」/周次切换在假期时 clamp 边界与计算周日期
        this._semWeekCount = semWeekCount;
        this._semStartDate = (sem && sem.startDate) || null;
        const weekRange = [];
        for (let i = 1; i <= this._semWeekCount; i++) weekRange.push('第' + i + '周');
        this.setData({
          scheduleHidden: false,
          semStatus,
          semNotice,
          currentWeek: week,
          weekPickerIndex: week - 1,
          weekPickerRange: weekRange
        });
        // 将扁平数组转换为 { day → [课程] }，兼容原课表渲染逻辑
        const map = {};
        for (let day = 1; day <= 7; day++) map[day] = [];
        schedules.forEach(c => {
          const d = c.dayOfWeek;
          if (d < 1 || d > 7) return;
          map[d].push({
            id: c.scheduleId || c.id,
            name: c.courseName || c.name,
            teacher: c.teacherName || c.teacher || '',
            classroom: c.classroom || '',
            startTime: c.startTime,
            endTime: c.endTime,
            weeks: c.weeks || '[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16]',
            color: util.COURSE_COLORS[d % util.COURSE_COLORS.length],
            dayOfWeek: d,
            status: 1
          });
        });
        this._scheduleCache = map;
        this._cacheUserId = (wx.getStorageSync('userInfo') || {}).userId || null;
        const totalDays = this._detectTotalDays();
        const now = new Date();
        const dayOfWeek = now.getDay() || 7;
        // 若今天是周末但当前未显示周末，则选中最后一天，避免 swiper 越界
        const selectedDayIndex = dayOfWeek > totalDays ? totalDays - 1 : dayOfWeek - 1;
        // 学期 startDate 已缓存，按学期周次重新生成日期导航（与自然周解耦）
        this.generateWeekDays(null, totalDays);
        this._processAllDayCourses(this.data.currentWeek, totalDays);
        this._processWeekOverview(this.data.currentWeek, totalDays);
        this.setData({ selectedDayIndex });
      });
  },

  loadAllDayCourses(week) {
    const currentWeek = week || this.data.currentWeek;
    if (!currentWeek) return;
    if (this._scheduleCache) {
      this._processAllDayCourses(currentWeek);
    } else {
      this.loadSchedule();
    }
  },

  // 始终显示 7 天（周一至周日）
  _detectTotalDays() {
    const swiperDaysArr = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];
    this.setData({
      hasWeekendCourses: true,
      totalDays: 7,
      swiperDays: swiperDaysArr
    });
    return 7;
  },

  // 生成每日课程 swiper 数据
  _processAllDayCourses(currentWeek, totalDays) {
    const schedule = this._scheduleCache || {};
    const swiperCourses = [];
    totalDays = totalDays || this.data.totalDays || 7;
    for (let day = 1; day <= totalDays; day++) {
      const dayCourses = schedule[day] || [];
      const filtered = dayCourses.filter(c => {
        const weeks = util.parseWeeks(c.weeks);
        if (weeks === null) return true;
        return weeks.includes(currentWeek);
      });
      swiperCourses.push(filtered.map(c => ({
        ...c,
        startTimeText: util.timeToString(c.startTime),
        endTimeText: util.timeToString(c.endTime),
        weeksText: c.weeks ? util.formatWeeksDisplay(c.weeks) : ''
      })));
    }
    this.setData({ swiperCourses }, () => {
      this.updateSwiperHeight();
    });
  },

  // 根据当前页课程数自适应 swiper 高度
  updateSwiperHeight() {
    const idx = this.data.selectedDayIndex;
    const courses = this.data.swiperCourses[idx] || [];
    // 每个课程卡片约 132rpx + 标题 60rpx + 底部间距 20rpx；无课时保留足够高度显示空状态
    const height = courses.length > 0 ? (60 + courses.length * 132 + 20) : 260;
    this.setData({ swiperHeight: height });
  },

  loadWeekOverview(week) {
    const currentWeek = week || this.data.currentWeek;
    if (!currentWeek) return;
    if (this._scheduleCache) {
      this._processWeekOverview(currentWeek);
    } else {
      this.loadSchedule();
    }
  },

  // ==================== 作息时间表（参照 aiStudy 安卓端） ====================
  // 标准大节作息：按本校节次时间（数据库节1/3/5/7），每大节含休息分隔
  _SLOT_ROWS: [
    { start: '08:10', end: '09:40' },            // 第一大节（1-2节）
    { start: '10:00', end: '11:30' },            // 第二大节（3-4节）
    { isBreak: true, text: '午餐 · 午休 11:30 — 14:00' },
    { start: '14:00', end: '15:30' },            // 第三大节（5-6节）
    { start: '15:40', end: '17:10' },            // 第四大节（7-8节）
    { isBreak: true, text: '晚餐 · 晚休 17:10 — 19:00' },
    { start: '19:00', end: '20:30' }             // 第五大节（9-10节）
  ],

  // 课程卡片柔和色板（12色，与 aiStudy 安卓端一致），按课程名稳定分配
  _COURSE_COLORS: [
    '#E8F0FE', '#E8F8ED', '#FFF3E0', '#F3E8FF', '#FCE4EC', '#E0F7FA',
    '#F9F0E0', '#EDE7F6', '#E8EAF6', '#FBE9E7', '#E0F2F1', '#FFF8E1'
  ],

  // 课程名 → 色板下标（字符码求和取模，同名课程颜色稳定）
  _colorOf(name) {
    let hash = 0;
    const s = name || '';
    for (let i = 0; i < s.length; i++) hash = (hash * 31 + s.charCodeAt(i)) >>> 0;
    return this._COURSE_COLORS[hash % this._COURSE_COLORS.length];
  },

  // 生成本周课表网格数据（参照 aiStudy：标准作息大节 + 午/晚餐分隔 + 课程色块）
  _processWeekOverview(currentWeek, totalDays) {
    const schedule = this._scheduleCache || {};
    totalDays = totalDays || this.data.totalDays || 7;

    // 课程归入标准大节（按上课时间区间匹配），未匹配的课后置追加
    const extraSlots = {};
    const rows = this._SLOT_ROWS.map(slot => {
      const row = { isBreak: !!slot.isBreak };
      if (slot.isBreak) {
        row.breakText = slot.text;
        return row;
      }
      row.startTime = slot.start;
      row.endTime = slot.end;
      for (let d = 1; d <= 7; d++) row['day' + d] = [];
      return row;
    });

    for (let day = 1; day <= 7; day++) {
      const dayCourses = schedule[day] || [];
      dayCourses.forEach(c => {
        const weeks = util.parseWeeks(c.weeks);
        const inWeek = weeks && weeks.includes(currentWeek);
        if (!inWeek) return;
        const start = util.timeToString(c.startTime) || '';
        const end = util.timeToString(c.endTime) || '';
        if (!start || !end) return;
        const item = {
          id: c.id,
          name: c.name,
          classroom: c.classroom || '',
          bgColor: this._colorOf(c.name)
        };
        // 匹配标准大节：上课时间落在 [slot.start, slot.end) 区间
        const toMin = t => { const [h, m] = t.split(':').map(Number); return h * 60 + m; };
        const startMin = toMin(start);
        const slotRow = rows.find(r => !r.isBreak && startMin >= toMin(r.startTime) && startMin < toMin(r.endTime));
        let cell;
        if (slotRow) {
          cell = slotRow['day' + day];
        } else {
          // 非标准时段：追加动态行
          const key = start + '-' + end;
          if (!extraSlots[key]) {
            extraSlots[key] = { startTime: start, endTime: end };
            for (let d = 1; d <= 7; d++) extraSlots[key]['day' + d] = [];
          }
          cell = extraSlots[key]['day' + day];
        }
        cell.push(item);
        // 标记同一时间同一格子存在多门课（数据冲突）
        cell.conflict = cell.length > 1;
      });
    }

    // 非标准时段行按时间排序后追加到晚上大节之后
    const extraRows = Object.values(extraSlots).sort((a, b) => a.startTime.localeCompare(b.startTime));

    // 全周（当前显示天范围内）完全无课 → 空态；行过滤：完全没课的大节行压缩不显示
    let hasAnyCourse = false;
    for (let day = 1; day <= totalDays; day++) {
      for (const r of rows) { if (!r.isBreak && r['day' + day] && r['day' + day].length > 0) { hasAnyCourse = true; break; } }
      if (hasAnyCourse) break;
    }
    const weekScheduleEmpty = !hasAnyCourse;

    const courseRows = rows.filter(r => r.isBreak || r.day1.length || r.day2.length || r.day3.length
      || r.day4.length || r.day5.length || r.day6.length || r.day7.length);
    const filteredExtras = extraRows.filter(r => r.day1.length || r.day2.length || r.day3.length
      || r.day4.length || r.day5.length || r.day6.length || r.day7.length);
    let weekGrid = courseRows.concat(filteredExtras);

    // 去掉首尾的分隔行（无课大节被压缩后可能残留）
    while (weekGrid.length && weekGrid[0].isBreak) weekGrid.shift();
    while (weekGrid.length && weekGrid[weekGrid.length - 1].isBreak) weekGrid.pop();
    // 去掉连续重复的分隔行
    weekGrid = weekGrid.filter((r, i) => !r.isBreak || !(i > 0 && weekGrid[i - 1].isBreak));
    // 稳定 key 供 wxml 渲染
    weekGrid.forEach((r, i) => { r._key = i; });

    // 本周周一日期 & 今天列
    const allDayNames = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日'];
    const now = new Date();
    const todayStr = this.formatDateStr(now);
    const monday = new Date(this._getSemesterStart());
    monday.setDate(monday.getDate() + (currentWeek - 1) * 7);
    let todayDayIndex = -1;
    const dayDateList = [];
    for (let i = 1; i <= 7; i++) {
      const d = new Date(monday);
      d.setDate(monday.getDate() + (i - 1));
      dayDateList.push((d.getMonth() + 1) + '/' + d.getDate());
      if (this.formatDateStr(d) === todayStr) todayDayIndex = i;
    }

    // 表头：周几 + 日期 + 今天标记
    const weekGridDays = allDayNames.slice(1, totalDays + 1).map((name, idx) => ({
      name,
      date: dayDateList[idx],
      isToday: (idx + 1) === todayDayIndex
    }));
    const gridCols = [];
    for (let di = 1; di <= totalDays; di++) gridCols.push(di);

    // 标记当前时间段行（仅当今天在当前周时，参照 aiStudy 高亮当前大节）
    const nowMinutes = now.getHours() * 60 + now.getMinutes();
    weekGrid.forEach(row => {
      if (row.isBreak) return;
      const [startH, startM] = row.startTime.split(':').map(Number);
      const [endH, endM] = row.endTime.split(':').map(Number);
      row.isCurrentTime = todayDayIndex !== -1
        && nowMinutes >= startH * 60 + startM && nowMinutes < endH * 60 + endM;
    });

    this.setData({ weekGrid, weekGridDays, gridCols, todayCol: todayDayIndex, weekScheduleEmpty });
  },





  checkCurrentSession() {
    api.get('/sessions/current')
      .then(res => {
        if (res.code === 200 && res.data) {
          const session = res.data;
          // 获取课程名
          api.get(`/courses/${session.courseId}`)
            .then(courseRes => {
              if (courseRes.code === 200) {
                session.courseName = courseRes.data.name;
                session.classroom = courseRes.data.classroom;
                this.setData({ currentSession: session });
              }
            });
        } else {
          this.setData({ currentSession: null });
        }
      });
  },

  selectDate(e) {
    const idx = parseInt(e.currentTarget.dataset.index);
    const date = e.currentTarget.dataset.date;
    const weekDays = this.data.weekDays.map(d => ({
      ...d,
      isSelected: d.date === date
    }));
    this.setData({ selectedDate: date, weekDays, selectedDayIndex: idx }, () => {
      this.updateSwiperHeight();
    });
  },

  onSwiperChange(e) {
    const idx = e.detail.current;
    const date = this.data.weekDays[idx] ? this.data.weekDays[idx].date : '';
    const weekDays = this.data.weekDays.map(d => ({
      ...d,
      isSelected: d.date === date
    }));
    this.setData({ selectedDayIndex: idx, selectedDate: date, weekDays }, () => {
      this.updateSwiperHeight();
    });
    this._touchStartX = null;
  },

  onSwiperTouchStart(e) {
    this._touchStartX = e.touches[0].clientX;
  },

  onSwiperTouchEnd(e) {
    if (this._touchStartX == null) return;
    var deltaX = this._touchStartX - e.changedTouches[0].clientX;
    var idx = this.data.selectedDayIndex;
    var totalDays = this.data.totalDays || 7;
    // 手指向左滑动 > 50px → 切换到下一天(周)
    if (deltaX > 50) {
      if (idx === (totalDays - 1)) { // 最后一天往左滑 → 下一周周一
        this.jumpToWeek(this.data.currentWeek + 1);
      }
    // 手指向右滑动 > 50px → 切换到上一天(周)
    } else if (deltaX < -50) {
      if (idx === 0) { // 第一天往右滑 → 上一周最后一天
        this.jumpToWeek(this.data.currentWeek - 1, totalDays - 1);
      }
    }
    this._touchStartX = null;
  },

  // 获取学期起始周一（优先用后端返回的真实学期起始日，兜底 3月9日）
  _getSemesterStart() {
    if (this._semStartDate) {
      return new Date((this._semStartDate + '').replace(/-/g, '/'));
    }
    const year = new Date().getFullYear();
    return new Date(year, 2, 9);
  },

  jumpToWeek(week, dayIndex) {
    const maxWeek = this._semWeekCount || 18;
    if (week < 1 || week > maxWeek) return;
    const semStart = this._getSemesterStart();
    const monday = new Date(semStart);
    monday.setDate(monday.getDate() + (week - 1) * 7);
    dayIndex = dayIndex || 0; // 默认跳到周一
    const targetDate = new Date(monday);
    targetDate.setDate(monday.getDate() + dayIndex);
    this.setData({
      currentWeek: week,
      weekPickerIndex: week - 1,
      selectedDate: this.formatDateStr(targetDate),
      selectedDayIndex: dayIndex
    });
    // currentWeek 已更新，generateWeekDays 会按学期周次生成日期
    this.generateWeekDays(null, this.data.totalDays);
    this._refreshAll(week);
  },

  onWeekPickerChange(e) {
    const week = parseInt(e.detail.value) + 1;
    const semStart = this._getSemesterStart();
    const monday = new Date(semStart);
    monday.setDate(monday.getDate() + (week - 1) * 7);
    const mondayDate = this.formatDateStr(monday);
    this.setData({ currentWeek: week, weekPickerIndex: week - 1, selectedDate: mondayDate, selectedDayIndex: 0 });
    this.generateWeekDays(null, this.data.totalDays);
    this._refreshAll(week);
  },

  goToToday() {
    const now = new Date();
    const dayOfWeek = now.getDay() || 7;
    const totalDays = this.data.totalDays || 5;
    const today = this.formatDateStr(now);
    const semStart = this._getSemesterStart();
    const week = Math.floor((now - semStart.getTime()) / (7 * 86400000)) + 1;
    // 假期超出学期周数时 clamp 到边界周（与 aiStudy 一致），保证课表仍可查看
    const maxWeek = this._semWeekCount || 18;
    const currentWeek = Math.max(1, Math.min(maxWeek, week));
    // 若今天是周末但课表未显示周末，则选中最后一天
    const selectedDayIndex = Math.min(dayOfWeek - 1, totalDays - 1);
    this.setData({
      selectedDate: today,
      selectedDayIndex,
      currentWeek,
      weekPickerIndex: currentWeek - 1
    });
    this.generateWeekDays(null, totalDays);
    this._refreshAll(currentWeek);
  },

  _refreshAll(week) {
    const w = week || this.data.currentWeek;
    this.loadWeekOverview(w);
    this.loadAllDayCourses(w);
  },

  // 非开学/已结束/无本学期：隐藏课表网格，居中显示状态提示
  hideScheduleWith(status, notice) {
    this._scheduleCache = null;
    this.setData({
      scheduleHidden: true,
      semStatus: status,
      semNotice: notice,
      currentWeek: 1,
      weekPickerIndex: 0,
      swiperCourses: [[], [], [], [], [], [], []],
      weekGrid: [],
      weekGridDays: [],
      gridCols: [],
      todayCol: -1,
      weekScheduleEmpty: true,
      selectedDayIndex: 0
    });
  },

    goToAddCourse() {
    wx.navigateTo({ url: '/pages/course/course?mode=add' });
  },

  // 调试：注入测试网格数据
  testGridRender() {
    const mockGrid = [
      { startTime: '08:10', endTime: '09:40',
        day1: [{ id: 9991, name: '测试课程A', classroom: 'A101', bgColor: this._colorOf('测试课程A') }],
        day2: [], day3: [{ id: 9994, name: '测试课程D', classroom: 'C301', bgColor: this._colorOf('测试课程D') }],
        day4: [], day5: [], day6: [], day7: [] },
      { startTime: '10:00', endTime: '11:30',
        day1: [], day2: [{ id: 9992, name: '测试课程B', classroom: 'B202', bgColor: this._colorOf('测试课程B') }],
        day3: [], day4: [{ id: 9995, name: '测试课程E', classroom: 'D102', bgColor: this._colorOf('测试课程E') }],
        day5: [], day6: [], day7: [] },
      { isBreak: true, breakText: '午餐 · 午休 11:30 — 14:00' },
      { startTime: '14:00', endTime: '15:30',
        day1: [], day2: [{ id: 9993, name: '测试课程C', classroom: 'C303', bgColor: this._colorOf('测试课程C') }],
        day3: [], day4: [], day5: [{ id: 9996, name: '测试课程F', classroom: 'E404', bgColor: this._colorOf('测试课程F') }],
        day6: [], day7: [] }
    ];
    const weekGridDays = [
      { name: '周一', date: '8/24', isToday: false },
      { name: '周二', date: '8/25', isToday: false },
      { name: '周三', date: '8/26', isToday: false },
      { name: '周四', date: '8/27', isToday: false },
      { name: '周五', date: '8/28', isToday: false }
    ];
    this.setData({ weekGrid: mockGrid, weekGridDays, todayCol: -1, gridCols: [1, 2, 3, 4, 5], weekScheduleEmpty: false });
    wx.showToast({ title: '已注入测试数据', icon: 'none' });
  },

  goToCurrentSession() {
    if (this.data.currentSession) {
      wx.navigateTo({
        url: `/pages/session/session?sessionId=${this.data.currentSession.id}&courseId=${this.data.currentSession.courseId}`
      });
    }
  },

  goToPhoto() {
    const session = this.data.currentSession;
    if (session) {
      wx.navigateTo({
        url: `/pages/photo/photo?sessionId=${session.id}&courseId=${session.courseId}`
      });
    }
  },

  goToNote() {
    const session = this.data.currentSession;
    if (session) {
      wx.navigateTo({
        url: `/pages/note/note?sessionId=${session.id}&courseId=${session.courseId}`
      });
    }
  },

  endSession() {
    const session = this.data.currentSession;
    if (!session) return;

    wx.showModal({
      title: '下课确认',
      content: '确定结束本节课段吗？系统将自动整理本课段所有资料。',
      success: (res) => {
        if (res.confirm) {
          api.post(`/sessions/end/${session.id}`)
            .then(res => {
              wx.showToast({ title: '下课成功', icon: 'success' });
              this.setData({ currentSession: null });
            });
        }
      }
    });
  },

  onGridCellTap(e) {
    const day = e.currentTarget.dataset.day;
    if (day >= 1 && day <= 7) {
      this.setData({ selectedDayIndex: day - 1 });
    }
  },

  formatDateStr(date) {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }
});
