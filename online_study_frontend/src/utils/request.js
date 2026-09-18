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

  // ---------- 统一返回格式兼容层 ----------
  // 后端正在分阶段改造：新接口返回 { code, message, data }，旧接口仍返回裸数据。
  // 这里自动识别并解包，页面代码无需改动，也不必等后端全部改完才能跑。
  if (isWrappedResult(data)) {
    if (data.code === 200) {
      // 成功：直接把业务数据交给页面，页面拿到的还是原来的东西
      return data.data
    }
    // 业务失败（参数错 / 业务规则不满足）：提示并中断后续逻辑
    const msg = data.message || '操作失败'
    fail(response.config, msg)
    return Promise.reject(new Error(msg))
  }

  // 旧接口：原样返回
  return data
}, error => {
  if (error.code === 'ECONNABORTED') {
    ElMessage.error('请求超时，请稍后重试')
  } else if (error.response && error.response.status === 401) {
    // 401 = 未登录 / token 失效 → 清干净本地状态并回登录页
    clearAuth()
    if (router.currentRoute.value.path !== '/login') {
      router.push('/login')
      ElMessage.error('登录已过期，请重新登录')
    }
  } else if (error.response && error.response.status === 403) {
    // 403 = 已登录但权限不足 → 不该踢回登录页（那是 401 的事），只提示
    ElMessage.error(error.response.data?.message || '没有权限执行该操作')
  } else {
    ElMessage.error(error.response?.data?.message || error.message || '请求失败')
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
