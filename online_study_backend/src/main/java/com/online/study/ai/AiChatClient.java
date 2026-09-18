package com.online.study.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.online.study.common.ResultCode;
import com.online.study.config.AiProperties;
import com.online.study.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话客户端 —— 把「提示词」交给大模型，拿回一段回答。
 *
 * <p><b>和向量化客户端的区别</b>：
 * <ul>
 *   <li>{@link AiEmbeddingClient}：负责"把话变成坐标"，用于检索</li>
 *   <li>本类：负责"把话说漂亮"，用于生成最终答案</li>
 * </ul>
 * 这是两个不同的接口、两次独立调用，也是两笔独立的 token 费用。
 *
 * <p>走的是百炼「OpenAI 兼容」端点，请求体格式与 OpenAI 官方一致，
 * 因此换成任何 OpenAI 兼容的服务商（DeepSeek 官方、智谱、火山方舟……）
 * 只需要改 {@code ai.base-url} 与 {@code ai.chat-model}，代码一行不用动。
 */
@Slf4j
@Component
public class AiChatClient {

    private final AiProperties props;
    private final RestClient client;

    public AiChatClient(AiProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.getConnectTimeout()));
        factory.setReadTimeout(Duration.ofMillis(props.getReadTimeout()));
        // 注意：这里刻意不设 baseUrl —— 改由 props.endpoint(...) 拼绝对地址后传给 .uri(URI)，
        // 否则 Spring 拼接相对路径时不会补斜杠（见 AiProperties#endpoint 的说明）。
        this.client = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 单轮对话：给定系统提示词与用户问题，返回模型回答。
     *
     * @param systemPrompt 系统提示词（约束模型的角色与回答范围，RAG 里用来限定"只依据资料回答"）
     * @param userMessage  用户问题（RAG 里会把检索到的资料一起拼进来）
     */
    public ChatResult chat(String systemPrompt, String userMessage) {
        return chat(systemPrompt, userMessage, 0.3);
    }

    /**
     * @param temperature 随机性。0 最稳定、适合"照着资料答"；越高越放飞。
     *                    RAG 场景建议 0.1~0.3 —— 我们要的是忠实于资料，不是创作。
     */
    public ChatResult chat(String systemPrompt, String userMessage, double temperature) {
        ToolChatResult result = chatWithTools(List.of(
                message("system", systemPrompt),
                message("user", userMessage)), null, temperature);
        if (!StringUtils.hasText(result.content())) {
            throw new BizException(ResultCode.SYSTEM_ERROR.getCode(), "模型没有返回任何内容");
        }
        return new ChatResult(result.content(), result.promptTokens(), result.completionTokens());
    }

    /**
     * 带「工具」的对话 —— 工具调用（Function Calling）的核心方法。
     *
     * <p>和 {@link #chat} 的区别只有两点：
     * <ol>
     *   <li>多传一个 {@code tools} 参数：把「有哪些工具、各要什么参数」告诉模型</li>
     *   <li>返回值可能不是文字，而是 {@code toolCalls}（模型说"我要调这个工具"）</li>
     * </ol>
     *
     * <p><b>为什么 messages 要用 List&lt;Map&gt; 而不是固定两个字段</b>：
     * 工具调用是<b>多轮</b>的 —— 第一轮模型回 tool_calls，第二轮我们要把
     * 「模型的那条请求」和「工具的执行结果」作为新消息追加进去再问一次。
     * 消息列表会越来越长，所以必须用可变的通用结构。
     *
     * @param messages    完整消息链（含历史轮次），每条形如
     *                    {@code {"role":"user","content":"..."}}、
     *                    {@code {"role":"assistant","tool_calls":[...]}}、
     *                    {@code {"role":"tool","tool_call_id":"...","content":"..."}}
     * @param tools       工具定义；传 null 或空表示本次不允许调工具
     * @param temperature 随机性
     */
    public ToolChatResult chatWithTools(List<Map<String, Object>> messages,
                                        List<Map<String, Object>> tools,
                                        double temperature) {
        requireConfigured();

        String url = props.endpoint("/chat/completions");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getChatModel());
        body.put("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            // auto = 由模型自己决定调不调；若改成 required 就变成"必须调一个工具"，会破坏普通问答
            body.put("tool_choice", "auto");
        }
        body.put("temperature", temperature);
        body.put("stream", false);

        ChatResponse response;
        try {
            response = client.post()
                    .uri(URI.create(url))
                    .body(body)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (RestClientResponseException e) {
            log.error("对话调用失败：POST {} -> HTTP {}，响应体={}", url, e.getStatusCode(), e.getResponseBodyAsString());
            throw new BizException(ResultCode.SYSTEM_ERROR.getCode(),
                    "对话调用失败（HTTP " + e.getStatusCode().value() + "）：" + describe(e, url));
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new BizException(ResultCode.SYSTEM_ERROR.getCode(),
                    "对话返回为空，请确认模型 code 是否正确：" + props.getChatModel());
        }

        // 注意：Message / RawToolCall 是 ChatResponse 的嵌套 record，必须用完整限定名引用
        ChatResponse.Message msg = response.choices().get(0).message();
        List<ToolCall> calls = new ArrayList<>();
        if (msg != null && msg.tool_calls() != null) {
            for (ChatResponse.RawToolCall raw : msg.tool_calls()) {
                if (raw != null && raw.function() != null) {
                    calls.add(new ToolCall(raw.id(), raw.function().name(), raw.function().arguments()));
                }
            }
        }

        int promptTokens = response.usage() == null || response.usage().prompt_tokens() == null
                ? 0 : response.usage().prompt_tokens();
        int completionTokens = response.usage() == null || response.usage().completion_tokens() == null
                ? 0 : response.usage().completion_tokens();
        return new ToolChatResult(msg == null ? null : msg.content(), calls, promptTokens, completionTokens);
    }

    /**
     * 构造一条消息。用 LinkedHashMap 而不是 Map.of —— 因为需要放 null 值
     * （模型请求工具时 {@code content} 就是 null），而 Map.of 遇到 null 会直接抛异常。
     */
    public static Map<String, Object> message(String role, String content) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", role);
        map.put("content", content);
        return map;
    }

    /** 是否已在配置里填好 Key */
    public boolean isConfigured() {
        return StringUtils.hasText(props.getApiKey());
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new BizException(ResultCode.SYSTEM_ERROR.getCode(),
                    "尚未配置 AI API Key。请在 application.yml 的 ai.api-key 填写百炼 Key 后重启服务。");
        }
    }

    private static String describe(RestClientResponseException e, String url) {
        String body = e.getResponseBodyAsString();
        if (!StringUtils.hasText(body)) {
            return "服务端未返回内容，请核对 ai.base-url 是否正确（实际请求：" + url + "）";
        }
        return body;
    }

    // ==================== 响应体结构（OpenAI 兼容格式） ====================

    /**
     * 一次对话的结果。
     *
     * @param content          模型回答正文
     * @param promptTokens     本次消耗的输入 token（可用于界面展示与成本核算）
     * @param completionTokens 本次消耗的输出 token
     */
    public record ChatResult(String content, int promptTokens, int completionTokens) {
    }

    /**
     * 一次带工具的对话结果。
     *
     * <p>{@code content} 与 {@code toolCalls} 通常只有一个有值：
     * <ul>
     *   <li>模型直接回答 → content 有值、toolCalls 为空</li>
     *   <li>模型要查数据 → content 为 null、toolCalls 非空（此时要执行工具后再问一轮）</li>
     * </ul>
     *
     * @param content          模型回答正文（模型请求工具时为 null）
     * @param toolCalls        模型希望调用的工具列表
     * @param promptTokens     本次消耗的输入 token
     * @param completionTokens 本次消耗的输出 token
     */
    public record ToolChatResult(String content, List<ToolCall> toolCalls,
                                 int promptTokens, int completionTokens) {
    }

    /**
     * 模型发起的一次工具调用请求。
     *
     * @param id        调用编号。服务端执行完要把结果按这个 id 回传，模型才知道"这是哪一次请求的结果"
     * @param name      工具名
     * @param arguments 参数（JSON 字符串。即便没有参数，模型通常也会给一个 "{}"）
     */
    public record ToolCall(String id, String name, String arguments) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatResponse(List<Choice> choices, Usage usage) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Choice(Message message) {
        }

        /**
         * 一条消息。注意 {@code tool_calls} 只在"模型请求调工具"时出现，
         * 平时为 null —— 解析时要做空判断。
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Message(String role, String content, List<RawToolCall> tool_calls) {
        }

        /** 原始的工具调用结构，字段名与 OpenAI 协议一致（下划线风格） */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record RawToolCall(String id, String type, RawFunction function) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record RawFunction(String name, String arguments) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Usage(Integer prompt_tokens, Integer completion_tokens, Integer total_tokens) {
        }
    }
}
