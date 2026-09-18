-- ============================================================
-- migrate_v1.7.sql
-- 新增「敏感操作审批」表，用于承接两件教师不能自己说了算的事：
--   1. 删除课程（连带资源、报名、作业全部级联删除，不可恢复）
--   2. 驳回学员报名（直接影响学员能否上课，且要写进驳回原因给学员看）
--
-- 改造前的行为：教师点一下按钮就直接生效，管理员事后才知道。
-- 改造后：教师提交申请（必填理由）→ 管理员在「审批中心」同意 / 驳回
--         → 同意的才会真正执行，驳回的意见会回给教师。
--
-- 纯新增表，不改动任何已有表结构，可重复执行。
-- ============================================================

CREATE TABLE IF NOT EXISTS approval_request
(
    request_id    INT AUTO_INCREMENT PRIMARY KEY COMMENT '申请 ID',
    request_type  VARCHAR(30)  NOT NULL COMMENT '申请类型：COURSE_DELETE 删除课程 / APPLY_REJECT 驳回报名',
    target_id     INT          NOT NULL COMMENT '目标 ID：COURSE_DELETE→course_id；APPLY_REJECT→course_apply.apply_id',
    course_id     INT          NULL COMMENT '冗余课程 ID，便于列表展示与筛选',
    applicant_id  INT          NOT NULL COMMENT '发起申请的教师 ID（teacher.teacher_id）',
    reason        VARCHAR(500) NOT NULL COMMENT '教师填写的申请理由（驳回报名时同时作为给学员看的原因）',
    status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0 待审批 / 1 已通过（已执行）/ 2 已驳回',
    audit_comment VARCHAR(500) NULL COMMENT '管理员审批意见',
    auditor_id    INT          NULL COMMENT '审批管理员 ID（admin.admin_id）',
    audit_time    DATETIME     NULL COMMENT '审批时间',
    create_time   DATETIME     NOT NULL COMMENT '申请提交时间',
    KEY idx_status (status),
    KEY idx_type_target (request_type, target_id),
    KEY idx_applicant (applicant_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='敏感操作审批申请（教师发起 → 管理员审批）';

-- ---------- 核对 ----------
SELECT COUNT(*) AS 审批申请表已就绪 FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'approval_request';
