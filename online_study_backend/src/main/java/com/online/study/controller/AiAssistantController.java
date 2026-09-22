package com.online.study.controller;

import com.online.study.ai.AiAssistantService;
import com.online.study.ai.AiChatHistoryService;
import com.online.study.ai.KnowledgeIngestService;
import com.online.study.annotation.OperationLog;
import com.online.study.common.Result;
import com.online.study.common.ResultCode;
import com.online.study.config.QuotaProperties;
import com.online.study.exception.BizException;
import com.online.study.utils.CurrentUserUtil;
import com.online.study.utils.DailyQuota;
import com.online.study.vo.AiAnswerVO;
import com.online.study.vo.AiChatMessageVO;
import com.online.study.vo.AiKnowledgeVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 智能助教接口
 *
 * <h3>接口清单</h3>
 * <pre>
 *   GET    /ai/status              知识库状态（多少块、维度、用的什么模型）
 *   POST   /ai/ask                 提问，返回带出处的回答
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
@RestController
@RequestMapping("/ai")
public class AiAssistantController {

    private final AiAssistantService assistantService;
    private final KnowledgeIngestService ingestService;
    private final AiChatHistoryService historyService;

    /** 每人每日提问额度（演示环境防刷，见 QuotaProperties 的说明） */
    private final QuotaProperties quota;
    private final DailyQuota dailyQuota;

    public AiAssistantController(AiAssistantService assistantService,
                                 KnowledgeIngestService ingestService,
                                 AiChatHistoryService historyService,
                                 QuotaProperties quota,
                                 DailyQuota dailyQuota) {
        this.assistantService = assistantService;
        this.ingestService = ingestService;
        this.historyService = historyService;
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

        return Result.success(assistantService.ask(question));
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
