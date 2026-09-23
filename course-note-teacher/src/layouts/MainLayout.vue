<template>
  <div class="main-layout">
    <!-- 侧边栏 — 极简白色，无多余阴影 -->
    <aside class="sidebar" :class="{ collapsed }">
      <div class="sidebar-inner">
        <!-- Logo -->
        <div class="logo-section" @click="collapsed = !collapsed">
          <div class="logo-icon">
            <svg width="24" height="24" viewBox="0 0 32 32" fill="none">
              <rect width="32" height="32" rx="8" fill="#1D1D1F"/>
              <path d="M10 16L14 20L22 12" stroke="white" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
          <span v-show="!collapsed" class="logo-text">课堂助手</span>
        </div>

        <!-- 导航菜单 -->
        <nav class="nav-menu">
          <div
            v-for="group in navGroups"
            :key="group.title"
            class="nav-group"
          >
            <div v-show="!collapsed" class="nav-group-title">{{ group.title }}</div>
            <div
              v-for="item in group.items"
              :key="item.path"
              class="nav-item"
              :class="{ active: activeMenu === item.path }"
              @click="handleNav(item.path)"
            >
              <div class="nav-icon" v-html="item.icon"></div>
              <span v-show="!collapsed" class="nav-label">{{ item.label }}</span>
            </div>
          </div>
        </nav>

      </div>
    </aside>

    <!-- 主内容区 -->
    <div class="content-area">
      <!-- 顶部导航栏 — 白色半透明磨砂毛玻璃效果 -->
      <header class="top-navbar">
        <div class="navbar-left">
          <button class="menu-toggle" @click="collapsed = !collapsed">
            <svg width="18" height="18" viewBox="0 0 18 18" fill="none">
              <path d="M3 5H15" stroke="#86868B" stroke-width="1.5" stroke-linecap="round"/>
              <path d="M3 9H15" stroke="#86868B" stroke-width="1.5" stroke-linecap="round"/>
              <path d="M3 13H15" stroke="#86868B" stroke-width="1.5" stroke-linecap="round"/>
            </svg>
          </button>
          <span class="navbar-title">{{ pageTitle }}</span>
        </div>
        <div class="navbar-right">
          <div class="user-area" @click.stop="showDropdown = !showDropdown">
            <span class="user-name">{{ userStore.userInfo?.realName || '教师' }}</span>
            <div class="user-avatar">{{ (userStore.userInfo?.realName || '教').charAt(0) }}</div>
            <div class="user-dropdown" v-if="showDropdown" @click.stop>
              <div class="dropdown-item" @click="handleLogout">
                <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                  <path d="M6 14H3C2.4 14 2 13.6 2 13V3C2 2.4 2.4 2 3 2H6" stroke="#86868B" stroke-width="1.2" stroke-linecap="round"/>
                  <path d="M11 11L14 8L11 5" stroke="#86868B" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
                  <path d="M14 8H6" stroke="#86868B" stroke-width="1.2" stroke-linecap="round"/>
                </svg>
                退出登录
              </div>
            </div>
          </div>
        </div>
      </header>

      <!-- 内容 -->
      <main class="main-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const collapsed = ref(false)
const activeMenu = ref('/dashboard')
const showDropdown = ref(false)

const pageTitle = computed(() => route.meta.title || '课堂助手')

// 角色判断工具
function hasRole(r) {
  return userStore.roles.map(x => String(x).toUpperCase()).includes(String(r).toUpperCase())
}
// 按角色过滤菜单项：未声明 roles 的项对所有已登录角色可见
function visible(items) {
  return items.filter(it => !it.roles || it.roles.some(hasRole))
}

const navGroups = computed(() => {
  const groups = [
    {
      title: '概览',
      items: [
        {
          path: '/dashboard',
          label: '仪表盘',
          icon: '<svg width="20" height="20" viewBox="0 0 20 20" fill="none"><rect x="2" y="2" width="7" height="7" rx="1.5" stroke="currentColor" stroke-width="1.3"/><rect x="11" y="2" width="7" height="4" rx="1.5" stroke="currentColor" stroke-width="1.3"/><rect x="2" y="11" width="7" height="7" rx="1.5" stroke="currentColor" stroke-width="1.3"/><rect x="11" y="8" width="7" height="10" rx="1.5" stroke="currentColor" stroke-width="1.3"/></svg>'
        },
        {
          path: '/statistics',
          label: '数据统计',
          icon: '<svg width="20" height="20" viewBox="0 0 20 20" fill="none"><path d="M4 14V10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/><path d="M8 14V6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/><path d="M12 14V8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/><path d="M16 14V4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>'
        }
      ]
    },
    {
      title: '教学',
      items: [
        {
          path: '/courses',
          label: '课程管理',
          icon: '<svg width="20" height="20" viewBox="0 0 20 20" fill="none"><path d="M4 6H16" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/><path d="M4 10H16" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/><path d="M4 14H16" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>'
        },
        {
          path: '/schedule',
          label: '课表管理',
          icon: '<svg width="20" height="20" viewBox="0 0 20 20" fill="none"><rect x="2" y="3" width="16" height="14" rx="2" stroke="currentColor" stroke-width="1.3"/><path d="M2 7H18" stroke="currentColor" stroke-width="1.3"/><path d="M7 2V5" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/><path d="M13 2V5" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/><path d="M6 10L8 12L12 9" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/></svg>'
        },
        {
          path: '/admin/schedule',
          label: '排课管理',
          roles: ['ADMIN', 'TEACHER'],
          icon: '<svg width="20" height="20" viewBox="0 0 20 20" fill="none"><rect x="2" y="3" width="16" height="14" rx="2" stroke="currentColor" stroke-width="1.3"/><path d="M2 7H18" stroke="currentColor" stroke-width="1.3"/><path d="M9 12L11 14L15 9" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/></svg>'
        },
        {
          path: '/admin/semester',
          label: '学期管理',
          roles: ['ADMIN'],
          icon: '<svg width="20" height="20" viewBox="0 0 20 20" fill="none"><circle cx="10" cy="10" r="8" stroke="currentColor" stroke-width="1.3"/><path d="M10 6V10L13 12" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/></svg>'
        },
        {
          path: '/admin/course-import',
          label: '课程导入',
          roles: ['ADMIN'],
          icon: '<svg width="20" height="20" viewBox="0 0 20 20" fill="none"><path d="M6 14V12" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/><path d="M14 14V12" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/><path d="M4 7H16" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/><path d="M10 8V3M8 5L10 3L12 5" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/></svg>'
        }
      ]
    },
    {
      title: '资源',
      items: [
        {
          path: '/materials',
          label: '资料管理',
          icon: '<svg width="20" height="20" viewBox="0 0 20 20" fill="none"><path d="M3 6C3 5 4 4 5 4H9L11 6H17C18 6 19 7 19 8V16C19 17 18 18 17 18H5C4 18 3 17 3 16V6Z" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/></svg>'
        },
        {
          path: '/knowledge',
          label: '知识库管理',
          icon: '<svg width="20" height="20" viewBox="0 0 20 20" fill="none"><path d="M3 5C3 4 4 3 5 3H15C16 3 17 4 17 5V15C17 16 16 17 15 17H5C4 17 3 16 3 15V5Z" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/><path d="M7 7H13" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/><path d="M7 11H13" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/></svg>'
        },
        {
          path: '/exam-homework',
          label: '考试作业',
          roles: ['ADMIN', 'TEACHER'],
          icon: '<svg width="20" height="20" viewBox="0 0 20 20" fill="none"><rect x="3" y="3" width="14" height="14" rx="2" stroke="currentColor" stroke-width="1.3"/><path d="M7 8L9 10L13 6" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/></svg>'
        }
      ]
    },
    {
      title: '学生',
      items: [
        {
          path: '/students',
          label: '学生管理',
          roles: ['ADMIN', 'TEACHER'],
          icon: '<svg width="20" height="20" viewBox="0 0 20 20" fill="none"><circle cx="10" cy="6" r="3.5" stroke="currentColor" stroke-width="1.3"/><path d="M3 18C3 14.1 6.1 11 10 11C13.9 11 17 14.1 17 18" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/></svg>'
        }
      ]
    },
    {
      title: '系统',
      items: [
        {
          path: '/settings',
          label: '系统设置',
          icon: '<svg width="20" height="20" viewBox="0 0 20 20" fill="none"><circle cx="10" cy="10" r="3" stroke="currentColor" stroke-width="1.5"/><path d="M10 2V5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/><path d="M10 15V18" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/><path d="M2.5 10H5.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/><path d="M14.5 10H17.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/><path d="M4.5 4.5L6.5 6.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/><path d="M13.5 13.5L15.5 15.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/><path d="M4.5 15.5L6.5 13.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/><path d="M13.5 6.5L15.5 4.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>'
        }
      ]
    }
  ]
  return groups
    .map(g => ({ ...g, items: visible(g.items) }))
    .filter(g => g.items.length)
})


watch(() => route.path, (path) => {
  activeMenu.value = path.startsWith('/students') ? '/students' : path
}, { immediate: true })

function handleNav(path) {
  router.push(path)
}

function handleLogout() {
  userStore.logout()
  router.push('/login')
}

// 点击外部关闭下拉
function onClickOutside(e) {
  if (!e.target.closest('.user-area')) {
    showDropdown.value = false
  }
}

watch(showDropdown, (val) => {
  if (val) {
    document.addEventListener('click', onClickOutside)
  } else {
    document.removeEventListener('click', onClickOutside)
  }
})
</script>

<style scoped>
.main-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: #FAFAFA;
}

/* ========== 侧边栏 ========== */
.sidebar {
  width: 220px;
  flex-shrink: 0;
  background: #FFFFFF;
  border-right: 1px solid rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  transition: width 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
  z-index: 20;
}

.sidebar.collapsed {
  width: 68px;
}

.sidebar-inner {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 0 12px;
}

/* Logo */
.logo-section {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 12px;
  cursor: pointer;
  user-select: none;
  transition: opacity 0.2s;
}
.logo-section:hover {
  opacity: 0.7;
}

.logo-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.logo-text {
  font-size: 17px;
  font-weight: 600;
  color: #1D1D1F;
  letter-spacing: -0.02em;
  white-space: nowrap;
}

/* 导航菜单 */
.nav-menu {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 4px 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.nav-group-title {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #B0B0B8;
  padding: 0 12px;
  margin-bottom: 4px;
  white-space: nowrap;
}


.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  color: #86868B;
  white-space: nowrap;
}

.nav-item:hover {
  background: #F5F5F7;
  color: #1D1D1F;
}

.nav-item.active {
  background: rgba(0, 113, 227, 0.06);
  color: #0071E3;
}

.nav-icon {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.nav-label {
  font-size: 14px;
  font-weight: 500;
  letter-spacing: -0.01em;
}

/* ========== 主内容区 ========== */
.content-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

/* ========== 顶部导航栏 — 白色半透明磨砂毛玻璃效果 ========== */
.top-navbar {
  height: 56px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: saturate(180%) blur(20px);
  -webkit-backdrop-filter: saturate(180%) blur(20px);
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
  z-index: 10;
}

.navbar-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.menu-toggle {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
  padding: 0;
  color: inherit;
}
.menu-toggle:hover {
  background: #F5F5F7;
}

.navbar-title {
  font-size: 15px;
  font-weight: 600;
  color: #1D1D1F;
  letter-spacing: -0.01em;
}

/* 右侧用户 */
.navbar-right {
  display: flex;
  align-items: center;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px 4px 14px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.2s;
  position: relative;
}

.user-area:hover {
  background: #F5F5F7;
}

.user-name {
  font-size: 13px;
  color: #86868B;
  font-weight: 400;
  letter-spacing: -0.01em;
}

.user-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #F0F0F2;
  color: #86868B;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 500;
}

.user-dropdown {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 6px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: saturate(180%) blur(20px);
  -webkit-backdrop-filter: saturate(180%) blur(20px);
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 12px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
  min-width: 140px;
  z-index: 100;
  overflow: hidden;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  font-size: 14px;
  color: #1D1D1F;
  cursor: pointer;
  transition: background 0.15s;
  font-weight: 500;
}
.dropdown-item:hover {
  background: #F5F5F7;
}

/* ========== 主内容 ========== */
.main-content {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  background: #FAFAFA;
}

/* ========== 响应式：移动端隐藏侧边栏 ========== */
@media (max-width: 768px) {
  .sidebar {
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 100;
    box-shadow: 4px 0 24px rgba(0,0,0,0.08);
  }
  .sidebar.collapsed {
    transform: translateX(-100%);
    width: 220px;
  }
  .main-layout {
    position: relative;
  }
  .top-navbar {
    padding: 0 16px;
  }
}
</style>
