package com.online.study.ai.engine;

import com.online.study.ai.InMemoryVectorStore;
import com.online.study.common.ResultCode;
import com.online.study.config.AiProperties;
import com.online.study.exception.BizException;
import com.online.study.vo.AiAnswerVO;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

/**
 * spring-ai 引擎 —— 用 Spring AI 2.0 的 ChatClient 重写后的链路。
 *
 * <h3>当前在第几步（S3）</h3>
 * <b>只做纯对话</b>：系统提示词 + 用户问题 → 模型回答。
 * 还没接的两块是刻意留空的，不是漏了：
 * <ul>
 *   <li><b>检索</b>（S6）：要先把向量库换成可落库的实现，否则接上了也得重启重灌</li>
 *   <li><b>工具</b>（S4）：5 个工具要从 {@code switch} 改成 {@code @Tool} 注解</li>
 * </ul>
 *
 * <p>所以这一步跑 60 题，分数会<b>明显低于</b> legacy 的 88% —— 那是预期结果，
 * 不是 bug。它的作用是证明"新引擎这条线能跑通、能出分"，先把对照的坐标轴画出来，
 * 后面每补一块再回头量一次，才看得出每块各自值多少分。
 *
 * <h3>为什么不直接用 starter 的自动配置</h3>
 * {@code spring-ai-starter-model-openai} 的自动配置由 {@code spring.ai.model.chat=openai}
 * 全局开关控制，一旦打开，<b>没配 Key 就会在启动阶段抛异常</b> —— 而 legacy 用户
 * 根本不需要这个 Bean，凭什么要为一个用不上的东西准备 Key。
 *
 * <p>所以这里改成<b>手工构造 + 懒加载</b>：
 * <ul>
 *   <li>legacy 模式下这个 Bean 只是被创建出来等着，不建连接、不读 Key，零开销</li>
 *   <li>spring-ai 模式下第一次提问才真正构造客户端；构造失败也不炸启动，
 *       只是把原因记下来，提问时返回明确的业务异常 —— 对应「企业级 = 可降级」</li>
 * </ul>
 *
 * <h3>baseUrl 的坑</h3>
 * {@code ai.base-url} 是「完整接口前缀」（带 {@code /v1}），这正好是 OpenAI SDK
 * 对 baseUrl 的语义（SDK 自己拼 {@code /chat/completions}）。所以两个引擎能共用
 * 同一个配置项，不用像 yml 里注释担心的那样分两套。
 */
@Slf4j
@Component
public class SpringAiEngine implements AiEngine {

    /** 引擎标识，与 {@code ai.engine} 配置值一致 */
    public static final String TYPE = "spring-ai";

    /**
     * 温度：与 legacy 保持一致（0.2）。
     * <p>对比实验里这个值必须固定 —— 温度一改，答案随机性就变了，分数差异说不清是谁造成的。
     */
    private static final double TEMPERATURE = 0.2;

    private final AiProperties props;
    private final InMemoryVectorStore vectorStore;

    /** 懒加载出来的客户端；volatile + 双重检查，避免并发首次提问时重复构造 */
    private volatile ChatClient chatClient;

    /** 构造失败的原因，用于把"为什么用不了"直接讲给用户听 */
    private volatile String buildError;

    public SpringAiEngine(AiProperties props, InMemoryVectorStore vectorStore) {
        this.props = props;
        this.vectorStore = vectorStore;
    }

    @Override
    public String type() {
        return TYPE;
    }

    /**
     * 只看配置，不碰网络。
     *
     * <p>为什么不在 ready() 里试构造：ready() 会被状态接口、前端轮询反复调用，
     * 每次都建 HTTP 客户端会拖慢页面，而且"连不上"属于运行期问题，
     * 该在真正提问时暴露，不该让状态接口变成探测器。
     */
    @Override
    public boolean ready() {
        return StringUtils.hasText(props.getApiKey());
    }

    @Override
    public String unavailableReason() {
        return "spring-ai 引擎尚未配置 AI API Key。请在 application.yml 的 ai.api-key 填写百炼 Key 后重启服务。";
    }

    @Override
    public AiAnswerVO ask(String question) {
        ChatClient client = client();

        AiAnswerVO vo = new AiAnswerVO();
        vo.setModel(props.getChatModel());
        vo.setChunkCount(vectorStore.size());
        vo.setKnowledgeEmpty(vectorStore.isEmpty());
        // S3 还没接检索与工具：命中数 0、出处空、工具轨迹空。
        // 这三个字段留空而不是省略，是为了让前端拿到的结构在 S3/S6 之间保持一致。
        vo.setHitCount(0);
        vo.setSources(List.of());
        vo.setUsedTools(List.of());
        vo.setPromptTokens(0);
        vo.setCompletionTokens(0);

        try {
            String answer = client.prompt()
                    .user(question)
                    .call()
                    .content();
            vo.setAnswer(StringUtils.hasText(answer)
                    ? answer
                    : "抱歉，这次没能整理出完整答案。可以换个说法再问一次。");
        } catch (Exception e) {
            log.error("spring-ai 引擎调用失败（model={}）", props.getChatModel(), e);
            throw new BizException(ResultCode.SYSTEM_ERROR,
                    "AI 服务暂时不可用：" + rootMessage(e) + "。可临时把 ai.engine 改回 legacy 应急。");
        }
        return vo;
    }

    /**
     * 取（必要时构造）ChatClient。失败只记一次，避免每次提问都重试一遍网络。
     */
    private ChatClient client() {
        ChatClient cached = this.chatClient;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (this.chatClient != null) {
                return this.chatClient;
            }
            if (this.buildError != null) {
                throw new BizException(ResultCode.SYSTEM_ERROR, "AI 引擎初始化失败：" + this.buildError);
            }
            try {
                this.chatClient = build();
                log.info("spring-ai 引擎已就绪：model={}，baseUrl={}",
                        props.getChatModel(), props.getBaseUrl());
                return this.chatClient;
            } catch (Exception e) {
                this.buildError = rootMessage(e);
                log.error("spring-ai 引擎初始化失败", e);
                throw new BizException(ResultCode.SYSTEM_ERROR,
                        "AI 引擎初始化失败：" + this.buildError + "。可临时把 ai.engine 改回 legacy 应急。");
            }
        }
    }

    /**
     * 手工装配 ChatClient：HTTP 客户端 → OpenAI SDK 客户端 → Spring AI 的 ChatModel → ChatClient。
     *
     * <p>四层每一层的职责：
     * <ol>
     *   <li>{@code SpringAiOpenAiHttpClient} —— 真正发 HTTP 的那层（OkHttp），超时设在这里</li>
     *   <li>{@code ClientOptions} —— 认证与地址：apiKey 走它、baseUrl 走它</li>
     *   <li>{@code OpenAiChatModel} —— Spring AI 对 OpenAI 协议的封装，模型名与温度挂在 options 上</li>
     *   <li>{@code ChatClient} —— 面向业务的流畅 API，把系统提示词固化成默认值</li>
     * </ol>
     *
     * <p>{@code maxRetries(1)} 而不是默认值：大模型调用本身慢（十几秒），
     * 失败重试太多次会把用户请求拖到超时，宁可直接报错让他重问。
     */
    private ChatClient build() {
        Duration timeout = Duration.ofMillis(props.getReadTimeout());

        SpringAiOpenAiHttpClient http = SpringAiOpenAiHttpClient.builder()
                .timeout(timeout)
                // 不接监控系统，用 NOOP 而不是 null —— Spring AI 内部可能直接调用它
                .observationRegistry(ObservationRegistry.NOOP)
                .build();

        ClientOptions options = ClientOptions.builder()
                .httpClient(http)
                .apiKey(props.getApiKey())
                .baseUrl(props.getBaseUrl())
                .timeout(timeout)
                .maxRetries(1)
                .build();

        OpenAIClient openAiClient = new OpenAIClientImpl(options);

        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiClient(openAiClient)
                // 必须同时给异步客户端。踩过的坑：只给同步的，build() 内部还会走
                // OpenAiSetup.setupAsyncClient() 现造一个，而它只从 options 里找 credential，
                // 找不到就抛 "At least one credential source must be specified" ——
                // 报错发生在 build() 里，看起来像"客户端没传"，其实传了，缺的是另一半。
                // OpenAIClient.async() 直接复用同一份 ClientOptions，不会再要凭证。
                .openAiClientAsync(openAiClient.async())
                .options(OpenAiChatOptions.builder()
                        .model(props.getChatModel())
                        .temperature(TEMPERATURE)
                        // 第三道保险：万一某个分支只从 options 取凭证，这里也有
                        .apiKey(props.getApiKey())
                        .build())
                .build();

        return ChatClient.builder(model)
                .defaultSystem(EnginePrompts.SYSTEM_PROMPT)
                .build();
    }

    /** 取最内层异常信息，避免给用户看一长串包装类的类名 */
    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String msg = t.getMessage();
        return StringUtils.hasText(msg) ? msg : t.getClass().getSimpleName();
    }
}
