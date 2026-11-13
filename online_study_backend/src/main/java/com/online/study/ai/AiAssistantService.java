package com.online.study.ai;

import com.online.study.ai.engine.AiEngine;
import com.online.study.ai.engine.LegacyAiEngine;
import com.online.study.common.ResultCode;
import com.online.study.config.AiProperties;
import com.online.study.exception.BizException;
import com.online.study.vo.AiAnswerVO;
import com.online.study.vo.AiKnowledgeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 智能助教门面 —— 对外只暴露这一层，内部按配置路由到具体引擎。
 *
 * <h3>这一层存在的意义</h3>
 * P2 改造前，这个类里装着全部问答逻辑（检索 + 提示词 + 工具循环，约 260 行）。
 * 现在拆成三块，各自职责清楚：
 * <pre>
 *   AiAssistantController   ← 接口层：权限、每日额度、参数解码
 *         ↓
 *   AiAssistantService      ← 门面（本类）：入参校验 + 选引擎 + 知识库状态
 *         ↓
 *   AiEngine                ← 引擎接口
 *      ├── LegacyAiEngine   ← 手写协议层（2026-09 上线版，保留作对照与降级）
 *      └── SpringAiEngine   ← Spring AI 2.0 版（S3 起逐步接入）
 * </pre>
 *
 * <h3>为什么用「注入全部实现 + 按配置挑一个」而不是 @ConditionalOnProperty</h3>
 * 两种做法都能实现切换，但这里选前者，因为：
 * <ul>
 *   <li><b>能看到全部候选</b>：启动日志能打出"发现 2 个引擎，配置选的是 spring-ai"，
 *       配置写错时一眼看出是拼写问题还是类没被扫描到</li>
 *   <li><b>降级更自然</b>：{@code @ConditionalOnProperty} 选不中时是"这个 Bean 不存在"，
 *       启动直接失败；这里选不中只是打个 warn 然后回退 legacy，接口照常能用</li>
 *   <li><b>以后加引擎不用改注解</b>：新增一个 {@code @Component} 实现即可，
 *       连 {@code ai.engine} 的取值列表都不用维护</li>
 * </ul>
 *
 * <h3>哪些事不归引擎管</h3>
 * 灌库（{@link #rebuild}）和状态查询（{@link #status}）留在本类 ——
 * 它们操作的是向量库，两套引擎共用同一份向量库，跟用哪个大脑无关。
 */
@Slf4j
@Service
public class AiAssistantService {

    /** 入参上限。超过这个长度多半不是真提问（粘贴整篇文档），只会白烧 token */
    private static final int MAX_QUESTION_CHARS = 500;

    private final List<AiEngine> engines;
    /** 启动时按 {@code ai.engine} 选定，运行期不变 —— 切引擎要重启，这是刻意的（见类注释） */
    private final AiEngine engine;

    private final InMemoryVectorStore vectorStore;
    private final KnowledgeIngestService ingestService;
    private final AiProperties props;

    public AiAssistantService(List<AiEngine> engines,
                              InMemoryVectorStore vectorStore,
                              KnowledgeIngestService ingestService,
                              AiProperties props) {
        this.engines = engines;
        this.vectorStore = vectorStore;
        this.ingestService = ingestService;
        this.props = props;
        this.engine = pick(engines, props.getEngine());
    }

    /**
     * 按配置挑引擎，挑不到就回退 legacy。
     *
     * <p><b>为什么不抛异常</b>：配置写错（比如把 {@code spring-ai} 打成 {@code springai}）
     * 属于运维失误，不该让整个平台起不来 —— 学员连课程都看不了，代价远大于"AI 用了旧引擎"。
     * 这里降级 + 打 warn，状态接口会把实际生效的引擎名显示出来，问题可见但不扩散。
     */
    private static AiEngine pick(List<AiEngine> engines, String wanted) {
        String target = StringUtils.hasText(wanted) ? wanted.trim() : LegacyAiEngine.TYPE;

        for (AiEngine candidate : engines) {
            if (candidate.type().equalsIgnoreCase(target)) {
                log.info("AI 引擎已启用：{}（候选 {} 个：{}）",
                        candidate.type(), engines.size(), types(engines));
                return candidate;
            }
        }

        for (AiEngine candidate : engines) {
            if (LegacyAiEngine.TYPE.equals(candidate.type())) {
                log.warn("配置 ai.engine={} 找不到对应实现，已回退 legacy（候选：{}）。"
                        + "请检查拼写，或确认对应引擎是否已注册为 Spring Bean。", target, types(engines));
                return candidate;
            }
        }

        // 连 legacy 都没有，说明代码被改坏了，这时必须让启动失败 —— 早点炸比线上 500 好
        throw new IllegalStateException("没有任何可用的 AI 引擎实现，请检查 ai.engine 包是否被组件扫描到");
    }

    private static String types(List<AiEngine> engines) {
        return engines.stream().map(AiEngine::type).sorted().reduce((a, b) -> a + ", " + b).orElse("无");
    }

    /**
     * 提问并拿到带出处的回答。
     *
     * <p>本方法只做两件事：<b>校验入参</b>、<b>交给引擎</b>。
     * 检索怎么做、提示词怎么拼、工具循环几轮，全是引擎内部的事。
     *
     * @param question 学生的原话，不需要提炼关键词
     */
    public AiAnswerVO ask(String question) {
        if (!StringUtils.hasText(question)) {
            throw new BizException(ResultCode.PARAM_ERROR, "问题不能为空");
        }
        String q = question.trim();
        if (q.length() > MAX_QUESTION_CHARS) {
            throw new BizException(ResultCode.PARAM_ERROR,
                    "问题过长，请控制在 " + MAX_QUESTION_CHARS + " 字以内");
        }
        if (!engine.ready()) {
            throw new BizException(ResultCode.SYSTEM_ERROR, engine.unavailableReason());
        }
        return engine.ask(q);
    }

    /**
     * 知识库状态，供前端页面顶部展示与排障。
     *
     * <p>额外回显 {@code engine} / {@code engineCandidates}：改造期间要能一眼看出
     * "这次跑出来的分数是哪套引擎给的"，否则新旧对比的结论没法复核。
     */
    public AiKnowledgeVO status() {
        AiKnowledgeVO vo = new AiKnowledgeVO();
        vo.setConfigured(engine.ready());
        vo.setChunkCount(vectorStore.size());
        vo.setDimension(vectorStore.dimension());
        vo.setLoadedAt(vectorStore.loadedAt() == 0L ? null : vectorStore.loadedAtText());
        vo.setExtraDocCount(ingestService.extraDocCount());
        vo.setExtraDocTitles(ingestService.extraDocTitles());
        vo.setChatModel(props.getChatModel());
        vo.setEmbeddingModel(props.getEmbeddingModel());
        vo.setEngine(engine.type());
        vo.setEngineCandidates(types(engines));
        return vo;
    }

    /** 重建知识库（先收集资料，再整体灌库），返回最新状态 */
    public AiKnowledgeVO rebuild() {
        int count = ingestService.rebuildAll();
        log.info("知识库重建完成，共 {} 块", count);
        return status();
    }

    /** 当前生效的引擎标识，供日志与内部排查使用 */
    public String currentEngine() {
        return engine.type();
    }
}
