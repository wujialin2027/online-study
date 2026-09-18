package com.online.study.service;

import com.online.study.entity.CourseApply;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 报名服务
 *
 * <p>这三个方法都涉及「报名记录」与「课程已占名额」两处数据的同步修改，
 * 所以必须在 Service 层用事务包起来 —— 这也是把它们从 Controller
 * 下沉到 Service 的原因（Controller 只做参数接收与转发）。
 */
public interface CourseApplyService extends IService<CourseApply> {

    /**
     * 学员报名课程（带名额控制，防并发超卖）。
     *
     * <p>流程：校验课程状态 → <b>原子占位</b> → 写入报名记录。
     * 任一环节失败整体回滚，名额不会泄漏。
     *
     * @param courseId 课程 ID
     */
    void apply(Integer courseId);

    /**
     * 审核报名（教师 / 管理员）。
     *
     * <p>名额口径：{@code audit_status} 为 0（待审核）或 1（已通过）时占用名额，
     * 为 2（已驳回）时不占。因此：
     * <ul>
     *   <li>驳回 —— 释放名额</li>
     *   <li>撤销驳回（2 → 0/1）—— <b>需要重新抢名额</b>，可能因已满而失败</li>
     * </ul>
     *
     * @param applyId     报名记录 ID
     * @param auditStatus 审核结果：1 通过 / 2 驳回 / 0 撤销驳回
     * @param remark      审核意见（驳回时必填）
     */
    void auditApply(Integer applyId, Integer auditStatus, String remark);

    /**
     * 管理员审批通过后，代为执行「驳回报名」。
     *
     * <p>为什么要单独一个方法，而不是让审批流直接调 {@link #auditApply}：
     * {@code course_apply.audit_teacher_id} 上有指向 {@code teacher} 表的外键，
     * 而当前登录人是<b>管理员</b>（ID 在 admin 表里）。直接调 auditApply 会在
     * 这个字段里写入管理员 ID —— 轻则外键报错，重则写进去一个"长得像教师 ID"的
     * 数字，把审核痕迹记到某个无辜教师头上。
     *
     * <p>所以驳回动作的审核人固定记为<b>发起申请的教师</b>，
     * 管理员的动作留在 {@code approval_request} 的 {@code auditor_id} 上，两边都对得上。
     *
     * @param applyId   报名记录 ID
     * @param reason    驳回原因（教师申请时填写，学员端会看到）
     * @param teacherId 发起申请的教师 ID，写入 audit_teacher_id
     */
    void rejectOnApproval(Integer applyId, String reason, Integer teacherId);

    /**
     * 撤销报名（学员撤自己的；管理员可撤任意一条），并释放名额。
     *
     * @param applyId 报名记录 ID
     */
    void cancelApply(Integer applyId);

    /**
     * 给报名列表补齐「驳回审批中」标记（派生状态，非数据库字段）。
     *
     * <p>教师提交驳回申请后，报名记录的 {@code audit_status} 仍然是「待审核」——
     * 因为它确实还没被驳回，只是有一张单子在等管理员签字。列表上如果只显示「待审核」，
     * 教师会以为自己的申请没提交成功，反复点驳回。
     *
     * <p>所以这里顺带查一次 {@code approval_request}：有待审批的驳回申请就把
     * {@code rejectPending} 标成 true，页面显示「驳回审批中」并禁用驳回按钮。
     *
     * @param applies 待补齐的报名列表（原地修改）
     */
    void fillRejectPending(List<CourseApply> applies);
}
