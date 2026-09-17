package com.online.study.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.online.study.common.ResultCode;
import com.online.study.entity.Course;
import com.online.study.entity.CourseApply;
import com.online.study.entity.CourseResource;
import com.online.study.entity.Homework;
import com.online.study.entity.HomeworkSubmit;
import com.online.study.entity.Score;
import com.online.study.exception.BizException;
import com.online.study.mapper.CourseMapper;
import com.online.study.service.CourseApplyService;
import com.online.study.service.CourseResourceService;
import com.online.study.service.CourseService;
import com.online.study.service.HomeworkService;
import com.online.study.service.HomeworkSubmitService;
import com.online.study.service.ScoreService;
import com.online.study.utils.CurrentUserUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements CourseService {

    /** 课程状态：待审核 */
    private static final int AUDIT_PENDING = 0;
    /** 课程状态：审核通过 */
    private static final int AUDIT_PASSED = 1;
    /** 课程状态：审核驳回 */
    private static final int AUDIT_REJECTED = 2;

    @Autowired
    private CourseResourceService courseResourceService;

    @Autowired
    private CourseApplyService courseApplyService;

    @Autowired
    private ScoreService scoreService;

    @Autowired
    private HomeworkService homeworkService;

    @Autowired
    private HomeworkSubmitService homeworkSubmitService;

    /**
     * 级联删除课程及其关联数据。
     *
     * <p><b>为什么必须加 {@code @Transactional}</b>：下面这 5 步删除是一个整体。
     * 原代码没有事务，如果第 4 步删除作业提交时抛出异常，
     * 就会出现「资源删了、报名删了、作业没删、课程还在」的中间状态 ——
     * 数据不一致，而且没法自动恢复，只能人工修。
     * 加了事务后，任何一步失败都会把前面已删的<b>全部回滚</b>。
     *
     * <p>{@code rollbackFor = Exception.class}：Spring 默认只对
     * {@code RuntimeException} 回滚，显式声明后连受检异常也会回滚，更稳妥。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeCourseCascade(Integer courseId) {
        if (courseId == null) {
            return false;
        }

        // 1. 课程资源
        courseResourceService.remove(
                new QueryWrapper<CourseResource>().eq("course_id", courseId));

        // 2. 报名记录
        courseApplyService.remove(
                new QueryWrapper<CourseApply>().eq("course_id", courseId));

        // 3. 成绩
        scoreService.remove(
                new QueryWrapper<Score>().eq("course_id", courseId));

        // 4. 作业及其提交记录（先删子表的提交，再删父表的作业）
        List<Homework> homeworks = homeworkService.list(
                new QueryWrapper<Homework>().eq("course_id", courseId));
        if (homeworks != null && !homeworks.isEmpty()) {
            List<Integer> homeworkIds = homeworks.stream()
                    .map(Homework::getHomeworkId)
                    .collect(Collectors.toList());
            homeworkSubmitService.remove(
                    new QueryWrapper<HomeworkSubmit>().in("homework_id", homeworkIds));
            homeworkService.removeByIds(homeworkIds);
        }

        // 5. 课程本身
        return removeById(courseId);
    }

    /**
     * 审核课程。
     *
     * <p>这里有两层保护：
     * <ol>
     *   <li><b>参数与业务预校验</b>：结果只能是 1/2；驳回必须说明原因。
     *       （校验放在这里而不是 Controller，是为了让别的调用方也享受同样的保护）</li>
     *   <li><b>条件更新兜底</b>：把「必须是待审核状态」写进 SQL 的 WHERE，
     *       即使两个管理员同时提交通过 / 驳回，也只有一个能改成功。</li>
     * </ol>
     *
     * <p>审核人取自 JWT（{@link CurrentUserUtil}），不接收前端传值 ——
     * 否则任何人都能在请求里指定一个别的管理员 ID 来"留痕"。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditCourse(Integer courseId, Integer auditStatus, String remark) {
        if (courseId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "课程 ID 不能为空");
        }
        if (auditStatus == null || (auditStatus != AUDIT_PASSED && auditStatus != AUDIT_REJECTED)) {
            throw new BizException("审核结果只能是 1（通过）或 2（驳回）");
        }
        if (auditStatus == AUDIT_REJECTED && !StringUtils.hasText(remark)) {
            throw new BizException("驳回课程时必须填写原因");
        }

        Integer adminId = CurrentUserUtil.getId();
        if (adminId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }

        // 条件更新：只有仍处于「待审核」的课程才会被改动
        int rows = baseMapper.auditIfPending(courseId, auditStatus, adminId, remark);
        if (rows == 0) {
            // 两种情况：课程不存在，或已被审核过（含并发同时审核）
            Course course = getById(courseId);
            if (course == null) {
                throw new BizException(ResultCode.DATA_NOT_FOUND, "课程不存在");
            }
            throw new BizException("该课程已审核完成，不能重复审核；如需调整请让教师修改后重新提交");
        }
    }
}
