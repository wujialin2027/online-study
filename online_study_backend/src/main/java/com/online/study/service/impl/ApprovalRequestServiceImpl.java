package com.online.study.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.online.study.common.ResultCode;
import com.online.study.entity.ApprovalRequest;
import com.online.study.entity.Course;
import com.online.study.entity.CourseApply;
import com.online.study.exception.BizException;
import com.online.study.mapper.ApprovalRequestMapper;
import com.online.study.service.ApprovalRequestService;
import com.online.study.service.CourseApplyService;
import com.online.study.service.CourseService;
import com.online.study.utils.CurrentUserUtil;
import com.online.study.utils.UserNameResolver;
import com.online.study.vo.ApprovalRequestVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 敏感操作审批实现
 *
 * <h3>状态机</h3>
 * <pre>
 *   0 待审批 ──管理员同意──→ 1 已通过（动作已执行）
 *          └──管理员驳回──→ 2 已驳回（动作不执行）
 *          └──目标已消失──→ 2 已驳回（自动失效，意见里写明原因）
 * </pre>
 *
 * <h3>执行时机为什么放在"审批通过"而不是"提交申请"</h3>
 * 如果提交时就先删了、再等管理员补一个签字，那这道审批就是摆设。
 * 所以教师提交的只是<b>意图</b>（类型 + 目标 + 理由），
 * 真正调用 {@code removeCourseCascade} / {@code rejectOnApproval}
 * 的地方只有这里 —— 且整段包在一个事务里：动作抛异常，申请状态不会变成"已通过"，
 * 不会出现「标记已通过但课程还在」这种对不上的记录。
 */
@Service
public class ApprovalRequestServiceImpl extends ServiceImpl<ApprovalRequestMapper, ApprovalRequest>
        implements ApprovalRequestService {

    @Autowired
    private CourseService courseService;

    @Autowired
    private CourseApplyService courseApplyService;

    @Autowired
    private UserNameResolver userNameResolver;

    // ---------------------------------------------------------------- 提交申请

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(String requestType, Integer targetId, String reason) {
        if (!StringUtils.hasText(requestType) || targetId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "申请类型与申请对象不能为空");
        }
        if (!StringUtils.hasText(reason)) {
            throw new BizException(ResultCode.PARAM_ERROR, "请填写申请理由，管理员需要据此审批");
        }
        String trimmedReason = reason.trim();
        if (trimmedReason.length() > 500) {
            throw new BizException(ResultCode.PARAM_ERROR, "申请理由不能超过 500 字");
        }

        Integer teacherId = CurrentUserUtil.getId();
        if (teacherId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }

        if (ApprovalRequest.TYPE_COURSE_DELETE.equals(requestType)) {
            Course course = courseService.getById(targetId);
            if (course == null) {
                throw new BizException(ResultCode.DATA_NOT_FOUND, "课程不存在");
            }
            if (!Objects.equals(course.getPublishTeacherId(), teacherId)) {
                throw new BizException(ResultCode.FORBIDDEN, "只能为自己发布的课程申请删除");
            }
            assertNoPending(requestType, targetId, "该课程已有一条待审批的删除申请，请等待管理员处理");
            save(build(requestType, targetId, course.getCourseId(), teacherId, trimmedReason));
            return;
        }

        if (ApprovalRequest.TYPE_APPLY_REJECT.equals(requestType)) {
            CourseApply apply = courseApplyService.getById(targetId);
            if (apply == null) {
                throw new BizException(ResultCode.DATA_NOT_FOUND, "报名记录不存在");
            }
            Course course = courseService.getById(apply.getCourseId());
            if (course == null) {
                throw new BizException(ResultCode.DATA_NOT_FOUND, "报名所属课程不存在");
            }
            if (!Objects.equals(course.getPublishTeacherId(), teacherId)) {
                throw new BizException(ResultCode.FORBIDDEN, "只能为自己发布课程的报名申请驳回");
            }
            if (apply.getAuditStatus() == null || apply.getAuditStatus() != 0) {
                throw new BizException("该报名当前不是「待审核」状态，无需驳回");
            }
            assertNoPending(requestType, targetId, "该报名已有一条待审批的驳回申请，请等待管理员处理");
            save(build(requestType, targetId, course.getCourseId(), teacherId, trimmedReason));
            return;
        }

        throw new BizException(ResultCode.PARAM_ERROR, "不支持的申请类型：" + requestType);
    }

    private ApprovalRequest build(String type, Integer targetId, Integer courseId,
                                  Integer teacherId, String reason) {
        ApprovalRequest request = new ApprovalRequest();
        request.setRequestType(type);
        request.setTargetId(targetId);
        request.setCourseId(courseId);
        request.setApplicantId(teacherId);
        request.setReason(reason);
        request.setStatus(ApprovalRequest.STATUS_PENDING);
        request.setCreateTime(new Date());
        return request;
    }

    /** 同一目标不允许堆叠多条待审批申请 —— 否则管理员批完第一条，后面几条全是空转 */
    private void assertNoPending(String type, Integer targetId, String message) {
        Long pending = count(new QueryWrapper<ApprovalRequest>()
                .eq("request_type", type)
                .eq("target_id", targetId)
                .eq("status", ApprovalRequest.STATUS_PENDING));
        if (pending != null && pending > 0) {
            throw new BizException(message);
        }
    }

    // ---------------------------------------------------------------- 审批列表

    @Override
    public List<ApprovalRequestVO> listDetail(Integer status) {
        QueryWrapper<ApprovalRequest> wrapper = new QueryWrapper<>();
        if (status != null) {
            wrapper.eq("status", status);
        }
        // 教师只能看到自己提交的申请（用于确认进度），管理员看全部。
        // 与其它接口一致：过滤条件由服务端按 JWT 里的身份决定，不接受前端传 applicantId。
        if (!CurrentUserUtil.isAdmin()) {
            wrapper.eq("applicant_id", CurrentUserUtil.getId());
        }
        wrapper.orderByDesc("create_time").orderByDesc("request_id");
        return toVOList(list(wrapper));
    }

    @Override
    public int pendingCount() {
        return Math.toIntExact(count(new QueryWrapper<ApprovalRequest>()
                .eq("status", ApprovalRequest.STATUS_PENDING)));
    }

    /**
     * 批量补齐展示字段。
     *
     * <p>不管列表多少条，这里最多只发 4 次查询（课程、报名、姓名），
     * 不做「循环里逐条查」—— 那是典型 N+1，审批列表虽然不长，
     * 但这个写法一旦被复制到别的列表接口就会变成事故。
     */
    private List<ApprovalRequestVO> toVOList(List<ApprovalRequest> list) {
        List<ApprovalRequestVO> result = new ArrayList<>();
        if (list == null || list.isEmpty()) {
            return result;
        }

        Set<String> nameRefs = new HashSet<>();
        Set<Integer> courseIds = new HashSet<>();
        Set<Integer> applyIds = new HashSet<>();
        for (ApprovalRequest r : list) {
            nameRefs.add(UserNameResolver.key(UserNameResolver.ROLE_TEACHER, r.getApplicantId()));
            if (r.getAuditorId() != null) {
                nameRefs.add(UserNameResolver.key(UserNameResolver.ROLE_ADMIN, r.getAuditorId()));
            }
            if (r.getCourseId() != null) {
                courseIds.add(r.getCourseId());
            }
            if (ApprovalRequest.TYPE_APPLY_REJECT.equals(r.getRequestType()) && r.getTargetId() != null) {
                applyIds.add(r.getTargetId());
            }
        }

        Map<Integer, CourseApply> applyMap = new HashMap<>();
        if (!applyIds.isEmpty()) {
            for (CourseApply apply : courseApplyService.listByIds(applyIds)) {
                applyMap.put(apply.getApplyId(), apply);
                nameRefs.add(UserNameResolver.key(UserNameResolver.ROLE_STUDENT, apply.getStudentId()));
                if (apply.getCourseId() != null) {
                    courseIds.add(apply.getCourseId());
                }
            }
        }

        Map<Integer, String> courseNames = new HashMap<>();
        if (!courseIds.isEmpty()) {
            for (Course course : courseService.listByIds(courseIds)) {
                courseNames.put(course.getCourseId(), course.getCourseName());
            }
        }

        Map<String, String> names = userNameResolver.resolve(nameRefs);

        for (ApprovalRequest r : list) {
            ApprovalRequestVO vo = new ApprovalRequestVO();
            BeanUtils.copyProperties(r, vo);
            vo.setRequestTypeText(typeText(r.getRequestType()));
            vo.setStatusText(statusText(r.getStatus()));
            vo.setApplicantName(pick(names, UserNameResolver.ROLE_TEACHER, r.getApplicantId()));
            if (r.getAuditorId() != null) {
                vo.setAuditorName(pick(names, UserNameResolver.ROLE_ADMIN, r.getAuditorId()));
            }

            if (ApprovalRequest.TYPE_APPLY_REJECT.equals(r.getRequestType())) {
                CourseApply apply = applyMap.get(r.getTargetId());
                if (apply == null) {
                    // 学员撤销报名会删掉这条记录，申请本身仍保留作为留痕
                    vo.setTargetDesc("该报名记录已不存在");
                } else {
                    String studentName = pick(names, UserNameResolver.ROLE_STUDENT, apply.getStudentId());
                    vo.setCourseName(courseNames.get(apply.getCourseId()));
                    vo.setTargetDesc(studentName + " · " + courseNames.getOrDefault(apply.getCourseId(), "课程已删除"));
                }
            } else {
                String courseName = courseNames.get(r.getCourseId());
                vo.setCourseName(courseName);
                vo.setTargetDesc(courseName == null ? "该课程已不存在" : courseName);
            }
            result.add(vo);
        }
        return result;
    }

    private String pick(Map<String, String> names, String role, Integer id) {
        return names.getOrDefault(UserNameResolver.key(role, id), "—");
    }

    private String typeText(String type) {
        if (ApprovalRequest.TYPE_COURSE_DELETE.equals(type)) {
            return "删除课程";
        }
        if (ApprovalRequest.TYPE_APPLY_REJECT.equals(type)) {
            return "驳回报名";
        }
        return "未知申请";
    }

    private String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case ApprovalRequest.STATUS_PENDING -> "待审批";
            case ApprovalRequest.STATUS_APPROVED -> "已通过";
            case ApprovalRequest.STATUS_REJECTED -> "已驳回";
            default -> "未知";
        };
    }

    // ---------------------------------------------------------------- 审批

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Integer requestId, boolean approved, String auditComment) {
        if (requestId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "申请 ID 不能为空");
        }
        ApprovalRequest request = getById(requestId);
        if (request == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "审批申请不存在");
        }
        if (request.getStatus() != null && request.getStatus() != ApprovalRequest.STATUS_PENDING) {
            throw new BizException("该申请已被处理，无需重复审批");
        }
        if (!approved && !StringUtils.hasText(auditComment)) {
            throw new BizException(ResultCode.PARAM_ERROR, "驳回申请时必须填写审批意见");
        }

        Integer adminId = CurrentUserUtil.getId();
        if (adminId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }

        String comment = auditComment == null ? null : auditComment.trim();
        // 状态必须跟着「同意 / 驳回」走：这一行写错就会出现
        // 「管理员明明点了驳回，申请却显示已通过」——审批记录与实际结果对不上。
        int targetStatus = approved
                ? ApprovalRequest.STATUS_APPROVED
                : ApprovalRequest.STATUS_REJECTED;

        if (approved) {
            // 先执行动作再改状态：动作失败会抛异常并回滚，申请仍然是「待审批」，
            // 不会出现「显示已通过、但课程还在」这种对不上的记录。
            String voidReason = executeOrExplain(request);
            if (voidReason != null) {
                // 目标已经消失（课程被删 / 学员撤销报名）：不报错卡住，直接标为失效
                targetStatus = ApprovalRequest.STATUS_REJECTED;
                comment = voidReason;
            }
        }

        int rows = baseMapper.auditIfPending(requestId, targetStatus, comment, adminId);
        if (rows == 0) {
            throw new BizException("该申请已被其他管理员处理，请刷新后重试");
        }
    }

    /**
     * 执行申请里描述的动作。
     *
     * @return {@code null} 表示动作已执行；返回非空字符串表示目标已不存在，
     *         该字符串会作为「自动失效」的说明写进申请记录
     */
    private String executeOrExplain(ApprovalRequest request) {
        String type = request.getRequestType();

        if (ApprovalRequest.TYPE_COURSE_DELETE.equals(type)) {
            Course course = courseService.getById(request.getTargetId());
            if (course == null) {
                return "课程已不存在（可能已被删除），本申请自动失效";
            }
            courseService.removeCourseCascade(request.getTargetId());
            return null;
        }

        if (ApprovalRequest.TYPE_APPLY_REJECT.equals(type)) {
            CourseApply apply = courseApplyService.getById(request.getTargetId());
            if (apply == null) {
                return "报名记录已不存在（学员可能已撤销报名），本申请自动失效";
            }
            if (apply.getAuditStatus() == null || apply.getAuditStatus() != 0) {
                // 申请提交时这条报名还是「待审核」。如果审批期间它已经被处理过
                // （教师点了通过、或管理员直接从别处驳回了），这条申请的语义
                // ——「驳回一个待审核的报名」—— 就已经不成立。
                // 尤其是：不能因为它已经「已通过」就放开，那等于把一个已经入学的学员再踢出去。
                return "该报名已不是「待审核」状态（可能已被通过或驳回），本申请自动失效";
            }
            // 审核人记为发起申请的教师：audit_teacher_id 有外键指向 teacher 表，
            // 直接写管理员 ID 会记错人（详见 CourseApplyService#rejectOnApproval）
            courseApplyService.rejectOnApproval(request.getTargetId(), request.getReason(), request.getApplicantId());
            return null;
        }

        return "未知的申请类型，本申请自动失效";
    }
}
