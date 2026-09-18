<template>
  <div class="forum-page">
    <!-- ==================== 范围切换 ====================
         收藏完必须有地方能把收藏找回来 —— 原来点完「已收藏」就再也没入口了。
         三个范围共用同一个分页接口（多带一个 scope 参数），
         分页、搜索、排序的逻辑完全复用，不必复制三份。 -->
    <el-tabs v-model="scope" class="scope-tabs" @tab-change="handleScopeChange">
      <el-tab-pane label="全部帖子" name="all" />
      <el-tab-pane label="我的收藏" name="collected" />
      <el-tab-pane label="我的发帖" name="mine" />
    </el-tabs>

    <!-- ==================== 工具栏 ==================== -->
    <el-card shadow="never" class="toolbar">
      <el-input
        v-model="keyword"
        class="search"
        placeholder="搜索帖子标题或内容"
        clearable
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-button type="primary" plain @click="handleSearch">搜索</el-button>
      <!-- 有搜索词时才出现：一键回到全部帖子，不用去点输入框里那个小叉 -->
      <el-button v-if="keyword" @click="clearSearch">查看全部</el-button>

      <div class="spacer"></div>

      <!-- 发帖走独立页面：要写几千字正文，塞进 620px 的弹窗里体验很差 -->
      <el-button type="primary" @click="goCreatePost">发帖</el-button>
    </el-card>

    <!-- ==================== 列表 ==================== -->
    <div v-loading="loading" class="post-list">
      <el-empty v-if="!loading && posts.length === 0" description="没有找到帖子，来发第一条吧" />

      <el-card v-for="post in posts" :key="post.postId" shadow="hover" class="post-card">
        <div class="post-head">
          <h3 class="post-title" @click="goDetail(post)">{{ post.postTitle }}</h3>
          <!-- 改过就留痕：比"一律禁止编辑"更能建立信任，也提醒读者内容变过 -->
          <el-tag v-if="post.editTime" size="small" type="info">已编辑</el-tag>
        </div>

        <p class="post-excerpt" @click="goDetail(post)">{{ excerpt(post.postContent) }}</p>

        <!-- 发帖人姓名 + 角色：原来只有一个"学员"标签，看不出究竟是谁 -->
        <div class="post-meta">
          <span class="post-author">{{ post.publisherName || '未知用户' }}</span>
          <el-tag size="small" :type="roleTagType(post.publisherRole)">
            {{ roleText(post.publisherRole) }}
          </el-tag>
          <span class="dot">·</span>
          <span>{{ formatDate(post.publishTime) }}</span>
          <span class="dot">·</span>
          <span>{{ post.replyNum }} 条回复</span>
        </div>

        <div class="post-actions">
          <!-- 点赞 / 收藏：状态与数字都由接口返回，不做前端自增（并发下会算歪） -->
          <el-button
            size="small"
            text
            :class="{ 'is-on': post.liked }"
            @click="toggleLike(post)"
          >
            {{ post.liked ? '已赞' : '点赞' }} {{ post.likeNum }}
          </el-button>

          <el-button
            size="small"
            text
            :class="{ 'is-on': post.collected }"
            @click="toggleCollect(post)"
          >
            {{ post.collected ? '已收藏' : '收藏' }} {{ post.collectNum }}
          </el-button>

          <div class="spacer"></div>

          <el-button size="small" @click="goDetail(post)">查看详情</el-button>

          <!-- 编辑只在发布后 30 分钟内出现；真正的时限判定在后端，前端只管显隐 -->
          <el-button
            v-if="canEdit(post)"
            size="small"
            @click="goEditPost(post)"
          >
            编辑
          </el-button>

          <el-button
            v-if="canManage(post) && isRecallAllowed(post)"
            size="small"
            type="warning"
            plain
            @click="recallPost(post)"
          >
            撤回
          </el-button>

          <el-button
            v-if="canManage(post)"
            size="small"
            type="danger"
            plain
            @click="deletePost(post)"
          >
            删除
          </el-button>
        </div>
      </el-card>
    </div>

    <!-- ==================== 分页 ==================== -->
    <div v-if="total > 0" class="pagination-bar">
      <el-pagination
        background
        layout="total, prev, pager, next, sizes"
        :pager-count="5"
        :total="total"
        :current-page="query.pageNum"
        :page-size="query.pageSize"
        :page-sizes="[5, 10, 20, 50]"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import request from '../utils/request'
import { getRole, getUser } from '../utils/auth'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()

const posts = ref([])
const total = ref(0)
const loading = ref(false)

/**
 * 列表状态（关键词 / 页码 / 每页条数）全部同步到地址栏。
 *
 * 为什么：这些状态原本只活在组件内存里。点进详情页时列表组件被销毁，
 * 返回时状态全没了 —— 用户搜到结果、翻到第 2 页，点进去看一眼再返回，
 * 看到的却是「没有搜索条件的第 1 页」，得重新搜一遍。
 *
 * 同步到 URL 后，返回、刷新浏览器、甚至把地址发给别人，看到的都是同一批结果。
 */
const query = reactive({
  pageNum: Number(route.query.page) || 1,
  pageSize: Number(route.query.size) || 10
})
const keyword = ref(String(route.query.keyword || ''))
/**
 * 列表范围：all = 全部帖子 / collected = 我收藏的 / mine = 我发的。
 * 同样同步进地址栏 —— 从收藏夹点进详情再返回，应该回到收藏夹而不是全部帖子。
 */
const scope = ref(
  ['all', 'collected', 'mine'].includes(route.query.scope) ? route.query.scope : 'all'
)

/**
 * 把当前查询条件写回地址栏。
 *
 * 只写非默认值（默认是第 1 页 / 每页 10 条 / 无关键词），保持 URL 干净易读。
 * 用 replace 而不是 push —— 翻一页就往浏览器历史里塞一条记录的话，
 * 用户想靠「返回」回到上一个页面得按好几十次。
 */
const syncUrl = () => {
  const q = {}
  if (keyword.value.trim()) q.keyword = keyword.value.trim()
  if (scope.value !== 'all') q.scope = scope.value
  if (query.pageNum > 1) q.page = String(query.pageNum)
  if (query.pageSize !== 10) q.size = String(query.pageSize)
  router.replace({ path: '/forum', query: q })
}

const role = ref(getRole())
const user = ref(getUser())


const ROLE_TEXT = { admin: '管理员', teacher: '教师', student: '学员' }
const ROLE_TAG = { admin: 'danger', teacher: 'warning', student: '' }

const roleText = (r) => ROLE_TEXT[r] || r || '未知'
const roleTagType = (r) => ROLE_TAG[r] || ''

const getUserId = () => {
  if (role.value === 'student') return user.value.studentId
  if (role.value === 'teacher') return user.value.teacherId
  return user.value.adminId
}

const formatDate = (value) => (value ? new Date(value).toLocaleString() : '')

/** 列表里只显示摘要，完整内容进详情页看 */
const excerpt = (text) => {
  if (!text) return ''
  const flat = String(text).replace(/\s+/g, ' ').trim()
  return flat.length > 120 ? flat.slice(0, 120) + '…' : flat
}

// ==================== 列表加载 ====================

const fetchPosts = async () => {
  // 每次取数都顺手把条件写进地址栏，保证 URL 与实际看到的内容始终一致
  syncUrl()
  loading.value = true
  try {
    const body = { pageNum: query.pageNum, pageSize: query.pageSize }
    if (keyword.value.trim()) body.keyword = keyword.value.trim()
    // 收藏 / 我的发帖不是新接口，只是给同一个分页接口多带一个范围参数。
    // 后端按 publisher / 收藏关系过滤，分页与搜索逻辑完全复用。
    if (scope.value !== 'all') body.scope = scope.value
    const res = await request.post('/forum-post/page', body)
    posts.value = res?.records || []
    total.value = Number(res?.total || 0)
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

/** 换了搜索词必须回到第 1 页，否则会停在上一次的页码上看到空列表 */
const handleSearch = () => {
  query.pageNum = 1
  fetchPosts()
}

const handlePageChange = (page) => {
  query.pageNum = page
  fetchPosts()
}

/** 同理：改了每页条数也要回到第 1 页 */
const handleSizeChange = (size) => {
  query.pageSize = size
  query.pageNum = 1
  fetchPosts()
}

/** 清空搜索条件，回到全部帖子 */
const clearSearch = () => {
  keyword.value = ''
  query.pageNum = 1
  fetchPosts()
}

/** 切换范围（全部 / 我的收藏 / 我的发帖）：同样要回到第 1 页 */
const handleScopeChange = () => {
  query.pageNum = 1
  fetchPosts()
}

// ==================== 点赞 / 收藏 ====================

const toggleLike = async (post) => {
  try {
    const res = await request.post(`/forum-post/${post.postId}/like`)
    post.liked = res.active
    post.likeNum = res.count
  } catch (e) {
    console.error(e)
  }
}

const toggleCollect = async (post) => {
  try {
    const res = await request.post(`/forum-post/${post.postId}/collect`)
    post.collected = res.active
    post.collectNum = res.count
  } catch (e) {
    console.error(e)
  }
}

// ==================== 发帖 / 编辑 ====================

/**
 * 发帖与编辑改走独立页面（/forum/new、/forum/:id/edit）。
 *
 * 为什么不用弹窗：写几千字正文属于「长表单」，弹窗既挤又不能中途离开去查资料，
 * 关掉就全没了。独立页面有自己的地址、能刷新、能分享，也不压在原列表上面。
 * 导航栏照样不动 —— 这是 SPA 的路由切换，不是整站跳页。
 */
const goCreatePost = () => router.push('/forum/new')

const goEditPost = (post) => router.push(`/forum/${post.postId}/edit`)

/**
 * 编辑窗口：发布后 30 分钟内可改。
 * 这里只控制按钮显隐，真正的时限判定在后端（前端时间可以被改，不能拿来当依据）。
 */
const EDIT_WINDOW_MS = 30 * 60 * 1000
const canEdit = (post) => {
  if (!canManage(post) || !post.publishTime) return false
  return Date.now() - new Date(post.publishTime).getTime() < EDIT_WINDOW_MS
}

// ==================== 删除 / 撤回 ====================

const canManage = (post) =>
  role.value === 'admin' ||
  (post.publisherRole === role.value && post.publisherId === getUserId())

/** 5 分钟内才显示撤回按钮（真正的时限判定在后端） */
const isRecallAllowed = (post) => {
  if (!post.publishTime) return false
  return Date.now() - new Date(post.publishTime).getTime() < 5 * 60 * 1000
}

const recallPost = async (post) => {
  try {
    await ElMessageBox.confirm('撤回后帖子及其回复都会被删除，确定吗？', '撤回帖子', {
      confirmButtonText: '确定撤回',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await request.delete(`/forum-post/${post.postId}/recall`)
    ElMessage.success('已撤回')
    fetchPosts()
  } catch (e) {
    console.error(e)
  }
}

const deletePost = async (post) => {
  try {
    await ElMessageBox.confirm('删除后不可恢复，确定吗？', '删除帖子', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await request.delete(`/forum-post/${post.postId}`)
    ElMessage.success('删除成功')
    fetchPosts()
  } catch (e) {
    console.error(e)
  }
}

const goDetail = (post) => router.push(`/forum/${post.postId}`)

onMounted(fetchPosts)
</script>

<style scoped>
.toolbar :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 10px;
}

.search {
  max-width: 320px;
}

.spacer {
  flex: 1;
}

/* 范围切换（全部 / 收藏 / 我的发帖）：贴着页面顶部，与下面的列表拉开一点距离 */
.scope-tabs {
  margin-bottom: 4px;
}

.scope-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.post-list {
  margin-top: 14px;
  min-height: 120px;
}

.post-card {
  margin-bottom: 12px;
}

.post-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.post-title {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
}

.post-title:hover {
  color: var(--el-color-primary);
}

.post-excerpt {
  margin: 10px 0;
  color: #606266;
  font-size: 13px;
  line-height: 1.7;
  cursor: pointer;
}

.post-meta {
  color: #909399;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 发帖人姓名比时间重要，给深色 + 中等字重压住，避免被误当成时间戳 */
.post-author {
  color: #303133;
  font-size: 13px;
  font-weight: 500;
}

.post-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--app-border, #ebeef5);
}

/* 已点赞 / 已收藏的按钮高亮，让"我参与过"这件事一眼可见 */
.post-actions :deep(.is-on) {
  color: var(--el-color-primary);
  font-weight: 500;
}

/* 分页容器样式统一走 theme.css 的 .pagination-bar，此处不再单独定义 */
</style>
