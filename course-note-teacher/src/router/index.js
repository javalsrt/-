import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/request'

function hasAnyRole(roles) {
  if (!roles || !roles.length) return true // 未限定角色 = 任何已登录角色可访问
  const mine = JSON.parse(localStorage.getItem('roles') || '[]').map(r => String(r).toUpperCase())
  return roles.some(r => mine.includes(r))
}

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '教务平台登录' }
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '仪表盘', icon: 'dashboard' }
      },
      {
        path: 'statistics',
        name: 'Statistics',
        component: () => import('@/views/Statistics.vue'),
        meta: { title: '数据统计', icon: 'chart' }
      },
      {
        path: 'courses',
        name: 'CourseList',
        component: () => import('@/views/CourseList.vue'),
        meta: { title: '课程管理', icon: 'list' }
      },
      {
        path: 'schedule',
        name: 'Schedule',
        component: () => import('@/views/Schedule.vue'),
        meta: { title: '课表管理', icon: 'calendar' }
      },
      {
        path: 'materials',
        name: 'Materials',
        component: () => import('@/views/Materials.vue'),
        meta: { title: '资料管理', icon: 'folder' }
      },
      {
        path: 'knowledge',
        name: 'Knowledge',
        component: () => import('@/views/Knowledge.vue'),
        meta: { title: '知识库管理', icon: 'book' }
      },
      {
        path: 'students',
        name: 'Students',
        component: () => import('@/views/Students.vue'),
        meta: { title: '学生管理', icon: 'user', roles: ['ADMIN', 'TEACHER'] }
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/Settings.vue'),
        meta: { title: '系统设置', icon: 'setting' }
      },
      {
        path: 'students/:id',
        name: 'StudentDetail',
        component: () => import('@/views/StudentDetail.vue'),
        meta: { title: '学生详情', hidden: true, roles: ['ADMIN', 'TEACHER'] }
      },

      // ===== React 端迁入的班级制教务功能 =====
      {
        path: 'admin/semester',
        name: 'SemesterManage',
        component: () => import('@/views/admin/Semester.vue'),
        meta: { title: '学期管理', icon: 'semester', roles: ['ADMIN'] }
      },
      {
        path: 'admin/schedule',
        name: 'ScheduleAdmin',
        component: () => import('@/views/admin/ScheduleAdmin.vue'),
        meta: { title: '排课管理', icon: 'schedule', roles: ['ADMIN', 'TEACHER'] }
      },
      {
        path: 'admin/course-import',
        name: 'CourseImport',
        component: () => import('@/views/admin/CourseImport.vue'),
        meta: { title: '课程导入', icon: 'import', roles: ['ADMIN'] }
      },
      {
        path: 'exam-homework',
        name: 'ExamHomework',
        component: () => import('@/views/teaching/ExamHomework.vue'),
        meta: { title: '考试作业', icon: 'exam', roles: ['ADMIN', 'TEACHER'] }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：登录态 + 角色
router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - 课堂助手` : '课堂助手'

  // 在线答题页需登录但任何角色均可
  if (!getToken()) {
    if (to.path !== '/login') return next('/login')
    return next()
  }
  // 已登录访问登录页 → 回首页
  if (to.path === '/login') return next('/dashboard')

  // 角色校验
  if (to.meta.roles && !hasAnyRole(to.meta.roles)) {
    return next('/dashboard')
  }
  next()
})

export default router