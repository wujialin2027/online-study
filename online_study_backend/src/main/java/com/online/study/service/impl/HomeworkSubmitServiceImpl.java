package com.online.study.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.online.study.entity.Course;
import com.online.study.entity.CourseApply;
import com.online.study.entity.Homework;
import com.online.study.entity.HomeworkSubmit;
import com.online.study.common.ResultCode;
import com.online.study.exception.BizException;
import com.online.study.mapper.CourseApplyMapper;
import com.online.study.mapper.CourseMapper;
import com.online.study.mapper.HomeworkMapper;
import com.online.study.mapper.HomeworkSubmitMapper;
import com.online.study.service.HomeworkSubmitService;
import com.online.study.utils.CurrentUserUtil;
import com.online.study.vo.HomeworkSubmitStatsVO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class HomeworkSubmitServiceImpl extends ServiceImpl<HomeworkSubmitMapper, HomeworkSubmit> implements HomeworkSubmitService {

    /** 报名状态：已通过（只有在读学员才算「应交作业」的分母） */
    private static final int APPLY_PASSED = 1;
    /** 批改状态：已批改 */
    private static final int CORRECT_DONE = 1;

    /**
     * 这里注入的是 Mapper 而不是别的 Service。
     *
     * <p>不是随手写的：{@code ApprovalRequestServiceImpl} 已经依赖了
     * {@code CourseApplyService}，如果报名服务反过来依赖作业提交服务，
     * 就会形成 Bean 循环依赖 —— Spring Boot 2.6 起默认禁止循环引用，
     * 应用会直接启动失败。用 Mapper 只有单向依赖，天然没有这个问题。
     */
    @Autowired
    private HomeworkMapper homeworkMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private CourseApplyMapper courseApplyMapper;

    @Override
    public HomeworkSubmitStatsVO courseStats(Integer courseId) {
        if (courseId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "courseId 不能为空");
        }
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "课程不存在");
        }
        // 越权校验：课程 ID 来自前端，必须核对归属 ——
        // 否则换个 courseId 就能看到别人课程的提交情况
        if (!CurrentUserUtil.isAdmin()
                && !Objects.equals(course.getPublishTeacherId(), CurrentUserUtil.getId())) {
            throw new BizException(ResultCode.FORBIDDEN, "只能查看自己发布课程的作业情况");
        }

        HomeworkSubmitStatsVO result = new HomeworkSubmitStatsVO();

        // 分母：在读学员数（待审核 / 已驳回的报名都不算）
        Long studentCount = courseApplyMapper.selectCount(new QueryWrapper<CourseApply>()
                .eq("course_id", courseId)
                .eq("audit_status", APPLY_PASSED));
        result.setStudentCount(studentCount == null ? 0 : studentCount.intValue());

        List<Homework> homeworks = homeworkMapper.selectList(
                new QueryWrapper<Homework>().eq("course_id", courseId));
        if (homeworks.isEmpty()) {
            result.setItems(List.of());
            return result;
        }

        List<Integer> homeworkIds = homeworks.stream().map(Homework::getHomeworkId).toList();
        // 一次查询取回整门课的提交记录，再在内存里分组统计。
        // 不写成「循环里逐份作业 count(*)」—— 那是 N+1，作业一多就是 N 次数据库往返。
        List<HomeworkSubmit> submits = list(
                new QueryWrapper<HomeworkSubmit>().in("homework_id", homeworkIds));

        Map<Integer, HomeworkSubmitStatsVO.Item> itemMap = new HashMap<>();
        for (Integer homeworkId : homeworkIds) {
            HomeworkSubmitStatsVO.Item item = new HomeworkSubmitStatsVO.Item();
            item.setHomeworkId(homeworkId);
            item.setSubmitCount(0);
            item.setGradedCount(0);
            item.setPendingCount(0);
            itemMap.put(homeworkId, item);
        }
        for (HomeworkSubmit submit : submits) {
            HomeworkSubmitStatsVO.Item item = itemMap.get(submit.getHomeworkId());
            if (item == null) {
                continue;
            }
            item.setSubmitCount(item.getSubmitCount() + 1);
            if (submit.getCorrectStatus() != null && submit.getCorrectStatus() == CORRECT_DONE) {
                item.setGradedCount(item.getGradedCount() + 1);
            } else {
                item.setPendingCount(item.getPendingCount() + 1);
            }
        }

        // 按作业列表的顺序输出，前端直接按下标对应即可
        result.setItems(homeworks.stream().map(h -> itemMap.get(h.getHomeworkId())).toList());
        return result;
    }
}
