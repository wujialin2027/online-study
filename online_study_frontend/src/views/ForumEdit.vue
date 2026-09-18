<template>
  <div class="post-edit-page">
    <!-- 统一的二级页头：与作业管理、课程资源保持一致的返回按钮 -->
    <div class="subpage-head">
      <el-button class="subpage-back" :icon="ArrowLeft" @click="goBack">
        返回论坛
      </el-button>
      <span class="subpage-title">{{ isEdit ? '编辑帖子' : '发布新帖子' }}</span>
    </div>

    <el-card shadow="never" v-loading="loading">
      <!-- 编辑时限提示：把"还能改多久"讲清楚，避免用户以为被无故禁止 -->
      <el-alert
        v-if="isEdit"
        class="edit-tip"
        :title="editTipTitle"
        type="info"
        :closable="false"
        show-icon
      />

      <el-form :model="form" label-position="top" class="post-form">
        <el-form-item label="标题">
          <el-input
            v-model="form.postTitle"
            maxlength="100"
            show-word-limit
            placeholder="用一句话说清你的问题或观点"
          />
        </el-form-item>

        <el-form-item label="正文">
          <el-input
            v-model="form.postContent"
            type="textarea"
            :rows="16"
            maxlength="5000"
            show-word-limit
            placeholder="支持换行。可以贴代码、描述问题背景、说明你已经尝试过什么。"
          />
        </el-form-item>
      </el-form>

      <div class="post-form-footer">
        <span class="hint">发布后 30 分钟内可以修改，超时后只能删除重发</span>
        <div class="spacer"></div>
        <el-button @click="goBack">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ isEdit ? '保存修改' : '发布' }}
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import { getRole, getUser } from '../utils/auth'

const route = useRoute()
const router = useRouter()

/** 有 id 就是编辑，没有就是新建 —— 两个路由复用同一个组件 */
const postId = route.params.id ? Number(route.params.id) : null
const isEdit = computed(() => postId !== null)

const loading = ref(false)
const submitting = ref(false)
const form = ref({ postTitle: '', postContent: '' })
/** 原帖发布时间：用来算「还剩多久不能改」，也用来判断当前用户是不是作者 */
const publishTime = ref(null)
const isAdmin = ref(getRole() === 'admin')

const EDIT_WINDOW_MS = 30 * 60 * 1000

/** 编辑剩余时间（毫秒）；管理员不受限，这里只给作者算 */
const remainMs = computed(() => {
  if (!publishTime.value) return 0
  return EDIT_WINDOW_MS - (Date.now() - new Date(publishTime.value).getTime())
})

const editTipTitle = computed(() => {
  if (isAdmin.value) {
    return '你是管理员，编辑不受时间限制'
  }
  if (remainMs.value <= 0) {
    return '该帖已发布超过 30 分钟，不能再编辑'
  }
  const min = Math.max(1, Math.ceil(remainMs.value / 60000))
  return `发布后 30 分钟内可修改，还剩约 ${min} 分钟；修改后帖子会标记「已编辑」`
})

/** 超时且不是管理员 → 禁止提交（后端也会拦，这里只是提前给出明确反馈） */
const editLocked = computed(() => isEdit.value && !isAdmin.value && remainMs.value <= 0)

const loadPost = async () => {
  loading.value = true
  try {
    const res = await request.get(`/forum-post/${postId}`)
    if (!res) {
      ElMessage.error('帖子不存在或已被删除')
      router.replace('/forum')
      return
    }
    form.value.postTitle = res.postTitle || ''
    form.value.postContent = res.postContent || ''
    publishTime.value = res.publishTime || null

    // 不是本人也不是管理员 → 直接退出，别让用户填半天才发现发不出去
    const role = getRole()
    const user = getUser() || {}
    const myId = role === 'student' ? user.studentId : role === 'teacher' ? user.teacherId : user.adminId
    if (role !== 'admin' && !(res.publisherRole === role && res.publisherId === myId)) {
      ElMessage.warning('只能编辑自己发布的帖子')
      router.replace('/forum')
    }
  } catch (e) {
    console.error(e)
    router.replace('/forum')
  } finally {
    loading.value = false
  }
}

const handleSubmit = async () => {
  if (!form.value.postTitle.trim() || !form.value.postContent.trim()) {
    ElMessage.warning('标题和内容都要填')
    return
  }
  if (editLocked.value) {
    ElMessage.error('已超过 30 分钟编辑时限，不能再修改')
    return
  }

  submitting.value = true
  try {
    // 不传 publisherId / publisherRole —— 服务端从登录凭证里取，前端传了也会被覆盖
    await request.post('/forum-post/save', {
      postId,
      postTitle: form.value.postTitle.trim(),
      postContent: form.value.postContent.trim()
    })
    ElMessage.success(isEdit.value ? '修改成功' : '发布成功')
    // 编辑完回详情页看结果；新建完回列表（刚发的帖排在最前面）
    if (isEdit.value) {
      router.replace(`/forum/${postId}`)
    } else {
      router.replace('/forum')
    }
  } catch (e) {
    console.error(e)
  } finally {
    submitting.value = false
  }
}

const goBack = () => {
  // 编辑态直接退到详情页更自然；新建态没有详情页可退，回列表
  if (isEdit.value) {
    router.replace(`/forum/${postId}`)
  } else {
    router.replace('/forum')
  }
}

onMounted(() => {
  if (isEdit.value) {
    loadPost()
  }
})
</script>

<style scoped>
.post-form {
  margin-top: 6px;
}

.edit-tip {
  margin-bottom: 18px;
}

.post-form-footer {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-top: 6px;
  border-top: 1px solid var(--app-border, #ebeef5);
}

.hint {
  font-size: 12px;
  color: #909399;
}

.spacer {
  flex: 1;
}
</style>
