package com.online.study.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.online.study.annotation.OperationLog;
import com.online.study.common.PageQuery;
import com.online.study.common.PageResult;
import com.online.study.common.Result;
import com.online.study.common.ResultCode;
import com.online.study.entity.Course;
import com.online.study.exception.BizException;
import com.online.study.service.CourseService;
import com.online.study.utils.CurrentUserUtil;
import com.online.study.utils.QueryUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 课程接口
 *
 * <h3>本类涉及的三处越权防护（改造点）</h3>
 * <ol>
 *   <li><b>审核状态不能由前端直接写</b>：原 {@code /course/save} 直接
 *       {@code saveOrUpdate} 前端传来的实体，任何登录用户塞一个
 *       {@code "auditStatus": 1} 就能让自己的课程绕过审核。现在保存与审核分离，
 *       并且会拿新值与库中当前值比对，只有管理员才能改动审核状态。</li>
 *   <li><b>发布教师 ID 取自 JWT</b>：原来完全信任前端传值，可以伪造成别人发布的课程。</li>
 *   <li><b>删除限本人 / 管理员</b>：原来任何登录用户都能删除任意课程。</li>
 * </ol>
 *
 * <p>权限注解 {@code @PreAuthorize} 需要 {@code @EnableMethodSecurity} 生效，
 * 已加在 {@link com.online.study.config.SecurityConfig} 上。
 * 权限不足时抛出的 {@code AccessDeniedException} 由
 * {@link com.online.study.exception.GlobalExceptionHandler} 转成统一的 403 响应。
 */
@RestController
@RequestMapping("/course")
public class CourseController {

    /** 课程状态：待审核 */
    private static final int AUDIT_PENDING = 0;
    /** 课程状态：审核通过 */
    private static final int AUDIT_PASSED = 1;

    @Autowired
    private CourseService service;

    @GetMapping("/list")
    public List<Course> list() {
        return service.list();
    }

    /** 条件查询：条件经 QueryUtil 白名单过滤，杜绝列名拼接注入 */
    @PostMapping("/query")
    public List<Course> query(@RequestBody Map<String, Object> params) {
        QueryWrapper<Course> wrapper = QueryUtil.buildSafeWrapper(Course.class, params);
        return service.list(wrapper);
    }

    /**
     * 分页查询。
     * 请求体示例：{"pageNum": 1, "pageSize": 10, "courseName": "Java"}
     * 前两个字段由 PageQuery 解析（含默认值与上限保护），其余作为查询条件经白名单校验。
     */
    @PostMapping("/page")
    public Result<PageResult<Course>> page(@RequestBody Map<String, Object> params) {
        Page<Course> page = PageQuery.of(params);
        QueryWrapper<Course> wrapper = QueryUtil.buildSafeWrapper(Course.class, params);
        return Result.success(PageResult.of(service.page(page, wrapper)));
    }

    /**
     * 保存课程（新增 / 编辑）。
     *
     * <p>处理逻辑分两条路：
     * <ul>
     *   <li><b>审核请求</b>：前端传的 {@code auditStatus} 与库中当前值不同 →
     *       说明是在点「通过 / 驳回」，要求管理员身份，转交 {@code auditCourse}；
     *       这样旧前端的审核按钮不需要改动就能继续使用，但权限与留痕都由服务端把关。</li>
     *   <li><b>普通保存</b>：教师提交的课程一律重置为「待审核」，
     *       避免改完内容后还挂着上一次的「已通过」。</li>
     * </ul>
     */
    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @OperationLog(module = "课程", operation = "保存课程")
    public Result<Void> save(@RequestBody Course entity) {
        Course db = null;
        if (entity.getCourseId() != null) {
            db = service.getById(entity.getCourseId());
            if (db == null) {
                throw new BizException(ResultCode.DATA_NOT_FOUND, "课程不存在");
            }

            // ① 审核状态的改动：只允许管理员，且必须走审核逻辑
            //    注意这一步要在「课程名称非空」校验之前 ——
            //    审核请求只关心 courseId + auditStatus，不该被表单校验拦住
            if (entity.getAuditStatus() != null && !entity.getAuditStatus().equals(db.getAuditStatus())) {
                if (!CurrentUserUtil.isAdmin()) {
                    throw new BizException(ResultCode.FORBIDDEN, "只有管理员可以审核课程");
                }
                service.auditCourse(entity.getCourseId(), entity.getAuditStatus(), entity.getAuditRemark());
                return Result.success();
            }

            // ② 编辑权限：教师只能改自己发布的课程
            if (!CurrentUserUtil.isAdmin()
                    && !Objects.equals(db.getPublishTeacherId(), CurrentUserUtil.getId())) {
                throw new BizException(ResultCode.FORBIDDEN, "只能编辑自己发布的课程");
            }
        }

        // 普通保存（新增 / 编辑）才需要完整的表单字段
        if (!StringUtils.hasText(entity.getCourseName()) || !StringUtils.hasText(entity.getTrainCycle())) {
            throw new BizException(ResultCode.PARAM_ERROR, "课程名称和培训周期不能为空");
        }

        if (CurrentUserUtil.isAdmin()) {
            // 管理员创建的课程直接视为已通过，并留下审核痕迹
            entity.setAuditStatus(AUDIT_PASSED);
            entity.setAuditAdminId(CurrentUserUtil.getId());
            entity.setAuditTime(new Date());
            if (db == null && entity.getPublishTeacherId() == null) {
                // publish_teacher_id 有外键指向 teacher 表，管理员 ID 不在其中，
                // 所以管理员建课时必须由前端指定负责教师，不能拿当前登录 ID 顶上
                throw new BizException(ResultCode.PARAM_ERROR, "请指定发布课程的教师");
            }
        } else {
            // 教师新增 / 修改后需要重新审核
            entity.setAuditStatus(AUDIT_PENDING);
            if (entity.getCourseId() == null) {
                entity.setPublishTeacherId(CurrentUserUtil.getId());
            }
        }

        if (entity.getMaxStudents() == null || entity.getMaxStudents() < 0) {
            entity.setMaxStudents(0);   // 0 表示不限名额
        }
        // 已占名额由报名逻辑维护；新增时置 0，编辑时不更新（保持库中原值）
        entity.setCurrentStudents(entity.getCourseId() == null ? 0 : null);

        service.saveOrUpdate(entity);
        return Result.success();
    }

    /**
     * 审核课程（正式接口，仅管理员）。
     * 请求体示例：{"courseId": 3, "auditStatus": 1, "auditRemark": "资料齐全，通过"}
     * auditStatus：1 通过 / 2 驳回（驳回时 auditRemark 必填）。
     */
    @PostMapping("/audit")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(module = "课程", operation = "审核课程")
    public Result<Void> audit(@RequestBody Map<String, Object> body) {
        Object courseId = body.get("courseId");
        Object auditStatus = body.get("auditStatus");
        Object remark = body.get("auditRemark");

        if (courseId == null || auditStatus == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "courseId 与 auditStatus 不能为空");
        }
        service.auditCourse(
                Integer.valueOf(courseId.toString()),
                Integer.valueOf(auditStatus.toString()),
                remark == null ? null : remark.toString());
        return Result.success();
    }

    /**
     * 删除课程（含级联）。
     *
     * <h3>为什么教师不能直接删</h3>
     * 这是一个「级联 + 不可恢复」的动作：课程下的资源、报名、成绩、作业与全部
     * 提交记录会一起消失，学员的作业和分数也随之丢失。所以：
     * <ul>
     *   <li><b>管理员</b>：直接删除（本人就是终审，没有审批自己的道理）；</li>
     *   <li><b>教师</b>：只能通过 {@code POST /approval-request/submit}
     *       提交删除申请并写明理由，由管理员在「审批中心」同意后才真正执行。</li>
     * </ul>
     * 原来教师点一下按钮就删库，管理员事后只能翻日志 —— 现在留痕在申请与审批意见里。
     *
     * <p>级联删除的逻辑在 {@code CourseServiceImpl#removeCourseCascade}，
     * 那里加了 {@code @Transactional} 保证 5 张表的删除是一个原子操作。
     * Controller 只做权限判断与转发。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @OperationLog(module = "课程", operation = "删除课程")
    public Result<Boolean> delete(@PathVariable Integer id) {
        if (!CurrentUserUtil.isAdmin()) {
            throw new BizException(ResultCode.FORBIDDEN,
                    "删除课程需要管理员审批，请提交删除申请并填写理由");
        }
        Course db = service.getById(id);
        if (db == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "课程不存在");
        }
        return Result.success(service.removeCourseCascade(id));
    }
}
