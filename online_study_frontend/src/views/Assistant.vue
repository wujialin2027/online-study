<template>
  <div class="assistant-page">
    <!-- ==================== 顶部：资料状态 ==================== -->
    <el-card shadow="never" class="status-card">
      <div class="status-left">
        <div class="title-row">
          <h2>智能助教</h2>
          <el-tag v-if="status.configured === false" type="info" size="small">未配置 Key</el-tag>
          <el-tag v-else-if="status.chunkCount > 0" type="success" size="small">资料已就绪</el-tag>
          <el-tag v-else type="warning" size="small">知识库为空</el-tag>
        </div>
        <p class="subtitle">
          基于平台里的课程 / 作业 / 资料回答问题，答案下方附带出处；资料里没有的内容不会编造。
        </p>
      </div>

      <div class="status-right">
        <el-button :loading="rebuilding" @click="rebuild">重建知识库</el-button>
        <el-button v-if="canManage" type="primary" plain @click="openDocDialog">
          补充资料
        </el-button>
      </div>
    </el-card>

    <!-- 未配置 Key 时给出明确指引，而不是等用户提问后报错 -->
    <el-alert
      v-if="status.configured === false"
      type="warning"
      show-icon
      :closable="false"
      class="config-alert"
      title="尚未配置大模型 API Key"
      description="请在 online_study_backend/src/main/resources/application.yml 的 ai.api-key 填入百炼 Key 后重启后端服务。"
    />

    <!-- ==================== 资料统计 ==================== -->
    <div class="stat-row">
      <div class="stat">
        <span class="num">{{ status.chunkCount ?? 0 }}</span>
        <span class="lbl">知识块</span>
      </div>
      <div class="stat">
        <span class="num">{{ status.dimension ?? 0 }}</span>
        <span class="lbl">向量维度</span>
      </div>
      <div class="stat">
        <span class="num">{{ status.extraDocCount ?? 0 }}</span>
        <span class="lbl">补充资料</span>
      </div>
      <div class="stat">
        <span class="num">{{ lastTokens }}</span>
        <span class="lbl">本次 token</span>
      </div>
    </div>

    <!-- ==================== 对话区 ==================== -->
    <el-card shadow="never" class="chat-card">
      <!-- 对话工具条：有记录时才出现，避免空页面多一行没用的东西 -->
      <div v-if="messages.length" class="chat-toolbar">
        <span class="chat-count">
          共 {{ messages.length }} 条消息
          <template v-if="hasHistory">（含历史记录，换设备登录同一账号也能看到）</template>
        </span>
        <el-button text type="danger" size="small" @click="clearHistory">清空对话</el-button>
      </div>

      <el-empty
        v-if="messages.length === 0"
        description="还没有对话，试着问一句吧（下方有示例问题）"
      />

      <div v-for="(msg, index) in messages" :key="index" class="msg" :class="msg.role">
        <div class="avatar">{{ msg.role === 'user' ? '我' : 'AI' }}</div>

        <div class="bubble-wrap">
          <div class="bubble">
            <!-- 用 div 而不是 p：回答里可能含 Markdown 表格，而 p 不允许包含块级元素，
                 浏览器会把 table 强行移出 p，导致结构错乱 -->
            <div class="text" v-html="renderText(msg.text)"></div>
          </div>

          <!-- 工具调用轨迹：告诉用户"助教刚去数据库查了什么" -->
          <div v-if="msg.usedTools && msg.usedTools.length" class="tool-trace">
            <span class="trace-label">已查询</span>
            <el-tag
              v-for="(tool, ti) in msg.usedTools"
              :key="ti"
              size="small"
              effect="plain"
              :type="tool.success ? 'success' : 'danger'"
            >
              {{ tool.label }}<template v-if="tool.args">（{{ tool.args }}）</template>
            </el-tag>
          </div>

          <!-- 出处：RAG 与普通聊天机器人的最大区别 -->
          <el-collapse v-if="msg.sources && msg.sources.length" class="sources">
            <el-collapse-item :title="`参考了 ${msg.sources.length} 条资料`" :name="index">
              <div v-for="src in msg.sources" :key="src.chunkId" class="source-item">
                <div class="source-head">
                  <el-tag size="small" :type="scoreTagType(src.score)">{{ src.score }}%</el-tag>
                  <span class="source-name">{{ src.source }}</span>
                </div>
                <p class="source-snippet">{{ src.snippet }}</p>
              </div>
            </el-collapse-item>
          </el-collapse>

          <p v-if="msg.meta" class="msg-meta">
            {{ msg.meta.model }} · 命中 {{ msg.meta.hitCount }} 条 ·
            输入 {{ msg.meta.promptTokens }} / 输出 {{ msg.meta.completionTokens }} token
          </p>
        </div>
      </div>

      <div v-if="asking" class="msg assistant">
        <div class="avatar">AI</div>
        <div class="bubble-wrap">
          <div class="bubble typing">正在检索资料并生成回答…</div>
        </div>
      </div>
    </el-card>

    <!-- ==================== 输入区 ==================== -->
    <el-card shadow="never" class="input-card">
      <el-input
        v-model="question"
        type="textarea"
        :rows="3"
        resize="none"
        maxlength="500"
        show-word-limit
        :placeholder="questionPlaceholder"
        @keydown.enter.exact.prevent="ask"
      />

      <div class="examples">
        <span class="examples-label">示例：</span>
        <el-button
          v-for="example in examples"
          :key="example"
          size="small"
          text
          @click="question = example"
        >
          {{ example }}
        </el-button>
      </div>

      <div class="input-actions">
        <span class="tip">Enter 发送 · Shift + Enter 换行</span>
        <el-button type="primary" :loading="asking" @click="ask">发送</el-button>
      </div>
    </el-card>

    <!-- ==================== 补充资料 ==================== -->
    <el-dialog v-model="showDocDialog" title="补充文字资料" width="640px">
      <el-form label-width="80px">
        <el-form-item label="资料标题">
          <el-input
            v-model="docForm.source"
            maxlength="60"
            show-word-limit
            placeholder="例如：Java 基础 - 第 2 章讲义"
          />
        </el-form-item>
        <el-form-item label="资料正文">
          <el-input
            v-model="docForm.text"
            type="textarea"
            :rows="10"
            placeholder="把讲义、课件正文、常见问题等内容粘贴到这里（会自动切块入库）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDocDialog = false">取消</el-button>
        <el-button type="primary" :loading="savingDoc" @click="saveDoc">入库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'
import { getRole } from '../utils/auth'

const role = ref(getRole())
const canManage = role.value === 'admin' || role.value === 'teacher'

const status = reactive({
  configured: true,
  chunkCount: 0,
  dimension: 0,
  extraDocCount: 0,
  extraDocTitles: [],
  chatModel: '',
  embeddingModel: '',
  loadedAt: null
})

const messages = ref([])
const question = ref('')
const asking = ref(false)
const rebuilding = ref(false)
const lastTokens = ref(0)

const showDocDialog = ref(false)
const savingDoc = ref(false)
const docForm = reactive({ source: '', text: '' })

/**
 * 示例问题按角色给。
 *
 * <p>原来的示例是"学生专用"的：『我考了多少分』『我还有哪几门作业没交』。
 * 教师点开页面看到的全是自己答不了的问法，会直接觉得"这功能跟我没关系"。
 * 教师 / 管理员的关注点完全不同：
 * <pre>
 *   学员：我这门课学了什么、我交了没、我多少分
 *   教师：我的课有多少人报名、都叫什么名字、作业交得怎么样、谁还在等审核
 * </pre>
 */
const STUDENT_EXAMPLES = [
  'Java 基础这门课主要讲什么？',
  '我还有哪几门作业没交？',
  '我的成绩怎么样？',
  '这门课的报名条件是什么？'
]

const TEACHER_EXAMPLES = [
  '我发布了哪几门课程？各有多少人报名？',
  'Java 基础这门课有多少学生选了？都叫什么名字？',
  '这门课的作业提交情况怎么样？',
  '有哪些学员还在等我审核？'
]

const ADMIN_EXAMPLES = [
  '平台一共有多少门课程？',
  'Java 基础这门课有多少学生选了？都叫什么名字？',
  '这门课的作业提交情况怎么样？',
  '有哪些课程还在等待审核？'
]

const examples = computed(() => {
  if (role.value === 'student') return STUDENT_EXAMPLES
  if (role.value === 'admin') return ADMIN_EXAMPLES
  return TEACHER_EXAMPLES
})

/** 输入框占位提示同样跟着角色走，避免教师看到"作业什么时候截止"这种学生问法 */
const questionPlaceholder = computed(() =>
  role.value === 'student'
    ? '例如：Java 基础的作业什么时候截止？'
    : '例如：这门课有多少学生选了？作业交得怎么样？'
)

const loadStatus = async () => {
  try {
    const data = await request.get('/ai/status')
    Object.assign(status, data || {})
  } catch (e) {
    // 拦截器已提示，这里只保证页面不崩
  }
}

/**
 * 拉取历史对话，回填到对话框里。
 *
 * <p>后端存的是纯文本（user 提问 + assistant 回答各一行），所以回填出来的消息
 * 只有 role 与 text —— 出处（sources）和工具轨迹（usedTools）属于"当次回答的现场证据"，
 * 没有落库，模板里那几个 v-if 判断会让它们自然不渲染。
 *
 * <p>拉取失败不阻断提问：历史是锦上添花，这次看不到下次还在。
 */
const loadHistory = async () => {
  try {
    // silent: 历史记录属于"拿不到也不影响主流程"的可选数据 ——
    // 后端若还没重启（/ai/history 尚未生效），这里会 404，
    // 不该因此弹一屏红字让用户以为整个页面坏了，静默降级即可。
    const data = await request.get('/ai/history', { silent: true })
    const rows = Array.isArray(data) ? data : []
    messages.value = rows.map((row) => ({
      role: row.role,
      text: row.content,
      fromHistory: true
    }))
    if (messages.value.length) {
      await scrollToBottom()
    }
  } catch (e) {
    console.warn('[智能助教] 历史对话加载失败，本轮仅展示新对话：', e?.message || e)
  }
}

/** 当前展示的内容里是否包含历史记录（用于提示文案） */
const hasHistory = computed(() => messages.value.some((msg) => msg.fromHistory))

/** 清空当前账号的全部对话记录（后端只删自己的，不会碰到别人的） */
const clearHistory = async () => {
  try {
    await ElMessageBox.confirm('确定要清空全部对话记录吗？清空后无法恢复。', '提示', {
      confirmButtonText: '清空',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return // 用户取消
  }
  await request.delete('/ai/history')
  messages.value = []
  ElMessage.success('对话记录已清空')
}

// 状态条与历史对话并行拉取：前者决定顶部徽标，后者回填对话内容
onMounted(() => {
  loadStatus()
  loadHistory()
})

const ask = async () => {
  const text = question.value.trim()
  if (!text || asking.value) return

  messages.value.push({ role: 'user', text })
  question.value = ''
  asking.value = true
  // 滚到底部，避免回答出来后要手动往下拉
  await scrollToBottom()

  try {
    const data = await request.post('/ai/ask', { question: text })
    messages.value.push({
      role: 'assistant',
      text: data?.answer || '（模型没有返回内容）',
      sources: data?.sources || [],
      usedTools: data?.usedTools || [],
      meta: {
        model: data?.model || '',
        hitCount: data?.hitCount ?? 0,
        promptTokens: data?.promptTokens ?? 0,
        completionTokens: data?.completionTokens ?? 0
      }
    })
    lastTokens.value = (data?.promptTokens ?? 0) + (data?.completionTokens ?? 0)
    // 首次提问会自动灌库：这里整体刷新一次状态（块数、向量维度、补充资料数都要更新）
    await loadStatus()
    await scrollToBottom()
  } catch (e) {
    messages.value.push({
      role: 'assistant',
      text: '调用失败了，请稍后再试或联系管理员。'
    })
  } finally {
    asking.value = false
  }
}

const rebuild = async () => {
  rebuilding.value = true
  try {
    const data = await request.post('/ai/knowledge/rebuild')
    ElMessage.success(
      `重建完成，当前共 ${data?.chunkCount ?? 0} 个知识块`
    )
    await loadStatus()
  } finally {
    rebuilding.value = false
  }
}

const openDocDialog = () => {
  docForm.source = ''
  docForm.text = ''
  showDocDialog.value = true
}

const saveDoc = async () => {
  if (!docForm.source.trim() || !docForm.text.trim()) {
    ElMessage.warning('资料标题与正文都不能为空')
    return
  }
  savingDoc.value = true
  try {
    await request.post('/ai/knowledge/doc', {
      source: docForm.source.trim(),
      text: docForm.text.trim()
    })
    ElMessage.success('资料已入库')
    showDocDialog.value = false
    await loadStatus()
  } finally {
    savingDoc.value = false
  }
}

const scrollToBottom = async () => {
  await new Promise((resolve) => setTimeout(resolve, 30))
  window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' })
}

const scoreTagType = (score) => {
  if (score >= 70) return 'success'
  if (score >= 45) return 'warning'
  return 'info'
}

/**
 * 极简 Markdown 渲染：只处理 **加粗** 和 **表格**。
 *
 * 为什么是这两样：
 * - **加粗** —— 模型习惯用它标重点，不处理就会看到一堆星号
 * - **表格** —— 问"我还有哪几门作业没交"时模型会输出 Markdown 表格，
 *   不处理就是一排竖线横杠，完全读不了；而表格恰好是这类清单最清楚的呈现方式
 *
 * 为什么不直接引 marked / markdown-it：
 * 对话内容由模型生成，完整 Markdown 渲染意味着还得连带引入 XSS 过滤库（DOMPurify）。
 * 只支持两个语法，代价是二十行代码，比多两个依赖划算。
 *
 * 安全做法：**先把 HTML 转义、再做替换** —— 这样即使模型输出 <script>，
 * 也只会被当普通文字显示，不会执行。
 */
const HTML_ESCAPES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }

/** Markdown 表格的分隔行，形如 | --- | :---: | */
const isTableSeparator = (line) => /^\s*\|[\s:|-]+\|\s*$/.test(line)

/** 把 "| a | b |" 拆成 ['a', 'b'] */
const tableCells = (line) =>
  line.trim().replace(/^\||\|$/g, '').split('|').map((cell) => cell.trim())

/** 把攒下来的表格行拼成真正的 <table> */
const buildTable = (rows) => {
  const body = rows.filter((line) => !isTableSeparator(line))
  if (body.length === 0) return ''
  const [head, ...rest] = body
  const thead = `<thead><tr>${tableCells(head).map((c) => `<th>${c}</th>`).join('')}</tr></thead>`
  const tbody = `<tbody>${rest
    .map((line) => `<tr>${tableCells(line).map((c) => `<td>${c}</td>`).join('')}</tr>`)
    .join('')}</tbody>`
  return `<table class="md-table">${thead}${tbody}</table>`
}

const renderText = (text) => {
  if (!text) return ''
  const safe = String(text).replace(/[&<>"']/g, (ch) => HTML_ESCAPES[ch])
  const lines = safe.split('\n')

  // 按行扫描：连续的表格行攒成一块，其余行原样保留
  const parts = []
  let tableRows = []
  const flushTable = () => {
    if (tableRows.length) {
      parts.push(buildTable(tableRows))
      tableRows = []
    }
  }
  for (const line of lines) {
    if (/^\s*\|.*\|\s*$/.test(line)) {
      tableRows.push(line)
    } else {
      flushTable()
      parts.push(line)
    }
  }
  flushTable()

  return (
    parts
      .join('\n')
      // 表格是块级元素，会自己换行；去掉紧邻的换行符，避免 pre-wrap 多顶出一行空隙
      .replace(/\n(<table class="md-table">)/g, '$1')
      .replace(/(<\/table>)\n/g, '$1')
      // 换行由 CSS 的 white-space: pre-wrap 负责，这里只管加粗
      .replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')
  )
}
</script>

<style scoped>
.assistant-page {
  display: flex;
  flex-direction: column;
  gap: var(--app-gap);
}

/* ==================== 状态区 ==================== */
.status-card :deep(.el-card__body) {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
}

.status-left {
  flex: 1;
  min-width: 260px;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.title-row h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--app-text-secondary);
}

.status-right {
  display: flex;
  gap: 10px;
}

.config-alert {
  margin-bottom: 0;
}

/* ==================== 统计 ==================== */
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--app-gap);
}

.stat {
  background: #fff;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat .num {
  font-size: 22px;
  font-weight: 600;
  color: #303133;
}

.stat .lbl {
  font-size: 12px;
  color: var(--app-text-secondary);
}

/* ==================== 对话 ==================== */
.chat-card {
  min-height: 320px;
}

/* 对话工具条：左提示右操作，用一条细分隔线和下面的消息列表区分开 */
.chat-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 12px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--app-border, #ebeef5);
}

.chat-count {
  font-size: 13px;
  color: var(--app-text-secondary, #909399);
}

.msg {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

/* 用户消息整行反向排列：头像与气泡一起跑到右侧（标准聊天布局） */
.msg.user {
  flex-direction: row-reverse;
}

.msg .avatar {
  flex: 0 0 34px;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  background: #ecf5ff;
  color: #409eff;
}

.msg.user .avatar {
  background: #409eff;
  color: #fff;
}

.msg.assistant .avatar {
  background: #f0f9eb;
  color: #67c23a;
}

.bubble-wrap {
  flex: 1;
  min-width: 0;
}

/* 用户侧：气泡靠右，宽度随内容收缩，不铺满整行 */
.msg.user .bubble-wrap {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.bubble {
  display: inline-block;
  max-width: 100%;
  padding: 10px 14px;
  border-radius: var(--app-radius);
  background: #f4f4f5;
  line-height: 1.7;
}

.msg.user .bubble {
  max-width: 78%;
  background: #409eff;
  color: #fff;
  border-top-right-radius: var(--app-radius-sm);
}

.msg.user .msg-meta {
  text-align: right;
}

.msg.assistant .bubble {
  background: #fafafa;
  border: 1px solid var(--app-border);
  border-top-left-radius: var(--app-radius-sm);
}

.bubble .text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 回答里的 Markdown 表格（如"未交作业清单""成绩单"） */
.bubble .text :deep(.md-table) {
  width: 100%;
  border-collapse: collapse;
  margin: 8px 0;
  font-size: 13px;
  /* 表格内部恢复常规换行，避免单元格文字被 pre-wrap 撑开 */
  white-space: normal;
}

.bubble .text :deep(.md-table th),
.bubble .text :deep(.md-table td) {
  border: 1px solid var(--app-border);
  padding: 6px 10px;
  text-align: left;
  vertical-align: top;
}

.bubble .text :deep(.md-table th) {
  background: #f5f7fa;
  font-weight: 600;
  color: #303133;
}

/* ==================== 工具调用轨迹 ==================== */
.tool-trace {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.tool-trace .trace-label {
  font-size: 12px;
  color: var(--app-text-secondary);
}

.bubble.typing {
  color: var(--app-text-secondary);
  font-size: 13px;
}

.msg-meta {
  margin: 6px 0 0;
  font-size: 12px;
  color: #b1b3b8;
}

/* ==================== 出处 ==================== */
.sources {
  margin-top: 8px;
  border-top: none;
}

.sources :deep(.el-collapse-item__header) {
  font-size: 13px;
  color: #409eff;
  height: 36px;
  line-height: 36px;
  border-bottom: none;
}

.sources :deep(.el-collapse-item__wrap) {
  border-bottom: none;
}

.source-item {
  padding: 8px 12px;
  background: #fff;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  margin-bottom: 8px;
}

.source-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.source-name {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.source-snippet {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--app-text-secondary);
}

/* ==================== 输入区 ==================== */
.examples {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  margin-top: 10px;
}

.examples-label {
  font-size: 12px;
  color: var(--app-text-secondary);
}

.input-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
}

.input-actions .tip {
  font-size: 12px;
  color: var(--app-text-secondary);
}

@media (max-width: 900px) {
  .stat-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
