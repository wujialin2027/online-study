import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getRole, getToken } from '../utils/auth'

/**
 * 路由表
 *
 * meta.title —— 面包屑与浏览器标签页标题
 * meta.roles —— 允许访问的角色（不写表示所有已登录用户可访问）
 * meta.public —— 无需登录
 */
const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    name: 'Layout',
    component: () => import('../views/Layout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { title: '首页概览' }
      },
      {
        path: 'courses',
        name: 'Courses',
        component: () => import('../views/Courses.vue'),
        meta: { title: '课程资源', roles: ['student', 'teacher'] }
      },
      {
        path: 'homework',
        name: 'Homework',
        component: () => import('../views/Homework.vue'),
        meta: { title: '作业管理', roles: ['student', 'teacher'] }
      },
      {
        // 智能助教：所有登录角色都能用（学员问知识题、教师查作业要求都合适）
        path: 'assistant',
        name: 'Assistant',
        component: () => import('../views/Assistant.vue'),
        meta: { title: '智能助教' }
      },
      {
        path: 'forum',
        name: 'Forum',
        component: () => import('../views/Forum.vue'),
        meta: { title: '交流论坛' }
      },
      {
        // 发帖 / 编辑帖子：独立页面而不是弹窗（长表单该有自己的页面）。
        // 必须写在 'forum/:id' 之前 —— 否则 /forum/new 会被 :id 当成 id="new" 吃掉。
        // （vue-router 4 本身也会按静态优先排序，但显式放前面更好读、不依赖内部规则）
        path: 'forum/new',
        name: 'ForumNew',
        component: () => import('../views/ForumEdit.vue'),
        meta: { title: '发布帖子', hideInMenu: true }
      },
      {
        path: 'forum/:id/edit',
        name: 'ForumEdit',
        component: () => import('../views/ForumEdit.vue'),
        meta: { title: '编辑帖子', hideInMenu: true }
      },
      {
        // 帖子详情：hideInMenu 让侧边栏菜单跳过它（菜单是从路由表生成的，
        // 不加这个标记的话会多出一项「帖子详情」，而且点进去还没有 id 可传）
        path: 'forum/:id',
        name: 'ForumDetail',
        component: () => import('../views/ForumDetail.vue'),
        meta: { title: '帖子详情', hideInMenu: true }
      },
      {
        path: 'admin',
        name: 'Admin',
        component: () => import('../views/Admin.vue'),
        meta: { title: '系统管理', roles: ['admin'] }
      }
    ]
  },
  // 兜底：未匹配的地址统一回首页，避免出现空白页
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/** 当前登录角色、token 的读取统一走 utils/auth.js（兼容 localStorage 与 sessionStorage） */

/**
 * 全局前置守卫
 *
 * 修复的缺陷：原项目前端没有任何路由层校验 ——
 * 未登录时直接在地址栏输 localhost:3000/courses 也能进页面，
 * 只能靠「页面发请求 → 接口返回 401 → 跳登录页」兜底。表现为：
 *   · 页面会先闪一下再被弹回登录页；
 *   · 有些页面在未登录时压根不发请求（按 role 分支判断），于是既不显示数据、
 *     也不跳转，卡在空白状态 —— 这是最难排查的一种。
 * 现在改为「进入页面前先判断」，并且带 redirect 参数，登录后能跳回原目标页。
 *
 * <p>关于"为什么一打开就是已登录"：token 存在 localStorage（勾了「记住我」）时会跨会话保留，
 * 所以直接访问 /login 也会被下面的第一个分支弹回首页。不勾「记住我」时存的是
 * sessionStorage，关掉浏览器标签就失效，再打开会正常看到登录页。见 utils/auth.js。
 */
router.beforeEach((to) => {
  const token = getToken()

  // 公开页面（登录页）
  if (to.meta?.public) {
    // 已登录的用户再访问登录页 → 直接回首页
    return token ? { path: '/dashboard' } : true
  }

  // 需要登录但没有凭证 → 跳登录页并记住原目标
  if (!token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 角色白名单（例如 /admin 只允许管理员，防止直接输网址绕过菜单隐藏）
  //
  // 注意这里跳回首页而不是登录页：用户已经登录、身份是明确的，只是权限不够，
  // 让他重新登录没有任何意义（登完依然没有管理员权限）。
  // 但静默跳转会让用户以为"点错了"，所以补一句提示。
  const roles = to.meta?.roles
  if (Array.isArray(roles) && roles.length > 0 && !roles.includes(getRole())) {
    ElMessage.warning('没有权限访问该页面')
    return { path: '/dashboard' }
  }

  return true
})

/** 同步浏览器标签页标题 */
router.afterEach((to) => {
  const title = to.meta?.title
  document.title = title ? `${title} · 在线学习平台` : '在线学习平台'
})

export default router
