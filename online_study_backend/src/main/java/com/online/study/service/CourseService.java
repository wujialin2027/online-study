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

    /**
     * 审核课程（仅管理员）。
     *
     * <p>状态机：{@code 0 待审核} → {@code 1 已通过} 或 {@code 2 已驳回}。
     * 只有处于「待审核」的课程可以被审核，审核结果与审核人、时间、意见一并落库留痕。
     *
     * <p>并发保护：实际更新走 {@code CourseMapper#auditIfPending} 这一条
     * 带 {@code WHERE audit_status = 0} 的条件 UPDATE，两个管理员同时操作时
     * 只有一个能生效，另一个会收到「已被审核」的提示，不会互相覆盖。
     *
     * @param courseId    课程 ID
     * @param auditStatus 审核结果：1 通过 / 2 驳回
     * @param remark      审核意见（驳回时必填）
     */
    void auditCourse(Integer courseId, Integer auditStatus, String remark);
}
