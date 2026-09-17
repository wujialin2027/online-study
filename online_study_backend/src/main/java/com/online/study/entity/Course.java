package com.online.study.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.Date;

/**
 * 课程
 *
 * <h3>字段的可写性（这一块是安全设计，别随手改）</h3>
 * 下面几个字段用 {@code @JsonProperty(access = READ_ONLY)} 标记为<b>只读</b>：
 * <pre>
 *   auditStatus / auditAdminId / auditTime / auditRemark
 *   currentStudents
 * </pre>
 * 含义是：<b>接口可以把它返回给前端，但前端传进来的值一律忽略</b>。
 *
 * <p>为什么必需这么做：课程保存接口是 {@code POST /course/save}，
 * 它直接接收本实体。改之前，任何登录用户只要在请求体里塞一个
 * {@code "auditStatus": 1}，就能让自己的课程绕过审核直接变成「已通过」，
 * 或者塞 {@code "currentStudents": 0} 把报名人数清零。
 * 加上注解后，这些字段只能由服务端的审核接口写入。
 */
@Data
@TableName("course")
public class Course {

    @TableId(type = IdType.AUTO)
    private Integer courseId;

    private String courseName;

    private String courseIntro;

    /** 培训周期，例如「3 个月」 */
    private String trainCycle;

    /** 报名条件 */
    private String applyCond;

    /** 发布教师 ID（服务端从 JWT 取，不信任前端传值） */
    private Integer publishTeacherId;

    /** 名额上限；0 表示不限（教师可设置） */
    private Integer maxStudents;

    /**
     * 课程状态：0 待审核 / 1 已通过 / 2 已驳回。
     *
     * <p>字段本身可以被反序列化（为了兼容旧前端把「通过 / 驳回」按钮
     * 也走 /course/save 的写法），但 {@code CourseController#save} 里
     * 会把它和数据库里的当前值做比对：
     * <b>只有管理员且确实发生了状态变化时，才转发到审核逻辑</b>；
     * 教师提交的保存请求一律强制重置为待审核。
     */
    private Integer auditStatus;

    /** 审核管理员 ID（只读：只能由服务端审核逻辑写入，前端传值会被忽略） */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer auditAdminId;

    /** 审核时间（只读） */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Date auditTime;

    /** 审核意见 / 驳回原因（审核时由管理员提交） */
    private String auditRemark;

    /**
     * 当前已占名额（只读）。
     * 口径 = 该课程下 audit_status 为 0（待审核）或 1（已通过）的报名数，
     * 由「条件 UPDATE」原子维护，不能由前端直接赋值。
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer currentStudents;

    private Date publishTime;

    /** 下线时间 */
    private Date offlineTime;
}
