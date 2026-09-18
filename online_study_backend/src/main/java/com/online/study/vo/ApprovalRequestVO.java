package com.online.study.vo;

import com.online.study.entity.ApprovalRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审批申请视图对象
 *
 * <p>在实体之上补上「人话」字段：申请人姓名、课程名、审批对象描述。
 * 审批中心的表格里如果只显示 {@code targetId=57}、{@code applicantId=7}，
 * 管理员根本不知道自己在批哪件事。
 *
 * <p>这些字段不是数据库列，由 {@code ApprovalRequestServiceImpl#listDetail} 批量补齐
 * —— 一次请求最多 3 次查询（申请人、课程、被驳回的学员），不是逐条反查。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ApprovalRequestVO extends ApprovalRequest {

    /** 申请类型的中文文案 */
    private String requestTypeText;

    /** 审批状态的中文文案 */
    private String statusText;

    /** 申请教师姓名 */
    private String applicantName;

    /** 审批管理员姓名 */
    private String auditorName;

    /** 课程名称（冗余字段已存 courseId，这里补名字） */
    private String courseName;

    /**
     * 申请对象描述。
     * 驳回报名时为「学员姓名（课程名）」，删除课程时为课程名。
     */
    private String targetDesc;
}
