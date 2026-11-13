package com.online.study.ai.engine;

import com.online.study.ai.AiChatClient;
import com.online.study.ai.AiChatHistoryService;
import com.online.study.ai.AiEmbeddingClient;
import com.online.study.ai.AiToolService;
import com.online.study.ai.InMemoryVectorStore;
import com.online.study.ai.KnowledgeIngestService;
import com.online.study.common.ResultCode;
import com.online.study.config.AiProperties;
import com.online.study.exception.BizException;
import com.online.study.vo.AiAnswerVO;
import com.online.study.vo.AiChatMessageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * legacy 引擎 —— 手写协议层的那套实现，2026 年 9 月上线版本。
 *
 * <h3>一次提问发生了什么</h3>
 * <pre>
 *   1. 把学生的问题转成向量        → AiEmbeddingClient
 *   2. 在向量库里找最像的 4 块资料  → InMemoryVectorStore（余弦相似度排序）
 *   3. 把「资料 + 问题 + 工具清单」交给大模型 → AiChatClient
 *   4. 模型要查数据时执行工具、回传结果再问一轮 → AiToolService
 *   5. 返回答案 + 资料出处 + 工具轨迹 → AiAnswerVO
 * </pre>
 *
 * <p><b>两条取信息的路径，各管一摊</b>：
 * <ul>
 *   <li><b>检索（第 2、3 步）</b>—— 静态资料，"翻小抄"。适合"这门课讲什么"</li>
 *   <li><b>工具（第 4 步）</b>—— 实时数据，"打电话问人"。适合"我还有哪几门作业没交"</li>
 * </ul>
 * 两者的边界很好判断：<b>换个人问，答案会不会变</b>。会变就必须查库（工具），
 * 不会变就可以提前灌进向量库（检索）。
 *
 * <p><b>第 3 步的提示词是整个 RAG 的成败关键</b>，见 {@link EnginePrompts#SYSTEM_PROMPT}：
 * 必须明确要求"只依据资料回答、没有就说没有"。不写这句，模型会用自己训练时的
 * 记忆作答 —— 看起来更流畅，但答案可能和课程要求不符（这就是"幻觉"）。
 *
 * <p><b>为什么检索结果还要按相似度过滤</b>：向量检索总会返回 top-k 条，
 * 哪怕库里根本没有相关内容。如果把相似度 0.1 的片段也塞进提示词，模型
 * 会被无关内容误导。所以设一个下限，低于下限的不采纳。
 *
 * <h3>这个类在 P2 改造中的定位</h3>
 * 它的代码是从原 {@code AiAssistantService} 原样搬过来的 —— <b>包括每一行注释，一个字没改</b>。
 * 这是刻意的：S2 阶段的验收门槛是"legacy 跑 60 题分数与基线逐题一致"，
 * 只有逻辑零改动，分数差异才能归因到"抽象本身"，而不是"顺手改了点什么"。
 */
@Slf4j
@Component
public class LegacyAiEngine implements AiEngine {

    /** 引擎标识，与 {@code ai.engine} 配置值一致 */
    public static final String TYPE = "legacy";

    /** 取最相关的几块资料。3~5 块是经验区间：太少可能漏答案，太多会稀释提示词、白烧 token */
    private static final int TOP_K = 4;

    /**
     * 相似度下限（余弦相似度，0~1）。
     * 低于这个值说明"资料和问题基本无关"，宁可回答"没找到"也不塞给模型。
     */
    private static final double MIN_SCORE = 0.28;

    /** 送进提示词的资料总量上限（字符），防止某个问题捞出 4 大块长文把 token 撑爆 */
    private static final int MAX_CONTEXT_CHARS = 4000;

    /** 单次工具结果回传给模型时的长度上限（字符），防止一条超长查询结果撑爆上下文 */
    private static final int MAX_TOOL_RESULT_CHARS = 4000;

    /**
     * 工具调用的最大轮数。
     * <p>为什么要设上限：模型每次都能决定"再调一个工具"，
     * 万一它反复调同一个工具，没有上限就会死循环、无限烧 token。
     * 正常情况下 1~2 轮就够（查一次数据 → 生成答案）。
     */
    private static final int MAX_TOOL_ROUNDS = 5;

    /**
     * 每次提问带回几轮历史问答（一轮 = 一问一答）。
     *
     * <p>为什么要带：模型是无状态的，每次请求都从零开始看。不带历史，"那门课要学多久"
     * 里的"那门课"它就无从判断，只能反问用户 —— 而用户觉得自己明明刚说过。
     *
     * <p>为什么只带 5 轮：历史是"线索"不是"证据"，带得越多越危险 ——
     * ① token 线性增长（每问多几千 token，成本直接翻倍）；
     * ② 太久以前的答案会被模型当成本次答案抄走（比如半小时前查的分数，早过期了）。
     */
    private static final int MAX_HISTORY_ROUNDS = 5;

    /**
     * 单条历史消息的长度上限（字符）。
     * <p>历史里的长表格对"接住指代"毫无帮助，只会把当前问题和参考资料挤到后面去。
     */
    private static final int MAX_HISTORY_CHARS = 300;

    /**
     * 系统提示词本体在 {@link EnginePrompts} —— 两个引擎共用同一份。
     *
     * <p>为什么不在本类里再留一份：P3 要对比的是「换引擎带来了多少提升」。
     * 一旦两个引擎各写一份提示词，分数差异就分不清是引擎的功劳还是提示词的改动，
     * 实验就白跑了。<b>要做提示词实验时也不要改这里</b>，在引擎内部允许覆盖，
     * 结论定了再回填到 {@link EnginePrompts}。
     */

    private final InMemoryVectorStore vectorStore;
    private final AiEmbeddingClient embeddingClient;
    private final AiChatClient chatClient;
    private final KnowledgeIngestService ingestService;
    private final AiToolService toolService;
    private final AiProperties props;
    private final AiChatHistoryService historyService;

    public LegacyAiEngine(InMemoryVectorStore vectorStore,
                          AiEmbeddingClient embeddingClient,
                          AiChatClient chatClient,
                          KnowledgeIngestService ingestService,
                          AiToolService toolService,
                          AiProperties props,
                          AiChatHistoryService historyService) {
        this.vectorStore = vectorStore;
        this.embeddingClient = embeddingClient;
        this.chatClient = chatClient;
        this.ingestService = ingestService;
        this.toolService = toolService;
        this.props = props;
        this.historyService = historyService;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public boolean ready() {
        return chatClient.isConfigured() && embeddingClient.isConfigured();
    }

    @Override
    public String unavailableReason() {
        return "尚未配置 AI API Key。请在 application.yml 的 ai.api-key 填写百炼 Key 后重启服务。";
    }

    /**
     * 提问并拿到带出处的回答。
     *
     * <h3>完整流程（RAG + 工具调用融合）</h3>
     * <pre>
     *   1. 问题向量化 → 检索最像的 4 块资料         （RAG 的"翻小抄"）
     *   1.5 取最近 5 轮历史对话，拼在 system 之后     （让"那门课"接得上）
     *   2. 把「资料 + 问题 + 工具清单」一起发给模型
     *   3. 模型要么直接回答，要么说"我要调 XXX 工具"
     *   4. 若要工具：带着当前登录身份去查库 → 把结果回传给模型 → 回到第 3 步
     *   5. 拿到最终答案 + 资料出处 + 工具调用轨迹
     *   6. 把这一轮问答写进 ai_chat_message，供下次进入页面时回放
     * </pre>
     *
     * <p>两条路径不是二选一：一句"Java 基础的作业什么时候交，我交了没"，
     * 前半句走检索（截止时间在作业要求里），后半句走工具（提交记录在数据库里）。
     *
     * @param q 学生的原话
     */
    @Override
    public AiAnswerVO ask(String q) {
        // 懒加载：第一次提问时自动灌库，避免服务一启动就调接口（没人用的功能不花 token）
        if (vectorStore.isEmpty()) {
            try {
                int count = ingestService.rebuildAll();
                log.info("首次提问触发自动灌库，共 {} 块", count);
            } catch (Exception e) {
                // 灌库失败不中断本次问答 —— 纯数据类问题（"我还有哪几门作业没交"）
                // 靠工具就能答，不依赖向量库，没必要让整个请求失败
                log.warn("自动灌库失败，本次对话将不带参考资料", e);
            }
        }

        AiAnswerVO vo = new AiAnswerVO();
        vo.setModel(props.getChatModel());
        vo.setChunkCount(vectorStore.size());
        vo.setKnowledgeEmpty(vectorStore.isEmpty());

        // ①② 检索（问题向量化 + 余弦相似度 top-k，都在 vectorStore 内部完成）
        List<InMemoryVectorStore.SearchHit> hits = vectorStore.isEmpty()
                ? List.of()
                : vectorStore.search(q, TOP_K);

        // 过滤掉相似度太低的片段；若全被过滤掉但确实有结果，至少保留最相关的一条供模型判断
        List<InMemoryVectorStore.SearchHit> usable = new ArrayList<>();
        for (InMemoryVectorStore.SearchHit hit : hits) {
            if (hit.score() >= MIN_SCORE) {
                usable.add(hit);
            }
        }
        boolean lowConfidence = false;
        if (usable.isEmpty() && !hits.isEmpty()) {
            usable.add(hits.get(0));
            lowConfidence = true;
        }

        vo.setHitCount(usable.size());
        vo.setSources(usable.stream()
                .map(hit -> AiAnswerVO.Source.of(
                        hit.chunk().id(), hit.chunk().source(), hit.chunk().text(), hit.percent()))
                .toList());

        // ③ 拼消息：系统提示词 + 历史对话 + （参考资料 + 问题）
        // 历史夹在 system 和当前问题之间，顺序必须是「早的在前」——
        // 模型按时间顺序理解对话，倒着放会把它当"未来发生的事"。
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(AiChatClient.message("system", EnginePrompts.SYSTEM_PROMPT));
        appendHistory(messages);
        messages.add(AiChatClient.message("user", buildUserMessage(q, usable)));

        List<Map<String, Object>> tools = toolService.definitions();
        List<AiAnswerVO.ToolTrace> traces = new ArrayList<>();
        int promptTokens = 0;
        int completionTokens = 0;
        String answer = null;

        // ④ 多轮循环：模型请求工具 → 执行 → 回传 → 再问
        for (int round = 1; round <= MAX_TOOL_ROUNDS; round++) {
            AiChatClient.ToolChatResult result = chatClient.chatWithTools(messages, tools, 0.2);
            promptTokens += result.promptTokens();
            completionTokens += result.completionTokens();

            if (result.toolCalls().isEmpty()) {
                // 模型没有再要工具，这一轮就是最终答案
                answer = result.content();
                break;
            }

            log.info("第 {} 轮：模型请求调用 {} 个工具", round, result.toolCalls().size());
            // 协议要求：必须把模型"请求工具"的那条消息原样放回去，
            // 下一步的 tool 结果才能和它按 tool_call_id 对上
            messages.add(toolCallMessage(result));

            for (AiChatClient.ToolCall call : result.toolCalls()) {
                String text;
                boolean success = true;
                try {
                    // 注意：这里不传用户 ID —— 身份由 AiToolService 从 JWT 里取，
                    // 模型只能决定"查什么"，不能决定"查谁"
                    text = toolService.execute(call.name(), call.arguments());
                } catch (Exception e) {
                    success = false;
                    log.error("工具 {} 执行失败", call.name(), e);
                    text = "工具执行失败：" + e.getMessage();
                }
                traces.add(AiAnswerVO.ToolTrace.of(call.name(),
                        AiToolService.label(call.name()), summarizeArgs(call.arguments()), success));
                messages.add(toolResultMessage(call.id(), truncate(text, MAX_TOOL_RESULT_CHARS)));
            }
        }

        if (!StringUtils.hasText(answer)) {
            answer = "抱歉，这次没能整理出完整答案。可以换个说法再问一次，"
                    + "或者直接到「课程管理」或「作业管理」页面查看。";
        }

        vo.setAnswer(answer);
        vo.setUsedTools(traces);
        vo.setPromptTokens(promptTokens);
        vo.setCompletionTokens(completionTokens);

        // ⑤ 落库：等一轮问答完整结束后再记录。
        // 不提前记"用户提问"是为了避免出现半截记录 —— 如果模型调用中途失败，
        // 表里会留下一条只有问题、没有答案的孤儿消息，回放时看起来像页面坏了。
        // record() 内部自己吞异常，落库失败不影响用户拿到答案。
        historyService.record(AiChatHistoryService.ROLE_USER, q);
        historyService.record(AiChatHistoryService.ROLE_ASSISTANT, answer);

        if (lowConfidence) {
            log.info("问题「{}」的检索相似度偏低（最高 {}%），回答可能不够准确", q, hits.get(0).percent());
        }
        return vo;
    }

    /**
     * 构造模型"请求调用工具"的那条 assistant 消息。
     * <p>字段名必须与 OpenAI 协议一致（tool_calls / tool_call_id），否则服务端会报参数错误。
     */
    private static Map<String, Object> toolCallMessage(AiChatClient.ToolChatResult result) {
        List<Map<String, Object>> calls = new ArrayList<>();
        for (AiChatClient.ToolCall call : result.toolCalls()) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", call.name());
            function.put("arguments", call.arguments());

            Map<String, Object> one = new LinkedHashMap<>();
            one.put("id", call.id());
            one.put("type", "function");
            one.put("function", function);
            calls.add(one);
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", result.content());
        message.put("tool_calls", calls);
        return message;
    }

    /** 构造工具执行结果的消息，靠 tool_call_id 与上面那条请求对应上 */
    private static Map<String, Object> toolResultMessage(String toolCallId, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "tool");
        message.put("tool_call_id", toolCallId);
        message.put("content", content);
        return message;
    }

    /**
     * 拼装用户消息：参考资料在前、问题在后。
     *
     * <p>为什么把资料放在问题前面：模型对提示词末尾的内容注意力更强，
     * 结尾放问题能让它聚焦在"要回答什么"，而不是被资料带跑。
     */
    private String buildUserMessage(String question, List<InMemoryVectorStore.SearchHit> hits) {
        StringBuilder sb = new StringBuilder();
        sb.append("【参考资料】\n");
        if (hits.isEmpty()) {
            sb.append("（本次没有检索到相关参考资料。如果这个问题涉及你的个人数据，请调用工具查询。）\n");
        }
        int used = 0;
        int index = 1;
        for (InMemoryVectorStore.SearchHit hit : hits) {
            String text = hit.chunk().text();
            if (used + text.length() > MAX_CONTEXT_CHARS && index > 1) {
                break;
            }
            used += text.length();
            sb.append('[').append(index).append("] 来源：").append(hit.chunk().source())
                    .append("（相似度 ").append(hit.percent()).append("%）\n")
                    .append(text).append("\n\n");
            index++;
        }
        sb.append("【问题】\n").append(question);
        return sb.toString();
    }

    /**
     * 把最近 {@value #MAX_HISTORY_ROUNDS} 轮对话拼进 messages。
     *
     * <p><b>历史从哪来</b>：{@link AiChatHistoryService#recent()}，身份由它从 JWT 里取，
     * 前端改不了 —— 否则改个 userId 就能把别人的对话喂给模型。
     *
     * <p><b>三条裁剪规则，缺一不可</b>：
     * <ol>
     *   <li>只取尾巴：recent() 最多返回 50 条，这里只留最近 N 轮（2N 条）</li>
     *   <li>开头对齐到 user：万一上次调用中途失败，表里会留下"只有提问没有回答"的半截记录，
     *       直接从中间截会以 assistant 开头，而对话协议要求第一条必须是 user</li>
     *   <li>单条截断：历史里的长表格对"接住指代"毫无帮助，只会挤占上下文</li>
     * </ol>
     *
     * <p>注意这里<b>只带正文</b>，不带上次的出处和工具轨迹 —— 那些是"当次现场证据"，
     * 带进来反而会让模型把旧数据当新数据。
     */
    private void appendHistory(List<Map<String, Object>> messages) {
        List<AiChatMessageVO> history = historyService.recent();
        if (history.isEmpty()) {
            return;
        }
        int from = Math.max(0, history.size() - MAX_HISTORY_ROUNDS * 2);
        List<AiChatMessageVO> tail = history.subList(from, history.size());
        int start = 0;
        while (start < tail.size() && !AiChatHistoryService.ROLE_USER.equals(tail.get(start).getRole())) {
            start++;
        }
        for (AiChatMessageVO msg : tail.subList(start, tail.size())) {
            String role = AiChatHistoryService.ROLE_USER.equals(msg.getRole()) ? "user" : "assistant";
            String content = truncate(msg.getContent(), MAX_HISTORY_CHARS);
            if (StringUtils.hasText(content)) {
                messages.add(AiChatClient.message(role, content));
            }
        }
    }

    /** 把模型给的参数 JSON 压成一行摘要，用于前端展示"这次查了什么条件" */
    private static String summarizeArgs(String argumentsJson) {
        if (!StringUtils.hasText(argumentsJson)) {
            return "";
        }
        String trimmed = argumentsJson.trim();
        if ("{}".equals(trimmed)) {
            return "";
        }
        return trimmed.replaceAll("^\\{|}$", "")
                .replace("\"", "")
                .replace(",", "，")
                .replace(":", "=");
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…（已截断）";
    }
}
