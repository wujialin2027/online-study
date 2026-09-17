<template>
  <div>
    <div class="page-head">
      <h2>作业管理</h2>
      <p class="subtitle">{{ role === 'student' ? '提交作业 · 查看成绩' : '发布作业 · 批改评分' }}</p>
    </div>

    <el-tabs v-if="role === 'student'" v-model="studentActiveTab">
      <el-tab-pane label="我的作业" name="homework">
        <div class="filter-bar">
          <el-input
            v-model="hwQuery.homeworkName"
            placeholder="按作业名称搜索"
            clearable
            style="width: 220px"
            @keyup.enter="searchHomework"
            @clear="searchHomework"
          />
          <el-button type="primary" @click="searchHomework">搜索</el-button>
          <el-button @click="resetHomeworkSearch">重置</el-button>
        </div>
        <el-table :data="homeworkList" v-loading="hwLoading" style="width: 100%">
          <el-table-column prop="homeworkName" label="作业名称"></el-table-column>
          <el-table-column prop="deadline" label="截止时间">
            <template #default="scope">{{ formatDate(scope.row.deadline) }}</template>
          </el-table-column>
          <el-table-column label="操作">
            <template #default="scope">
              <el-button size="small" type="primary" @click="openSubmitDialog(scope.row)">提交 / 查看作业</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-bar">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="hwTotal"
            :page-sizes="[5, 10, 20, 50]"
            v-model:current-page="hwQuery.pageNum"
            v-model:page-size="hwQuery.pageSize"
            @current-change="fetchHomework"
            @size-change="searchHomework"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="我的成绩" name="score">
        <el-table :data="scoreList" v-loading="scoreLoading" style="width: 100%">
          <el-table-column label="课程名称">
            <template #default="scope">{{ getCourseName(scope.row.courseId) }}</template>
          </el-table-column>
          <el-table-column prop="totalScore" label="总成绩"></el-table-column>
          <el-table-column prop="scoreComment" label="教师评语">
            <template #default="scope">{{ scope.row.scoreComment || '暂无评语' }}</template>
          </el-table-column>
          <el-table-column label="评分教师">
            <template #default="scope">{{ getTeacherName(scope.row.scoreTeacherId) }}</template>
          </el-table-column>
          <el-table-column prop="scoreTime" label="评分时间">
            <template #default="scope">{{ formatDate(scope.row.scoreTime) }}</template>
          </el-table-column>
        </el-table>
        <div class="pagination-bar">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="scoreTotal"
            :page-sizes="[5, 10, 20, 50]"
            v-model:current-page="scoreQuery.pageNum"
            v-model:page-size="scoreQuery.pageSize"
            @current-change="fetchStudentScores"
            @size-change="searchScores"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <template v-else>
      <div class="filter-bar">
        <el-input
          v-model="hwQuery.homeworkName"
          placeholder="按作业名称搜索"
          clearable
          style="width: 220px"
          @keyup.enter="searchHomework"
          @clear="searchHomework"
        />
        <el-button type="primary" @click="searchHomework">搜索</el-button>
        <el-button @click="resetHomeworkSearch">重置</el-button>
        <div class="spacer"></div>
        <el-button type="success" @click="openAddDialog">发布新作业</el-button>
      </div>
      <el-table :data="homeworkList" v-loading="hwLoading" style="width: 100%">
        <el-table-column prop="homeworkName" label="作业名称"></el-table-column>
        <el-table-column prop="deadline" label="截止时间">
          <template #default="scope">{{ formatDate(scope.row.deadline) }}</template>
        </el-table-column>
        <el-table-column label="操作">
          <template #default="scope">
            <el-button size="small" type="success" @click="gradeHomework(scope.row)">查看提交并批改</el-button>
            <el-button size="small" type="danger" @click="deleteHomework(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="hwTotal"
          :page-sizes="[5, 10, 20, 50]"
          v-model:current-page="hwQuery.pageNum"
          v-model:page-size="hwQuery.pageSize"
          @current-change="fetchHomework"
          @size-change="searchHomework"
        />
      </div>
    </template>

    <el-dialog v-model="showAddDialog" title="发布新作业">
      <el-form :model="newHomework">
        <el-form-item label="作业名称"><el-input v-model="newHomework.homeworkName"></el-input></el-form-item>
        <el-form-item label="作业内容"><el-input type="textarea" v-model="newHomework.homeworkContent"></el-input></el-form-item>
        <el-form-item label="所属课程">
          <el-select v-model="newHomework.courseId" placeholder="请选择课程" style="width: 100%">
            <el-option v-for="c in teacherCourses" :key="c.courseId" :label="c.courseName" :value="c.courseId"></el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAddHomework">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showSubmitDialog" title="提交 / 查看作业">
      <div v-if="currentHomework" style="margin-bottom: 20px; padding: 15px; background: #f4f4f5; border-radius: 4px;">
        <h4 style="margin-top: 0; color: #303133;">{{ currentHomework.homeworkName }}</h4>
        <p style="color: #606266; white-space: pre-wrap;"><strong>作业内容：</strong><br/>{{ currentHomework.homeworkContent || '无详细内容' }}</p>
      </div>

      <div v-if="hasSubmitted" style="margin-bottom: 20px; padding: 15px; background: #f0f9eb; border-radius: 4px;">
        <p style="color: #67c23a; font-weight: bold; margin-top: 0;"><el-icon><Check /></el-icon> 你已提交过该作业</p>
        <p><strong>提交内容：</strong>{{ submitContent || '无' }}</p>
        <p v-if="submitFile">
          <strong>附件：</strong>
          <el-button link type="primary" @click="downloadFile(submitFile)">下载附件</el-button>
        </p>
        <el-button type="warning" size="small" @click="handleRecallSubmit">撤回并重新提交</el-button>
      </div>
      
      <el-form v-else>
        <el-form-item label="作业内容">
          <el-input type="textarea" v-model="submitContent" rows="5" placeholder="请输入作业内容（可选）"></el-input>
        </el-form-item>
        <el-form-item label="上传照片/附件">
          <el-upload
            class="upload-demo"
            action="/api/file/upload"
            :headers="uploadHeaders"
            :on-success="handleUploadSuccess"
            :on-error="handleUploadError"
            :limit="1"
            ref="uploadRef"
          >
            <el-button type="primary">点击上传</el-button>
            <template #tip>
              <div class="el-upload__tip">支持上传各类作业照片或文档格式</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSubmitDialog = false">取消</el-button>
        <el-button type="primary" v-if="!hasSubmitted" @click="handleSubmitHomework">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showGradeDialog" title="批改作业" width="900px" top="5vh">
      <!--
        作业信息常驻顶部：原来批改时看不到题目和截止时间，得退出去翻。
        顺带给出「已批改 X / 共 Y」，教师随时知道还剩多少。
      -->
      <div class="hw-info" v-if="currentHomework">
        <div class="hw-info-head">
          <span class="hw-info-name">{{ currentHomework.homeworkName }}</span>
          <span class="hw-info-meta">
            截止 {{ formatDate(currentHomework.deadline) }} ·
            共 {{ submitList.length }} 份提交 ·
            已批改 {{ gradedCount }} / {{ submitList.length }}
          </span>
        </div>
        <div class="hw-info-content">{{ currentHomework.homeworkContent || '无详细内容' }}</div>
      </div>

      <div class="grade-body">
        <!-- 左：学生提交列表（点一下切换，不用关弹窗重开） -->
        <div class="grade-list">
          <div
            v-for="item in submitList"
            :key="item.submitId"
            class="grade-item"
            :class="{ 'is-active': currentSubmit && currentSubmit.submitId === item.submitId }"
            @click="selectSubmit(item)"
          >
            <div class="grade-item-row">
              <span class="grade-item-name">{{ getStudentName(item.studentId) }}</span>
              <el-tag size="small" :type="item.correctStatus === 1 ? 'success' : 'info'">
                {{ item.correctStatus === 1 ? '已批改' : '待批改' }}
              </el-tag>
            </div>
            <div class="grade-item-row grade-item-sub">
              <span>{{ formatDate(item.submitTime) }}</span>
              <el-tag v-if="isLate(item)" size="small" type="danger">迟交</el-tag>
            </div>
          </div>
          <div v-if="submitList.length === 0" class="grade-empty">暂无学生提交</div>
        </div>

        <!-- 右：内容预览 + 打分表单（一屏完成，不再"下载 → 看 → 弹窗 → 弹窗"） -->
        <div class="grade-panel" v-if="currentSubmit">
          <div class="grade-block">
            <div class="grade-block-title">提交内容</div>
            <div class="grade-text">{{ currentSubmit.submitContent || '（无文字内容）' }}</div>

            <!-- 附件在线预览：图片直接显示、PDF 内嵌，其他格式才需要下载 -->
            <div v-if="currentSubmit.submitFile" class="grade-file">
              <img
                v-if="isImageFile(currentSubmit.submitFile)"
                :src="currentSubmit.submitFile"
                class="grade-image"
                alt="作业附件"
              />
              <iframe
                v-else-if="isPdfFile(currentSubmit.submitFile)"
                :src="currentSubmit.submitFile"
                class="grade-pdf"
              ></iframe>
              <div v-else class="grade-file-tip">该格式无法在线预览</div>
              <!-- 预览之外仍保留下载入口：图片、PDF 有时也需要另存到本地 -->
              <div class="grade-file-actions">
                <el-button link type="primary" @click="downloadFile(currentSubmit.submitFile)">
                  下载附件
                </el-button>
                <span class="grade-file-name">{{ fileNameOf(currentSubmit.submitFile) }}</span>
              </div>
            </div>
          </div>

          <div class="grade-block">
            <div class="grade-block-title">评分</div>
            <div class="grade-form">
              <span class="grade-label">分数</span>
              <el-input-number v-model="gradeForm.score" :min="0" :max="100" :step="1" controls-position="right" />
              <span class="grade-label">评语</span>
              <el-input
                v-model="gradeForm.comment"
                type="textarea"
                :rows="3"
                placeholder="给学生几句反馈（可选）"
              />
              <div class="grade-quick">
                <span class="grade-quick-label">快捷评语</span>
                <el-tag
                  v-for="q in QUICK_COMMENTS"
                  :key="q"
                  class="grade-quick-tag"
                  @click="gradeForm.comment = q"
                >{{ q }}</el-tag>
              </div>
            </div>
          </div>

          <div class="grade-actions">
            <el-button v-if="currentSubmit.correctStatus === 1" @click="goNextPending">下一份待批改</el-button>
            <el-button type="primary" @click="doGrade">保存批改</el-button>
          </div>
        </div>
        <div class="grade-panel grade-empty" v-else>请从左侧选择一份提交</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, reactive } from 'vue'
import { useRoute } from 'vue-router'
import request from '../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check } from '@element-plus/icons-vue'

const homeworkList = ref([])
const hwTotal = ref(0)
const hwLoading = ref(false)
/** 作业列表的分页与搜索条件（服务端分页，见 fetchHomework） */
const hwQuery = reactive({ pageNum: 1, pageSize: 10, homeworkName: '' })

const scoreList = ref([])
const scoreTotal = ref(0)
const scoreLoading = ref(false)
/** 成绩列表的分页条件（服务端分页，见 fetchStudentScores） */
const scoreQuery = reactive({ pageNum: 1, pageSize: 10 })
const teacherCourses = ref([])
const role = ref(localStorage.getItem('role'))
const user = ref(JSON.parse(localStorage.getItem('user') || '{}'))
const studentActiveTab = ref('homework')

const route = useRoute()

/**
 * 支持从首页卡片带着 ?tab=score 直接落到「我的成绩」页签。
 * 该页签只对学员存在（教师端没有 el-tabs），所以非学员时直接忽略。
 */
const applyQueryTab = () => {
  const tab = route.query.tab
  if (typeof tab === 'string' && ['homework', 'score'].includes(tab) && role.value === 'student') {
    studentActiveTab.value = tab
  }
}
const courseMap = ref({})
const teacherMap = ref({})

const showAddDialog = ref(false)
const newHomework = ref({
  homeworkName: '',
  homeworkContent: '',
  courseId: null
})

const openAddDialog = async () => {
  if (teacherCourses.value.length === 0) {
    try {
      const res = await request.post('/course/query', { publishTeacherId: user.value.teacherId })
      teacherCourses.value = res
    } catch (e) {
      console.error(e)
    }
  }
  showAddDialog.value = true
}

const deleteHomework = async (hw) => {
  try {
    await ElMessageBox.confirm('确定要删除该作业吗？删除后相关的提交记录也会被清除。', '提示', { type: 'warning' })
    await request.delete(`/homework/${hw.homeworkId}`)
    ElMessage.success('删除成功')
    fetchHomework()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const showSubmitDialog = ref(false)
const showGradeDialog = ref(false)
const submitList = ref([])
const currentHomework = ref(null)
const submitContent = ref('')
const submitFile = ref('')
const hasSubmitted = ref(false)
const currentSubmitId = ref(null)
const uploadRef = ref(null)

/**
 * el-upload 走的是内置 XMLHttpRequest，**不经过** request.js 拦截器，
 * 拿到的是后端原始响应 { code, message, data }，需自行判断；
 * headers 也必须补上 token，否则上传请求会被鉴权拦成 401。
 */
const uploadHeaders = {
  Authorization: `Bearer ${localStorage.getItem('token') || ''}`
}

const handleUploadSuccess = (res) => {
  if (res.code === 200 && res.data) {
    submitFile.value = res.data.url
    ElMessage.success('照片/附件上传成功')
  } else {
    ElMessage.error(res.message || '上传失败')
  }
}

const handleUploadError = () => {
  ElMessage.error('上传失败')
}

const downloadFile = (url) => {
  if (!url) {
    ElMessage.warning('暂无可下载的附件')
    return
  }

  const link = document.createElement('a')
  link.href = url
  link.download = ''
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

// 原「下载内容」按钮已移除：批改面板支持附件在线预览，不再需要先把文件下载到本地

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString()
}

const getCourseName = (courseId) => {
  return courseMap.value[courseId] || `课程ID: ${courseId}`
}

const getTeacherName = (teacherId) => {
  return teacherMap.value[teacherId] || `教师ID: ${teacherId}`
}

const fetchStudentScores = async () => {
  if (role.value !== 'student') {
    return
  }

  scoreLoading.value = true
  try {
    // 成绩走服务端分页；课程名与教师名的映射表仍一次性拉取
    //（数据量小，且表格每一行都要用它们把 id 换成名字）
    const [page, courses, teachers] = await Promise.all([
      request.post('/score/page', {
        pageNum: scoreQuery.pageNum,
        pageSize: scoreQuery.pageSize,
        studentId: user.value.studentId
      }),
      request.get('/course/list'),
      request.get('/teacher/list')
    ])

    scoreList.value = page?.records || []
    scoreTotal.value = page?.total || 0
    courseMap.value = (courses || []).reduce((acc, item) => {
      acc[item.courseId] = item.courseName
      return acc
    }, {})
    teacherMap.value = (teachers || []).reduce((acc, item) => {
      acc[item.teacherId] = item.teacherName
      return acc
    }, {})
  } catch (e) {
    console.error(e)
  } finally {
    scoreLoading.value = false
  }
}

/**
 * 作业列表（服务端分页 + 搜索）。
 *
 * 教师：按 publishTeacherId 过滤，只查自己发布的作业。
 * 学员：先取出"已报名且审核通过"的课程 ID，再用 courseIdIn 传给分页接口 ——
 *       过滤在数据库里完成，不再把全部作业拉到浏览器里 filter。
 */
const fetchHomework = async () => {
  hwLoading.value = true
  try {
    const params = { pageNum: hwQuery.pageNum, pageSize: hwQuery.pageSize }
    // 搜索用 homeworkNameLike：QueryUtil 约定「条件名以 Like 结尾」即模糊匹配
    if (hwQuery.homeworkName) params.homeworkNameLike = hwQuery.homeworkName.trim()

    if (role.value === 'teacher') {
      params.publishTeacherId = user.value.teacherId
    } else if (role.value === 'student') {
      const applies = await request.post('/course-apply/query', {
        studentId: user.value.studentId,
        auditStatus: 1
      })
      const courseIds = (applies || []).map((apply) => apply.courseId)
      if (courseIds.length === 0) {
        // 没有任何已通过的课程 → 直接空列表。
        // 注意不能把空数组当条件传下去：SQL 里 IN () 是语法错误。
        homeworkList.value = []
        hwTotal.value = 0
        return
      }
      params.courseIdIn = courseIds
    }

    const res = await request.post('/homework/page', params)
    homeworkList.value = res?.records || []
    hwTotal.value = res?.total || 0
  } catch (e) {
    console.error(e)
  } finally {
    hwLoading.value = false
  }
}

/** 条件变化后回到第 1 页重新查 */
const searchHomework = () => {
  hwQuery.pageNum = 1
  fetchHomework()
}

const resetHomeworkSearch = () => {
  hwQuery.homeworkName = ''
  hwQuery.pageNum = 1
  fetchHomework()
}

/** 成绩分页：改每页条数时同样要回到第 1 页 */
const searchScores = () => {
  scoreQuery.pageNum = 1
  fetchStudentScores()
}

const handleAddHomework = async () => {
  newHomework.value.publishTeacherId = user.value.teacherId
  newHomework.value.publishTime = new Date()
  newHomework.value.deadline = new Date(Date.now() + 7 * 24 * 3600 * 1000) // 7 days later
  await request.post('/homework/save', newHomework.value)
  ElMessage.success('发布成功')
  showAddDialog.value = false
  fetchHomework()
}

const openSubmitDialog = async (hw) => {
  currentHomework.value = hw
  submitContent.value = ''
  submitFile.value = ''
  hasSubmitted.value = false
  currentSubmitId.value = null
  
  if (uploadRef.value) {
    uploadRef.value.clearFiles()
  }

  try {
    const res = await request.post('/homework-submit/query', {
      homeworkId: hw.homeworkId,
      studentId: user.value.studentId
    })
    if (res && res.length > 0) {
      hasSubmitted.value = true
      currentSubmitId.value = res[0].submitId
      submitContent.value = res[0].submitContent
      submitFile.value = res[0].submitFile
    }
  } catch (e) {
    console.error(e)
  }

  showSubmitDialog.value = true
}

const handleRecallSubmit = async () => {
  try {
    await ElMessageBox.confirm('确定要撤回该次作业提交吗？撤回后需要重新提交。', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await request.delete(`/homework-submit/${currentSubmitId.value}`)
    ElMessage.success('撤回成功，请重新提交')
    hasSubmitted.value = false
    submitContent.value = ''
    submitFile.value = ''
    if (uploadRef.value) {
      uploadRef.value.clearFiles()
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('撤回失败')
    }
  }
}

const handleSubmitHomework = async () => {
  if (!submitContent.value && !submitFile.value) {
    ElMessage.warning('内容或附件至少填写一项')
    return
  }
  const data = {
    homeworkId: currentHomework.value.homeworkId,
    studentId: user.value.studentId,
    submitContent: submitContent.value,
    submitFile: submitFile.value,
    submitTime: new Date(),
    correctStatus: 0
  }
  await request.post('/homework-submit/save', data)
  ElMessage.success('作业提交成功')
  showSubmitDialog.value = false
  fetchStudentScores()
}

/* ==================== 批改面板 ==================== */

/** 当前选中的提交记录 */
const currentSubmit = ref(null)
/** 打分表单：分数 + 评语（一次提交，不再两次弹窗） */
const gradeForm = reactive({ score: null, comment: '' })
/** 学员 ID → 姓名，避免批改时只能看到一串 ID */
const studentMap = ref({})

/** 常用评语，点一下填进评语框，省去每次手打 */
const QUICK_COMMENTS = [
  '思路清晰，完成质量高。',
  '基本完成，注意边界情况的处理。',
  '代码可运行，建议补充注释与文档。',
  '问题较多，建议重做后重新提交。'
]

/** 已批改份数（弹窗顶部显示进度） */
const gradedCount = computed(
  () => submitList.value.filter((item) => item.correctStatus === 1).length
)

const getStudentName = (studentId) => studentMap.value[studentId] || `学员 ${studentId}`

/**
 * 是否迟交：提交时间晚于作业截止时间。
 *
 * 放在前端算是因为两个时间都已经在手上了（作业里有 deadline、提交记录里有 submitTime），
 * 不必为此多跑一个接口。
 */
const isLate = (record) => {
  const deadline = currentHomework.value?.deadline
  if (!deadline || !record?.submitTime) return false
  return new Date(record.submitTime) > new Date(deadline)
}

const isImageFile = (url) => /\.(png|jpe?g|gif|webp|bmp)$/i.test(url || '')
const isPdfFile = (url) => /\.pdf$/i.test(url || '')

/** 从附件地址里取文件名，显示在下载按钮旁边 */
const fileNameOf = (url) => (url || '').split('/').pop()

/** 选中一份提交，并把已打过的分数 / 评语回填到表单 */
const selectSubmit = async (record) => {
  currentSubmit.value = record
  gradeForm.score = null
  gradeForm.comment = ''
  try {
    const res = await request.post('/score/query', {
      studentId: record.studentId,
      courseId: currentHomework.value.courseId
    })
    const exist = res && res.length > 0 ? res[0] : null
    if (exist) {
      gradeForm.score = exist.homeworkScore ?? exist.totalScore ?? null
      gradeForm.comment = exist.scoreComment || ''
    }
  } catch (e) {
    console.error(e)
  }
}

/** 跳到下一份未批改的提交，支持连续批改 */
const goNextPending = () => {
  const list = submitList.value
  const index = list.findIndex((item) => item.submitId === currentSubmit.value?.submitId)
  const next = list.slice(index + 1).find((item) => item.correctStatus === 0)
    || list.find((item) => item.correctStatus === 0)
  if (next) {
    selectSubmit(next)
  } else {
    ElMessage.success('这份作业的提交都已批改完成')
  }
}

const gradeHomework = async (hw) => {
  currentHomework.value = hw
  currentSubmit.value = null
  gradeForm.score = null
  gradeForm.comment = ''
  try {
    const [submits, students] = await Promise.all([
      request.post('/homework-submit/query', { homeworkId: hw.homeworkId }),
      // 取学员姓名做映射；失败也不该挡住批改，所以单独兜底
      request.get('/student/list').catch(() => [])
    ])
    submitList.value = submits || []
    studentMap.value = (students || []).reduce((acc, student) => {
      acc[student.studentId] = student.studentName
      return acc
    }, {})
    // 默认选中第一份待批改的，打开弹窗就能直接干活
    const firstPending = submitList.value.find((item) => item.correctStatus === 0)
      || submitList.value[0]
    if (firstPending) {
      await selectSubmit(firstPending)
    }
    showGradeDialog.value = true
  } catch (e) {
    console.error(e)
  }
}

/**
 * 保存批改：分数、评语、提交状态一次写入。
 *
 * 与原实现的区别：原来分两次弹窗（先输分数、再输评语），中途点取消会丢掉已输入的内容；
 * 现在表单集中在一个面板里，点一次「保存批改」即可。
 */
const doGrade = async () => {
  if (!currentSubmit.value) {
    ElMessage.warning('请先从左侧选择一份提交')
    return
  }
  const score = Number(gradeForm.score)
  if (gradeForm.score === null || gradeForm.score === undefined || gradeForm.score === ''
      || !Number.isInteger(score) || score < 0 || score > 100) {
    ElMessage.warning('分数需要是 0~100 的整数')
    return
  }

  try {
    const record = currentSubmit.value
    const scoreRes = await request.post('/score/query', {
      studentId: record.studentId,
      courseId: currentHomework.value.courseId
    })
    const exist = scoreRes && scoreRes.length > 0 ? scoreRes[0] : null

    // 已有成绩则更新，没有则新建
    const scoreData = exist
      ? { ...exist }
      : {
          studentId: record.studentId,
          courseId: currentHomework.value.courseId,
          examScore: 0
        }
    scoreData.homeworkScore = score
    scoreData.totalScore = Math.round(((scoreData.homeworkScore || 0) + (scoreData.examScore || 0)) / 2)
    scoreData.scoreComment = gradeForm.comment || ''
    scoreData.scoreTeacherId = user.value.teacherId
    scoreData.scoreTime = new Date()
    await request.post('/score/save', scoreData)

    // 提交记录标记为已批改
    const submitPayload = { ...record, correctStatus: 1 }
    delete submitPayload.scoreId   // 移除不属于该表的字段，防止后端报错
    await request.post('/homework-submit/save', submitPayload)

    ElMessage.success('批改已保存')

    // 刷新列表并保持当前选中，方便接着批下一份
    const res = await request.post('/homework-submit/query', { homeworkId: currentHomework.value.homeworkId })
    submitList.value = res
    const refreshed = res.find((item) => item.submitId === record.submitId)
    if (refreshed) {
      currentSubmit.value = refreshed
    }
    fetchStudentScores()
  } catch (e) {
    ElMessage.error('批改失败')
  }
}

onMounted(() => {
  // 先按 URL 参数定位页签，再拉数据（从首页点卡片过来能直接落在对应页签上）
  applyQueryTab()
  fetchHomework()
  fetchStudentScores()
})
</script>

<style scoped>
/* ==================== 批改面板 ==================== */

/* 顶部作业信息：批改时不必退出去翻题目和截止时间 */
.hw-info {
  margin-bottom: 14px;
  padding: 12px 14px;
  background: #f5f7fa;
  border-radius: 6px;
}

.hw-info-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}

.hw-info-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.hw-info-meta {
  font-size: 12px;
  color: #909399;
}

.hw-info-content {
  max-height: 76px;
  overflow-y: auto;
  font-size: 13px;
  line-height: 1.6;
  color: #606266;
  white-space: pre-wrap;
}

/* 左右两栏：左边选人、右边批改，一屏完成 */
.grade-body {
  display: grid;
  grid-template-columns: 240px 1fr;
  gap: 14px;
  min-height: 420px;
}

.grade-list {
  max-height: 480px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.grade-item {
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
  transition: background 0.2s;
}

.grade-item:last-child {
  border-bottom: none;
}

.grade-item:hover {
  background: #f5f7fa;
}

/* 选中项用左侧色条标记，比整块变色更容易分辨 */
.grade-item.is-active {
  background: #ecf5ff;
  box-shadow: inset 3px 0 0 var(--el-color-primary);
}

.grade-item-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.grade-item-name {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.grade-item-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.grade-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.grade-block {
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.grade-block-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.grade-text {
  max-height: 120px;
  overflow-y: auto;
  font-size: 13px;
  line-height: 1.7;
  color: #606266;
  white-space: pre-wrap;
}

.grade-file {
  margin-top: 10px;
}

/* 预览之外的下载入口：图片 / PDF 有时也需要另存到本地 */
.grade-file-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.grade-file-name {
  font-size: 12px;
  color: #909399;
}

.grade-file-tip {
  font-size: 13px;
  color: #909399;
}

/* 图片直接铺开预览，省掉"下载再打开"这一步 */
.grade-image {
  max-width: 100%;
  max-height: 240px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.grade-pdf {
  width: 100%;
  height: 260px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.grade-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.grade-label {
  font-size: 13px;
  color: #606266;
}

.grade-quick {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
}

.grade-quick-label {
  font-size: 12px;
  color: #909399;
}

.grade-quick-tag {
  cursor: pointer;
}

.grade-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.grade-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  font-size: 13px;
  color: #b1b3b8;
}

@media (max-width: 768px) {
  .grade-body {
    grid-template-columns: 1fr;
  }
}
</style>
