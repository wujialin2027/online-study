<template>
  <div class="detail-page">
    <el-page-header content="帖子详情" class="page-header" @back="goBack" />

    <el-card v-loading="loading" shadow="never" class="post-card">
      <template v-if="post">
        <div class="post-head">
          <h2 class="post-title">{{ post.postTitle }}</h2>
          <el-tag size="small" :type="roleTagType(post.publisherRole)">
            {{ roleText(post.publisherRole) }}
          </el-tag>
        </div>

        <div class="post-meta">
          <span>{{ formatDate(post.publishTime) }}</span>
          <span class="dot">·</span>
          <span>{{ post.replyNum }} 条回复</span>
        </div>

        <el-divider />

        <!-- 详情页展示完整正文，保留换行（white-space: pre-wrap） -->
        <div class="post-content">{{ post.postContent }}</div>

        <div class="post-actions">
          <el-button
            size="small"
            text
            :class="{ 'is-on': post.liked }"
            @click="toggleLike"
          >
            {{ post.liked ? '已赞' : '点赞' }} {{ post.likeNum }}
          </el-button>

          <el-button
            size="small"
            text
            :class="{ 'is-on': post.collected }"
            @click="toggleCollect"
          >
            {{ post.collected ? '已收藏' : '收藏' }} {{ post.collectNum }}
          </el-button>

          <div class="spacer"></div>

          <el-button v-if="canManage" size="small" type="danger" plain @click="deletePost">
            删除帖子
          </el-button>
        </div>
      </template>

      <el-empty v-else-if="!loading" description="帖子不存在或已被删除" />
    </el-card>

    <el-card v-if="post" shadow="never" class="reply-card">
      <template #header>
        <span>全部回复（{{ replies.length }}）</span>
      </template>

      <el-empty v-if="replies.length === 0" description="还没有人回复，来抢沙发" :image-size="80" />

      <div v-for="reply in replies" :key="reply.replyId" class="reply-item">
        <div class="reply-head">
          <el-tag size="small" :type="roleTagType(reply.replierRole)">
            {{ roleText(reply.replierRole) }}
          </el-tag>
          <span class="reply-time">{{ formatDate(reply.replyTime) }}</span>
          <div class="spacer"></div>
          <el-button
            v-if="canDeleteReply(reply)"
            size="small"
            text
            type="danger"
            @click="deleteReply(reply)"
          >
            删除
          </el-button>
        </div>
        <div class="reply-content">{{ reply.replyContent }}</div>
      </div>

      <el-divider />

      <el-input
        v-model="replyContent"
        type="textarea"
        :rows="4"
        maxlength="2000"
        show-word-limit
        placeholder="写下你的回复…"
      />
      <div class="reply-submit">
        <el-button type="primary" :loading="submitting" @click="handleReply">发表回复</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()

const post = ref(null)
const replies = ref([])
const loading = ref(false)
const submitting = ref(false)
const replyContent = ref('')

const role = ref(localStorage.getItem('role') || '')
const user = ref(JSON.parse(localStorage.getItem('user') || '{}'))

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

const postId = () => Number(route.params.id)

/**
 * 返回上一页。
 *
 * 列表页的搜索词与页码都存在地址栏里，所以 router.back() 回去时
 * 能原样恢复「搜索到第 2 页」的现场，而不是回到没有条件的第一页。
 *
 * history.state.back 是 Vue Router 记录的「上一个页面」。
 * 直接打开详情页链接（刷新后、或别人发来的链接）时它是空的，
 * 此时 back 会退出整个站点，所以改成跳回列表页。
 */
const goBack = () => {
  const state = window.history.state
  if (state && state.back) {
    router.back()
  } else {
    router.push('/forum')
  }
}

// ==================== 数据加载 ====================

const fetchDetail = async () => {
  loading.value = true
  try {
    post.value = await request.get(`/forum-post/${postId()}`)
  } catch (e) {
    post.value = null
    console.error(e)
  } finally {
    loading.value = false
  }
}

const fetchReplies = async () => {
  try {
    const res = await request.post('/forum-reply/page', {
      postId: postId(),
      pageNum: 1,
      pageSize: 100
    })
    replies.value = res?.records || []
  } catch (e) {
    console.error(e)
  }
}

const loadAll = () => {
  fetchDetail()
  fetchReplies()
}

// ==================== 点赞 / 收藏 ====================

const toggleLike = async () => {
  try {
    const res = await request.post(`/forum-post/${postId()}/like`)
    post.value.liked = res.active
    post.value.likeNum = res.count
  } catch (e) {
    console.error(e)
  }
}

const toggleCollect = async () => {
  try {
    const res = await request.post(`/forum-post/${postId()}/collect`)
    post.value.collected = res.active
    post.value.collectNum = res.count
  } catch (e) {
    console.error(e)
  }
}

// ==================== 回复 ====================

const handleReply = async () => {
  if (!replyContent.value.trim()) {
    ElMessage.warning('回复内容不能为空')
    return
  }
  submitting.value = true
  try {
    // 同样不传 replierId / replierRole，交给服务端从登录凭证取
    await request.post('/forum-reply/save', {
      postId: postId(),
      replyContent: replyContent.value.trim()
    })
    ElMessage.success('回复成功')
    replyContent.value = ''
    loadAll()
  } catch (e) {
    console.error(e)
  } finally {
    submitting.value = false
  }
}

const canDeleteReply = (reply) =>
  role.value === 'admin' ||
  (reply.replierRole === role.value && reply.replierId === getUserId())

const deleteReply = async (reply) => {
  try {
    await ElMessageBox.confirm('确定删除这条回复吗？', '提示', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await request.delete(`/forum-reply/${reply.replyId}`)
    ElMessage.success('已删除')
    loadAll()
  } catch (e) {
    console.error(e)
  }
}

// ==================== 帖子操作 ====================

/** 能否管理该帖（作者本人或管理员）—— 由后端把关，这里只控制按钮显隐 */
const canManage = computed(() => {
  if (!post.value) return false
  return (
    role.value === 'admin' ||
    (post.value.publisherRole === role.value && post.value.publisherId === getUserId())
  )
})

const deletePost = async () => {
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
    await request.delete(`/forum-post/${postId()}`)
    ElMessage.success('删除成功')
    router.push('/forum')
  } catch (e) {
    console.error(e)
  }
}

// 页码参数变化时重新加载（同一组件被不同帖子复用时 onMounted 不会再触发）
watch(() => route.params.id, loadAll)

onMounted(loadAll)
</script>

<style scoped>
.page-header {
  margin-bottom: 14px;
}

.post-card {
  margin-bottom: 14px;
}

.post-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.post-title {
  margin: 0;
  font-size: 19px;
  font-weight: 500;
}

.post-meta {
  color: #909399;
  font-size: 12px;
  display: flex;
  gap: 6px;
  margin-top: 8px;
}

/* pre-wrap 让用户在正文里敲的回车能真的换行显示 */
.post-content {
  font-size: 14px;
  line-height: 1.9;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}

.post-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 18px;
  padding-top: 12px;
  border-top: 1px solid var(--app-border, #ebeef5);
}

.post-actions :deep(.is-on) {
  color: var(--el-color-primary);
  font-weight: 500;
}

.spacer {
  flex: 1;
}

.reply-item {
  padding: 12px 0;
  border-bottom: 1px solid var(--app-border, #f2f3f5);
}

.reply-item:last-of-type {
  border-bottom: none;
}

.reply-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.reply-time {
  color: #909399;
  font-size: 12px;
}

.reply-content {
  font-size: 13px;
  line-height: 1.8;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}

.reply-submit {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
