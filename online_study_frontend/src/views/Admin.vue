<template>
  <div>
    <h2>系统管理 (超级管理员专属)</h2>
    <el-tabs v-model="activeTab" @tab-click="handleTabClick">
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
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const activeTab = ref('student')
const students = ref([])
const teachers = ref([])
const courses = ref([])

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

const handleTabClick = () => {
  if (activeTab.value === 'course') {
    fetchCourses()
  } else {
    fetchUsers()
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
  course.auditStatus = status
  const user = JSON.parse(localStorage.getItem('user') || '{}')
  course.auditAdminId = user.adminId
  await request.post('/course/save', course)
  ElMessage.success(status === 1 ? '审核已通过' : '已驳回')
  fetchCourses()
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

onMounted(() => {
  fetchUsers()
  fetchCourses()
})
</script>
