<template>
  <div>
    <h2>课程资源</h2>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="所有课程" name="all" v-if="role === 'student'">
        <el-table :data="courses" style="width: 100%">
          <el-table-column prop="courseName" label="课程名称"></el-table-column>
          <el-table-column prop="trainCycle" label="培训周期"></el-table-column>
          <el-table-column prop="publishTime" label="发布时间">
            <template #default="scope">{{ formatDate(scope.row.publishTime) }}</template>
          </el-table-column>
          <el-table-column label="操作">
            <template #default="scope">
              <el-button size="small" type="primary" @click="applyCourse(scope.row)">报名</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="我已报名的课程" name="enrolled" v-if="role === 'student'">
        <el-table :data="enrolledCourses" style="width: 100%">
          <el-table-column prop="courseName" label="课程名称"></el-table-column>
          <el-table-column prop="trainCycle" label="培训周期"></el-table-column>
          <el-table-column prop="publishTime" label="发布时间">
            <template #default="scope">{{ formatDate(scope.row.publishTime) }}</template>
          </el-table-column>
          <el-table-column label="操作">
            <template #default="scope">
              <el-button size="small" type="primary" @click="viewResources(scope.row)">资源下载</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="我发布的课程" name="my" v-if="role === 'teacher'">
        <el-table :data="courses" style="width: 100%">
          <el-table-column prop="courseName" label="课程名称"></el-table-column>
          <el-table-column prop="trainCycle" label="培训周期"></el-table-column>
          <el-table-column prop="auditStatus" label="状态">
            <template #default="scope">
              <el-tag :type="scope.row.auditStatus === 1 ? 'success' : (scope.row.auditStatus === 2 ? 'danger' : 'info')">
                {{ scope.row.auditStatus === 1 ? '已通过' : (scope.row.auditStatus === 2 ? '已驳回' : '待审核') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="300">
            <template #default="scope">
              <el-button size="small" @click="viewResources(scope.row)">管理资源</el-button>
              <el-button size="small" type="info" @click="viewStudents(scope.row)">查看学员</el-button>
              <el-button size="small" type="danger" @click="deleteCourse(scope.row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button type="success" style="margin-top: 20px" @click="showAddDialog = true">发布新课程</el-button>
      </el-tab-pane>
      <el-tab-pane label="报名审核" name="apply" v-if="role === 'teacher'">
        <el-table :data="applies" style="width: 100%">
          <el-table-column prop="courseName" label="课程名称"></el-table-column>
          <el-table-column prop="studentName" label="学员姓名"></el-table-column>
          <el-table-column prop="applyTime" label="申请时间">
            <template #default="scope">{{ formatDate(scope.row.applyTime) }}</template>
          </el-table-column>
          <el-table-column prop="auditStatus" label="状态">
            <template #default="scope">
              <el-tag :type="scope.row.auditStatus === 1 ? 'success' : (scope.row.auditStatus === 2 ? 'danger' : 'info')">
                {{ scope.row.auditStatus === 1 ? '已通过' : (scope.row.auditStatus === 2 ? '已驳回' : '待审核') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作">
            <template #default="scope">
              <el-button size="small" type="success" v-if="scope.row.auditStatus === 0" @click="auditApply(scope.row, 1)">通过</el-button>
              <el-button size="small" type="danger" v-if="scope.row.auditStatus === 0" @click="auditApply(scope.row, 2)">驳回</el-button>
              <el-button size="small" type="warning" v-if="scope.row.auditStatus === 2" @click="auditApply(scope.row, 0)">撤回驳回</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showAddDialog" title="发布新课程">
      <el-form :model="newCourse">
        <el-form-item label="课程名称"><el-input v-model="newCourse.courseName"></el-input></el-form-item>
        <el-form-item label="培训周期"><el-input v-model="newCourse.trainCycle"></el-input></el-form-item>
        <el-form-item label="课程介绍"><el-input type="textarea" v-model="newCourse.courseIntro"></el-input></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAddCourse">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showResourceDialog" title="课程资源" width="60%">
      <el-button type="primary" v-if="role === 'teacher'" @click="showAddResourceDialog = true" style="margin-bottom: 10px;">上传资源</el-button>
      <el-table :data="resources" style="width: 100%">
        <el-table-column prop="resourceName" label="资源名称"></el-table-column>
        <el-table-column prop="resourceType" label="类型"></el-table-column>
        <el-table-column prop="uploadTime" label="上传时间">
          <template #default="scope">{{ formatDate(scope.row.uploadTime) }}</template>
        </el-table-column>
        <el-table-column label="操作">
          <template #default="scope">
            <el-button size="small" type="primary" @click="downloadResource(scope.row)">直接下载</el-button>
            <el-button v-if="role === 'teacher'" size="small" type="danger" @click="deleteResource(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="showAddResourceDialog" title="上传资源">
      <el-form :model="newResource">
        <el-form-item label="资源名称"><el-input v-model="newResource.resourceName"></el-input></el-form-item>
        <el-form-item label="资源类型">
          <el-select v-model="newResource.resourceType" placeholder="请选择资源类型">
            <el-option label="视频 (Video)" value="Video"></el-option>
            <el-option label="课件 (PDF)" value="PDF"></el-option>
            <el-option label="图片 (Image)" value="Image"></el-option>
            <el-option label="其他 (Other)" value="Other"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="上传文件">
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
              <div class="el-upload__tip">支持图片、PDF、视频等文件</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddResourceDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAddResource">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showStudentsDialog" title="课程学员列表">
      <el-table :data="courseStudents" style="width: 100%">
        <el-table-column prop="studentAccount" label="学员账号"></el-table-column>
        <el-table-column prop="studentName" label="学员姓名"></el-table-column>
        <el-table-column prop="studentPhone" label="联系电话"></el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import request from '../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const courses = ref([])
const enrolledCourses = ref([])
const applies = ref([])
const resources = ref([])
const role = ref(localStorage.getItem('role'))
const user = ref(JSON.parse(localStorage.getItem('user') || '{}'))
const activeTab = ref(role.value === 'teacher' ? 'my' : 'all')

const showAddDialog = ref(false)
const showResourceDialog = ref(false)
const showAddResourceDialog = ref(false)
const showStudentsDialog = ref(false)
const courseStudents = ref([])
const currentCourse = ref(null)
const uploadRef = ref(null)
const newCourse = ref({
  courseName: '',
  trainCycle: '',
  courseIntro: ''
})
const newResource = ref({
  resourceName: '',
  resourceType: '',
  resourcePath: ''
})

/**
 * el-upload 用的是它内置的 XMLHttpRequest，**不经过** request.js 拦截器，
 * 所以这里拿到的是后端返回的原始 JSON { code, message, data }，要自己判断。
 * 同理 headers 必须显式补上 token —— 否则上传请求会被鉴权拦成 401。
 */
const uploadHeaders = {
  Authorization: `Bearer ${localStorage.getItem('token') || ''}`
}

const handleUploadSuccess = (res) => {
  if (res.code === 200 && res.data) {
    newResource.value.resourcePath = res.data.url
    ElMessage.success('文件上传成功')
  } else {
    ElMessage.error(res.message || '文件上传失败')
  }
}

const handleUploadError = () => {
  ElMessage.error('文件上传失败')
}

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString()
}

const fetchCourses = async () => {
  try {
    if (role.value === 'teacher') {
      const res = await request.post('/course/query', { publishTeacherId: user.value.teacherId })
      courses.value = res
    } else if (role.value === 'student') {
      const res = await request.post('/course/query', { auditStatus: 1 })
      courses.value = res
    }
  } catch (e) {
    console.error(e)
  }
}

const fetchEnrolledCourses = async () => {
  try {
    const appliesRes = await request.post('/course-apply/query', { studentId: user.value.studentId, auditStatus: 1 })
    const courseIds = appliesRes.map(a => a.courseId)
    if (courseIds.length === 0) {
      enrolledCourses.value = []
      return
    }
    const allCourses = await request.post('/course/query', { auditStatus: 1 })
    enrolledCourses.value = allCourses.filter(c => courseIds.includes(c.courseId))
  } catch (e) {
    console.error(e)
  }
}

const fetchApplies = async () => {
  try {
    const res = await request.get('/course-apply/list')
    const myCourses = await request.post('/course/query', { publishTeacherId: user.value.teacherId })
    const myCourseIds = myCourses.map(c => c.courseId)
    let filteredApplies = res.filter(apply => myCourseIds.includes(apply.courseId))

    const allStudents = await request.get('/student/list')
    
    filteredApplies = filteredApplies.map(apply => {
      const student = allStudents.find(s => s.studentId === apply.studentId)
      const course = myCourses.find(c => c.courseId === apply.courseId)
      return {
        ...apply,
        studentName: student ? student.studentName : '未知学员',
        courseName: course ? course.courseName : '未知课程'
      }
    })
    applies.value = filteredApplies
  } catch (e) {
    console.error(e)
  }
}

const handleTabChange = () => {
  if (activeTab.value === 'apply') {
    fetchApplies()
  } else if (activeTab.value === 'enrolled') {
    fetchEnrolledCourses()
  } else {
    fetchCourses()
  }
}

const auditApply = async (apply, status) => {
  try {
    apply.auditStatus = status
    apply.auditTeacherId = status === 0 ? null : user.value.teacherId
    await request.post('/course-apply/save', apply)
    ElMessage.success(
      status === 1 ? '报名已通过' : (status === 2 ? '报名已驳回' : '已撤回驳回，报名恢复为待审核')
    )
    fetchApplies()
  } catch (e) {
    ElMessage.error('审核失败')
  }
}

const handleAddCourse = async () => {
  if (!newCourse.value.courseName?.trim() || !newCourse.value.trainCycle?.trim()) {
    ElMessage.warning('课程名称和培训周期不能为空')
    return
  }
  newCourse.value.publishTeacherId = user.value.teacherId
  newCourse.value.publishTime = new Date()
  newCourse.value.auditStatus = 0 // 待审核
  await request.post('/course/save', newCourse.value)
  ElMessage.success('发布成功，等待管理员审核')
  showAddDialog.value = false
  newCourse.value = {
    courseName: '',
    trainCycle: '',
    courseIntro: ''
  }
  fetchCourses()
}

const applyCourse = async (course) => {
  try {
    // 检查是否已经报名过
    const checkRes = await request.post('/course-apply/query', { studentId: user.value.studentId, courseId: course.courseId })
    if (checkRes && checkRes.length > 0) {
      const applyRecord = checkRes[0]
      if (applyRecord.auditStatus === 0) {
        ElMessage.warning('你已报名该课程，请等待教师审核。')
      } else if (applyRecord.auditStatus === 1) {
        ElMessage.warning('你已报名成功，无需重复报名。')
      } else {
        ElMessage.warning('你的报名曾被驳回，请联系教师。')
      }
      return
    }

    const applyData = {
      studentId: user.value.studentId,
      courseId: course.courseId,
      applyTime: new Date(),
      auditStatus: 0 // 待教师审核
    }
    await request.post('/course-apply/save', applyData)
    ElMessage.success('报名申请已提交，等待教师审核')
  } catch (e) {
    ElMessage.error('报名失败')
  }
}

const deleteCourse = async (course) => {
  try {
    await ElMessageBox.confirm('确定要删除该课程吗？相关资源、报名和作业信息也将被删除。此操作不可恢复。', '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await request.delete(`/course/${course.courseId}`)
    ElMessage.success('课程删除成功')
    fetchCourses()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除课程失败')
    }
  }
}

const viewResources = async (course) => {
  currentCourse.value = course
  try {
    const res = await request.post('/course-resource/query', { courseId: course.courseId })
    resources.value = res
    showResourceDialog.value = true
  } catch (e) {
    console.error(e)
  }
}

const handleAddResource = async () => {
  if (!newResource.value.resourceName?.trim() || !newResource.value.resourceType) {
    ElMessage.warning('资源名称和资源类型不能为空')
    return
  }
  if (!newResource.value.resourcePath) {
    ElMessage.warning('请先上传文件')
    return
  }
  newResource.value.courseId = currentCourse.value.courseId
  newResource.value.uploadTeacherId = user.value.teacherId
  newResource.value.uploadTime = new Date()
  try {
    await request.post('/course-resource/save', newResource.value)
    ElMessage.success('保存成功')
    showAddResourceDialog.value = false
    if (uploadRef.value) {
      uploadRef.value.clearFiles()
    }
    const res = await request.post('/course-resource/query', { courseId: currentCourse.value.courseId })
    resources.value = res
    newResource.value = { resourceName: '', resourceType: '', resourcePath: '' }
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const downloadResource = (resource) => {
  if (resource.resourcePath) {
    const link = document.createElement('a')
    link.href = resource.resourcePath
    link.download = resource.resourceName || ''
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  } else {
    ElMessage.warning('该资源无有效路径')
  }
}

const deleteResource = async (resource) => {
  try {
    await ElMessageBox.confirm('确定要删除该资源吗？', '提示', { type: 'warning' })
    await request.delete(`/course-resource/${resource.resourceId}`)
    ElMessage.success('删除成功')
    const res = await request.post('/course-resource/query', { courseId: currentCourse.value.courseId })
    resources.value = res
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const viewStudents = async (course) => {
  try {
    const appliesRes = await request.post('/course-apply/query', { courseId: course.courseId, auditStatus: 1 })
    const allStudents = await request.get('/student/list')
    
    const studentIds = appliesRes.map(a => a.studentId)
    courseStudents.value = allStudents.filter(s => studentIds.includes(s.studentId))
    showStudentsDialog.value = true
  } catch (e) {
    console.error(e)
    ElMessage.error('获取学员列表失败')
  }
}

onMounted(() => {
  fetchCourses()
  if (role.value === 'teacher') {
    fetchApplies()
  } else if (role.value === 'student') {
    fetchEnrolledCourses()
  }
})

watch(activeTab, () => {
  handleTabChange()
})
</script>
