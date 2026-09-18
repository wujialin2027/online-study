-- ============================================================
-- migrate_v1.6.sql
-- 本次改造两处表结构 + 一次历史数据回填。
--
-- 现状说明（执行前已核对过库）：
--   homework_submit 已经有 score / score_comment / score_teacher_id / score_time
--   forum_post      已经有 edit_time
--   即表结构已经就绪，缺的是 Java 实体字段与业务代码没用上它们。
--
-- 所以本脚本只做两件可重复执行的事：备份 + 回填脏数据。
-- 有 12 条「correct_status=1 但 score 为空」的提交，需要从 score 表把
-- 课程分补回去，否则学员会看到「明明批过却显示待批改」。
-- ============================================================

-- ---------- 0. 备份（幂等，已存在则跳过） ----------
CREATE TABLE IF NOT EXISTS homework_submit_bak_20260918 AS SELECT * FROM homework_submit;
CREATE TABLE IF NOT EXISTS forum_post_bak_20260918 AS SELECT * FROM forum_post;

-- ---------- 1. 回填「已批改但没有分数」的提交 ----------
-- 老数据里课程级的分数无法还原成「哪一次作业得的」，只能把课程分补到该课程下
-- 缺分的已批改提交上。WHERE 里带 hs.score IS NULL，重复执行不会覆盖新数据。
UPDATE homework_submit hs
    JOIN homework h ON h.homework_id = hs.homework_id
    JOIN score s ON s.student_id = hs.student_id AND s.course_id = h.course_id
SET hs.score = s.homework_score,
    hs.score_comment = s.score_comment,
    hs.score_teacher_id = s.score_teacher_id,
    hs.score_time = s.score_time
WHERE hs.correct_status = 1
  AND hs.score IS NULL
  AND s.homework_score IS NOT NULL;

-- ---------- 2. 修正「标记已批改、却从未写入分数」的脏数据 ----------
-- 第 1 步回填后仍然没有分数的，说明 score 表里根本没有该学员该课程的记录 ——
-- 属于「批改动作只改了状态、没落分数」。没有分数的批改不算批改，退回未批改，
-- 让教师重新批一遍。执行后「已批改」与「有分数」两条计数应完全相等。
UPDATE homework_submit SET correct_status = 0
WHERE correct_status = 1 AND score IS NULL;

-- ---------- 3. 清掉「考试分 = 0」造成的假总评 ----------
-- score.exam_score 里的 0 是「没录入」而不是「考了 0 分」（最初的录入界面写死 0）。
-- 老代码按 (作业分 + 考试分) / 2 算总评，于是 90 分的作业被显示成总评 45 分。
-- 这里把 0 规范成 NULL（语义明确），并按「未录入考试分 → 总评 = 作业分」重算。
UPDATE score
SET exam_score = NULL,
    total_score = homework_score
WHERE exam_score = 0
  AND homework_score IS NOT NULL;

-- ---------- 3b. 按同一套规则重算全部总评 ----------
-- 与 ScoreCalculator.total 保持一致：没录考试分 → 总评 = 作业分；录了 → 各占一半。
UPDATE score
SET total_score = CASE
    WHEN exam_score IS NULL OR exam_score <= 0 THEN homework_score
    ELSE ROUND(homework_score * 0.5 + exam_score * 0.5)
END;

-- ---------- 4. 核对 ----------
SELECT COUNT(*) AS 提交总数,
       SUM(score IS NOT NULL) AS 已有分数,
       SUM(correct_status = 1) AS 已批改,
       SUM(correct_status = 1 AND score IS NULL) AS 已批改但无分数
FROM homework_submit;

-- 总评体检：没有考试分时，总评应当等于作业分；有考试分时应当等于两者均值。
SELECT COUNT(*) AS 总评与作业分不符的异常条数
FROM score
WHERE exam_score IS NULL
  AND homework_score IS NOT NULL
  AND total_score <> homework_score;
