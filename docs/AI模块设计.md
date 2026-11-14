# AI 智能助教模块设计

> 本文说明「智能助教」的设计决策与实现，供后续维护与技术评审时参考。
> 代码位置：`online_study_backend/src/main/java/com/online/study/ai/`（含 `engine/` 子包）。
>
> **版本说明**：第 2 节的"手写而不上框架"是 2026-09 上线版的决策，保留作为历史与对照；
> 2026-11 的改造另起一套 Spring AI 2.0 引擎（见第 3 节），两套并存、按 `ai.engine` 切换。

---

## 1. 目标

为平台实现一个基于业务数据的智能助教：

1. **知识库问答（RAG）**：学员问"集合框架讲什么"，助教基于平台内的课程/作业/资源内容回答；
2. **业务查询（Function Calling）**：学员问"我还有几次作业没交"，助教调用后端查询接口拿真实数据回答；
3. **角色感知**：同一个问题，学员和教师看到的答案不同（学员只看自己的数据）。

## 2. 第一版的关键决策：为什么手写而不上框架

| 备选 | 结论 | 原因 |
|---|---|---|
| Spring AI | ❌ 当时不用 | 1.0.0 GA 要求 Spring Boot 3.4+，项目是 3.3.5，强行升级风险大 |
| LangChain | ❌ 不用 | Python 生态，与 Java 后端不同进程，引入跨语言调用复杂度 |
| 阿里云托管知识库 | ❌ 不用 | 数据要出域，且无法自定义检索与工具逻辑 |
| **RestClient 直连大模型 + 自研向量库** | ✅ 采用 | 完全掌控每一环节，也是理解 RAG 原理的最佳方式 |


**与框架的对应关系**：LangChain/Spring AI 里的 ChatClient、Embeddings、TextSplitter、
VectorStore、Agent 五类组件，在本模块中分别由 `AiChatClient`、`AiEmbeddingClient`、
`DbVectorStore.splitIntoChunks()`、`DbVectorStore`、`AiAssistantService`
工具循环手工实现。模型本身调用百炼平台的 OpenAI 兼容端点，协议不变。

## 3. 模块结构

改造后按「引擎可切换 + 组件共用」组织：**两套引擎实现同一个接口**，配置 `ai.engine` 选一个，
检索、工具、历史、向量库这些组件两边共用 —— 换引擎只换"编排方式"，不换"数据与工具"。

| 类 | 职责 |
|---|---|
| `AiEngine` | 引擎接口：`type / ready / unavailableReason / ask`。身份不从参数传，实现内部从 JWT 取 |
| `LegacyAiEngine` | 手写协议层那套（`AiChatClient` + 手工工具循环），保留作对照与降级 |
| `SpringAiEngine` | Spring AI 2.0 版：ChatClient + ToolCallingAdvisor + 多轮记忆 + RAG |
| `AiStreamEngine` | 流式能力接口（可选能力，单独一个接口，legacy 不实现） |
| `AiAssistantService` | 门面：入参校验 → 按配置选引擎 → 转调；灌库与状态查询留在这里 |
| `AiChatClient` | 对话模型 HTTP 客户端（RestClient 直连 `/chat/completions`，含工具协议解析） |
| `AiEmbeddingClient` | 向量模型 HTTP 客户端（`text-embedding-v4`） |
| `DbVectorStore` | 向量库：切块、余弦相似度检索；向量落 `ai_vector_chunk` 表，启动时加载（**重启不丢**） |
| `VectorChunkStore` | 向量持久化：整表替换写入、读出并反序列化（短事务，不横跨网络调用） |
| `KnowledgeChunk` / `SearchHit` | 知识块与检索命中（含分数） |
| `KnowledgeIngestService` | 知识入库：收集库内数据 + 手工资料 → 切块 → 向量化 → 灌库 |
| `AiToolService` / `ToolSpecs` | 工具清单定义与执行（查作业、查课程学员等）；工具名与描述的唯一出处 |
| `StudentToolFunctions` / `TeacherToolFunctions` | 按角色拆的两组 `@Tool` 导入类：学员对象上物理没有教师工具 |
| `AiChatHistoryService` | 对话历史持久化（`ai_chat_message` 表） |
| `AiStreamConfig` | 流式问答专用线程池（与 Web 线程隔离，慢问答不拖垮整站） |

## 4. RAG 全链路

```
课程/作业/资源数据（MySQL）─┐
手工补充资料（ai_knowledge_doc）┤
                          ▼
              KnowledgeIngestService.rebuildAll()
              收集 → 切块(splitIntoChunks) → 向量化(text-embedding-v4)
                          ▼
              DbVectorStore ──写库──→ ai_vector_chunk（真相）
                    │ 启动时读一次
                    ▼
              进程内 List（索引/缓存）
                          ▼ 用户提问
              1. 问题向量化 → 余弦相似度检索 Top-K，按 min-score 过滤
              2. 命中块拼进用户消息（要求"只依据资料回答"）
              3. 调对话模型生成答案
```

**检索算法**：余弦相似度（点积 / 模长乘积），取 Top-K。
**重建（灌库）时机**：**首次提问时若向量库为空，后端会自动重建一次**（懒加载 ——
没人用就不调接口、不花 token）；教师 / 管理员另可在页面点「重建知识库」手动刷新。
向量库是内存实现，**后端重启后会在下次提问时自动重新加载**，不需要人工干预
（彻底持久化已列入路线图）。

## 5. 查库还是灌库？—— 判据

**换一个用户问，答案会不会变？**

- 会变（我的作业、我的课程）→ **不灌向量库**，走 Function Calling 实时查库；
- 不变（课程内容、知识点）→ **灌向量库**，走 RAG 检索。

这条判据决定了两个子系统的分工，也回答了"为什么不用 RAG 解决一切"。

## 6. Function Calling 的实现与安全

实现：OpenAI 工具协议手工实现 —— 请求带 `tools` + `tool_choice=auto`；
模型返回 `tool_calls` → 服务端执行对应工具 → 以 `assistant(tool_calls)` +
`tool(tool_call_id)` 两条消息追加进消息链 → 再次请求模型；最多 5 轮防止无限循环。

**安全底线**：

1. **工具参数里没有 studentId** —— 身份一律从 JWT 解出（`CurrentUserUtil`），
   模型只能决定"查什么"，不能决定"查谁"，杜绝提示词注入冒充他人；
2. 工具清单按角色裁剪 —— 学员的工具集里根本没有"查全体学员"这类能力；
3. 工具返回值经服务端整理后再进消息链，不把原始 SQL 结果直接暴露给模型拼答案。

## 7. 流式输出（SSE）

`POST /ai/ask/stream` 与 `POST /ai/ask` 入参完全相同，响应改成 `text/event-stream`，
**老接口一个字节没动** —— 前端不迁移也能继续用。

事件序列：

```
event: meta    {model, chunkCount, hitCount, sources}      检索完成、还没开始生成时先发一次
event: delta   {"text":"这门课的"}                          重复 N 次
event: delta   {"text":"作业要求是…"}
event: done    {完整 AiAnswerVO：answer/sources/usedTools/token}
data:  [DONE]                                              结束哨兵
```

四个关键取舍：

| 问题 | 做法 | 原因 |
|---|---|---|
| delta 发裸文本还是 JSON | JSON | SSE 的 `data:` 按行切分，模型输出里的换行会把一条事件劈成两条 |
| 线程放哪 | 单独的 `aiStreamExecutor` | 一次问答占线程几秒到几十秒，共用 Web 线程池会被几条慢请求吃光 |
| 异步线程怎么拿到身份 | `DelegatingSecurityContextExecutor` 包一层 | 工具靠 `CurrentUserUtil` 读 `SecurityContextHolder`（ThreadLocal），换线程就读不到 |
| 流到一半失败怎么办 | 一片都没发 → 降级成非流式；已发了一半 → 报错 | 重发会让页面出现两段接不上的答案 |

另外两处容易被漏掉的配置：SSE 必须显式设超时（默认落回容器 30 秒，带工具循环的问答不够用），
Spring Security 6 必须放行 `DispatcherType.ASYNC`（否则异步收尾那一次派发会被判 403）。

## 8. 已知限制与规划

| 限制 | 现状 / 规划 |
|---|---|
| 纯向量检索，专有名词召回弱 | 待做混合检索（向量 + 关键词）；2G 内存装不下 ES，只能走 MySQL FULLTEXT |
| 单机单进程，日额度计数在内存 | 扩成多实例时换成 Redis `INCR` + `EXPIRE`，调用方不用改 |
| 流式接口暂无前端页面消费 | 后端已验证可推送；前端接入排在最后（先保接口与非流式页面稳定） |
| 效果评估靠独立工具 | 已建 60 题评测集与红队集（见 `llm-eval-kit`），不放在本项目里 |
