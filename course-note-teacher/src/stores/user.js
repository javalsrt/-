import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '@/utils/request'

// RBAC 角色编码 → 标准角色（与后端 /auth/login 返回的 roles 对应）
const roleMap = { 1: 'student', 2: 'teacher', 3: 'admin' }

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || localStorage.getItem('teacher_token') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('user') || localStorage.getItem('teacher_info') || 'null'))
  const roles = ref(JSON.parse(localStorage.getItem('roles') || '[]'))
  const permissions = ref(JSON.parse(localStorage.getItem('permissions') || '[]'))

  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => roles.value.includes('ADMIN'))
  const isTeacher = computed(() => roles.value.includes('TEACHER'))

  // /auth/login → 裸数据体 { token, role, username, realName, userId, roles, permissions, ... }
  async function login(username, password) {
    const data = await request.post('/auth/login', { username, password })
    const role =
      (data.roles && data.roles.length
        ? String(data.roles[0]).toLowerCase()
        : roleMap[String(data.role)]) || 'student'

    const user = {
      id: data.userId,
      username: data.username,
      realName: data.realName,
      role,
      roles: data.roles || [],
      permissions: data.permissions || [],
      avatarUrl: data.avatarUrl || ''
    }

    token.value = data.token
    userInfo.value = user
    roles.value = user.roles
    permissions.value = user.permissions

    localStorage.setItem('token', data.token)
    localStorage.setItem('user', JSON.stringify(user))
    localStorage.setItem('roles', JSON.stringify(user.roles))
    localStorage.setItem('permissions', JSON.stringify(user.permissions))

    return user
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    roles.value = []
    permissions.value = []
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    localStorage.removeItem('roles')
    localStorage.removeItem('permissions')
    localStorage.removeItem('teacher_token')
    localStorage.removeItem('teacher_info')
  }

  function hasRole(code) {
    const c = String(code).toUpperCase()
    return roles.value.includes(c)
  }

  function hasPermission(perm) {
    return permissions.value.includes(perm)
  }

  return { token, userInfo, roles, permissions, isAuthenticated, isAdmin, isTeacher, login, logout, hasRole, hasPermission }
})