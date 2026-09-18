package com.online.study.controller;

import com.online.study.annotation.OperationLog;
import com.online.study.common.Result;
import com.online.study.common.ResultCode;
import com.online.study.exception.BizException;
import com.online.study.service.ApprovalRequestService;
import com.online.study.vo.ApprovalRequestVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 敏感操作审批接口
 *
 * <h3>为什么需要这道审批</h3>
 * 「删除课程」会级联清掉资源、报名、成绩、作业与全部提交记录，不可恢复；
 * 「驳回学员报名」直接决定学员能不能上这门课。
 * 这两件事原来教师点一下按钮就生效，管理员只能事后翻日志 ——
 * 现在改为教师提交申请（必填理由）→ 管理员在「审批中心」同意后才真正执行。
 *
 * <h3>权限边界</h3>
 * <ul>
 *   <li>{@code /submit}：仅教师。学员没有课程可删，管理员则是自己就能删（见 CourseController），
 *       不需要自己向自己申请。</li>
 *   <li>{@code /list}、{@code /audit}、{@code /pending-count}：仅管理员。</li>
 *   <li>教师端能看到的「我的申请」入口复用 {@code /list}，由服务端按当前登录人过滤。</li>
 * </ul>
 */
@RestController
@RequestMapping("/approval-request")
public class ApprovalRequestController {

    @Autowired
    private ApprovalRequestService service;

    /**
     * 教师提交审批申请。
     * 请求体示例：
     * <pre>
     * {"requestType": "COURSE_DELETE", "targetId": 51, "reason": "课程内容已并入另一门课"}
     * {"requestType": "APPLY_REJECT",  "targetId": 233, "reason": "学员未按要求提交前置作业"}
     * </pre>
     */
    @PostMapping("/submit")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @OperationLog(module = "审批", operation = "提交审批申请")
    public Result<Void> submit(@RequestBody Map<String, Object> body) {
        Object requestType = body.get("requestType");
        Object targetId = body.get("targetId");
        Object reason = body.get("reason");
        if (targetId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "targetId 不能为空");
        }
        service.submit(
                requestType == null ? null : requestType.toString(),
                Integer.valueOf(targetId.toString()),
                reason == null ? null : reason.toString());
        return Result.success();
    }

    /**
     * 审批列表。
     *
     * <p>管理员看到全部；教师只看到自己提交的申请（用于确认进度：待审批 / 已通过 / 已驳回）。
     *
     * @param status 可选，0 待审批 / 1 已通过 / 2 已驳回
     */
    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<List<ApprovalRequestVO>> list(@RequestParam(name = "status", required = false) Integer status) {
        // 教师看自己的申请、管理员看全部，判断放在 Service 里（其它调用方也能享受同样保护）
        return Result.success(service.listDetail(status));
    }

    /** 待审批条数（管理员端角标） */
    @GetMapping("/pending-count")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Integer> pendingCount() {
        return Result.success(service.pendingCount());
    }

    /**
     * 审批（仅管理员）。
     * 请求体示例：{"requestId": 3, "approved": true, "auditComment": "确认课程已结课，同意删除"}
     */
    @PostMapping("/audit")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(module = "审批", operation = "审批敏感操作")
    public Result<Void> audit(@RequestBody Map<String, Object> body) {
        Object requestId = body.get("requestId");
        Object approved = body.get("approved");
        Object comment = body.get("auditComment");
        if (requestId == null || approved == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "requestId 与 approved 不能为空");
        }
        service.audit(
                Integer.valueOf(requestId.toString()),
                Boolean.parseBoolean(approved.toString()),
                comment == null ? null : comment.toString());
        return Result.success();
    }
}
