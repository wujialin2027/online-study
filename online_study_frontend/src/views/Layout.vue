<template>
  <el-container class="layout">
    <!-- ==================== 侧边栏 ==================== -->
    <el-aside :width="isCollapse ? '64px' : '210px'" class="aside">
      <div class="logo">
        <span class="logo-full" v-if="!isCollapse">在线学习平台</span>
        <span class="logo-mini" v-else>OS</span>
      </div>

      <el-menu
        router
        :default-active="$route.path"
        :collapse="isCollapse"
        :collapse-transition="false"
        class="menu"
      >
        <!-- 菜单项由路由表驱动：新增页面只需改 router/index.js 的 meta，
             不用再来这里复制一段 el-menu-item -->
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.title }}</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- ==================== 顶栏 ==================== -->
      <el-header height="56px" class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="isCollapse = !isCollapse">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentTitle && $route.path !== '/dashboard'">
              {{ currentTitle }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-trigger">
              <el-avatar :size="28" class="avatar">{{ avatarText }}</el-avatar>
              <span class="username">{{ displayName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>{{ roleText }}</el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon>安全退出
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- ==================== 内容区 ==================== -->
      <el-main class="main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowDown,
  ChatDotRound,
  Document,
  Expand,
  Fold,
  HomeFilled,
  Notebook,
  Setting,
  SwitchButton
} from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()

const isCollapse = ref(false)

const user = ref(JSON.parse(localStorage.getItem('user') || '{}'))
const role = ref(localStorage.getItem('role') || '')

const ROLE_TEXT = { admin: '管理员', teacher: '教师', student: '学员' }

const displayName = computed(
  () =>
    user.value.studentName ||
    user.value.teacherName ||
    user.value.adminName ||
    user.value.name ||
    '用户'
)
const avatarText = computed(() => (displayName.value || 'U').slice(0, 1))
const roleText = computed(() => ROLE_TEXT[role.value] || '未知角色')
const currentTitle = computed(() => route.meta?.title || '')

/** 路径 → 图标。菜单的顺序与可见性由路由表决定，这里只负责配图标 */
const MENU_ICONS = {
  dashboard: HomeFilled,
  courses: Notebook,
  homework: Document,
  forum: ChatDotRound,
  admin: Setting
}

/**
 * 菜单项直接从路由表生成：
 * 过滤掉当前角色无权访问的项，并且只保留有 meta.title 的子路由。
 * 好处是「新增一个页面」只需要在 router/index.js 里加一条，
 * 不用再回来改菜单模板（原代码是手写 v-if，容易漏）。
 */
const menuItems = computed(() => {
  const layout = router.options.routes.find((item) => item.path === '/')
  return (layout?.children || [])
    .filter((child) => {
      if (!child.meta?.title) return false
      const roles = child.meta?.roles
      return !roles || roles.includes(role.value)
    })
    .map((child) => ({
      path: `/${child.path}`,
      title: child.meta.title,
      icon: MENU_ICONS[child.path] || Document
    }))
})

const handleCommand = async (command) => {
  if (command !== 'logout') return
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return // 用户取消
  }

  // ⚠️ token 必须一起清掉。
  // 原代码只删了 user / role，token 仍留在 localStorage ——
  // 相当于「点了退出，但凭证还在浏览器里」，任何人拿到这台电脑都能继续用。
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  localStorage.removeItem('role')
  router.push('/login')
}
</script>

<style scoped>
.layout {
  height: 100vh;
}

/* ---------------- 侧边栏 ---------------- */
.aside {
  background: #fff;
  border-right: 1px solid var(--app-border);
  transition: width 0.25s;
  overflow: hidden;
}

.logo {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 56px;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-color-primary);
  border-bottom: 1px solid var(--app-border);
  white-space: nowrap;
}

.logo-mini {
  font-size: 15px;
  letter-spacing: 1px;
}

.menu {
  border-right: none;
}

/* ---------------- 顶栏 ---------------- */
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid var(--app-border);
  padding: 0 18px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.collapse-btn {
  font-size: 18px;
  color: #606266;
  cursor: pointer;
}

.collapse-btn:hover {
  color: var(--el-color-primary);
}

.header-right {
  display: flex;
  align-items: center;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: var(--app-radius-sm);
  transition: background 0.2s;
}

.user-trigger:hover {
  background: #f5f7fa;
}

.avatar {
  background: var(--el-color-primary);
  font-size: 13px;
}

.username {
  font-size: 14px;
  color: #303133;
}

/* ---------------- 内容区 ---------------- */
.main {
  padding: 20px;
  overflow-y: auto;
}

/* 路由切换过渡 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
