package com.online.study.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.online.study.common.ResultCode;
import com.online.study.entity.Course;
import com.online.study.entity.CourseApply;
import com.online.study.exception.BizException;
import com.online.study.mapper.CourseApplyMapper;
import com.online.study.mapper.CourseMapper;
import com.online.study.service.CourseApplyService;
import com.online.study.utils.CurrentUserUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.Objects;

/**
 * 报名服务实现
 *
 * <h3>防超卖是怎么做的（面试重点，四层保护）</h3>
 * <ol>
 *   <li><b>原子占位</b>：{@code tryOccupySeat} 一条 UPDATE 同时完成「判断名额」与「名额 +1」，
 *       靠 InnoDB 行锁串行化并发请求；影响行数为 0 即表示已满。</li>
 *   <li><b>唯一索引兜底</b>：{@code uk_student_course} 保证同一学员不会重复报名，
 *       并发重复提交时后一条插入失败。</li>
 *   <li><b>事务回滚</b>：插入失败会让整个方法回滚，第 1 步占掉的名额自动退回，
 *       不会出现「占位成功但报名没写入」的名额泄漏。</li>
 *   <li><b>状态校验</b>：只有审核通过（audit_status = 1）的课程才允许报名。</li>
 * </ol>
 *
 * <p>如果只写「先 SELECT 查人数，再判断，再插入」—— 两个请求会同时读到
 * 「还有名额」，然后各自加一，最终报名数超过上限。这是超卖的根本原因。
 */
@Service
public class CourseApplyServiceImpl extends ServiceImpl<CourseApplyMapper, CourseApply>
        implements CourseApplyService {

    /** 待审核 */
    private static final int STATUS_PENDING = 0;
    /** 已通过 */
    private static final int STATUS_PASSED = 1;
    /** 已驳回 */
    private static final int STATUS_REJECTED = 2;

    @Autowired
    private CourseMapper courseMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void apply(Integer courseId) {
        Integer studentId = CurrentUserUtil.getId();
        if (studentId == null) {
            // 正常走到这里说明 JWT 已通过校验但 ID 没写进上下文，属于服务端问题
            throw new BizException(ResultCode.UNAUTHORIZED);
        }

        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "课程不存在");
        }
        if (course.getAuditStatus() == null || course.getAuditStatus() != STATUS_PASSED) {
            throw new BizException("该课程尚未通过审核，暂不能报名");
        }

        // ① 原子占位：判断与扣减在一条 SQL 内完成
        if (courseMapper.tryOccupySeat(courseId) == 0) {
            String limitText = (course.getMaxStudents() != null && course.getMaxStudents() > 0)
                    ? "（名额上限 " + course.getMaxStudents() + " 人）" : "";
            throw new BizException("课程名额已满" + limitText);
        }

        // ② 写入报名记录；重复报名会被唯一索引拦下
        CourseApply apply = new CourseApply();
        apply.setStudentId(studentId);
        apply.setCourseId(courseId);
        apply.setApplyTime(new Date());
        apply.setAuditStatus(STATUS_PENDING);
        try {
            save(apply);
        } catch (DuplicateKeyException e) {
            // 唯一索引 uk_student_course 触发：抛出新异常 → 事务回滚 → ① 占掉的名额自动退回
            throw new BizException("你已经报名过这门课程，不能重复报名");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditApply(Integer applyId, Integer auditStatus, String remark) {
        if (auditStatus == null || auditStatus < STATUS_PENDING || auditStatus > STATUS_REJECTED) {
            throw new BizException("审核结果不合法（只能是 0 待审核 / 1 通过 / 2 驳回）");
        }
        if (auditStatus == STATUS_REJECTED && !StringUtils.hasText(remark)) {
            throw new BizException("驳回报名时必须填写原因");
        }

        CourseApply apply = getById(applyId);
        if (apply == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "报名记录不存在");
        }

        // 越权校验：教师只能审核**自己发布的课程**的报名，管理员不受限。
        // 不加这个检查的话，任何教师拿着别人的 applyId 都能通过/驳回别人的学员 ——
        // 接口上的 @PreAuthorize 只挡住了学员，挡不住"教师 A 审教师 B 的课"。
        if (!CurrentUserUtil.isAdmin()) {
            Course course = courseMapper.selectById(apply.getCourseId());
            if (course == null
                    || !Objects.equals(course.getPublishTeacherId(), CurrentUserUtil.getId())) {
                throw new BizException(ResultCode.FORBIDDEN, "只能审核自己发布的课程的报名");
            }
        }

        int oldStatus = apply.getAuditStatus() == null ? STATUS_PENDING : apply.getAuditStatus();
        if (oldStatus == auditStatus) {
            throw new BizException("该报名当前已是此状态，无需重复操作");
        }

        boolean wasRejected = oldStatus == STATUS_REJECTED;
        boolean willReject = auditStatus == STATUS_REJECTED;

        if (wasRejected && !willReject) {
            // 撤销驳回：重新占用名额，可能因为已满而失败
            if (courseMapper.tryOccupySeat(apply.getCourseId()) == 0) {
                throw new BizException("课程名额已满，无法恢复该报名");
            }
        } else if (!wasRejected && willReject) {
            // 驳回：释放名额
            courseMapper.releaseSeat(apply.getCourseId());
        }

        // 条件更新：只有旧状态未变时才生效，避免并发下重复审核
        int rows = baseMapper.auditIfStatus(applyId, auditStatus, oldStatus,
                CurrentUserUtil.getId(), remark);
        if (rows == 0) {
            // 抛异常 → 事务回滚 → 上面已做的名额增减一并撤销
            throw new BizException("该报名已被其他操作修改，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelApply(Integer applyId) {
        CourseApply apply = getById(applyId);
        if (apply == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "报名记录不存在");
        }

        // 越权校验：学员只能撤销自己的报名，管理员不受限
        if (!CurrentUserUtil.isAdmin() && !Objects.equals(apply.getStudentId(), CurrentUserUtil.getId())) {
            throw new BizException(ResultCode.FORBIDDEN, "只能撤销自己的报名");
        }

        // 已驳回的报名本来就不占名额，不能重复释放
        if (apply.getAuditStatus() == null || apply.getAuditStatus() != STATUS_REJECTED) {
            courseMapper.releaseSeat(apply.getCourseId());
        }
        removeById(applyId);
    }
}
