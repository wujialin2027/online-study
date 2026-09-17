package com.online.study.mapper;

import com.online.study.entity.CourseApply;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 报名 Mapper
 *
 * <p>审核同样采用「带前置状态的条件更新」：只有当前状态等于旧状态时才更新，
 * 保证同一条报名不会被两个请求同时处理（结果只以先拿到行锁的为准）。
 */
@Mapper
public interface CourseApplyMapper extends BaseMapper<CourseApply> {

    /**
     * 审核报名（条件更新，防并发重复审核）。
     *
     * @param applyId      报名记录 ID
     * @param auditStatus  新的审核状态：1 通过 / 2 驳回 / 0 撤销驳回（回到待审核）
     * @param oldStatus    期望的旧状态，不匹配则不更新
     * @param teacherId    审核教师 ID
     * @param remark       审核意见
     * @return 影响行数：1 成功，0 说明状态已被其他请求改变
     */
    @Update("UPDATE course_apply SET audit_status = #{auditStatus}, "
            + "audit_teacher_id = #{teacherId}, audit_time = NOW(), audit_remark = #{remark} "
            + "WHERE apply_id = #{applyId} AND audit_status = #{oldStatus}")
    int auditIfStatus(@Param("applyId") Integer applyId,
                      @Param("auditStatus") Integer auditStatus,
                      @Param("oldStatus") Integer oldStatus,
                      @Param("teacherId") Integer teacherId,
                      @Param("remark") String remark);
}
