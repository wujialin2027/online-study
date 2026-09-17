-- ============================================================================
--  migrate_v1.2.sql —— 批次 3B：报名名额控制 + 审核留痕
--
--  执行方式（库里有数据，用增量变更，不要跑 init.sql）：
--      cd /d D:\d-projects\online_study
--      mysql -uroot -p --default-character-set=utf8mb4 online_study < sql\migrate_v1.2.sql
--
--  执行前建议备份：
--      mysqldump -uroot -p --databases online_study > online_study_backup_v12.sql
--
--  本次变更全部是「新增」，不删表、不改已有字段类型，现有数据不受影响。
-- ============================================================================

USE online_study;

-- ----------------------------------------------------------------------------
-- 1. course 表：新增「已占名额」计数列
--
--    为什么需要一个计数列，而不是每次实时 COUNT 报名表？
--      报名时要做「人数是否已满」的判断，如果先 SELECT COUNT(*) 再 INSERT，
--      两个请求同时进来会双双通过判断 —— 这就是超卖。
--      改成「一条带条件的 UPDATE」后，InnoDB 会对该行加锁，
--      判断与占用合并成一个原子操作，天然防并发。
-- ----------------------------------------------------------------------------
ALTER TABLE course
    ADD COLUMN current_students INT NOT NULL DEFAULT 0
        COMMENT '当前已占名额（audit_status 为 0/1 的报名数）' AFTER max_students;

-- ----------------------------------------------------------------------------
-- 2. 回填历史数据：按现有报名记录数对齐计数
--
--    口径与代码保持一致：audit_status IN (0,1) 视为占用名额
--      （0 = 待审核，也占位，否则会被无限重复申请；
--        2 = 已驳回，不占位）
-- ----------------------------------------------------------------------------
UPDATE course c
LEFT JOIN (
    SELECT course_id, COUNT(*) AS cnt
    FROM course_apply
    WHERE audit_status IN (0, 1)
    GROUP BY course_id
) t ON t.course_id = c.course_id
SET c.current_students = IFNULL(t.cnt, 0);

-- ----------------------------------------------------------------------------
-- 3. course_apply 表：审核留痕（驳回原因 + 审核时间）
--
--    原表只有 audit_teacher_id（谁审的），没有「什么时候审的、为什么驳回」。
--    业务上必须让学员看到驳回原因，否则只能反复提交。
-- ----------------------------------------------------------------------------
ALTER TABLE course_apply
    ADD COLUMN audit_time DATETIME DEFAULT NULL COMMENT '审核时间' AFTER audit_teacher_id,
    ADD COLUMN audit_remark VARCHAR(255) DEFAULT NULL COMMENT '审核意见（驳回原因）' AFTER audit_time;

-- ----------------------------------------------------------------------------
-- 4. 验证
-- ----------------------------------------------------------------------------
SELECT '--- course 表新列（应有 max_students / current_students）---' AS check_item;
SHOW COLUMNS FROM course WHERE Field IN ('max_students', 'current_students');

SELECT '--- course_apply 表新列（应有 audit_time / audit_remark）---' AS check_item;
SHOW COLUMNS FROM course_apply WHERE Field IN ('audit_teacher_id', 'audit_time', 'audit_remark');

SELECT '--- 计数回填结果：current_students 应等于报名数 ---' AS check_item;
SELECT c.course_id,
       c.course_name,
       c.max_students,
       c.current_students,
       (SELECT COUNT(*) FROM course_apply a
         WHERE a.course_id = c.course_id AND a.audit_status IN (0, 1)) AS real_count
FROM course c
ORDER BY c.course_id;

SELECT '--- 校验：是否存在计数与实际不符的课程（应为空）---' AS check_item;
SELECT c.course_id, c.current_students,
       (SELECT COUNT(*) FROM course_apply a
         WHERE a.course_id = c.course_id AND a.audit_status IN (0, 1)) AS real_count
FROM course c
HAVING current_students <> real_count;
