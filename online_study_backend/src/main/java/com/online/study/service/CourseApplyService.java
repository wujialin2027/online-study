package com.online.study.service;

import com.online.study.entity.CourseApply;
import com.baomidou.mybatisplus.extension.service.IService;

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
     * 撤销报名（学员撤自己的；管理员可撤任意一条），并释放名额。
     *
     * @param applyId 报名记录 ID
     */
    void cancelApply(Integer applyId);
}
