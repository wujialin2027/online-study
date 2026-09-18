<template>
  <div class="login-page">
    <!-- ==================== 左侧：品牌区 ==================== -->
    <div class="brand">
      <div class="brand-inner">
        <h1>在线学习平台</h1>
        <p class="slogan">课程 · 作业 · 资源 · 论坛，一站式教学管理</p>
        <ul class="features">
          <li><el-icon><Notebook /></el-icon><span>课程发布与课件资源管理</span></li>
          <li><el-icon><Document /></el-icon><span>作业发布、提交与批改</span></li>
          <li><el-icon><ChatDotRound /></el-icon><span>师生交流论坛</span></li>
          <li><el-icon><UserFilled /></el-icon><span>学员 / 教师 / 管理员多角色权限</span></li>
        </ul>
      </div>
    </div>

    <!-- ==================== 右侧：表单区 ==================== -->
    <div class="form-side">
      <div class="form-box">
        <h2 class="form-title">{{ activeTab === 'login' ? '欢迎回来' : '注册新账号' }}</h2>
        <p class="form-tip">
          {{ activeTab === 'login' ? '请使用你的账号登录系统' : '填写信息完成注册，教师账号需管理员审核' }}
        </p>

        <el-tabs v-model="activeTab">
          <!-- ---------------- 登录 ---------------- -->
          <el-tab-pane label="登录" name="login">
            <el-form ref="loginFormRef" :model="form" :rules="loginRules" label-position="top">
              <el-form-item label="账号" prop="account">
                <el-input
                  v-model="form.account"
                  size="large"
                  placeholder="请输入账号"
                  clearable
                  @keyup.enter="handleLogin"
                >
                  <template #prefix><el-icon><User /></el-icon></template>
                </el-input>
              </el-form-item>

              <el-form-item label="密码" prop="password">
                <el-input
                  v-model="form.password"
                  size="large"
                  type="password"
                  placeholder="请输入密码"
                  show-password
                  @keyup.enter="handleLogin"
                >
                  <template #prefix><el-icon><Lock /></el-icon></template>
                </el-input>
              </el-form-item>

              <el-form-item>
                <el-checkbox v-model="remember">记住我（下次打开浏览器免登录）</el-checkbox>
              </el-form-item>

              <el-form-item>
                <el-button
                  type="primary"
                  size="large"
                  class="submit-btn"
                  :loading="loading"
                  @click="handleLogin"
                >
                  登 录
                </el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <!-- ---------------- 注册 ---------------- -->
          <el-tab-pane label="注册" name="register">
            <el-form ref="regFormRef" :model="regForm" :rules="regRules" label-position="top">
              <el-form-item label="账号" prop="account">
                <el-input v-model="regForm.account" placeholder="用于登录，建议英文或数字" />
              </el-form-item>
              <el-form-item label="密码" prop="password">
                <el-input v-model="regForm.password" type="password" show-password placeholder="至少 6 位" />
              </el-form-item>
              <el-form-item label="姓名" prop="name">
                <el-input v-model="regForm.name" placeholder="请输入真实姓名" />
              </el-form-item>
              <el-form-item label="手机号" prop="phone">
                <el-input v-model="regForm.phone" maxlength="11" placeholder="请输入 11 位手机号" />
              </el-form-item>
              <el-form-item label="角色" prop="role">
                <el-select v-model="regForm.role" style="width: 100%" placeholder="请选择角色">
                  <el-option label="学员" value="student" />
                  <el-option label="教师" value="teacher" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button
                  type="primary"
                  size="large"
                  class="submit-btn"
                  :loading="regLoading"
                  @click="handleRegister"
                >
                  注 册
                </el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Document, Lock, Notebook, User, UserFilled } from '@element-plus/icons-vue'
import request from '../utils/request'
import { setAuth } from '../utils/auth'

const router = useRouter()
const route = useRoute()

const activeTab = ref('login')
const loading = ref(false)
const regLoading = ref(false)
/** 是否「记住我」：勾选存 localStorage，不勾只存当前标签页（关浏览器即失效） */
const remember = ref(false)

const loginFormRef = ref()
const regFormRef = ref()

const form = ref({ account: '', password: '' })
const regForm = ref({ account: '', password: '', name: '', phone: '', role: '' })

const loginRules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const regRules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 位', trigger: 'blur' }
  ],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '请输入正确的 11 位手机号', trigger: 'blur' }
  ],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

/**
 * 登录成功后要跳到哪里。
 *
 * 路由守卫拦下未登录访问时会带上 ?redirect=原目标地址，登录后应跳回那里。
 * 注意必须校验只能跳到站内路径（以 / 开头）—— 否则攻击者可以构造
 * /login?redirect=https://xxx.com，用户登录后被带到钓鱼站，
 * 这就是「开放重定向」漏洞。
 */
const resolveRedirect = () => {
  const target = route.query.redirect
  return typeof target === 'string' && target.startsWith('/') && !target.startsWith('//')
    ? target
    : '/dashboard'
}

/**
 * 登录。
 * 后端已统一返回 { code, message, data }，request.js 拦截器会：
 *   - code === 200  → 直接把 data 交给这里，即 { token, user, role }
 *   - code !== 200  → 自动弹出后端给的 message 并 reject
 * 因此这里不再需要判断 res.success，catch 里也不用重复提示。
 */
const handleLogin = async () => {
  try {
    await loginFormRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    const data = await request.post('/auth/login', {
      account: form.value.account.trim(),
      password: form.value.password
    })
    // 按「记住我」决定存 localStorage（长期）还是 sessionStorage（关标签即失效）
    setAuth({ token: data.token, user: data.user, role: data.role }, remember.value)
    ElMessage.success(remember.value ? '登录成功，下次打开浏览器将自动登录' : '登录成功')
    router.push(resolveRedirect())
  } catch (error) {
    // 失败提示已由 request.js 统一处理（账号密码错误、账号被禁用等）
  } finally {
    loading.value = false
  }
}

/**
 * 注册。成功时后端返回 { code: 200, data: null }，走到这里说明一定成功。
 */
const handleRegister = async () => {
  try {
    await regFormRef.value.validate()
  } catch {
    return
  }

  regLoading.value = true
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
  } catch (error) {
    // 失败提示已由 request.js 统一处理
  } finally {
    regLoading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  height: 100vh;
}

/* ---------------- 左侧品牌区 ---------------- */
.brand {
  flex: 1.1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #22456b 0%, #2e6da4 55%, #409eff 100%);
  color: #fff;
  padding: 40px;
}

.brand-inner {
  max-width: 420px;
}

.brand-inner h1 {
  margin: 0 0 12px;
  font-size: 34px;
  letter-spacing: 2px;
}

.slogan {
  margin: 0 0 36px;
  font-size: 15px;
  opacity: 0.85;
}

.features {
  list-style: none;
  padding: 0;
  margin: 0;
}

.features li {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  font-size: 15px;
  opacity: 0.92;
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
}

.features li .el-icon {
  font-size: 18px;
}

/* ---------------- 右侧表单区 ---------------- */
.form-side {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  padding: 40px;
  overflow-y: auto;
}

.form-box {
  width: 100%;
  max-width: 360px;
}

.form-title {
  margin: 0 0 6px;
  font-size: 24px;
  color: #303133;
}

.form-tip {
  margin: 0 0 18px;
  font-size: 13px;
  color: #909399;
}

.submit-btn {
  width: 100%;
  margin-top: 4px;
  letter-spacing: 4px;
}

/* 窄屏（手机 / 小窗口）隐藏品牌区，只留表单 */
@media (max-width: 768px) {
  .brand {
    display: none;
  }
}
</style>
