import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { clearAuth, getToken } from './auth'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// ==================== 请求拦截器 ====================
request.interceptors.request.use(config => {
  const token = getToken()
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
}, error => {
  return Promise.reject(error)
})

// ==================== 响应拦截器 ====================
request.interceptors.response.use(response => {
  const data = response.data

  // 页面可以给单个请求传 { silent: true }，声明「这条请求失败不用提示」。
  // 典型场景：首页/助教页的「拉最近记录」—— 拿不到不影响主流程，
  // 弹一屏红字反而让用户以为整个页面坏了（见 Dashboard.vue、Assistant.vue）。
  // 注意 silent 只关掉 *提示*，Promise 依然 reject，页面自己还能 catch 到。
  const silent = response.config?.silent === true

  // ---------- 统一返回格式兼容层 ----------
  // 后端正在分阶段改造：新接口返回 { code, message, data }，旧接口仍返回裸数据。
  // 这里自动识别并解包，页面代码无需改动，也不必等后端全部改完才能跑。
  if (isWrappedResult(data)) {
    if (data.code === 200) {
      // 成功：直接把业务数据交给页面，页面拿到的还是原来的东西
      return data.data
    }

    // 业务失败（参数错 / 业务规则不满足 / 权限不足）：提示并中断后续逻辑
    const msg = data.message || '操作失败'

    // 业务码里的 401：HTTP 状态仍是 200（见后端 ResultCode 的约定），
    // 但语义和下面 error 分支的 HTTP 401 一样 —— token 失效了。
    // 如果只在 error 分支处理，这类响应会静默停在当前页，用户以为"点了没反应"。
    if (data.code === 401) {
      clearAuth()
      if (router.currentRoute.value.path !== '/login') {
        router.push('/login')
      }
      if (!silent) ElMessage.error(msg)
      return Promise.reject(new Error(msg))
    }

    // 其他业务码（含 403 无权操作）：只提示、不跳转
    // —— 用户身份是明确的，让他重新登录也解决不了权限问题
    if (!silent) ElMessage.error(msg)
    return Promise.reject(new Error(msg))
  }

  // 旧接口：原样返回
  return data
}, error => {
  const silent = error.config?.silent === true

  if (error.code === 'ECONNABORTED') {
    // 超时：最常见的原因是 AI 问答这类长耗时请求（后端读超时 60s）
    if (!silent) ElMessage.error('请求超时，请稍后重试')
  } else if (error.response && error.response.status === 401) {
    // 401 = 未登录 / token 失效 → 清干净本地状态并回登录页
    clearAuth()
    if (router.currentRoute.value.path !== '/login') {
      router.push('/login')
      if (!silent) ElMessage.error('登录已过期，请重新登录')
    }
  } else if (error.response && error.response.status === 403) {
    // 403 = 已登录但权限不足 → 不该踢回登录页（那是 401 的事），只提示
    if (!silent) ElMessage.error(error.response.data?.message || '没有权限执行该操作')
  } else {
    // 走到这里的三种情况，分别给一句用户读得懂的话：
    //   1. 后端返回了 message         → 用它（GlobalExceptionHandler 的产物）
    //   2. 压根没连上（后端没起 / 断网）→ axios 给的是英文 "Network Error"，
    //      直接抛给用户等于没提示，必须换成中文
    //   3. 连上了但状态码异常（500/502/404）且没带 message → 至少把状态码说出来
    const serverMsg = error.response?.data?.message
    if (serverMsg) {
      if (!silent) ElMessage.error(serverMsg)
    } else if (error.code === 'ERR_NETWORK' || error.message === 'Network Error') {
      if (!silent) ElMessage.error('无法连接服务器，请检查网络后重试')
    } else if (error.response) {
      if (!silent) ElMessage.error(`请求失败（HTTP ${error.response.status}）`)
    } else {
      if (!silent) ElMessage.error(error.message || '请求失败')
    }
  }
  return Promise.reject(error)
})

/**
 * 判断是否为本项目的统一返回结构 Result<T> = { code:number, message:string, data:T }
 * 三重判断是为了避免误伤"业务数据里恰好也有 code 字段"的情况。
 */
function isWrappedResult(data) {
  return (
    data !== null &&
    typeof data === 'object' &&
    !Array.isArray(data) &&
    typeof data.code === 'number' &&
    'message' in data
  )
}

export default request
