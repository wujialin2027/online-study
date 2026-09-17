<template>
  <div>
    <div class="page-head">
      <h2>系统管理</h2>
      <p class="subtitle">账号管理 · 课程审核 · 操作日志（超级管理员专属）</p>
    </div>

    <el-tabs v-model="activeTab">
      <!-- ==================== 学员管理 ==================== -->
      <el-tab-pane label="学员管理" name="student">
        <el-table :data="students" style="width: 100%">
          <el-table-column prop="studentAccount" label="账号"></el-table-column>
          <el-table-column prop="studentName" label="姓名"></el-table-column>
          <el-table-column prop="studentPhone" label="手机号"></el-table-column>
          <el-table-column prop="accountStatus" label="状态">
            <template #default="scope">
              <el-tag :type="scope.row.accountStatus === 1 ? 'success' : 'danger'">
                {{ scope.row.accountStatus === 1 ? '正常' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220">
            <template #default="scope">
              <el-button size="small" type="primary" @click="changePassword(scope.row, 'student')">修改密码</el-button>
              <el-button size="small" type="danger" @click="toggleStatus(scope.row, 'student')">切换状态</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ==================== 教师管理 ==================== -->
      <el-tab-pane label="教师管理" name="teacher">
        <el-table :data="teachers" style="width: 100%">
          <el-table-column prop="teacherAccount" label="账号"></el-table-column>
          <el-table-column prop="teacherName" label="姓名"></el-table-column>
          <el-table-column prop="teacherPhone" label="手机号"></el-table-column>
          <el-table-column prop="accountStatus" label="状态">
            <template #default="scope">
              <el-tag :type="scope.row.accountStatus === 1 ? 'success' : 'danger'">
                {{ scope.row.accountStatus === 1 ? '正常' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220">
            <template #default="scope">
              <el-button size="small" type="primary" @click="changePassword(scope.row, 'teacher')">修改密码</el-button>
              <el-button size="small" type="danger" @click="toggleStatus(scope.row, 'teacher')">切换状态</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ==================== 课程审核 ==================== -->
      <el-tab-pane label="课程审核" name="course">
        <el-table :data="courses" style="width: 100%">
          <el-table-column prop="courseName" label="课程名称"></el-table-column>
          <el-table-column prop="trainCycle" label="周期"></el-table-column>
          <el-table-column prop="auditStatus" label="状态">
            <template #default="scope">
              <el-tag :type="scope.row.auditStatus === 1 ? 'success' : (scope.row.auditStatus === 2 ? 'danger' : 'info')">
                {{ scope.row.auditStatus === 1 ? '已通过' : (scope.row.auditStatus === 2 ? '已驳回' : '待审核') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200">
            <template #default="scope">
              <el-button size="small" type="success" v-if="scope.row.auditStatus === 0" @click="auditCourse(scope.row, 1)">通过</el-button>
              <el-button size="small" type="danger" v-if="scope.row.auditStatus === 0" @click="auditCourse(scope.row, 2)">驳回</el-button>
              <el-button size="small" type="danger" @click="deleteCourse(scope.row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ==================== 操作日志（AOP 自动记录） ==================== -->
      <el-tab-pane label="操作日志" name="log">
        <div class="filter-bar">
          <el-select v-model="logQuery.module" placeholder="全部模块" clearable style="width: 130px">
            <el-option label="课程" value="课程" />
            <el-option label="报名" value="报名" />
            <el-option label="用户" value="用户" />
          </el-select>
          <el-input
            v-model="logQuery.operatorName"
            placeholder="操作人账号"
            clearable
            style="width: 160px"
            @keyup.enter="searchLogs"
          />
          <el-select v-model="logQuery.success" placeholder="全部结果" clearable style="width: 120px">
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
          <el-button type="primary" @click="searchLogs">查询</el-button>
          <el-button @click="resetLogQuery">重置</el-button>
          <div class="spacer"></div>
          <el-button text @click="fetchLogs">刷新</el-button>
        </div>

        <el-table :data="logs" v-loading="logLoading" stripe style="width: 100%">
          <el-table-column prop="createTime" label="操作时间" width="170">
            <template #default="scope">{{ formatDateTime(scope.row.createTime) }}</template>
          </el-table-column>
          <el-table-column prop="operatorName" label="操作人" width="110" />
          <el-table-column label="角色" width="90">
            <template #default="scope">{{ ROLE_TEXT[scope.row.operatorRole] || scope.row.operatorRole }}</template>
          </el-table-column>
          <el-table-column prop="module" label="模块" width="90" />
          <el-table-column prop="operation" label="操作" width="130" />
          <el-table-column prop="requestMethod" label="方法" width="85" />
          <el-table-column prop="ip" label="客户端 IP" width="130" />
          <el-table-column label="耗时" width="90">
            <template #default="scope">{{ scope.row.costMs }} ms</template>
          </el-table-column>
          <el-table-column label="结果" width="90">
            <template #default="scope">
              <el-tag :type="scope.row.success === 1 ? 'success' : 'danger'" size="small">
                {{ scope.row.success === 1 ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="详情" width="80">
            <template #default="scope">
              <el-button size="small" text type="primary" @click="showLogDetail(scope.row)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-bar">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="logTotal"
            :page-sizes="[10, 20, 50]"
            v-model:current-page="logQuery.pageNum"
            v-model:page-size="logQuery.pageSize"
            @current-change="fetchLogs"
            @size-change="fetchLogs"
          />
        </div>

        <!-- 详情弹窗：请求参数（已由服务端脱敏）与失败原因 -->
        <el-dialog v-model="logDetailVisible" title="操作详情" width="640px">
          <el-descriptions :column="1" border v-if="currentLog">
            <el-descriptions-item label="操作时间">
              {{ formatDateTime(currentLog.createTime) }}
            </el-descriptions-item>
            <el-descriptions-item label="操作人">
              {{ currentLog.operatorName }}
              （{{ ROLE_TEXT[currentLog.operatorRole] || currentLog.operatorRole }}）
            </el-descriptions-item>
            <el-descriptions-item label="操作">
              {{ currentLog.module }} · {{ currentLog.operation }}
            </el-descriptions-item>
            <el-descriptions-item label="请求">
              {{ currentLog.requestMethod }} {{ currentLog.requestUri }}
            </el-descriptions-item>
            <el-descriptions-item label="客户端 IP">
              {{ currentLog.ip || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="耗时">
              {{ currentLog.costMs }} ms
            </el-descriptions-item>
            <el-descriptions-item label="请求参数">
              <pre class="param-pre">{{ prettyParams(currentLog.requestParams) }}</pre>
            </el-descriptions-item>
            <el-descriptions-item label="失败原因" v-if="currentLog.errorMsg">
              <span class="error-text">{{ currentLog.errorMsg }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </el-dialog>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import request from '../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const ROLE_TEXT = { admin: '管理员', teacher: '教师', student: '学员' }

const activeTab = ref('student')
const students = ref([])
const teachers = ref([])
const courses = ref([])

/* ------------------------- 账号与课程 ------------------------- */
const fetchUsers = async () => {
  try {
    const sRes = await request.get('/student/list')
    students.value = sRes
    const tRes = await request.get('/teacher/list')
    teachers.value = tRes
  } catch (e) {
    console.error(e)
  }
}

const fetchCourses = async () => {
  try {
    const res = await request.get('/course/list')
    courses.value = res
  } catch (e) {
    console.error(e)
  }
}

const toggleStatus = async (user, type) => {
  user.accountStatus = user.accountStatus === 1 ? 0 : 1
  await request.post(`/${type}/save`, user)
  ElMessage.success('状态更新成功')
  fetchUsers()
}

const changePassword = async (user, type) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入新密码', `修改${type === 'student' ? '学员' : '教师'}密码`, {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputType: 'password',
      inputValidator: (inputValue) => {
        if (!inputValue) {
          return '新密码不能为空'
        }
        if (inputValue.length < 6) {
          return '密码长度不能少于6位'
        }
        return true
      }
    })

    await request.post(`/${type}/update-password`, {
      [`${type}Id`]: user[`${type}Id`],
      newPassword: value
    })
    ElMessage.success('密码修改成功')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close' && !error?.response) {
      ElMessage.error('密码修改失败')
    }
  }
}

const auditCourse = async (course, status) => {
  try {
    // 驳回必须填写原因：服务端会强校验并把原因落到 course.audit_remark，
    // 教师端据此知道该改什么，所以这里必须先收集再提交。
    let remark = null
    if (status === 2) {
      const { value } = await ElMessageBox.prompt('请填写驳回原因，教师端将看到这条说明', '驳回课程', {
        confirmButtonText: '确定驳回',
        cancelButtonText: '取消',
        inputPlaceholder: '例如：课程介绍信息不完整 / 培训周期不合理',
        inputValidator: (v) => (v && v.trim() ? true : '驳回原因不能为空'),
        type: 'warning',
      })
      remark = value.trim()
    }

    // 审核走专用接口，权限（仅管理员）与留痕（审核人 / 时间）都由服务端写入
    await request.post('/course/audit', {
      courseId: course.courseId,
      auditStatus: status,
      auditRemark: remark,
    })
    ElMessage.success(status === 1 ? '审核已通过' : '已驳回')
    fetchCourses()
  } catch (e) {
    // 用户点了取消 / 关闭输入框，不算失败
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e?.message || '审核失败')
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

/* ------------------------- 操作日志 ------------------------- */
const logs = ref([])
const logTotal = ref(0)
const logLoading = ref(false)
const logDetailVisible = ref(false)
const currentLog = ref(null)

const logQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  module: '',
  operatorName: '',
  success: null,
})

/**
 * 查询操作日志。
 * 后端 /operation-log/page 返回统一分页结构 { records, total, size, current, pages }。
 * 查询条件同样经过后端的字段白名单校验，非法字段会被忽略。
 */
const fetchLogs = async () => {
  logLoading.value = true
  try {
    const params = { pageNum: logQuery.pageNum, pageSize: logQuery.pageSize }
    if (logQuery.module) params.module = logQuery.module
    if (logQuery.operatorName) params.operatorName = logQuery.operatorName.trim()
    if (logQuery.success !== null && logQuery.success !== '') params.success = logQuery.success

    const res = await request.post('/operation-log/page', params)
    logs.value = res?.records || []
    logTotal.value = res?.total || 0
  } catch (e) {
    console.error(e)
  } finally {
    logLoading.value = false
  }
}

const searchLogs = () => {
  logQuery.pageNum = 1
  fetchLogs()
}

const resetLogQuery = () => {
  logQuery.module = ''
  logQuery.operatorName = ''
  logQuery.success = null
  logQuery.pageNum = 1
  fetchLogs()
}

const showLogDetail = (row) => {
  currentLog.value = row
  logDetailVisible.value = true
}

/** 请求参数是 JSON 字符串，格式化后便于阅读；解析失败就原样显示 */
const prettyParams = (text) => {
  if (!text) return '（无）'
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    return text
  }
}

const formatDateTime = (value) => {
  if (!value) return '—'
  return new Date(value).toLocaleString()
}

/**
 * 按 tab 懒加载数据。
 *
 * 用 watch 监听 activeTab，而不用 el-tabs 的 @tab-click 事件 ——
 * 后者依赖"事件触发时 v-model 已经更新"这个前提，一旦时序不符就会出现
 * 「切了 tab 但数据没请求」的静默失败：页面显示 No Data，控制台却没有任何报错。
 * watch 监听的是数据本身，无论点击、键盘还是代码切换 tab 都能可靠触发。
 *
 * 放在文件末尾是因为 fetchLogs 等函数用 const 定义（不存在变量提升），
 * 虽然后续调用是延迟执行的、运行时不会出错，但集中放最后最不容易被误改。
 */
watch(activeTab, (tab) => {
  if (tab === 'course') {
    fetchCourses()
  } else if (tab === 'log') {
    fetchLogs()
  } else {
    fetchUsers()
  }
})

onMounted(() => {
  fetchUsers()
  fetchCourses()
})
</script>

<style scoped>
.param-pre {
  max-height: 220px;
  margin: 0;
  padding: 8px 10px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  background: #f5f7fa;
  border-radius: 4px;
}

.error-text {
  color: #f56c6c;
}
</style>
