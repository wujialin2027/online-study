<template>
  <el-container class="layout-container">
    <el-header>
      <div class="logo">培训学习交流系统</div>
      <div class="user-info">
        <span class="welcome-text">欢迎，{{ user.name || user.adminName || user.studentName || user.teacherName }}</span>
        <el-button type="danger" size="small" plain @click="logout">
          <el-icon><SwitchButton /></el-icon>安全退出
        </el-button>
      </div>
    </el-header>
    <el-container>
      <el-aside width="200px">
        <el-menu router :default-active="$route.path">
          <el-menu-item index="/dashboard">
            <span>首页概览</span>
          </el-menu-item>
          <el-menu-item index="/courses" v-if="role !== 'admin'">
            <span>课程资源</span>
          </el-menu-item>
          <el-menu-item index="/homework" v-if="role !== 'admin'">
            <span>作业管理</span>
          </el-menu-item>
          <el-menu-item index="/forum">
            <span>交流论坛</span>
          </el-menu-item>
          <el-menu-item index="/admin" v-if="role === 'admin'">
            <span>系统管理</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-main>
        <router-view></router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { SwitchButton } from '@element-plus/icons-vue'

const router = useRouter()
const user = ref(JSON.parse(localStorage.getItem('user') || '{}'))
const role = ref(localStorage.getItem('role') || '')

const logout = () => {
  // ⚠️ token 必须一起清掉。
  // 原代码只删了 user / role，token 仍留在 localStorage 里 ——
  // 相当于「点了退出登录，但凭证还在浏览器里」，任何人拿到这台电脑都能继续用。
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  localStorage.removeItem('role')
  router.push('/login')
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}
.el-header {
  background-color: #409EFF;
  color: white;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.logo {
  font-size: 20px;
  font-weight: bold;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
}
.el-aside {
  background-color: #fff;
  border-right: solid 1px #e6e6e6;
}
</style>