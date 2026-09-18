package com.online.study.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.online.study.entity.ApprovalRequest;
import com.online.study.vo.ApprovalRequestVO;

import java.util.List;

/**
 * 敏感操作审批服务
 *
 * <h3>为什么单独做一层，而不是在 Controller 里判断</h3>
 * 「提交申请」要校验目标归属，「审批通过」要在同一个事务里执行真正的动作
 * （删课程 / 驳回报名）并回写申请结果 —— 这两件事都必须有事务边界，
 * 属于业务职责，不该散落在 Controller。
 */
public interface ApprovalRequestService extends IService<ApprovalRequest> {

    /**
     * 教师提交审批申请。
     *
     * @param requestType COURSE_DELETE / APPLY_REJECT
     * @param targetId    目标 ID（课程 ID 或报名记录 ID）
     * @param reason      申请理由（必填）
     */
    void submit(String requestType, Integer targetId, String reason);

    /**
     * 审批列表。
     *
     * <p>管理员看到全部；教师只看到自己提交的申请 —— 过滤条件由服务端按 JWT 里的
     * 身份决定，不接受前端传 {@code applicantId}（否则教师能翻到别人申请了什么）。
     *
     * @param status 过滤状态：0 待审批 / 1 已通过 / 2 已驳回；传 null 表示全部
     */
    List<ApprovalRequestVO> listDetail(Integer status);

    /** 待审批条数，用于管理员端角标提示 */
    int pendingCount();

    /**
     * 审批（管理员）。
     *
     * <p>{@code approved = true} 时会<b>真正执行</b>申请里描述的动作：
     * 删除课程（级联）或驳回报名（把申请理由写给学员）。
     * 目标在这期间已经不存在（例如课程已被删除）时，申请会被标记为
     * 「已失效」并写入说明，而不是抛异常卡在待审批状态。
     *
     * @param requestId    申请 ID
     * @param approved     是否同意
     * @param auditComment 审批意见（驳回时必填）
     */
    void audit(Integer requestId, boolean approved, String auditComment);
}
