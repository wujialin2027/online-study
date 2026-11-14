package com.online.study.ai.engine;

import com.online.study.ai.AbstractToolFunctions;
import com.online.study.ai.AiChatHistoryService;
import com.online.study.ai.AiToolService;
import com.online.study.ai.DbVectorStore;
import com.online.study.ai.KnowledgeIngestService;
import com.online.study.ai.StudentToolFunctions;
import com.online.study.ai.TeacherToolFunctions;
import com.online.study.common.ResultCode;
import com.online.study.config.AiProperties;
import com.online.study.exception.BizException;
import com.online.study.utils.CurrentUserUtil;
import com.online.study.vo.AiAnswerVO;
import com.online.study.vo.AiChatMessageVO;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallLimitBehavior;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * spring-ai 引擎 —— 用 Spring AI 2.0 的 ChatClient 重写后的链路。
 *
 * <h3>当前在第几步（S7）</h3>
 * <b>检索 + 工具调用 + 多轮记忆 + 流式输出，四块齐了</b>：问题向量化 → 找最像的 4 块资料 →
 * 连同工具清单交给模型 → 模型自己决定"翻小抄"还是"查数据库" → 边生成边往浏览器推字。
 *
 * <h3>S7 的取舍：流式只换"运输方式"，不换"生产流程"</h3>
 * 流式最容易犯的错是为了"能边出字"把整条链路重写一遍（检索、提示词、记忆各写一份），
 * 结果两条链路慢慢分叉：非流式修了 bug，流式没修；两边的分数也对不上。
 * 所以这里把"准备阶段"抽成 {@link #prepare(String)}（灌库 + 检索 + 历史 + 工具对象 + VO 骨架），
 * 两种输出方式<b>共用同一份准备结果</b>，差别只在最后那一步是 {@code .call()} 还是 {@code .stream()}。
 *
 * <p>三条实现细节：
 * <ul>
 *   <li><b>用 {@code .stream().chatResponse()} 而不是 {@code .stream().content()}</b>：
 *       前者每片还带着 metadata（含 token 用量），后者只有字符串。</li>
 *   <li><b>一片都没吐出去就失败 → 降级成一次性回答</b>（见 {@link #askStream}）。
 *       已经吐了一半再失败只能报错 —— 重发会让用户看到两段接不上的答案。</li>
 *   <li><b>落库仍等整轮结束</b>：流式过程中往 {@code ai_chat_message} 写半截答案，
 *       用户中途关页面就会在历史里留下残缺记录。</li>
 * </ul>
 *
 * <p><b>S6 的验证状态</b>：落库/重启不丢已实测（重启后日志「向量库已从数据库加载：106 块」）；
 * 检索层用只打向量接口的探针验过（知识类 17/22 命中）。<b>60 题全量没跑</b> ——
 * 对话模型免费额度用尽，等充值后随 S8 一起跑，届时才有和 legacy 88% 的正式对照。
 * 若对不上，说明差异不在"有没有检索"，而在"检索怎么用"（提示词拼装顺序、
 * 阈值过滤、低置信兜底），要逐条对比 {@link LegacyAiEngine#ask} 的同一段逻辑。
 *
 * <h3>S6 的取舍：检索结果不过模型，但过阈值</h3>
 * 向量检索<b>总会</b>返回 top-k 条，哪怕库里根本没有相关内容 —— 那也是"最像的 4 条"。
 * 所以必须按相似度筛一遍（{@code ai.rag.min-score}，默认 0.28），
 * 否则相似度 0.1 的无关片段会被塞进提示词，模型照着它编答案，比"没有资料"更糟。
 * 全被筛掉时保留最相关的一条并打日志，而不是一条都不给：
 * 一条低分资料 + 严格的"没写就说没有"提示词，效果优于"完全没有资料"（后者模型倾向自由发挥）。
 *
 * <h3>S5 的取舍：为什么不用 {@code MessageWindowChatMemory}</h3>
 * Spring AI 自带一套记忆抽象（{@code ChatMemory} + {@code ChatMemoryRepository} +
 * {@code MessageChatMemoryAdvisor}），看起来"应该"用它。这里没用，理由三条：
 * <ol>
 *   <li><b>记忆的真相在数据库，不在内存。</b>本项目要求换设备登录还能看到自己的历史、
 *       能清空、能审计（{@code ai_chat_message} 表）。框架的默认实现是
 *       {@code InMemoryChatMemoryRepository} —— 重启即丢，对一个"要能演示、要能回放"
 *       的系统来说等于没有。</li>
 *   <li><b>要落库就得写一个 {@code ChatMemoryRepository} 适配层，而这个适配层有个坑：</b>
 *       {@code saveAll(conversationId, messages)} 每次传的是<b>整个窗口</b>，
 *       而表里只该存"用户提问 + 最终回答"两条。工具循环过程中还有
 *       {@code AssistantMessage(toolCalls)} 这类中间消息，它们不该进历史表
 *       （否则回放时页面上会出现一堆空消息）。要在适配层里按类型过滤，
 *       逻辑量已经超过"自己算窗口"。</li>
 *   <li><b>要和 legacy 逐题可比。</b>legacy 的记忆策略是三条裁剪规则
 *       （取尾巴 / 对齐到 user / 单条截断），这里照抄同一套，
 *       两边的多轮分数差异才只由"引擎"解释，而不是"记忆实现不同"。</li>
 * </ol>
 * 结论：{@code props.getMemory().getWindow()} 轮 × 2 条，直接从表里取、
 * 转成 {@link Message} 显式传给 {@code ChatClient.messages(...)}。
 * 框架的记忆抽象适合"没有持久层、只在一台机器上跑"的场景，本项目不属于那种场景。
 *
 * <h3>S4 的关键点：工具怎么进来</h3>
 * <pre>
 *   ① 工具定义：{@code @Tool} 注解（{@code StudentToolFunctions} / {@code TeacherToolFunctions}）
 *   ② 工具执行：{@code ToolCallingAdvisor} —— Spring AI 2.0 把"调工具再回问"的循环从 ChatModel
 *      挪到了 Advisor 链上。不注册这个 Advisor，模型会一直返回 tool_calls，但<b>没人执行</b>，
 *      最终答案就是空的。这是 2.0 与 1.x 最不一样的地方，也是升级时最容易漏的一步。
 *   ③ 调用上限：{@code DefaultToolCallingManager.maxTotalToolCalls} —— 对应 legacy 的
 *      {@code MAX_TOOL_ROUNDS}，防止模型反复查库把费用烧光。
 * </pre>
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
public class SpringAiEngine implements AiEngine, AiStreamEngine {

    /** 引擎标识，与 {@code ai.engine} 配置值一致 */
    public static final String TYPE = "spring-ai";

    /**
     * 温度：与 legacy 保持一致（0.2）。
     * <p>对比实验里这个值必须固定 —— 温度一改，答案随机性就变了，分数差异说不清是谁造成的。
     */
    private static final double TEMPERATURE = 0.2;

    /**
     * 历史单条截断长度，与 legacy 的 {@code MAX_HISTORY_CHARS} 一致。
     * <p>历史里的长表格对"接住指代"（把上一句的"它""那门课"指回具体对象）毫无帮助，
     * 只挤占上下文预算，所以每条只留开头 300 字。
     */
    private static final int MAX_HISTORY_CHARS = 300;

    /**
     * 送进提示词的资料总量上限（字符），与 legacy 的 {@code MAX_CONTEXT_CHARS} 一致。
     * <p>4 块 × 400 字本身就在 1600 字上下，这个上限真正的作用是防"某份资料的块特别长"
     * （手工补的长讲义），避免单个问题把上下文预算吃光。
     */
    private static final int MAX_CONTEXT_CHARS = 4000;

    private final AiProperties props;
    private final DbVectorStore vectorStore;

    /**
     * 知识入库服务。只在"第一次提问、向量库还是空的"时候用一次 —— 与 legacy 一致，
     * 懒加载灌库，不让服务一启动就调几十次向量接口（没人用 AI 的时候不该花这笔钱）。
     */
    private final KnowledgeIngestService ingestService;

    /**
     * 工具的真正实现。两个引擎共用同一个实例 —— 换个引擎不能让"查数据库"这件事换一套逻辑，
     * 否则 60 题分不清是"编排方式变了"还是"查询结果变了"。
     */
    private final AiToolService toolService;

    /**
     * 对话记录。两个引擎共用同一个 Service、同一张表 ——
     * 这是"换引擎不能换掉用户的历史"的直接体现：切回 legacy 后，
     * 用户在 spring-ai 模式下问过的话还在，模型接得上。
     */
    private final AiChatHistoryService historyService;

    /** 懒加载出来的客户端；volatile + 双重检查，避免并发首次提问时重复构造 */
    private volatile ChatClient chatClient;

    /** 构造失败的原因，用于把"为什么用不了"直接讲给用户听 */
    private volatile String buildError;

    public SpringAiEngine(AiProperties props, DbVectorStore vectorStore, AiToolService toolService,
                          AiChatHistoryService historyService, KnowledgeIngestService ingestService) {
        this.props = props;
        this.vectorStore = vectorStore;
        this.toolService = toolService;
        this.historyService = historyService;
        this.ingestService = ingestService;
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
        return generate(prepare(question), question);
    }

    /**
     * 非流式生成：拿一份已经准备好的上下文，调一次模型，落库，返回完整结果。
     *
     * <p>单独抽出来是为了给流式做<b>降级</b>用：流式一片都没吐出去就失败时，
     * 直接拿同一份 {@link Prepared} 再走一遍非流式，而不是重新 {@code prepare} ——
     * 重新准备意味着再调一次向量接口（检索要钱、要时间），而这些结果本来就没变。
     */
    private AiAnswerVO generate(Prepared prep, String question) {
        try {

            ChatResponse response = prep.client().prompt()
                    // 历史在前、本轮问题在后。顺序不能反 —— 对话协议要求最后一条是 user，
                    // 反了等于让模型"先答再问"。
                    .messages(prep.history())
                    // 参考资料拼进用户消息（不是 system）：资料属于"这一次的现场证据"，
                    // 而 system 是整场对话立下的规矩，两者混在一起会让模型把旧资料当规矩
                    .user(prep.userMessage())
                    // 按角色给工具清单：学员手里根本没有教师工具（见两个 Tools 类的注释）
                    .tools(prep.functions())
                    .call()
                    .chatResponse();

            String answer = extractText(response);
            AiAnswerVO vo = prep.vo();
            vo.setAnswer(StringUtils.hasText(answer)
                    ? answer
                    : "抱歉，这次没能整理出完整答案。可以换个说法再问一次。");
            vo.setUsedTools(prep.functions().traces());
            fillTokens(vo, response);

            // 落库：等整轮问答成功后才写两条。
            // 顺序很重要 —— 先 record(user) 再 record(assistant)，
            // 且只在拿到答案之后写，避免模型中途失败时表里留下"只有提问没有回答"的孤儿消息。
            // record() 内部自己吞异常：记不上只影响回放，不能让用户连答案都拿不到。
            recordHistory(question, vo);

            log.info("spring-ai 引擎问答完成：参考资料 {} 块，带入历史 {} 条，工具调用 {} 次，prompt={}，completion={}",
                    prep.usable().size(), prep.history().size(), vo.getUsedTools().size(),
                    vo.getPromptTokens(), vo.getCompletionTokens());
            return vo;
        } catch (Exception e) {
            log.error("spring-ai 引擎调用失败（model={}）", props.getChatModel(), e);
            throw new BizException(ResultCode.SYSTEM_ERROR,
                    "AI 服务暂时不可用：" + rootMessage(e) + "。可临时把 ai.engine 改回 legacy 应急。");
        }
    }

    /**
     * 流式回答（S7）。
     *
     * <p>生产流程与 {@link #ask} <b>完全一致</b>（同一份 {@link #prepare} 结果），
     * 只有最后一步从 {@code .call()} 换成 {@code .stream()}。
     *
     * <p><b>失败处理分两种情况</b>：
     * <ul>
     *   <li><b>还没吐过任何字符</b>（连不上、Key 错、模型拒答）：降级调一次 {@code ask()}，
     *       把整段答案当一片推出去。对用户来说只是"慢了一点"，不是"坏了"。</li>
     *   <li><b>已经吐了一半</b>：只能发错误。重发会让页面上出现两段接不上的答案，
     *       用户分不清哪段是真的 —— 宁可报错让他重问。</li>
     * </ul>
     *
     * <p><b>为什么用 {@code blockLast()} 而不是全程响应式</b>：这条链路是"阻塞式 Servlet 模型"，
     * 线程由 {@code aiStreamExecutor} 提供。改成纯响应式要一路把 Controller 换成 WebFlux，
     * 为一个接口不值得。{@code blockLast()} 在这里的作用只是"把 Flux 拉成一个循环"。
     */
    @Override
    public void askStream(String question, StreamSink sink) {
        Prepared prep = prepare(question);

        // 出处此时已确定：先推一拍上下文，前端可以把引用列表和"正在输入…"一起渲染出来
        sink.onMeta(prep.vo());

        StringBuilder collected = new StringBuilder();
        AtomicBoolean emitted = new AtomicBoolean(false);
        AtomicReference<Usage> lastUsage = new AtomicReference<>();

        try {
            prep.client().prompt()
                    .messages(prep.history())
                    .user(prep.userMessage())
                    .tools(prep.functions())
                    .stream()
                    // chatResponse() 而不是 content()：每一片还带 metadata，
                    // 最后一片里有 token 用量，content() 会把它们丢掉。
                    .chatResponse()
                    .doOnNext(chunk -> {
                        String delta = extractText(chunk);
                        if (StringUtils.hasText(delta)) {
                            collected.append(delta);
                            emitted.set(true);
                            // 先推给调用方再记日志：推字的先后顺序就是用户看到的顺序
                            sink.onDelta(delta);
                        }
                        Usage usage = usageOf(chunk);
                        if (usage != null) {
                            lastUsage.set(usage);
                        }
                    })
                    .blockLast();
        } catch (Exception e) {
            log.error("spring-ai 流式调用失败（model={}，已输出 {} 字）",
                    props.getChatModel(), collected.length(), e);
            if (emitted.get()) {
                sink.onError(e);
                return;
            }
            // 一片都没出去 —— 降级成一次性回答（复用同一份 prep，不再重复检索）
            try {
                AiAnswerVO fallback = generate(prep, question);
                sink.onDelta(fallback.getAnswer());
                sink.onComplete(fallback);
            } catch (Exception inner) {
                sink.onError(inner);
            }
            return;
        }

        AiAnswerVO vo = prep.vo();
        String text = collected.toString();
        vo.setAnswer(StringUtils.hasText(text)
                ? text
                : "抱歉，这次没能整理出完整答案。可以换个说法再问一次。");
        vo.setUsedTools(prep.functions().traces());
        // 流式下 token 只能拿到"最后一片"的 metadata，与 ask() 同一个口径（见 fillTokens 注释）
        fillTokens(vo, lastUsage.get());

        // 落库仍等整轮结束：中途写半截答案会在历史里留下残缺记录。
        // 此时 vo 里 token 已由 fillTokens 填好，落库顺带把成本口径记下来。
        recordHistory(question, vo);

        log.info("spring-ai 流式问答完成：参考资料 {} 块，带入历史 {} 条，工具调用 {} 次，输出 {} 字",
                prep.usable().size(), prep.history().size(), vo.getUsedTools().size(), text.length());
        sink.onComplete(vo);
    }

    /**
     * 准备阶段：两种输出方式共用的那一段（流式改造时抽出来的）。
     *
     * <p>顺序<b>必须</b>与原 {@code ask()} 一致，否则两边的分数差异就说不清来源：
     * 取客户端 → 空库则灌库 → 建工具对象 → 建 VO 骨架 → 检索 → 读历史 → 拼用户消息。
     *
     * @return 一份不可变的准备结果，交给 {@code .call()} 或 {@code .stream()}
     */
    private Prepared prepare(String question) {
        ChatClient client = client();

        // 懒加载灌库：只有库里一块都没有时才触发（通常是第一次部署、或手动清了表）。
        // 注意 S6 之后这里已经不会"每次重启都灌一遍"了 —— 向量存在 ai_vector_chunk 里，
        // 启动时由 DbVectorStore 读进内存，只有首次提问 + 空库才真的调向量接口。
        if (vectorStore.isEmpty()) {
            try {
                int count = ingestService.rebuildAll();
                log.info("首次提问触发自动灌库，共 {} 块", count);
            } catch (Exception e) {
                // 灌库失败不中断本次问答：纯数据类问题（"我还有哪几门作业没交"）
                // 靠工具就能答，不依赖向量库，没必要让整个请求失败
                log.warn("自动灌库失败，本次对话将不带参考资料", e);
            }
        }

        // 每次问答新建一个工具对象：它带着"本次调过哪些工具"的轨迹，不能跨请求共用
        AbstractToolFunctions functions = toolFunctions();

        AiAnswerVO vo = new AiAnswerVO();
        vo.setModel(props.getChatModel());
        vo.setChunkCount(vectorStore.size());
        vo.setKnowledgeEmpty(vectorStore.isEmpty());

        // ①② 检索 + 过滤（逻辑与 legacy 的同一段一一对应，原因见类注释「S6 的取舍」）
        List<DbVectorStore.SearchHit> usable = retrieve(question);
        vo.setHitCount(usable.size());
        vo.setSources(usable.stream()
                .map(hit -> AiAnswerVO.Source.of(
                        hit.chunk().id(), hit.chunk().source(), hit.chunk().text(), hit.percent()))
                .toList());

        // 历史在本次提问<b>之前</b>读，所以不含本轮 —— 本轮的问答在成功之后才落库
        List<Message> history = loadHistoryMessages();

        return new Prepared(client, functions, usable, history,
                buildUserMessage(question, usable), vo);
    }

    /**
     * 一次问答的准备结果。
     * <p>字段全部在构造时定好、之后不再改，所以两种输出方式各拿一份也不会互相干扰
     * （{@code vo} 是可变的，但每次 {@code prepare} 都新建，不存在共用）。
     */
    private record Prepared(ChatClient client,
                            AbstractToolFunctions functions,
                            List<DbVectorStore.SearchHit> usable,
                            List<Message> history,
                            String userMessage,
                            AiAnswerVO vo) {
    }

    /**
     * 落库两条：先提问后回答。失败只打日志，不影响用户拿答案（见 {@code record} 内部实现）。
     *
     * <p>收 {@link AiAnswerVO} 而不是只收答案字符串：engine / model / token 都在 VO 里，
     * 传 VO 免得再拆成四个参数。token 只写 assistant 行 —— 这一轮累计的输入 token
     * 已经把提问算在里面，两边都写等于同一个问题收两次费。
     */
    private void recordHistory(String question, AiAnswerVO vo) {
        historyService.record(AiChatHistoryService.ROLE_USER, question,
                type(), vo.getModel(), 0, 0);
        historyService.record(AiChatHistoryService.ROLE_ASSISTANT, vo.getAnswer(),
                type(), vo.getModel(), vo.getPromptTokens(), vo.getCompletionTokens());
    }

    /**
     * 按当前登录角色挑工具对象。
     *
     * <p>这里<b>只做二选一</b>，不做"给全部再过滤"：两个类各自只声明自己那三个工具，
     * 学员的对象上物理没有教师工具，模型连"有这个东西"都不知道。
     * 安全边界靠"看不见"而不是"拦住"，这一点和 legacy 的 {@code definitions()} 是一致的。
     */
    private AbstractToolFunctions toolFunctions() {
        return "student".equals(CurrentUserUtil.getRole())
                ? new StudentToolFunctions(toolService)
                : new TeacherToolFunctions(toolService);
    }

    /**
     * 检索 + 阈值过滤。
     *
     * <p>与 legacy 的同一段<b>等价</b>，只有一处刻意的差别：top-k 与阈值读配置
     * （{@code ai.rag.top-k} / {@code ai.rag.min-score}），而 legacy 读类里的常量。
     * 两边的默认值相同（4 / 0.28），所以跑同一份题集结论可比；
     * 但调优时只需要改 yml 重启，不用改 legacy 代码重新打包。
     *
     * <p>全被阈值筛掉时保留最相关的一条 —— 见类注释「S6 的取舍」。
     */
    private List<DbVectorStore.SearchHit> retrieve(String question) {
        if (vectorStore.isEmpty()) {
            return List.of();
        }
        AiProperties.Rag rag = props.getRag();
        List<DbVectorStore.SearchHit> hits = vectorStore.search(question, rag.getTopK());

        List<DbVectorStore.SearchHit> usable = new ArrayList<>();
        for (DbVectorStore.SearchHit hit : hits) {
            if (hit.score() >= rag.getMinScore()) {
                usable.add(hit);
            }
        }
        if (usable.isEmpty() && !hits.isEmpty()) {
            usable.add(hits.get(0));
            log.info("问题「{}」的检索相似度偏低（最高 {}%），只保留最相关的一条", question, hits.get(0).percent());
        }
        return usable;
    }

    /**
     * 拼用户消息：参考资料在前、问题在后。
     *
     * <p>为什么资料在前、问题在后：模型对提示词<b>末尾</b>的内容注意力更强，
     * 结尾放问题能让它聚焦"要回答什么"，而不是被资料带着跑。
     *
     * <p>这段拼装与 legacy 的 {@code buildUserMessage} 逐字等价（含分隔符与"相似度"标注）——
     * 提示词一改，两个引擎的分数差异就说不清是引擎的功劳还是提示词的改动了。
     */
    private static String buildUserMessage(String question, List<DbVectorStore.SearchHit> hits) {
        StringBuilder sb = new StringBuilder();
        sb.append("【参考资料】\n");
        if (hits.isEmpty()) {
            sb.append("（本次没有检索到相关参考资料。如果这个问题涉及你的个人数据，请调用工具查询。）\n");
        }
        int used = 0;
        int index = 1;
        for (DbVectorStore.SearchHit hit : hits) {
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
     * 取最近 {@code ai.memory.window} 轮对话，转成 Spring AI 的 {@link Message} 列表。
     *
     * <p>三条裁剪规则与 legacy 的 {@code appendHistory} <b>逐条对齐</b> —— 这不是巧合：
     * 两边用不同实现跑同一份 60 题，记忆策略必须等价，否则多轮分数差就说不清
     * 到底是"引擎换了"还是"记忆口径变了"。
     * <ol>
     *   <li><b>取尾巴</b>：{@code recent()} 最多 50 条，这里只留最近 window 轮（2×window 条）</li>
     *   <li><b>开头对齐到 user</b>：上次调用中途失败会在表里留下"只有提问、没有回答"的半截记录。
     *       直接从中间截会得到一条以 assistant 开头的列表，而对话协议要求第一条是 user</li>
     *   <li><b>单条截断</b>：见 {@link #MAX_HISTORY_CHARS}</li>
     * </ol>
     *
     * <p>只带正文，<b>不带</b>上次的出处与工具轨迹 —— 那些是"当次现场证据"。
     * 带进历史会让模型把上一次查到的旧数字当成这一次的事实，多轮里最容易出这种错。
     *
     * <p>读历史失败<b>不影响问答</b>：降级成"没有历史"继续问，只打一行 warn。
     * 记忆是锦上添花，不该变成单点故障。
     */
    private List<Message> loadHistoryMessages() {
        int window = Math.max(1, props.getMemory().getWindow());
        try {
            List<AiChatMessageVO> history = historyService.recent();
            if (history == null || history.isEmpty()) {
                return List.of();
            }
            int from = Math.max(0, history.size() - window * 2);
            List<AiChatMessageVO> tail = history.subList(from, history.size());

            int start = 0;
            while (start < tail.size()
                    && !AiChatHistoryService.ROLE_USER.equals(tail.get(start).getRole())) {
                start++;
            }

            List<Message> messages = new ArrayList<>(tail.size() - start);
            for (AiChatMessageVO msg : tail.subList(start, tail.size())) {
                String text = truncate(msg.getContent(), MAX_HISTORY_CHARS);
                if (!StringUtils.hasText(text)) {
                    continue;
                }
                messages.add(AiChatHistoryService.ROLE_USER.equals(msg.getRole())
                        ? new UserMessage(text)
                        : new AssistantMessage(text));
            }
            return messages;
        } catch (Exception e) {
            log.warn("读取对话历史失败，本次按无历史处理：{}", e.getMessage());
            return List.of();
        }
    }

    /** 超长截断；null 一律当空串，避免 null 混进消息体 */
    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…（已截断）";
    }

    /** 取回答正文；任何一环为 null 都安全返回 null，由调用方给兜底话术 */
    private static String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    /**
     * 填 token 用量。
     *
     * <p><b>口径提醒</b>：工具循环里每轮都是一次独立的模型调用，这里的 metadata 只覆盖
     * <b>最后一次</b>调用，不是全程累计（legacy 引擎是逐轮累加的）。
     * 所以两个引擎的 token 数字<b>不能直接对比</b>；要比成本，得看 Gateway 账单或加埋点。
     * 这个差异写在这里，是为了避免以后看到"spring-ai 的 token 忽然变少了"就去改代码。
     */
    private static void fillTokens(AiAnswerVO vo, ChatResponse response) {
        fillTokens(vo, usageOf(response));
    }

    /** 从任意一片 {@link ChatResponse} 里取用量；没有就返回 null（流式下只有最后一片带） */
    private static Usage usageOf(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        return response.getMetadata().getUsage();
    }

    private static void fillTokens(AiAnswerVO vo, Usage usage) {
        int prompt = 0;
        int completion = 0;
        if (usage != null) {
            if (usage.getPromptTokens() != null) {
                prompt = usage.getPromptTokens();
            }
            if (usage.getCompletionTokens() != null) {
                completion = usage.getCompletionTokens();
            }
        }
        vo.setPromptTokens(prompt);
        vo.setCompletionTokens(completion);
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
     * <p>各层职责：
     * <ol>
     *   <li>{@code SpringAiOpenAiHttpClient} —— 真正发 HTTP 的那层（OkHttp），超时设在这里</li>
     *   <li>{@code ClientOptions} —— 认证与地址：apiKey 走它、baseUrl 走它</li>
     *   <li>{@code OpenAiChatModel} —— Spring AI 对 OpenAI 协议的封装，模型名与温度挂在 options 上</li>
     *   <li>{@code ToolCallingAdvisor} —— 工具循环（S4 加的）。模型要工具 → 执行 → 把结果回传 → 再问，
     *       这个"再问"是它在做，不是 ChatModel 在做</li>
     *   <li>{@code ChatClient} —— 面向业务的流畅 API，把系统提示词与 Advisor 固化成默认值</li>
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
                // 工具循环必须靠这个 Advisor 跑起来（2.0 把循环从 ChatModel 挪到了 Advisor 链）。
                // 不注册它，模型返回的 tool_calls 没人执行，最终答案直接是空的 ——
                // 现象很像"模型不会用工具"，实际是少挂了一个拦截器。
                .defaultAdvisors(ToolCallingAdvisor.builder()
                        .toolCallingManager(toolCallingManager())
                        .build())
                .build();
    }

    /**
     * 工具执行管理器：真正去调 {@code @Tool} 方法、并把结果塞回对话的那一层。
     *
     * <p>{@code maxTotalToolCalls} 是费用护栏：模型有时会"查一次不满意再查一次"，
     * 没有上限时能连查十几轮，每轮都是一次付费调用。超限后选择
     * {@code RETURN_ERROR_RESPONSE} 而不是 {@code THROW}：把"工具次数已用完"
     * 作为一条工具结果回给模型，它还能基于已有信息作答；直接抛异常则整个请求失败、
     * 用户看到一个 500。宁可答得不全，也不要页面报错。
     *
     * <p>{@code observationRegistry(NOOP)}：本机没接指标系统，用 NOOP 而不是 null。
     */
    private ToolCallingManager toolCallingManager() {
        return DefaultToolCallingManager.builder()
                .observationRegistry(ObservationRegistry.NOOP)
                .maxTotalToolCalls(props.getTool().getMaxRounds())
                .onLimitExceeded(ToolCallLimitBehavior.RETURN_ERROR_RESPONSE)
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
