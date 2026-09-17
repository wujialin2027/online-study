-- ============================================================================
--  demo_data_v2.sql —— 演示数据（第二批：账号 / 选课 / 成绩 / 作业）
--
--  前置：先执行 demo_data.sql（补齐课程与基础报名）
--
--  用途：让首页与各列表页有足够真实感的数据 ——
--        · 5 名教师、10 名学员；课程按技术方向归属到不同教师
--        · 学员选课数量差异明显（从 1 门到十几门），报名时间分散在近 20 天
--        · 成绩覆盖及格线两侧，评语按分数分档（不再是清一色的"加油"）
--        · 作业与提交记录，让「待完成作业 / 待批改作业」有真实数字
--
--  执行方式（幂等，可重复执行）：
--      cd /d D:\d-projects\online_study
--      mysql -uroot -p --default-character-set=utf8mb4 online_study < sql\demo_data_v2.sql
--
--  ⚠️ 生产环境不要执行本脚本。
-- ============================================================================

USE online_study;

-- 新账号的密码统一沿用现有账号的密码（都是 123456），直接复制已有哈希，
-- 避免在脚本里硬编码 BCrypt 串
SET @teacher_pwd = (SELECT teacher_pwd FROM teacher WHERE teacher_id = 1);
SET @student_pwd = (SELECT student_pwd FROM student WHERE student_id = 1);

-- ----------------------------------------------------------------------------
-- 1. 教师：从 2 名扩充到 5 名（按技术方向分工）
-- ----------------------------------------------------------------------------
INSERT INTO teacher
    (teacher_account, teacher_pwd, teacher_name, teacher_phone, teacher_email,
     teacher_org, register_time, account_status)
SELECT * FROM (
    SELECT 'teacher3' AS acct, @teacher_pwd AS pwd, '陈老师' AS nm, '13900000003' AS ph,
           'teacher3@example.com' AS em, '软件工程学院' AS org,
           DATE_SUB(NOW(), INTERVAL 200 DAY) AS rt, 1 AS st
    UNION ALL
    SELECT 'teacher4', @teacher_pwd, '刘老师', '13900000004',
           'teacher4@example.com', '计算机学院', DATE_SUB(NOW(), INTERVAL 180 DAY), 1
    UNION ALL
    SELECT 'teacher5', @teacher_pwd, '周老师', '13900000005',
           'teacher5@example.com', '人工智能学院', DATE_SUB(NOW(), INTERVAL 160 DAY), 1
) t
WHERE NOT EXISTS (SELECT 1 FROM teacher x WHERE x.teacher_account = t.acct);

-- ----------------------------------------------------------------------------
-- 2. 学员：从 3 名扩充到 10 名
-- ----------------------------------------------------------------------------
INSERT INTO student
    (student_account, student_pwd, student_name, student_phone, student_email,
     register_time, account_status)
SELECT * FROM (
    SELECT 'student4' AS acct, @student_pwd AS pwd, '孙同学' AS nm, '13800000004' AS ph,
           'student4@example.com' AS em, DATE_SUB(NOW(), INTERVAL 150 DAY) AS rt, 1 AS st
    UNION ALL SELECT 'student5', @student_pwd, '周同学', '13800000005', 'student5@example.com', DATE_SUB(NOW(), INTERVAL 140 DAY), 1
    UNION ALL SELECT 'student6', @student_pwd, '吴同学', '13800000006', 'student6@example.com', DATE_SUB(NOW(), INTERVAL 130 DAY), 1
    UNION ALL SELECT 'student7', @student_pwd, '郑同学', '13800000007', 'student7@example.com', DATE_SUB(NOW(), INTERVAL 120 DAY), 1
    UNION ALL SELECT 'student8', @student_pwd, '冯同学', '13800000008', 'student8@example.com', DATE_SUB(NOW(), INTERVAL 110 DAY), 1
    UNION ALL SELECT 'student9', @student_pwd, '陈同学', '13800000009', 'student9@example.com', DATE_SUB(NOW(), INTERVAL 100 DAY), 1
    UNION ALL SELECT 'student10', @student_pwd, '褚同学', '13800000010', 'student10@example.com', DATE_SUB(NOW(), INTERVAL 90 DAY), 1
) t
WHERE NOT EXISTS (SELECT 1 FROM student x WHERE x.student_account = t.acct);

-- 取出 5 名教师的 ID（不写死数字，保证脚本可重复执行）
SET @t1 = (SELECT teacher_id FROM teacher WHERE teacher_account = 'teacher1');
SET @t2 = (SELECT teacher_id FROM teacher WHERE teacher_account = 'teacher2');
SET @t3 = (SELECT teacher_id FROM teacher WHERE teacher_account = 'teacher3');
SET @t4 = (SELECT teacher_id FROM teacher WHERE teacher_account = 'teacher4');
SET @t5 = (SELECT teacher_id FROM teacher WHERE teacher_account = 'teacher5');

-- ----------------------------------------------------------------------------
-- 3. 课程归属：按技术方向重新分给 5 名教师
--    （原来 42 门课全部堆在 2 名教师名下，21 + 21）
-- ----------------------------------------------------------------------------
-- 张老师：Java 语言核心 + 计算机基础
UPDATE course SET publish_teacher_id = @t1
WHERE course_id IN (1, 8, 13, 14, 15, 16, 31, 32, 33);
-- 李老师：Spring 生态与工程工具
UPDATE course SET publish_teacher_id = @t2
WHERE course_id IN (2, 17, 18, 19, 20, 29, 30, 34);
-- 陈老师：数据库与中间件
UPDATE course SET publish_teacher_id = @t3
WHERE course_id IN (21, 22, 23, 24, 25, 42, 43);
-- 刘老师：运维与工程化
UPDATE course SET publish_teacher_id = @t4
WHERE course_id IN (7, 12, 26, 27, 28, 44, 45, 46);
-- 周老师：前端与 AI 应用
UPDATE course SET publish_teacher_id = @t5
WHERE course_id IN (3, 10, 11, 35, 36, 37, 38, 39, 40, 41);

-- ----------------------------------------------------------------------------
-- 4. 选课：让每个学员选到不同的课，且选课数量拉开差距
--
--    选课规则用「取模散列」而不是随机数：
--      · MOD(course_id * 17 + student_id * 7, 100) < 阈值 → 选中
--      · 阈值随 student_id 递增（5→10→15…），于是学号越大选课越多，
--        自然形成"有人只选 1~2 门、有人选十几门"的差异
--    这样做的好处是**每次执行结果完全一致**（可预测、可复现），
--    而随机数会导致每次跑出来的数据都不一样，不利于演示。
-- ----------------------------------------------------------------------------
INSERT INTO course_apply
    (student_id, course_id, apply_time, audit_status, audit_teacher_id, audit_time, audit_remark)
SELECT s.student_id, c.course_id,
       DATE_SUB(NOW(), INTERVAL MOD(c.course_id * 3 + s.student_id * 5, 20) DAY),
       -- 约 1/9 的报名留作「待审核」，给教师演示审核流程
       IF(MOD(c.course_id + s.student_id * 2, 9) = 0, 0, 1),
       IF(MOD(c.course_id + s.student_id * 2, 9) = 0, NULL, c.publish_teacher_id),
       IF(MOD(c.course_id + s.student_id * 2, 9) = 0, NULL, NOW()),
       NULL
FROM student s
JOIN course c ON c.audit_status = 1
WHERE MOD(c.course_id * 17 + s.student_id * 7, 100) < (5 + s.student_id * 5)
  AND NOT EXISTS (SELECT 1 FROM course_apply a
                   WHERE a.student_id = s.student_id AND a.course_id = c.course_id);

-- ----------------------------------------------------------------------------
-- 5. 成绩：为「已通过审核」的报名生成成绩
--
--    分数用确定性公式算出，取值范围 45~99，因此必然覆盖及格线两侧
--    （首页成绩图的参考线才有意义：能直接看出哪些科目没及格）。
--    只给约 6 成的已通过课程出成绩 —— 真实场景里总有课程还没结课。
--
--    ⚠️ 先清掉本脚本上次生成的成绩（按固定评语文案识别，不动手工录入的数据），
--       否则改了下面的规则也只会跳过已存在的行、旧结果不会更新。
-- ----------------------------------------------------------------------------
DELETE FROM score
 WHERE score_comment IN (
     '基础非常扎实，思路清晰，继续保持。',
     '掌握情况良好，注意细节处的严谨性。',
     '基本掌握，建议多动手练习加以巩固。',
     '刚过及格线，薄弱环节需要重点补一补。',
     '未达及格线，建议重做作业并向老师请教。'
 );

INSERT INTO score
    (student_id, course_id, homework_score, exam_score, total_score,
     score_comment, score_teacher_id, score_time)
SELECT t.student_id, t.course_id,
       LEAST(100, t.total + 6) AS homework_score,
       GREATEST(0, t.total - 4) AS exam_score,
       t.total,
       CASE
           WHEN t.total >= 90 THEN '基础非常扎实，思路清晰，继续保持。'
           WHEN t.total >= 80 THEN '掌握情况良好，注意细节处的严谨性。'
           WHEN t.total >= 70 THEN '基本掌握，建议多动手练习加以巩固。'
           WHEN t.total >= 60 THEN '刚过及格线，薄弱环节需要重点补一补。'
           ELSE '未达及格线，建议重做作业并向老师请教。'
       END,
       t.tid,
       DATE_SUB(NOW(), INTERVAL MOD(t.course_id, 15) DAY)
FROM (
    SELECT a.student_id, a.course_id, c.publish_teacher_id AS tid,
           -- ⚠️ 系数必须与模数互质：gcd(11, 55) = 11，
           --    用 course_id * 11 会让 MOD 55 只剩 0/11/22/33/44 五种取值，分数分布极不均匀。
           --    改用 7（gcd(7,55) = 1）。
           LEAST(99, GREATEST(40, 45 + MOD(a.student_id * 13 + a.course_id * 7, 55))) AS total
    FROM course_apply a
    JOIN course c ON c.course_id = a.course_id
    WHERE a.audit_status = 1
      -- ⚠️ 同理：gcd(5, 10) = 5，course_id * 5 对 MOD 10 只有 0/5 两种取值，
      --    会让"只有一半课程出成绩"（曾有学员 16 门课只出 5 条成绩）。改用 7。
      AND MOD(a.student_id * 3 + a.course_id * 7, 10) < 6
) t
WHERE NOT EXISTS (SELECT 1 FROM score sc
                   WHERE sc.student_id = t.student_id AND sc.course_id = t.course_id);

-- ----------------------------------------------------------------------------
-- 6. 作业：给几门核心课补上正常命名的作业
--    （原有作业名多为测试残留，如「123」「作业0917」，这里补的是可展示的）
-- ----------------------------------------------------------------------------
INSERT INTO homework
    (course_id, homework_name, homework_content, deadline, publish_teacher_id, publish_time)
SELECT * FROM (
    SELECT 13 AS cid, 'Java 基础语法练习（一）' AS nm,
           '完成教材第 3 章课后习题 1~10 题，提交代码文件与运行结果截图。' AS ct,
           DATE_ADD(NOW(), INTERVAL 5 DAY) AS dl, @t1 AS tid,
           DATE_SUB(NOW(), INTERVAL 6 DAY) AS pt
    UNION ALL SELECT 14, '集合框架源码分析报告',
           '从 ArrayList / HashMap / ConcurrentHashMap 中任选一个，分析其底层数据结构与扩容机制，提交 800 字以上报告。',
           DATE_ADD(NOW(), INTERVAL 8 DAY), @t1, DATE_SUB(NOW(), INTERVAL 4 DAY)
    UNION ALL SELECT 15, '多线程实战：生产者消费者模型',
           '用线程池实现一个生产者消费者模型，要求支持多生产者多消费者，提交完整代码与压测结果。',
           DATE_ADD(NOW(), INTERVAL 10 DAY), @t1, DATE_SUB(NOW(), INTERVAL 3 DAY)
    UNION ALL SELECT 16, 'JVM 调优实验报告',
           '模拟内存溢出场景，用 jmap / jstack 定位问题并给出调优方案，提交排查过程记录。',
           DATE_ADD(NOW(), INTERVAL 12 DAY), @t1, DATE_SUB(NOW(), INTERVAL 2 DAY)
    UNION ALL SELECT 18, 'RESTful 接口开发作业',
           '基于 Spring Boot 实现一套图书管理的增删改查接口，要求统一返回格式与全局异常处理。',
           DATE_ADD(NOW(), INTERVAL 7 DAY), @t2, DATE_SUB(NOW(), INTERVAL 5 DAY)
    UNION ALL SELECT 21, '慢查询定位与索引优化练习',
           '给定 3 条慢 SQL，用 EXPLAIN 分析执行计划并建立合适索引，对比优化前后的耗时。',
           DATE_ADD(NOW(), INTERVAL 6 DAY), @t3, DATE_SUB(NOW(), INTERVAL 4 DAY)
    UNION ALL SELECT 27, 'Linux 环境搭建实验',
           '在虚拟机中完成 JDK + MySQL + Nginx 的环境搭建，提交关键步骤命令与配置文件。',
           DATE_ADD(NOW(), INTERVAL 9 DAY), @t4, DATE_SUB(NOW(), INTERVAL 3 DAY)
    UNION ALL SELECT 36, 'Vue 3 组合式 API 重构作业',
           '把给定的选项式 API 页面改写成组合式 API，抽离可复用组合函数并说明设计思路。',
           DATE_ADD(NOW(), INTERVAL 7 DAY), @t5, DATE_SUB(NOW(), INTERVAL 2 DAY)
) t
WHERE NOT EXISTS (SELECT 1 FROM homework h WHERE h.homework_name = t.nm);

-- ----------------------------------------------------------------------------
-- 6b. 给每一门「已通过审核」的课程都补一次课后作业
--
--     为什么需要：作业只挂在少数几门课上时，学员首页的「待完成作业」
--     会出现 0 —— 因为他报的课恰好都没留作业，卡片就失去意义了。
--     真实平台每门课都会有作业，这里按课程名批量生成，保证每个学员都有待办。
-- ----------------------------------------------------------------------------
INSERT INTO homework
    (course_id, homework_name, homework_content, deadline, publish_teacher_id, publish_time)
SELECT c.course_id,
       CONCAT(c.course_name, ' · 课后作业（一）'),
       '按要求完成本次课后练习并提交，代码文件与运行结果截图请一并上传。',
       DATE_ADD(NOW(), INTERVAL (3 + MOD(c.course_id, 10)) DAY),
       c.publish_teacher_id,
       DATE_SUB(NOW(), INTERVAL (5 + MOD(c.course_id, 10)) DAY)
FROM course c
WHERE c.audit_status = 1
  AND NOT EXISTS (
      SELECT 1 FROM homework h
       WHERE h.course_id = c.course_id
         AND h.homework_name LIKE CONCAT(c.course_name, ' · %')
  );

-- ----------------------------------------------------------------------------
-- 7. 作业提交：提交率按学员分档
--      · 少部分学员（学号能被 5 整除）**全部提交** —— 对应"学霸"画像
--      · 其余学员提交 40% ~ 60% —— 更接近真实班级的完成率
--    因此首页「待完成作业」也会呈现差异，而不是清一色相同数字。
--
--    ⚠️ 先清掉本脚本上次生成的提交记录（按固定文案识别，不影响手工提交的数据），
--       否则改了下面的规则也只会跳过已存在的行、旧结果不会更新。
-- ----------------------------------------------------------------------------
DELETE FROM homework_submit
 WHERE submit_content LIKE '已按作业要求完成%'
    OR submit_content = '已完成，说明见下方文字。';

INSERT INTO homework_submit
    (student_id, homework_id, submit_content, submit_file, submit_time, correct_status)
SELECT s.student_id, h.homework_id,
       -- 约一半的提交带附件，文案也随之变化（没附件却说"见附件"会让人找不到）
       IF(MOD(h.homework_id * 3 + s.student_id, 2) = 0,
          '已按作业要求完成，代码与运行截图见附件。',
          '已完成，说明见下方文字。'),
       -- 附件指向 uploads 目录下的示例图片（由 scripts 生成，见 README 说明），
       -- 让批改面板的「在线预览」有真实内容可看
       CASE WHEN MOD(h.homework_id * 3 + s.student_id, 2) = 0 THEN
           CASE MOD(h.homework_id + s.student_id, 5)
               WHEN 0 THEN '/uploads/demo-hw-java-basic.png'
               WHEN 1 THEN '/uploads/demo-hw-collection.png'
               WHEN 2 THEN '/uploads/demo-hw-thread.png'
               WHEN 3 THEN '/uploads/demo-hw-jvm.png'
               ELSE '/uploads/demo-hw-vue.png'
           END
       ELSE NULL END,
       -- 同理：gcd(2, 8) = 2，*2 会让天偏移只落在偶数上，改用与 8 互质的 3
       DATE_SUB(NOW(), INTERVAL MOD(h.homework_id * 3 + s.student_id, 8) DAY),
       -- 一半已批改、一半待批改，教师端「待批改作业」才有东西可看
       MOD(h.homework_id + s.student_id, 2)
FROM student s
JOIN course_apply a ON a.student_id = s.student_id AND a.audit_status = 1
JOIN homework h ON h.course_id = a.course_id
-- 提交率分档：学号能被 5 整除的学员全交，其余交 40% / 50% / 60%
-- ⚠️ 取模的系数必须与模数互质：3 和 7 都与 10 互质，分布才均匀
--    （写成 MOD(homework_id * 2 + ..., 10) 就会退化 —— 2 与 10 不互质，只有偶数取值）
WHERE MOD(h.homework_id * 3 + s.student_id * 7, 10)
      < CASE WHEN MOD(s.student_id, 5) = 0 THEN 10
             ELSE 4 + MOD(s.student_id, 3) END
  AND NOT EXISTS (SELECT 1 FROM homework_submit hs
                   WHERE hs.student_id = s.student_id AND hs.homework_id = h.homework_id);

SELECT '--- 各学员作业提交情况（应交 / 已交 / 待完成）---' AS check_item;
SELECT s.student_name AS 学员,
       COUNT(DISTINCT h.homework_id) AS 应交,
       COUNT(DISTINCT hs.homework_id) AS 已交,
       COUNT(DISTINCT h.homework_id) - COUNT(DISTINCT hs.homework_id) AS 待完成,
       SUM(hs.submit_file IS NOT NULL) AS 带附件
FROM student s
JOIN course_apply a ON a.student_id = s.student_id AND a.audit_status = 1
JOIN homework h ON h.course_id = a.course_id
LEFT JOIN homework_submit hs ON hs.student_id = s.student_id AND hs.homework_id = h.homework_id
GROUP BY s.student_id ORDER BY 已交 DESC;

-- ----------------------------------------------------------------------------
-- 8. 重算 current_students（报名口径与业务代码一致：待审核 + 已通过）
-- ----------------------------------------------------------------------------
UPDATE course c SET c.current_students = (
    SELECT COUNT(*) FROM course_apply a
     WHERE a.course_id = c.course_id AND a.audit_status IN (0, 1)
);

-- ----------------------------------------------------------------------------
-- 9. 让三门课分别呈现「已满 / 即将满」，供首页进度条演示
--    用 current_students 反推名额上限，避免写死数字导致「报名数 > 名额上限」
--    （本节会覆盖 demo_data.sql 第 4 节里对这几门课的名额设置）
-- ----------------------------------------------------------------------------
UPDATE course SET max_students = current_students
WHERE course_name = 'Java 集合框架深入' AND current_students > 0;

UPDATE course SET max_students = current_students + 1
WHERE course_name = 'Java 多线程与并发编程' AND current_students > 0;

UPDATE course SET max_students = current_students + 2
WHERE course_name = 'JVM 内存模型与性能调优' AND current_students > 0;

-- ============================================================================
--  验证查询
-- ============================================================================

SELECT '--- 账号规模 ---' AS check_item;
SELECT
    (SELECT COUNT(*) FROM teacher) AS 教师数,
    (SELECT COUNT(*) FROM student) AS 学员数,
    (SELECT COUNT(*) FROM course) AS 课程数,
    (SELECT COUNT(*) FROM course_apply) AS 报名数,
    (SELECT COUNT(*) FROM score) AS 成绩数,
    (SELECT COUNT(*) FROM homework) AS 作业数,
    (SELECT COUNT(*) FROM homework_submit) AS 提交数;

SELECT '--- 课程按教师分布（应分散在 5 名教师）---' AS check_item;
SELECT t.teacher_name AS 教师, COUNT(c.course_id) AS 课程数,
       IFNULL(SUM(c.current_students), 0) AS 报名合计
FROM teacher t LEFT JOIN course c ON c.publish_teacher_id = t.teacher_id
GROUP BY t.teacher_id ORDER BY t.teacher_id;

SELECT '--- 学员选课数量（应有明显差异）---' AS check_item;
SELECT s.student_name AS 学员, COUNT(a.apply_id) AS 选课数,
       SUM(a.audit_status = 1) AS 已通过,
       SUM(a.audit_status = 0) AS 待审核
FROM student s LEFT JOIN course_apply a ON a.student_id = s.student_id
GROUP BY s.student_id ORDER BY 选课数 DESC;

SELECT '--- 成绩分布（应覆盖 60 分及格线两侧）---' AS check_item;
SELECT
    SUM(total_score >= 90) AS 优秀,
    SUM(total_score >= 80 AND total_score < 90) AS 良好,
    SUM(total_score >= 70 AND total_score < 80) AS 中等,
    SUM(total_score >= 60 AND total_score < 70) AS 及格,
    SUM(total_score < 60) AS 不及格
FROM score;

SELECT '--- 进度条三态校验（应看到 full / warning / normal / unlimited）---' AS check_item;
SELECT course_name AS 课程, current_students AS 已报, max_students AS 上限,
       CASE WHEN max_students = 0 THEN '不限'
            ELSE CONCAT(ROUND(current_students * 100 / max_students), '%') END AS 报名率,
       CASE WHEN max_students = 0 THEN 'unlimited'
            WHEN current_students >= max_students THEN 'full'
            WHEN current_students * 100 / max_students >= 80
              OR max_students - current_students <= 2 THEN 'warning'
            ELSE 'normal' END AS 状态
FROM course
WHERE current_students > 0
-- ⚠️ 排序必须用数值表达式：上面的「报名率」是 CONCAT 出来的字符串，
-- 按字符串排序会得到 '9%' > '88%' > '100%' 这种错误顺序
ORDER BY (max_students = 0),
         CASE WHEN max_students = 0 THEN 0
              ELSE current_students * 100 / max_students END DESC
LIMIT 12;
