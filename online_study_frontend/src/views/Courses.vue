<template>
  <div>
    <div class="page-head">
      <h2>课程资源</h2>
      <p class="subtitle">选课报名 · 课件下载 · 课程管理</p>
    </div>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="所有课程" name="all" v-if="role === 'student'">
        <div class="filter-bar">
          <el-input
            v-model="courseQuery.courseName"
            placeholder="按课程名称搜索"
            clearable
            style="width: 220px"
            @keyup.enter="searchCourses"
            @clear="searchCourses"
          />
          <el-button type="primary" @click="searchCourses">搜索</el-button>
          <el-button @click="resetCourseSearch">重置</el-button>
        </div>
        <el-table :data="courses" v-loading="courseLoading" style="width: 100%">
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
        <div class="pagination-bar">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="courseTotal"
            :page-sizes="[5, 10, 20, 50]"
            v-model:current-page="courseQuery.pageNum"
            v-model:page-size="courseQuery.pageSize"
            @current-change="fetchCourses"
            @size-change="searchCourses"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="我已报名的课程" name="enrolled" v-if="role === 'student'">
        <el-table :data="enrolledCourses" style="width: 100%">
          <el-table-column prop="courseName" label="课程名称"></el-table-column>
          <el-table-column prop="trainCycle" label="培训周期"></el-table-column>
          <el-table-column label="报名状态" width="110">
            <template #default="scope">
              <el-tag :type="scope.row.applyStatus === 1 ? 'success' : (scope.row.applyStatus === 2 ? 'danger' : 'warning')">
                {{ scope.row.applyStatus === 1 ? '已通过' : (scope.row.applyStatus === 2 ? '已驳回' : '待审核') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="applyRemark" label="审核意见" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.applyRemark || '—' }}</template>
          </el-table-column>
          <el-table-column prop="publishTime" label="发布时间">
            <template #default="scope">{{ formatDate(scope.row.publishTime) }}</template>
          </el-table-column>
          <el-table-column label="操作">
            <template #default="scope">
              <!-- 只有审核通过后才能下载课件资源 -->
              <el-button size="small" type="primary" v-if="scope.row.applyStatus === 1" @click="viewResources(scope.row)">资源下载</el-button>
              <span v-else style="color: #909399; font-size: 12px">审核通过后可下载</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="我发布的课程" name="my" v-if="role === 'teacher'">
        <div class="filter-bar">
          <el-input
            v-model="courseQuery.courseName"
            placeholder="按课程名称搜索"
            clearable
            style="width: 220px"
            @keyup.enter="searchCourses"
            @clear="searchCourses"
          />
          <el-select v-model="courseQuery.auditStatus" placeholder="全部状态" clearable style="width: 130px">
            <el-option label="待审核" :value="0" />
            <el-option label="已通过" :value="1" />
            <el-option label="已驳回" :value="2" />
          </el-select>
          <el-button type="primary" @click="searchCourses">搜索</el-button>
          <el-button @click="resetCourseSearch">重置</el-button>
          <div class="spacer"></div>
          <el-button type="success" @click="showAddDialog = true">发布新课程</el-button>
        </div>
        <el-table :data="courses" v-loading="courseLoading" style="width: 100%">
          <el-table-column prop="courseName" label="课程名称"></el-table-column>
          <el-table-column prop="trainCycle" label="培训周期"></el-table-column>
          <el-table-column prop="auditStatus" label="状态">
            <template #default="scope">
              <el-tag :type="scope.row.auditStatus === 1 ? 'success' : (scope.row.auditStatus === 2 ? 'danger' : 'info')">
                {{ scope.row.auditStatus === 1 ? '已通过' : (scope.row.auditStatus === 2 ? '已驳回' : '待审核') }}
              </el-tag>
            </template>
          </el-table-column>
          <!-- 被驳回时展示管理员填写的原因，教师据此修改后重新提交 -->
          <el-table-column prop="auditRemark" label="审核意见" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.auditRemark || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="300">
            <template #default="scope">
              <el-button size="small" @click="viewResources(scope.row)">管理资源</el-button>
              <el-button size="small" type="info" @click="viewStudents(scope.row)">查看学员</el-button>
              <el-button size="small" type="danger" @click="deleteCourse(scope.row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-bar">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="courseTotal"
            :page-sizes="[5, 10, 20, 50]"
            v-model:current-page="courseQuery.pageNum"
            v-model:page-size="courseQuery.pageSize"
            @current-change="fetchCourses"
            @size-change="searchCourses"
          />
        </div>
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
import { ref, onMounted, watch, reactive } from 'vue'
import { useRoute } from 'vue-router'
import request from '../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const courses = ref([])
const courseTotal = ref(0)
const courseLoading = ref(false)
/** 课程列表的分页与搜索条件（服务端分页，见 fetchCourses） */
const courseQuery = reactive({ pageNum: 1, pageSize: 10, courseName: '', auditStatus: null })
const enrolledCourses = ref([])
const applies = ref([])
const resources = ref([])
const role = ref(localStorage.getItem('role'))
const user = ref(JSON.parse(localStorage.getItem('user') || '{}'))
const activeTab = ref(role.value === 'teacher' ? 'my' : 'all')

const route = useRoute()

/** 本页允许的页签名（用于校验 URL 上的 ?tab= 参数） */
const TAB_NAMES = ['all', 'enrolled', 'my', 'apply']

/**
 * 支持从首页数字卡片带着 ?tab=xxx 直接定位到指定页签，例如：
 *   /courses?tab=enrolled  学员「我已报名的课程」
 *   /courses?tab=apply     教师「报名审核」
 *
 * 只接受白名单内的值 —— URL 是用户随手就能改的，
 * 不能让它把页面切到一个不存在的页签上。
 */
const applyQueryTab = () => {
  const tab = route.query.tab
  if (typeof tab === 'string' && TAB_NAMES.includes(tab)) {
    activeTab.value = tab
  }
}

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
  courseLoading.value = true
  try {
    const params = { pageNum: courseQuery.pageNum, pageSize: courseQuery.pageSize }
    // 搜索用 courseNameLike：QueryUtil 约定「条件名以 Like 结尾」即模糊匹配
    if (courseQuery.courseName) params.courseNameLike = courseQuery.courseName.trim()

    if (role.value === 'teacher') {
      params.publishTeacherId = user.value.teacherId
      if (courseQuery.auditStatus !== null && courseQuery.auditStatus !== '') {
        params.auditStatus = courseQuery.auditStatus
      }
    } else {
      // 学员只能看到审核通过的课程
      params.auditStatus = 1
    }

    const res = await request.post('/course/page', params)
    courses.value = res?.records || []
    courseTotal.value = res?.total || 0
  } catch (e) {
    console.error(e)
  } finally {
    courseLoading.value = false
  }
}

/** 条件变化后从第一页重新查 */
const searchCourses = () => {
  courseQuery.pageNum = 1
  fetchCourses()
}

/**
 * 注意：分页器的 @size-change 也接到 searchCourses（而不是直接 fetchCourses）。
 * 因为改了每页条数后总页数会变，如果还停在原来的页码上就会请求到空数据：
 * 例如原本在第 5 页（每页 5 条），改成每页 50 条后总页数只剩 1 页，
 * 却仍请求第 5 页 → 表格显示"暂无数据"，用户会以为数据丢了。
 */

const resetCourseSearch = () => {
  courseQuery.courseName = ''
  courseQuery.auditStatus = null
  courseQuery.pageNum = 1
  fetchCourses()
}

const fetchEnrolledCourses = async () => {
  try {
    // 查该学员的全部报名（不过滤状态）：待审核、已驳回的也要能看到进度与驳回原因，
    // 否则学员报名后不知道卡在哪一步
    const appliesRes = await request.post('/course-apply/query', { studentId: user.value.studentId })
    if (!appliesRes || appliesRes.length === 0) {
      enrolledCourses.value = []
      return
    }
    const allCourses = await request.post('/course/query', { auditStatus: 1 })
    const courseMap = new Map(allCourses.map(c => [c.courseId, c]))
    // 把报名状态 / 审核意见合并到课程行上，供表格展示
    enrolledCourses.value = appliesRes
      .filter(a => courseMap.has(a.courseId))
      .map(a => ({
        ...courseMap.get(a.courseId),
        applyStatus: a.auditStatus,
        applyRemark: a.auditRemark,
        applyTime: a.applyTime,
      }))
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
    // 驳回必须填写原因：服务端强校验，并会同步释放该课程占用的名额，
    // 学员端也能看到这条原因，避免反复提交。
    let remark = null
    if (status === 2) {
      const { value } = await ElMessageBox.prompt('请填写驳回原因，学员端将看到这条说明', '驳回报名', {
        confirmButtonText: '确定驳回',
        cancelButtonText: '取消',
        inputPlaceholder: '例如：不符合报名条件 / 该课程名额已满',
        inputValidator: (v) => (v && v.trim() ? true : '驳回原因不能为空'),
        type: 'warning',
      })
      remark = value.trim()
    }

    // 审核走专用接口：审核人由服务端从 JWT 取，不再由前端传 teacherId；
    // 同时服务端会处理「驳回释放名额 / 撤销驳回重新抢名额」
    await request.post('/course-apply/audit', {
      applyId: apply.applyId,
      auditStatus: status,
      auditRemark: remark,
    })
    ElMessage.success(
      status === 1 ? '报名已通过' : (status === 2 ? '报名已驳回' : '已撤回驳回，报名恢复为待审核')
    )
    fetchApplies()
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    // 例如「课程名额已满，无法恢复该报名」
    ElMessage.error(e?.message || '审核失败')
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
    // 报名改由服务端「原子占位」接口处理：
    //   服务端在一条 UPDATE 里同时完成「名额是否已满」的判断与占用，
    //   并用唯一索引兜底重复报名；名额满 / 已报名会以业务错误返回。
    // 前端不再做「先查是否已报名、再插入」的预检查 ——
    // 那种检查在并发下无效（两个请求会同时通过检查），而且多一次网络往返。
    await request.post('/course-apply/apply', { courseId: course.courseId })
    ElMessage.success('报名申请已提交，等待教师审核')
    fetchCourses()
  } catch (e) {
    // 服务端业务提示（如「课程名额已满（名额上限 5 人）」）优先展示
    ElMessage.error(e?.message || '报名失败')
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
  // 先按 URL 参数定位页签，再拉数据（这样从首页点卡片过来能直接落在对应页签上）
  applyQueryTab()
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
