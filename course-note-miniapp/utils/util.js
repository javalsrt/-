/**
 * 工具函数集合
 */

// 星期映射
const WEEKDAY = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日'];

// 颜色列表
const COURSE_COLORS = [
  '#A84A43', '#E8833A', '#6BCB77', '#2B7BC1',
  '#C1282D', '#845EC2', '#D4A04A', '#00C9A7',
  '#FF9671', '#0081B7', '#D65DB1', '#FF6F91'
];

// 获取星期几
function getWeekDay(day) {
  return WEEKDAY[day] || '';
}

// 格式化时间
function formatTime(date) {
  const d = new Date(date);
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  const hour = String(d.getHours()).padStart(2, '0');
  const minute = String(d.getMinutes()).padStart(2, '0');
  return `${year}-${month}-${day} ${hour}:${minute}`;
}

// 格式化日期
function formatDate(date) {
  const d = new Date(date);
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${month}月${day}日`;
}

// 获取当前学期
function getCurrentSemester() {
  const now = new Date();
  const year = now.getFullYear();
  const month = now.getMonth() + 1;
  if (month >= 2 && month <= 7) {
    return `${year - 1}-${year}-2`;
  } else {
    return month >= 8 ? `${year}-${year + 1}-1` : `${year - 1}-${year}-1`;
  }
}

// 资料类型转中文
function getMaterialTypeText(type) {
  const map = {
    'PHOTO': '拍照',
    'NOTE': '笔记',
    'THIRD_PARTY': '导入'
  };
  return map[type] || type;
}

// 资料类型样式类
function getMaterialTypeClass(type) {
  const map = {
    'PHOTO': 'tag-photo',
    'NOTE': 'tag-note',
    'THIRD_PARTY': 'tag-external'
  };
  return map[type] || '';
}

// 节次时间配置（南宁师范大学武鸣校区作息时间表 2019年11月启用）
const PERIOD_TIMES = {
  1: { start: '08:30', end: '09:10' },   // 第1节
  2: { start: '09:15', end: '09:55' },   // 第2节
  3: { start: '10:10', end: '10:50' },   // 第3节
  4: { start: '10:55', end: '11:35' },   // 第4节
  5: { start: '11:40', end: '12:20' },   // 第5节
  6: { start: '14:20', end: '15:00' },   // 第6节
  7: { start: '15:05', end: '15:45' },   // 第7节
  8: { start: '16:00', end: '16:40' },   // 第8节
  9: { start: '16:45', end: '17:25' },   // 第9节
  10: { start: '19:10', end: '19:50' },  // 第10节
  11: { start: '20:00', end: '20:40' },  // 第11节
  12: { start: '20:50', end: '21:30' }   // 第12节
};

/**
 * 解析周次字段，返回周次数字数组
 * 支持格式：
 *   - 范围字符串: "1-16" → [1,2,...,16]
 *   - JSON数组字符串: "[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16]" → [1,2,...,16]
 *   - 单周字符串: "11" → [11]
 *   - 空/无效: 返回 null
 */
function parseWeeks(weeksStr) {
  if (!weeksStr) return null;
  // 尝试 JSON.parse（格式为 "[1,2,3...]"）
  try {
    const parsed = JSON.parse(weeksStr);
    if (Array.isArray(parsed)) return parsed;
  } catch { /* 不是JSON格式，继续尝试范围解析 */ }
  // 尝试范围格式 "1-16" 或 "11"
  if (typeof weeksStr === 'string') {
    const trimmed = weeksStr.trim();
    if (/^\d+-\d+$/.test(trimmed)) {
      const [start, end] = trimmed.split('-').map(Number);
      if (!isNaN(start) && !isNaN(end) && start <= end) {
        const weeks = [];
        for (let w = start; w <= end; w++) weeks.push(w);
        return weeks;
      }
    }
    // 单个数字如 "11"
    if (/^\d+$/.test(trimmed)) {
      const num = parseInt(trimmed, 10);
      if (!isNaN(num)) return [num];
    }
  }
  return null;
}

// 时间转字符串
function timeToString(time) {
  if (!time) return '';
  if (typeof time === 'string') return time.substring(0, 5);
  return time;
}

/**
 * 格式化周次显示（将 JSON 数组转为 "1-16" 范围格式）
 * 输入: "[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16]" 或 "[11]"
 * 输出: "1-16" 或 "11"
 */
function formatWeeksDisplay(weeksStr) {
  if (!weeksStr) return '';
  try {
    const weeks = JSON.parse(weeksStr);
    if (!Array.isArray(weeks) || weeks.length === 0) return '';
    const sorted = [...weeks].sort((a, b) => a - b);
    if (sorted.length === 1) return String(sorted[0]);
    return sorted[0] + '-' + sorted[sorted.length - 1];
  } catch {
    return weeksStr;
  }
}

module.exports = {
  WEEKDAY,
  COURSE_COLORS,
  PERIOD_TIMES,
  getWeekDay,
  formatTime,
  formatDate,
  getCurrentSemester,
  formatWeeksDisplay,
  getMaterialTypeText,
  getMaterialTypeClass,
  timeToString,
  parseWeeks
};
