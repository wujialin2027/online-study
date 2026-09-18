package com.online.study.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.online.study.entity.ApprovalRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 审批申请 Mapper
 *
 * <p>审批同样采用「带前置状态的条件更新」：只有申请仍处于「待审批」时才会被写入结果。
 * 两个管理员同时点「同意」时，只有一个能改成功，另一个会收到提示，
 * 也就不可能出现「同意 + 驳回」两条结果，或者同一个申请被执行两次。
 */
@Mapper
public interface ApprovalRequestMapper extends BaseMapper<ApprovalRequest> {

    /**
     * 写入审批结果（条件更新，防并发重复审批）。
     *
     * @param requestId    申请 ID
     * @param status       审批结果：1 通过 / 2 驳回
     * @param auditComment 审批意见
     * @param auditorId    审批管理员 ID
     * @return 影响行数：1 成功，0 说明申请已被别的管理员处理过
     */
    @Update("UPDATE approval_request SET status = #{status}, audit_comment = #{auditComment}, "
            + "auditor_id = #{auditorId}, audit_time = NOW() "
            + "WHERE request_id = #{requestId} AND status = 0")
    int auditIfPending(@Param("requestId") Integer requestId,
                       @Param("status") Integer status,
                       @Param("auditComment") String auditComment,
                       @Param("auditorId") Integer auditorId);
}
