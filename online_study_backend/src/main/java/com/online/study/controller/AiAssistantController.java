package com.online.study.controller;

import com.online.study.ai.AiAssistantService;
import com.online.study.ai.AiChatHistoryService;
import com.online.study.ai.KnowledgeIngestService;
import com.online.study.ai.engine.AiStreamEngine;
import com.online.study.annotation.OperationLog;
import com.online.study.common.Result;
import com.online.study.common.ResultCode;
import com.online.study.config.AiStreamConfig;
import com.online.study.config.QuotaProperties;
import com.online.study.exception.BizException;
import com.online.study.utils.CurrentUserUtil;
import com.online.study.utils.DailyQuota;
import com.online.study.vo.AiAnswerVO;
import com.online.study.vo.AiChatMessageVO;
import com.online.study.vo.AiKnowledgeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 智能助教接口
 *
 * <h3>接口清单</h3>
 * <pre>
 *   GET    /ai/status              知识库状态（多少块、维度、用的什么模型）
 *   POST   /ai/ask                 提问，返回带出处的回答
 *   POST   /ai/ask/stream          提问（SSE 流式），边生成边推送
 *   GET    /ai/history             我的历史对话（最近 50 条，按时间正序）
 *   DELETE /ai/history             清空我的历史对话
 *   POST   /ai/knowledge/rebuild   重建知识库（管理员 / 教师）
 *   POST   /ai/knowledge/doc       补充一份文字资料（管理员 / 教师）
 *   POST   /ai/knowledge/doc/clear 清空手工补充的资料（管理员 / 教师）
 * </pre>
 *
 * <h3>权限设计</h3>
 * 「提问」所有登录用户都能用；「灌库」只给管理员和教师 —— 因为灌库会真实消耗
 * 向量接口的 token，且会把内容写进所有学生都能检索到的知识库，属于内容管理动作。
 * 这里用 {@code @PreAuthorize} 做方法级控制（依赖 SecurityConfig 上的
 * {@code @EnableMethodSecurity}），比在 filterChain 里堆路径规则更精确。
 *
 * <p>「历史对话」的两个接口不加 {@code @PreAuthorize}：它们是"我的数据"，
 * 身份完全由 JWT 决定（见 {@link AiChatHistoryService}），任何登录用户都只能
 * 看到、删掉自己的记录，越权在数据层就被挡住了，不需要额外限角色。
 *
 * <h3>额度</h3>
 * 「提问」虽然不限角色，但按「账号 + 自然日」限次数（{@code app.quota.ai-daily}）——
 * 它是唯一会按 token 计费的接口，演示环境必须卡住上限。详见 {@link #ask}。
 */
@Slf4j
@RestController
@RequestMapping("/ai")
public class AiAssistantController {

    /**
     * SSE 连接的最长存活时间。
     *
     * <p>必须显式设：不设就落回容器默认（Tomcat 30 秒），而一次带工具循环的问答
     * 走 5 轮、每轮都是一次完整的模型调用，30 秒根本不够 —— 现象是"答案吐到一半连接被掐"，
     * 看起来像模型的锅。180 秒 = 单次调用超时 60 秒 × 最多 3 次真实生成 + 余量。
     */
    private static final long STREAM_TIMEOUT_MS = 180_000L;

    /** SSE 里 JSON 用；空内容不序列化。与 SecurityConfig 里的用法保持一致 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AiAssistantService assistantService;
    private final KnowledgeIngestService ingestService;
    private final AiChatHistoryService historyService;

    /** 流式问答的执行线程（带 SecurityContext 传播，见构造函数注释） */
    private final Executor streamExecutor;

    /** 每人每日提问额度（演示环境防刷，见 QuotaProperties 的说明） */
    private final QuotaProperties quota;
    private final DailyQuota dailyQuota;

    public AiAssistantController(AiAssistantService assistantService,
                                 KnowledgeIngestService ingestService,
                                 AiChatHistoryService historyService,
                                 @Qualifier(AiStreamConfig.EXECUTOR_BEAN) ThreadPoolTaskExecutor streamPool,
                                 QuotaProperties quota,
                                 DailyQuota dailyQuota) {
        this.assistantService = assistantService;
        this.ingestService = ingestService;
        this.historyService = historyService;
        // 关键：包一层 DelegatingSecurityContextExecutor。
        // 引擎里的工具要靠 CurrentUserUtil 才知道"我是谁"，而它读的是
        // SecurityContextHolder（ThreadLocal）—— 换个线程就读不到了。
        // 不包这一层，流式问答里所有工具都会拿到 null 身份：
        // 轻则查不到自己的作业，重则（教师工具）按 null 去查别人。
        // 这一层在提交任务的线程上捕获上下文、在执行线程上还原，业务代码零改动。
        this.streamExecutor = new DelegatingSecurityContextExecutor(streamPool);
        this.quota = quota;
        this.dailyQuota = dailyQuota;
    }

    /** 知识库状态：前端用它渲染页面顶部的资料状态条 */
    @GetMapping("/status")
    public Result<AiKnowledgeVO> status() {
        return Result.success(assistantService.status());
    }

    /**
     * 提问
     * <p>请求体：{@code {"question": "作业什么时候交？"}}
     *
     * <p><b>为什么这里要卡一道额度</b>：一次提问会真实调用百炼的向量接口（检索）
     * 和大模型接口（生成），是按 token 计费的。演示环境注册开放，任何陌生人
     * 拿到账号后都能调这个接口 —— 不加限制就是一个可以无限刷的付费接口。
     * 所以按「账号 + 自然日」计数，到量直接拒绝，在最前面就挡住，
     * 连向量化都不会触发（也就不产生任何费用）。
     *
     * <p>注意限额放在 Controller 而不是 Service：Service 里的
     * {@link AiAssistantService#ask} 是纯业务算法流程，不该关心"谁能用几次"
     * 这种访问策略；把访问策略留在接口层，两件事的边界才清楚。
     */
    @PostMapping("/ask")
    public Result<AiAnswerVO> ask(@RequestBody Map<String, String> body) {
        String question = body == null ? null : body.get("question");
        if (question == null || question.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "问题不能为空");
        }

        checkQuota();

        return Result.success(assistantService.ask(question));
    }

    /**
     * 提问（SSE 流式）—— S7 新增，<b>老接口 {@code /ai/ask} 一个字节没动</b>。
     *
     * <p>请求体与 {@code /ai/ask} 完全一样：{@code {"question": "作业什么时候交？"}}，
     * 但响应是 {@code text/event-stream}，事件序列固定为：
     * <pre>
     *   event: meta    {"model":"...","chunkCount":106,"hitCount":4,"sources":[...]}
     *   event: delta   {"text":"这门课的"}          ← 重复 N 次（N 取决于模型吐字节奏）
     *   event: delta   {"text":"作业要求是…"}
     *   event: done    {……完整 AiAnswerVO……}        ← 出处 / 工具轨迹 / token 都在这里
     *   data:  [DONE]                                ← 结束标记
     * </pre>
     *
     * <h3>为什么 delta 也包成 JSON 而不是裸文本</h3>
     * SSE 的 {@code data:} 是<b>按行</b>解析的：模型输出里的一个换行就会把一条事件劈成两条，
     * 前端拼出来的答案会多出一堆空白行甚至截断。JSON 序列化会把 {@code \n} 转义成两个字符，
     * 一条事件永远是一行 —— 这是"格式选错，线上必踩"的典型。
     *
     * <h3>为什么还要单独发一个 meta</h3>
     * 出处（sources）在第一个字吐出来之前就确定了（检索在生成之前）。
     * 先推 meta，前端就能在"正在输入…"的同时把引用列表渲染出来，
     * 而不是等全文结束才一起蹦出来 —— 这是流式体验的一半。
     *
     * <h3>额度与老接口共用同一份计数</h3>
     * 两个入口都算一次提问，否则"流式接口"就成了绕过每日限额的后门。
     */
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@RequestBody Map<String, String> body) {
        String question = body == null ? null : body.get("question");
        if (question == null || question.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "问题不能为空");
        }

        // 额度必须在请求线程上扣：这一步要用 CurrentUserUtil，异步线程虽然也带上了上下文，
        // 但"还没开始就拒绝"的语义应该在返回 emitter 之前完成 —— 否则前端拿到 200 才开始等，
        // 等到的是 error 事件，比直接收到 429 更难处理。
        checkQuota();

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        streamExecutor.execute(() -> pushStream(question, emitter));
        return emitter;
    }

    /**
     * 真正跑流式问答的线程体。
     *
     * <p>这一层只做两件事：<b>把引擎回调翻译成 SSE 事件</b>、<b>保证 emitter 只被关一次</b>。
     * 业务（检索、提示词、工具、落库）全在引擎里 —— Controller 不该知道"AI 怎么思考"。
     */
    private void pushStream(String question, SseEmitter emitter) {
        AtomicBoolean closed = new AtomicBoolean(false);

        AiStreamEngine.StreamSink sink = new AiStreamEngine.StreamSink() {

            @Override
            public void onMeta(AiAnswerVO context) {
                send(emitter, closed, "meta", context);
            }

            @Override
            public void onDelta(String text) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("text", text);
                send(emitter, closed, "delta", payload);
            }

            @Override
            public void onComplete(AiAnswerVO full) {
                if (closed.get()) {
                    return;
                }
                send(emitter, closed, "done", full);
                // [DONE] 是给前端一个"可以停止读流"的明确信号。
                // 只靠 done 也能判断，但客户端要看帧来确定流结束，多一个哨兵更省事。
                send(emitter, closed, null, "[DONE]");
                finish(emitter, closed);
            }

            @Override
            public void onError(Throwable error) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("message", error == null ? "未知错误"
                        : (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
                send(emitter, closed, "error", payload);
                finish(emitter, closed);
            }
        };

        try {
            assistantService.askStream(question, sink);
        } catch (Exception e) {
            // 引擎以外的失败（入参校验、Key 没配、线程池拒绝）也走同一条错误通道，
            // 前端不需要区分"哪一层抛的"，只需要知道"这次没成、原因是什么"。
            log.error("流式问答失败", e);
            sink.onError(e);
        }
    }

    /**
     * 发一条 SSE 事件。
     *
     * @param event 事件名；传 null 表示不指定（默认 message 事件，用给 {@code [DONE]}）
     * @param data  对象会被序列化成 JSON；字符串原样发送（{@code [DONE]} 需要原样）
     */
    private void send(SseEmitter emitter, AtomicBoolean closed, String event, Object data) {
        if (closed.get()) {
            return;
        }
        try {
            String text;
            if (data instanceof String str) {
                text = str;
            } else {
                text = OBJECT_MAPPER.writeValueAsString(data);
            }
            SseEmitter.SseEventBuilder builder = SseEmitter.event();
            if (event != null) {
                builder.name(event);
            }
            // 显式 TEXT_PLAIN：让 StringHttpMessageConverter 原样写出，
            // 否则会被当成 JSON 再包一层引号（{"text":"…"} 变成 "\"…\""）。
            emitter.send(builder.data(text, MediaType.TEXT_PLAIN));
        } catch (Exception e) {
            // 客户端断开（关页面 / 切路由）时这里必然抛 IOException，属于正常现象，不当错误处理
            log.debug("SSE 推送中断：{}", e.getMessage());
            closed.set(true);
        }
    }

    /** 收尾：先 complete 再置标记，避免 onComplete 与异常分支各关一次 */
    private void finish(SseEmitter emitter, AtomicBoolean closed) {
        if (closed.compareAndSet(false, true)) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("SSE 关闭异常：{}", e.getMessage());
            }
        }
    }

    /**
     * 扣一次提问额度。两个提问接口共用，规则见 {@link #ask} 的注释。
     */
    private void checkQuota() {
        String role = CurrentUserUtil.getRole();
        String account = CurrentUserUtil.getAccount();
        Integer userId = CurrentUserUtil.getId();
        int limit = quota.askLimit(account);

        // 计数键带上角色：学员 12 号和教师 12 号是两个人，不能共用一个额度
        String bucket = "ai:" + role + ":" + userId;
        if (!dailyQuota.tryAcquire(bucket, limit)) {
            throw new BizException(ResultCode.PARAM_ERROR,
                    "今天的提问次数已用完（每日 " + limit + " 次），明天再试；"
                            + "也可以直接到课程详情或作业页面查看资料。");
        }
    }

    /**
     * 我的历史对话：最近 50 条，按时间正序（早的在前）。
     * <p>前端进入页面时调用一次，把之前的问答回填到对话框里 —— 刷新页面不再丢记录。
     */
    @GetMapping("/history")
    public Result<List<AiChatMessageVO>> history() {
        return Result.success(historyService.recent());
    }

    /**
     * 清空我的历史对话。
     * <p>只删当前登录用户自己的记录，别人的不受影响。
     */
    @DeleteMapping("/history")
    public Result<Integer> clearHistory() {
        int removed = historyService.clear();
        return Result.success("已清空 " + removed + " 条对话记录", removed);
    }

    /**
     * 重建知识库：把数据库里的课程 / 作业 / 资源重新收集、切块、向量化。
     * <p>什么时候需要点：课程或作业有改动、补充了新资料、服务刚重启。
     */
    @PostMapping("/knowledge/rebuild")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    @OperationLog(module = "智能助教", operation = "重建知识库", saveParams = false)
    public Result<AiKnowledgeVO> rebuild() {
        return Result.success("知识库重建成功", assistantService.rebuild());
    }

    /**
     * 追加一份文字资料（课件正文、讲义、常见问题等）。
     * <p>请求体：{@code {"source": "Java 基础 - 第 2 章讲义", "text": "……"}}
     * <p>同名资料会被覆盖，重复提交不会产生两份。
     */
    @PostMapping("/knowledge/doc")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    @OperationLog(module = "智能助教", operation = "补充知识资料", saveParams = false)
    public Result<AiKnowledgeVO> addDoc(@RequestBody Map<String, String> body) {
        String source = body == null ? null : body.get("source");
        String text = body == null ? null : body.get("text");
        int count = ingestService.addDoc(source, text);
        return Result.success("资料已入库，当前知识库共 " + count + " 个知识块", assistantService.status());
    }

    /** 清空手工补充的资料（数据库资料保留，会一并重建） */
    @PostMapping("/knowledge/doc/clear")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    @OperationLog(module = "智能助教", operation = "清空补充资料", saveParams = false)
    public Result<AiKnowledgeVO> clearDocs() {
        int count = ingestService.clearExtraDocs();
        return Result.success("已清空补充资料，当前知识库共 " + count + " 个知识块", assistantService.status());
    }
}
