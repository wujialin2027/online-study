<template>
  <div class="forum-page">
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

      <el-button type="primary" @click="openPostDialog(null)">发帖</el-button>
    </el-card>

    <!-- ==================== 列表 ==================== -->
    <div v-loading="loading" class="post-list">
      <el-empty v-if="!loading && posts.length === 0" description="没有找到帖子，来发第一条吧" />

      <el-card v-for="post in posts" :key="post.postId" shadow="hover" class="post-card">
        <div class="post-head">
          <h3 class="post-title" @click="goDetail(post)">{{ post.postTitle }}</h3>
          <el-tag size="small" :type="roleTagType(post.publisherRole)">
            {{ roleText(post.publisherRole) }}
          </el-tag>
        </div>

        <p class="post-excerpt" @click="goDetail(post)">{{ excerpt(post.postContent) }}</p>

        <div class="post-meta">
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

          <el-button
            v-if="canManage(post)"
            size="small"
            @click="openPostDialog(post)"
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
    <div v-if="total > 0" class="pager">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :current-page="query.pageNum"
        :page-size="query.pageSize"
        :page-sizes="[5, 10, 20, 50]"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <!-- ==================== 发帖 / 编辑 ==================== -->
    <el-dialog
      v-model="showPostDialog"
      :title="form.postId ? '编辑帖子' : '发布新帖子'"
      width="620px"
    >
      <el-form :model="form" label-width="56px">
        <el-form-item label="标题">
          <el-input v-model="form.postTitle" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="内容">
          <el-input
            v-model="form.postContent"
            type="textarea"
            :rows="7"
            maxlength="5000"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPostDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import request from '../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()

const posts = ref([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)

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
 * 把当前查询条件写回地址栏。
 *
 * 只写非默认值（默认是第 1 页 / 每页 10 条 / 无关键词），保持 URL 干净易读。
 * 用 replace 而不是 push —— 翻一页就往浏览器历史里塞一条记录的话，
 * 用户想靠「返回」回到上一个页面得按好几十次。
 */
const syncUrl = () => {
  const q = {}
  if (keyword.value.trim()) q.keyword = keyword.value.trim()
  if (query.pageNum > 1) q.page = String(query.pageNum)
  if (query.pageSize !== 10) q.size = String(query.pageSize)
  router.replace({ path: '/forum', query: q })
}

const role = ref(localStorage.getItem('role') || '')
const user = ref(JSON.parse(localStorage.getItem('user') || '{}'))

const showPostDialog = ref(false)
const form = ref({ postId: null, postTitle: '', postContent: '' })

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

const openPostDialog = (post) => {
  form.value = post
    ? { postId: post.postId, postTitle: post.postTitle, postContent: post.postContent }
    : { postId: null, postTitle: '', postContent: '' }
  showPostDialog.value = true
}

const handleSubmit = async () => {
  if (!form.value.postTitle.trim() || !form.value.postContent.trim()) {
    ElMessage.warning('标题和内容都要填')
    return
  }
  submitting.value = true
  try {
    // 不再传 publisherId / publisherRole —— 服务端会从登录凭证里取，
    // 前端传了也会被覆盖（旧代码是直接信任前端，可以冒名发帖）
    await request.post('/forum-post/save', {
      postId: form.value.postId,
      postTitle: form.value.postTitle.trim(),
      postContent: form.value.postContent.trim()
    })
    ElMessage.success(form.value.postId ? '修改成功' : '发布成功')
    showPostDialog.value = false
    fetchPosts()
  } catch (e) {
    console.error(e)
  } finally {
    submitting.value = false
  }
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
  gap: 6px;
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

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
