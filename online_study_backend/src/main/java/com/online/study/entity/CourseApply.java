package com.online.study.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.Date;

/**
 * 课程报名记录
 *
 * <p>表上有一个联合唯一索引 {@code uk_student_course(student_id, course_id)}，
 * 它配合服务端的「原子占位」一起构成防重复报名 / 防超卖的双保险：
 * 即使并发请求同时通过了名额判断，也只能有一条插入成功，
 * 另一条会抛 {@code DuplicateKeyException} 并回滚（名额自动退回）。
 *
 * <p>{@code auditStatus} 与审核相关字段都是只读的，只能由审核接口修改 ——
 * 与原 {@code /course-apply/save} 直接把实体交给前端不同，
 * 改之前学员可以自己把 {@code auditStatus} 改成 1（审核通过）。
 */
@Data
@TableName("course_apply")
public class CourseApply {

    @TableId(type = IdType.AUTO)
    private Integer applyId;

    /** 学员 ID（服务端从 JWT 取） */
    private Integer studentId;

    private Integer courseId;

    private Date applyTime;

    /**
     * 审核状态：0 待审核 / 1 已通过 / 2 已驳回。
     *
     * <p>可被反序列化以兼容旧前端的审核按钮，但
     * {@code CourseApplyController#save} 会与库中当前值比对：
     * <b>只有发生了状态变化才走审核逻辑</b>，且要经过教师 / 管理员权限校验，
     * 因此学员无法自己把状态改成「已通过」。
     */
    private Integer auditStatus;

    /** 审核教师 ID（只读：由服务端审核逻辑写入） */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer auditTeacherId;

    /** 审核时间（只读） */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Date auditTime;

    /** 审核意见 / 驳回原因（审核时提交） */
    private String auditRemark;
}
