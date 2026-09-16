<template>
  <div>
    <h2>作业管理</h2>

    <el-tabs v-if="role === 'student'" v-model="studentActiveTab">
      <el-tab-pane label="我的作业" name="homework">
        <el-table :data="homeworkList" style="width: 100%">
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
      </el-tab-pane>

      <el-tab-pane label="我的成绩" name="score">
        <el-table :data="scoreList" style="width: 100%">
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
      </el-tab-pane>
    </el-tabs>

    <template v-else>
      <el-table :data="homeworkList" style="width: 100%">
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

      <el-button type="success" style="margin-top: 20px" @click="openAddDialog">发布新作业</el-button>
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

    <el-dialog v-model="showGradeDialog" title="批改作业" width="60%">
      <el-table :data="submitList" style="width: 100%">
        <el-table-column prop="studentId" label="学员ID"></el-table-column>
        <el-table-column prop="submitTime" label="提交时间">
          <template #default="scope">{{ formatDate(scope.row.submitTime) }}</template>
        </el-table-column>
        <el-table-column prop="correctStatus" label="状态">
          <template #default="scope">
            <el-tag :type="scope.row.correctStatus === 1 ? 'success' : 'info'">
              {{ scope.row.correctStatus === 1 ? '已批改' : '未批改' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作">
          <template #default="scope">
            <el-button size="small" type="info" @click="downloadSubmitContent(scope.row)">下载内容</el-button>
            <el-button size="small" :type="scope.row.correctStatus === 0 ? 'primary' : 'warning'" @click="doGrade(scope.row)">
              {{ scope.row.correctStatus === 0 ? '批改' : '修改评语/分数' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check } from '@element-plus/icons-vue'

const homeworkList = ref([])
const scoreList = ref([])
const teacherCourses = ref([])
const role = ref(localStorage.getItem('role'))
const user = ref(JSON.parse(localStorage.getItem('user') || '{}'))
const studentActiveTab = ref('homework')
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

const handleUploadSuccess = (res) => {
  if (res.success) {
    submitFile.value = res.url
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

const downloadSubmitContent = (record) => {
  if (record.submitFile) {
    downloadFile(record.submitFile)
    return
  }

  if (record.submitContent) {
    const blob = new Blob([record.submitContent], { type: 'text/plain;charset=utf-8' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${currentHomework.value?.homeworkName || '作业内容'}-${record.studentId}.txt`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    return
  }

  ElMessage.warning('该提交暂无可下载内容')
}

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

  try {
    const [scores, courses, teachers] = await Promise.all([
      request.post('/score/query', { studentId: user.value.studentId }),
      request.get('/course/list'),
      request.get('/teacher/list')
    ])

    scoreList.value = scores || []
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
  }
}

const fetchHomework = async () => {
  try {
    if (role.value === 'teacher') {
      const res = await request.post('/homework/query', { publishTeacherId: user.value.teacherId })
      homeworkList.value = res
    } else if (role.value === 'student') {
      // 获取已报名且审核通过的课程
      const appliesRes = await request.post('/course-apply/query', { studentId: user.value.studentId, auditStatus: 1 })
      if (appliesRes && appliesRes.length > 0) {
        const courseIds = appliesRes.map(apply => apply.courseId)
        const allHomeworks = await request.get('/homework/list')
        // 过滤出已报名课程的作业
        homeworkList.value = allHomeworks.filter(hw => courseIds.includes(hw.courseId))
      } else {
        // 没有已报名的课程，作业列表为空
        homeworkList.value = []
      }
    } else {
      const res = await request.get('/homework/list')
      homeworkList.value = res
    }
  } catch (e) {
    console.error(e)
  }
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

const gradeHomework = async (hw) => {
  currentHomework.value = hw
  try {
    const res = await request.post('/homework-submit/query', { homeworkId: hw.homeworkId })
    submitList.value = res
    showGradeDialog.value = true
  } catch (e) {
    console.error(e)
  }
}

const doGrade = async (submitRecord) => {
  try {
    const scoreRes = await request.post('/score/query', {
      studentId: submitRecord.studentId,
      courseId: currentHomework.value.courseId
    })

    const currentScore = scoreRes && scoreRes.length > 0 ? scoreRes[0] : null

    const { value: score } = await ElMessageBox.prompt('请输入分数', '批改作业', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValidator: (value) => {
        if (value === '') {
          return '分数不能为空'
        }
        if (!/^\d+$/.test(value)) {
          return '分数必须是整数'
        }
        const numericValue = Number(value)
        if (numericValue < 0 || numericValue > 100) {
          return '分数必须在0到100之间'
        }
        return true
      },
      inputValue: currentScore?.homeworkScore ?? currentScore?.totalScore ?? ''
    })

    if (score) {
      const { value: comment } = await ElMessageBox.prompt('请输入教师评语', '批改评语', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputValue: currentScore?.scoreComment || '',
        inputPlaceholder: '请输入教师评语'
      })

      let scoreData = {
        studentId: submitRecord.studentId,
        courseId: currentHomework.value.courseId,
        homeworkScore: parseInt(score),
        examScore: 0,
        totalScore: parseInt(score),
        scoreComment: comment || '',
        scoreTeacherId: user.value.teacherId,
        scoreTime: new Date()
      }

      if (scoreRes && scoreRes.length > 0) {
        scoreData = scoreRes[0]
        scoreData.homeworkScore = parseInt(score)
        scoreData.totalScore = Math.round(((scoreData.homeworkScore || 0) + (scoreData.examScore || 0)) / 2)
        scoreData.scoreComment = comment || ''
        scoreData.scoreTeacherId = user.value.teacherId
        scoreData.scoreTime = new Date()
      } else {
        scoreData.totalScore = Math.round((scoreData.homeworkScore || 0) / 2)
      }

      submitRecord.correctStatus = 1
      delete submitRecord.scoreId // 移除不存在的字段，防止后端报错
      await request.post('/homework-submit/save', submitRecord)
      await request.post('/score/save', scoreData)

      ElMessage.success('批改成功')
      const res = await request.post('/homework-submit/query', { homeworkId: currentHomework.value.homeworkId })
      submitList.value = res
      fetchStudentScores()
    }
  } catch (e) {
    // cancelled or error
  }
}

onMounted(() => {
  fetchHomework()
  fetchStudentScores()
})
</script>
