package com.online.study.service;

import com.online.study.entity.Course;
import com.baomidou.mybatisplus.extension.service.IService;

public interface CourseService extends IService<Course> {

    /**
     * 级联删除课程，及其所有关联数据（资源、报名、成绩、作业、作业提交）。
     *
     * <p>把这个动作放在 Service 层（而不是散在 Controller 里）的原因：
     * 它需要 {@code @Transactional} 保证「要么全成功、要么全回滚」，
     * 而事务边界的划分属于业务职责。
     *
     * @param courseId 课程 ID
     * @return 是否删除成功
     */
    boolean removeCourseCascade(Integer courseId);
}
