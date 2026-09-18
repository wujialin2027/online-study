# AI 智能助教模块设计

> 本文说明「智能助教」的设计决策与实现，可作为面试深挖时的参考材料。
> 代码位置：`online_study_backend/src/main/java/com/online/study/ai/`（8 个类）。

---

## 1. 目标

在不引入任何 AI 框架的前提下，为平台实现一个基于业务数据的智能助教：

1. **知识库问答（RAG）**：学员问"集合框架讲什么"，助教基于平台内的课程/作业/资源内容回答；
2. **业务查询（Function Calling）**：学员问"我还有几次作业没交"，助教调用后端查询接口拿真实数据回答；
3. **角色感知**：同一个问题，学员和教师看到的答案不同（学员只看自己的数据）。

## 2. 关键设计决策：为什么手写而不上框架

| 备选 | 结论 | 原因 |
|---|---|---|
| Spring AI | ❌ 不用 | 1.0.0 GA 要求 Spring Boot 3.4+，项目是 3.3.5，强行升级风险大 |
| LangChain | ❌ 不用 | Python 生态，与 Java 后端不同进程，引入跨语言调用复杂度 |
| 阿里云托管知识库 | ❌ 不用 | 数据要出域，且无法自定义检索与工具逻辑 |
| **RestClient 直连大模型 + 自研向量库** | ✅ 采用 | 完全掌控每一环节，也是理解 RAG 原理的最佳方式 |

**与框架的对应关系**：LangChain/Spring AI 里的 ChatClient、Embeddings、TextSplitter、
VectorStore、Agent 五类组件，在本模块中分别由 `AiChatClient`、`AiEmbeddingClient`、
`InMemoryVectorStore.splitIntoChunks()`、`InMemoryVectorStore`、`AiAssistantService`
工具循环手工实现。模型本身调用百炼平台的 OpenAI 兼容端点，协议不变。

## 3. 模块结构（8 个类）

| 类 | 职责 |
|---|---|
| `AiAssistantService` | 编排入口：检索 → 组装提示词 → 工具循环 → 生成答案、对话落库 |
| `AiChatClient` | 对话模型 HTTP 客户端（RestClient 直连 `/chat/completions`，含工具协议解析） |
| `AiEmbeddingClient` | 向量模型 HTTP 客户端（`text-embedding-v4`） |
| `InMemoryVectorStore` | 内存向量库：切块、存储、余弦相似度检索 |
| `KnowledgeChunk` / `SearchHit` | 知识块与检索命中（含分数） |
| `KnowledgeIngestService` | 知识入库：收集库内数据 + 手工资料 → 切块 → 向量化 → 灌库 |
| `AiToolService` | 工具清单定义与执行（查作业、查课程学员等） |
| `AiChatHistoryService` | 对话历史持久化（`ai_chat_message` 表） |

## 4. RAG 全链路

```
课程/作业/资源数据（MySQL）─┐
手工补充资料 ──────────────┤
                          ▼
              KnowledgeIngestService.rebuildAll()
              收集 → 切块(splitIntoChunks) → 向量化(text-embedding-v4)
                          ▼
                  InMemoryVectorStore（内存）
                          ▼ 用户提问
              1. 问题向量化 → 余弦相似度检索 Top-K
              2. 命中块拼进 System 提示词（要求"只依据资料回答"）
              3. 调对话模型生成答案
```

**检索算法**：余弦相似度（点积 / 模长乘积），取 Top-K。
** rebuild 时机**：知识库为空时学员提问会收到"请先重建知识库"提示；
后端重启后内存向量库清空，需手动重建（路线图已列入持久化改造）。

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

## 7. 已知限制与规划

| 限制 | 规划 |
|---|---|
| 向量库在内存，重启即空 | 引入持久化存储（如 PGVector） |
| 纯向量检索，专有名词召回弱 | 混合检索（向量 + 关键词 BM25）+ 重排序 |
| 答案不标出处 | 引用溯源：检索时保留 source，答案标注来源 |
| 无效果评估 | 建立评测集，度量召回率与回答忠实度 |
