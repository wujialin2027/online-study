package com.online.study.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 敏感操作审批申请（teacher 发起 → admin 审批）
 *
 * <h3>为什么要有这张表</h3>
 * 有两件事的影响面超出了"教师自己的数据"，不能由教师一个人拍板：
 * <ol>
 *   <li><b>删除课程</b>：会级联删掉资源、报名、成绩、作业与全部提交记录，
 *       不可恢复；学员的学习记录会一起消失。</li>
 *   <li><b>驳回学员报名</b>：直接决定学员能不能上这门课，而且要给学员一个理由。</li>
 * </ol>
 * 改造前是「教师点一下立即生效」，管理员事后看日志才知道。
 * 现在统一走「申请 → 审批 → 执行」，理由与审批意见全程留痕，
 * 并且<b>执行动作发生在管理员同意的这一刻</b>（见 {@code ApprovalRequestServiceImpl#audit}）。
 */
@Data
@TableName("approval_request")
public class ApprovalRequest {

    /** 申请类型：删除课程 */
    public static final String TYPE_COURSE_DELETE = "COURSE_DELETE";
    /** 申请类型：驳回报名 */
    public static final String TYPE_APPLY_REJECT = "APPLY_REJECT";

    /** 审批状态：待审批 */
    public static final int STATUS_PENDING = 0;
    /** 审批状态：已通过（申请里描述的动作已执行） */
    public static final int STATUS_APPROVED = 1;
    /** 审批状态：已驳回（动作不执行） */
    public static final int STATUS_REJECTED = 2;

    @TableId(type = IdType.AUTO)
    private Integer requestId;

    /** COURSE_DELETE / APPLY_REJECT */
    private String requestType;

    /** 目标 ID：删除课程 → course_id；驳回报名 → course_apply.apply_id */
    private Integer targetId;

    /** 冗余的课程 ID：列表展示与筛选用，避免展示时再去反查目标表 */
    private Integer courseId;

    /** 发起申请的教师 ID */
    private Integer applicantId;

    /** 教师填写的理由（驳回报名时同时作为给学员看的原因） */
    private String reason;

    /** 0 待审批 / 1 已通过 / 2 已驳回 */
    private Integer status;

    /** 管理员的审批意见 */
    private String auditComment;

    /** 审批管理员 ID */
    private Integer auditorId;

    private Date auditTime;

    private Date createTime;
}
