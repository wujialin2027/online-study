package com.online.study.controller;

import com.online.study.ai.AiAssistantService;
import com.online.study.ai.AiChatHistoryService;
import com.online.study.ai.KnowledgeIngestService;
import com.online.study.annotation.OperationLog;
import com.online.study.common.Result;
import com.online.study.common.ResultCode;
import com.online.study.exception.BizException;
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
 */
@RestController
@RequestMapping("/ai")
public class AiAssistantController {

    private final AiAssistantService assistantService;
    private final KnowledgeIngestService ingestService;
    private final AiChatHistoryService historyService;

    public AiAssistantController(AiAssistantService assistantService,
                                 KnowledgeIngestService ingestService,
                                 AiChatHistoryService historyService) {
        this.assistantService = assistantService;
        this.ingestService = ingestService;
        this.historyService = historyService;
    }

    /** 知识库状态：前端用它渲染页面顶部的资料状态条 */
    @GetMapping("/status")
    public Result<AiKnowledgeVO> status() {
        return Result.success(assistantService.status());
    }

    /**
     * 提问
     * <p>请求体：{@code {"question": "作业什么时候交？"}}
     */
    @PostMapping("/ask")
    public Result<AiAnswerVO> ask(@RequestBody Map<String, String> body) {
        String question = body == null ? null : body.get("question");
        if (question == null || question.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "问题不能为空");
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
