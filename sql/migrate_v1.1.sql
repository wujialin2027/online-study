-- ============================================================================
--  在线学习平台 · 增量迁移脚本 v1.1
--  主题：业务增强（报名名额控制 / 审核留痕 / 防重复报名 / 操作日志）
-- ----------------------------------------------------------------------------
--  【为什么单独写迁移脚本，而不是改 init.sql】
--  init.sql 第 1 条语句是 DROP DATABASE，那是「全新初始化」用的；
--  而线上/已有数据的库只能做「增量变更」，否则数据全丢。
--  真实项目里这两类脚本是分开管理的（初始化脚本 + 版本化迁移脚本），
--  这个脚本的命名方式也是参照 Flyway 这类迁移工具的约定。
--
--  【执行方式】
--    mysql -uroot -p --default-character-set=utf8mb4 online_study < sql/migrate_v1.1.sql
--
--  【安全性】
--    全部是 ADD COLUMN / ADD INDEX / CREATE TABLE，不删表、不改已有字段类型，
--    已有数据不受影响。唯一一处 DELETE 是清理历史重复报名记录（有注释说明）。
-- ============================================================================

USE online_study;

-- ---------------------------------------------------------------------------
-- 1. course 表：补「名额上限」与「审核留痕」
--    说明：audit_status（0-未审核/1-通过/2-驳回）原本就有，这里只补：
--          - max_students   报名名额上限，用于并发场景下的防超卖
--          - audit_admin_id 谁审的
--          - audit_time     什么时候审的
--          - audit_remark   审核意见 / 驳回原因（要让教师知道为什么被驳回）
-- ---------------------------------------------------------------------------
ALTER TABLE course
    ADD COLUMN max_students   INT          NOT NULL DEFAULT 50 COMMENT '名额上限（0 表示不限）' AFTER apply_cond,
    ADD COLUMN audit_admin_id INT          NULL COMMENT '审核管理员ID'                AFTER audit_status,
    ADD COLUMN audit_time     DATETIME     NULL COMMENT '审核时间'                    AFTER audit_admin_id,
    ADD COLUMN audit_remark   VARCHAR(255) NULL COMMENT '审核意见（驳回原因）'          AFTER audit_time;

-- ---------------------------------------------------------------------------
-- 2. course_apply 表：从数据库层面杜绝「同一个人重复报同一门课」
--    原表只有普通索引，代码层靠 count 判断，存在并发竞态 ——
--    两个请求同时通过 count 校验，就会插入两条重复记录。
--    唯一索引是最后一道防线，即使代码有漏洞也插不进重复数据。
-- ---------------------------------------------------------------------------

-- 2.1 先查有没有历史重复数据（如果有，下面的 ALTER 会直接失败）
SELECT student_id, course_id, COUNT(*) AS dup_count
FROM course_apply
GROUP BY student_id, course_id
HAVING dup_count > 1;

-- 2.2 清理重复记录，每组只保留 apply_id 最小的那条
--     （如果 2.1 查出来是空结果，这一句不会删任何数据）
DELETE t1 FROM course_apply t1
INNER JOIN course_apply t2
        ON t1.student_id = t2.student_id
       AND t1.course_id  = t2.course_id
       AND t1.apply_id   > t2.apply_id;

-- 2.3 加唯一约束
ALTER TABLE course_apply
    ADD UNIQUE KEY uk_student_course (student_id, course_id);

-- ---------------------------------------------------------------------------
-- 3. 新增：系统操作日志表
--    用于记录「谁、在什么时候、对什么模块、做了什么操作、成功与否、耗时多少」。
--    通过 Spring AOP 切面自动写入，业务代码里不需要手动调用。
--    这张表的价值：
--      - 出问题时可以追溯「是谁改了这条数据」
--      - 面试里可讲：「用 AOP 做横切关注点，业务代码零侵入」
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_operation_log (
    log_id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    operator_id    INT          NULL     COMMENT '操作人ID（来自 JWT 解析结果）',
    operator_name  VARCHAR(50)  NULL     COMMENT '操作人账号',
    operator_role  VARCHAR(20)  NULL     COMMENT '操作人角色',
    module         VARCHAR(50)  NULL     COMMENT '业务模块，如 课程/作业/报名',
    operation      VARCHAR(100) NULL     COMMENT '操作描述，如 发布课程/删除作业',
    request_uri    VARCHAR(255) NULL     COMMENT '请求地址',
    request_method VARCHAR(10)  NULL     COMMENT '请求方法 GET/POST/PUT/DELETE',
    request_params TEXT         NULL     COMMENT '请求参数（敏感字段已脱敏）',
    ip             VARCHAR(64)  NULL     COMMENT '客户端IP',
    success        TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否成功 1-成功 0-失败',
    error_msg      VARCHAR(500) NULL     COMMENT '失败原因',
    cost_ms        BIGINT       NULL     COMMENT '接口耗时（毫秒）',
    create_time    DATETIME     NOT NULL COMMENT '操作时间',
    PRIMARY KEY (log_id),
    KEY idx_operator (operator_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统操作日志';

-- ---------------------------------------------------------------------------
-- 4. 验证变更结果
-- ---------------------------------------------------------------------------
SELECT '--- course 表新增字段 ---' AS step;
SHOW COLUMNS FROM course WHERE Field IN ('max_students','audit_admin_id','audit_time','audit_remark');

SELECT '--- course_apply 唯一索引 ---' AS step;
SHOW INDEX FROM course_apply WHERE Key_name = 'uk_student_course';

SELECT '--- sys_operation_log 表 ---' AS step;
SHOW COLUMNS FROM sys_operation_log;
