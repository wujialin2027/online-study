<template>
  <div>
    <h2>交流论坛</h2>
    <el-button type="primary" @click="showPostDialog = true">发帖</el-button>
    <div style="margin-top: 20px;">
      <el-card v-for="post in posts" :key="post.postId" style="margin-bottom: 10px;">
        <h3>{{ post.postTitle }}</h3>
        <p>{{ post.postContent }}</p>
        <div style="color: gray; font-size: 12px; margin-bottom: 10px;">发布时间: {{ formatDate(post.publishTime) }}</div>
        <el-button size="small" @click="viewReplies(post)">查看回复</el-button>
        <el-button size="small" type="warning" v-if="post.publisherRole === role && post.publisherId === getUserId() && isRecallAllowed(post)" @click="recallPost(post)">撤回</el-button>
        <el-button size="small" type="danger" v-if="role === 'admin' || (post.publisherRole === role && post.publisherId === getUserId())" @click="deletePost(post)">删除</el-button>
      </el-card>
    </div>

    <el-dialog v-model="showPostDialog" title="发布新帖子">
      <el-form :model="newPost">
        <el-form-item label="标题"><el-input v-model="newPost.postTitle"></el-input></el-form-item>
        <el-form-item label="内容"><el-input type="textarea" v-model="newPost.postContent"></el-input></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPostDialog = false">取消</el-button>
        <el-button type="primary" @click="handlePost">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showReplyDialog" title="帖子回复" width="60%">
      <div v-if="currentPost">
        <h3>{{ currentPost.postTitle }}</h3>
        <p>{{ currentPost.postContent }}</p>
        <el-divider></el-divider>
        <div v-for="reply in replies" :key="reply.replyId" style="margin-bottom: 10px; border-bottom: 1px solid #eee; padding-bottom: 10px;">
          <p>{{ reply.replyContent }}</p>
          <div style="color: gray; font-size: 12px;">回复时间: {{ formatDate(reply.replyTime) }} | 回复人角色: {{ reply.replierRole }}</div>
        </div>
        <el-input type="textarea" v-model="newReplyContent" placeholder="写下你的回复..." style="margin-top: 20px;"></el-input>
      </div>
      <template #footer>
        <el-button @click="showReplyDialog = false">关闭</el-button>
        <el-button type="primary" @click="handleReply">发表回复</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const posts = ref([])
const replies = ref([])
const role = ref(localStorage.getItem('role'))
const user = ref(JSON.parse(localStorage.getItem('user') || '{}'))

const getUserId = () => {
  if (role.value === 'student') return user.value.studentId
  if (role.value === 'teacher') return user.value.teacherId
  return user.value.adminId
}

const showPostDialog = ref(false)
const showReplyDialog = ref(false)
const currentPost = ref(null)
const newReplyContent = ref('')
const newPost = ref({
  postTitle: '',
  postContent: ''
})

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString()
}

const fetchPosts = async () => {
  try {
    const res = await request.get('/forum-post/list')
    posts.value = res
  } catch (e) {
    console.error(e)
  }
}

const handlePost = async () => {
  if (!newPost.value.postTitle || !newPost.value.postContent) {
    ElMessage.warning('请填写完整信息')
    return
  }
  newPost.value.publisherRole = role.value
  newPost.value.publisherId = getUserId()
  newPost.value.publishTime = new Date()
  newPost.value.viewCount = 0
  newPost.value.likeCount = 0
  await request.post('/forum-post/save', newPost.value)
  ElMessage.success('发布成功')
  showPostDialog.value = false
  newPost.value.postTitle = ''
  newPost.value.postContent = ''
  fetchPosts()
}

const deletePost = async (post) => {
  try {
    await ElMessageBox.confirm('确定要删除这条帖子吗？此操作不可恢复。', '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await request.delete(`/forum-post/${post.postId}`)
    ElMessage.success('删除成功')
    fetchPosts()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const isRecallAllowed = (post) => {
  if (!post.publishTime) return false
  const now = new Date().getTime()
  const publishTime = new Date(post.publishTime).getTime()
  return (now - publishTime) < 300000 // 5 minutes
}

const recallPost = async (post) => {
  try {
    await ElMessageBox.confirm('确定要撤回这条帖子吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info',
    })
    await request.delete(`/forum-post/${post.postId}`)
    ElMessage.success('撤回成功')
    fetchPosts()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('撤回失败')
    }
  }
}

const viewReplies = async (post) => {
  currentPost.value = post
  try {
    const res = await request.post('/forum-reply/query', { postId: post.postId })
    replies.value = res || []
    showReplyDialog.value = true
  } catch (e) {
    console.error(e)
  }
}

const handleReply = async () => {
  if (!newReplyContent.value) {
    ElMessage.warning('回复内容不能为空')
    return
  }
  const replyData = {
    postId: currentPost.value.postId,
    replyContent: newReplyContent.value,
    replierRole: role.value,
    replierId: role.value === 'student' ? user.value.studentId : (role.value === 'teacher' ? user.value.teacherId : user.value.adminId),
    replyTime: new Date()
  }
  try {
    await request.post('/forum-reply/save', replyData)
    ElMessage.success('回复成功')
    newReplyContent.value = ''
    const res = await request.post('/forum-reply/query', { postId: currentPost.value.postId })
    replies.value = res
  } catch (e) {
    ElMessage.error('回复失败')
  }
}

onMounted(() => {
  fetchPosts()
})
</script>