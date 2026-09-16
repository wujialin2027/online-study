<template>
  <div class="login-container">
    <el-card class="login-box">
      <h2>培训学习交流系统</h2>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="登录" name="login">
          <el-form :model="form" ref="loginForm" label-width="80px">
            <el-form-item label="账号">
              <el-input v-model="form.account"></el-input>
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="form.password" type="password" @keyup.enter="handleLogin"></el-input>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleLogin" style="width: 100%;">登录</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="注册" name="register">
          <el-form :model="regForm" label-width="80px">
            <el-form-item label="账号">
              <el-input v-model="regForm.account"></el-input>
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="regForm.password" type="password"></el-input>
            </el-form-item>
            <el-form-item label="姓名">
              <el-input v-model="regForm.name"></el-input>
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="regForm.phone" maxlength="11" placeholder="请输入11位手机号"></el-input>
            </el-form-item>
            <el-form-item label="角色">
              <el-select v-model="regForm.role" style="width: 100%;">
                <el-option label="学员" value="student"></el-option>
                <el-option label="教师" value="teacher"></el-option>
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="success" @click="handleRegister" style="width: 100%;">注册</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../utils/request'

const router = useRouter()
const activeTab = ref('login')

const form = ref({
  account: '',
  password: '',
  role: ''
})

const regForm = ref({
  account: '',
  password: '',
  name: '',
  phone: '',
  role: ''
})

/**
 * 登录。
 * 后端已统一返回 { code, message, data }，request.js 拦截器会：
 *   - code === 200  → 直接把 data 交给这里，所以 data 就是 { token, user, role }
 *   - code !== 200  → 自动弹出后端给的 message 并 reject
 * 因此这里不再需要判断 res.success，catch 里也不用重复提示。
 */
const handleLogin = async () => {
  if (!form.value.account?.trim() || !form.value.password) {
    ElMessage.warning('请填写账号和密码')
    return
  }
  try {
    const data = await request.post('/auth/login', {
      account: form.value.account.trim(),
      password: form.value.password
    })
    localStorage.setItem('token', data.token)
    localStorage.setItem('user', JSON.stringify(data.user))
    localStorage.setItem('role', data.role)
    ElMessage.success('登录成功')
    router.push('/')
  } catch (error) {
    // 失败提示已由 request.js 统一处理（含账号密码错误、账号被禁用等）
  }
}

/**
 * 注册。成功时后端只返回 { code: 200, message: '操作成功', data: null }，
 * 走到这里说明一定成功，不需要再判断。
 */
const handleRegister = async () => {
  if (!regForm.value.account?.trim() || !regForm.value.password || !regForm.value.name?.trim() || !regForm.value.phone || !regForm.value.role) {
    ElMessage.warning('请填写完整信息')
    return
  }
  if (regForm.value.password.length < 6) {
    ElMessage.warning('密码长度不能少于6位')
    return
  }
  if (!/^1\d{10}$/.test(regForm.value.phone)) {
    ElMessage.warning('请输入正确的11位手机号')
    return
  }
  try {
    await request.post('/auth/register', {
      ...regForm.value,
      account: regForm.value.account.trim(),
      name: regForm.value.name.trim()
    })
    ElMessage.success('注册成功，请登录')
    activeTab.value = 'login'
    form.value.account = regForm.value.account
    form.value.password = regForm.value.password
    form.value.role = regForm.value.role
  } catch (error) {
    // 失败提示已由 request.js 统一处理
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background-color: #f5f7fa;
}
.login-box {
  width: 400px;
}
h2 {
  text-align: center;
  margin-bottom: 20px;
}
</style>
