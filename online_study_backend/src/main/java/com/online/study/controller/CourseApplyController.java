package com.online.study.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.online.study.annotation.OperationLog;
import com.online.study.common.PageQuery;
import com.online.study.common.PageResult;
import com.online.study.common.Result;
import com.online.study.common.ResultCode;
import com.online.study.entity.CourseApply;
import com.online.study.exception.BizException;
import com.online.study.service.CourseApplyService;
import com.online.study.utils.CurrentUserUtil;
import com.online.study.utils.QueryUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 报名接口
 *
 * <h3>改造点</h3>
 * <ol>
 *   <li><b>新增报名接口 {@code /apply}</b>：带名额原子占位 + 唯一索引兜底，
 *       解决并发超卖与重复报名。原代码是前端直接调 {@code /save} 插一条记录，
 *       既不看名额也不防重复。</li>
 *   <li><b>审核与名额联动</b>：报名被驳回时释放名额，从驳回状态恢复时重新抢名额。</li>
 *   <li><b>撤销报名走 {@code cancelApply}</b>：原来直接 {@code removeById}，
 *       删除后名额不会退回，课程会被"少算"一个人。</li>
 * </ol>
 */
@RestController
@RequestMapping("/course-apply")
public class CourseApplyController {

    @Autowired
    private CourseApplyService service;

    /**
     * 报名记录列表（教师审核页用）。
     *
     * <p>学员调用时强制只返回**自己的**报名 —— 这张表里是全体学员的报名数据，
     * 原来不加过滤，任何学员登录后都能把别人的报名记录整表拉走。
     */
    @GetMapping("/list")
    public List<CourseApply> list() {
        List<CourseApply> applies;
        if ("student".equals(CurrentUserUtil.getRole())) {
            applies = service.list(new QueryWrapper<CourseApply>()
                    .eq("student_id", CurrentUserUtil.getId()));
        } else {
            applies = service.list();
        }
        // 补「驳回审批中」标记：教师提交驳回申请后，报名的 audit_status 仍是待审核，
        // 列表上得让两边都能看出"有一张单子正在等管理员签字"
        service.fillRejectPending(applies);
        return applies;
    }

    /**
     * 条件查询。
     *
     * <p>学员调用时忽略前端传的 {@code studentId}，一律以 JWT 里的身份为准 ——
     * 前端传的 ID 是可以随手改的，不能拿来决定"查谁的报名"。
     */
    @PostMapping("/query")
    public List<CourseApply> query(@RequestBody Map<String, Object> params) {
        if ("student".equals(CurrentUserUtil.getRole())) {
            params.put("studentId", CurrentUserUtil.getId());
        }
        QueryWrapper<CourseApply> wrapper = QueryUtil.buildSafeWrapper(CourseApply.class, params);
        List<CourseApply> applies = service.list(wrapper);
        service.fillRejectPending(applies);
        return applies;
    }

    /**
     * 分页查询。
     * 请求体示例：{"pageNum": 1, "pageSize": 10, "courseName": "Java"}
     * 前两个字段由 PageQuery 解析（含默认值与上限保护），其余作为查询条件经白名单校验。
     */
    @PostMapping("/page")
    public Result<PageResult<CourseApply>> page(@RequestBody Map<String, Object> params) {
        Page<CourseApply> page = PageQuery.of(params);
        QueryWrapper<CourseApply> wrapper = QueryUtil.buildSafeWrapper(CourseApply.class, params);
        return Result.success(PageResult.of(service.page(page, wrapper)));
    }

    /**
     * 学员报名课程（名额已满时返回业务错误）。
     * 请求体示例：{"courseId": 3}
     *
     * <p>学员 ID 由服务端从 JWT 取，不接受前端传值 ——
     * 否则可以替别人报名（占用他人名额 / 制造脏数据）。
     */
    @PostMapping("/apply")
    @PreAuthorize("hasRole('STUDENT')")
    @OperationLog(module = "报名", operation = "学员报名")
    public Result<Void> apply(@RequestBody Map<String, Object> body) {
        Object courseId = body.get("courseId");
        if (courseId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "courseId 不能为空");
        }
        service.apply(Integer.valueOf(courseId.toString()));
        return Result.success();
    }

    /**
     * 审核报名（教师 / 管理员）。
     * 请求体示例：{"applyId": 5, "auditStatus": 1, "auditRemark": "符合报名条件"}
     * auditStatus：1 通过 / 2 驳回（驳回必填原因）/ 0 撤销驳回。
     */
    @PostMapping("/audit")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @OperationLog(module = "报名", operation = "审核报名")
    public Result<Void> audit(@RequestBody Map<String, Object> body) {
        Object applyId = body.get("applyId");
        Object auditStatus = body.get("auditStatus");
        Object remark = body.get("auditRemark");

        if (applyId == null || auditStatus == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "applyId 与 auditStatus 不能为空");
        }
        service.auditApply(
                Integer.valueOf(applyId.toString()),
                Integer.valueOf(auditStatus.toString()),
                remark == null ? null : remark.toString());
        return Result.success();
    }

    /**
     * 兼容旧前端的审核入口。
     *
     * <p>旧版前端点「通过 / 驳回」时是把整条记录交给本接口保存的。
     * 为了不让页面立刻失效，这里识别「审核状态确实发生变化」的请求并转交审核逻辑；
     * 其余情况（例如学员想凭空插一条报名）一律拒绝，不再允许直接写库。
     */
    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<Void> save(@RequestBody CourseApply entity) {
        if (entity.getApplyId() != null && entity.getAuditStatus() != null) {
            CourseApply db = service.getById(entity.getApplyId());
            if (db == null) {
                throw new BizException(ResultCode.DATA_NOT_FOUND, "报名记录不存在");
            }
            if (!entity.getAuditStatus().equals(db.getAuditStatus())) {
                service.auditApply(entity.getApplyId(), entity.getAuditStatus(), entity.getAuditRemark());
                return Result.success();
            }
            // 状态没变，视为重复提交，直接返回成功
            return Result.success();
        }
        throw new BizException("报名请调用 /course-apply/apply，审核请调用 /course-apply/audit");
    }

    /**
     * 撤销报名：学员只能撤自己的，管理员可撤任意一条，撤销后释放名额。
     */
    @DeleteMapping("/{id}")
    @OperationLog(module = "报名", operation = "撤销报名")
    public Result<Boolean> delete(@PathVariable Integer id) {
        service.cancelApply(id);
        return Result.success(true);
    }
}
