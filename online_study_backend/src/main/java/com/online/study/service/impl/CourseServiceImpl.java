package com.online.study.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.online.study.entity.Course;
import com.online.study.entity.CourseApply;
import com.online.study.entity.CourseResource;
import com.online.study.entity.Homework;
import com.online.study.entity.HomeworkSubmit;
import com.online.study.entity.Score;
import com.online.study.mapper.CourseMapper;
import com.online.study.service.CourseApplyService;
import com.online.study.service.CourseResourceService;
import com.online.study.service.CourseService;
import com.online.study.service.HomeworkService;
import com.online.study.service.HomeworkSubmitService;
import com.online.study.service.ScoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements CourseService {

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
}
