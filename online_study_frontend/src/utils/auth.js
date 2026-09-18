/**
 * 登录态存储（token / user / role）
 *
 * <h3>为什么要有这个文件</h3>
 * 改造前三个值直接散落在 8 个文件里写 localStorage：
 *   · 关掉浏览器再打开还是登录状态，直接进首页看到上一次的账号，
 *     用户会以为"登录页坏了"；
 *   · 想改存储策略（比如换成 sessionStorage）得翻遍所有页面。
 * 这里把读写收口到一处，顺便加上「记住我」。
 *
 * <h3>两种存储的区别（这是理解"为什么一进来就是已登录"的关键）</h3>
 * <pre>
 *   localStorage    持久保存，关浏览器、重启电脑都还在   → 适合「记住我」
 *   sessionStorage  只活在当前标签页，关掉标签即清空     → 默认行为
 * </pre>
 *
 * <h3>读取为什么要两边都找</h3>
 * 用户上次可能勾了「记住我」（数据在 localStorage），这次可能没勾（在 sessionStorage）。
 * 读取时按 sessionStorage → localStorage 的顺序找第一个有值的，
 * 页面代码不用关心当初存在哪一边。
 *
 * <h3>写入为什么要先清空</h3>
 * 如果这次不勾「记住我」，上一次勾选留下的 localStorage 记录必须清掉，
 * 否则会出现「明明没勾记住我，关了浏览器却还是登录状态」这种灵异现象。
 */

const TOKEN = 'token'
const USER = 'user'
const ROLE = 'role'

/** 按「本次会话优先」的顺序返回可用的存储对象 */
function stores() {
  if (typeof window === 'undefined') {
    return []
  }
  const list = []
  if (window.sessionStorage) {
    list.push(window.sessionStorage)
  }
  if (window.localStorage) {
    list.push(window.localStorage)
  }
  return list
}

/** 依次在两个存储里找 key，返回第一个有值的 */
function read(key) {
  for (const store of stores()) {
    const value = store.getItem(key)
    if (value !== null && value !== '') {
      return value
    }
  }
  return null
}

/** 当前登录凭证；未登录返回空串 */
export function getToken() {
  return read(TOKEN) || ''
}

/** 当前登录角色（student / teacher / admin）；未登录返回空串 */
export function getRole() {
  return read(ROLE) || ''
}

/** 当前登录用户信息对象；未登录或数据损坏返回 {} */
export function getUser() {
  const raw = read(USER)
  if (!raw) {
    return {}
  }
  try {
    return JSON.parse(raw)
  } catch (e) {
    // 存储被手工改坏时不抛异常，当作未登录处理，避免整个页面白屏
    return {}
  }
}

/** 是否已登录 */
export function isLoggedIn() {
  return !!getToken()
}

/**
 * 写入登录态
 * @param {{token: string, user: object, role: string}} auth 登录接口返回的三件套
 * @param {boolean} remember 是否「记住我」（true 存 localStorage，false 存 sessionStorage）
 */
export function setAuth(auth, remember = false) {
  clearAuth()
  const target = remember && window.localStorage ? window.localStorage : window.sessionStorage
  target.setItem(TOKEN, auth.token)
  target.setItem(USER, JSON.stringify(auth.user))
  target.setItem(ROLE, auth.role)
}

/** 清空登录态（退出登录 / token 失效时调用），两个存储都要清 */
export function clearAuth() {
  for (const store of stores()) {
    store.removeItem(TOKEN)
    store.removeItem(USER)
    store.removeItem(ROLE)
  }
}
