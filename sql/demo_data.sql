-- ============================================================================
--  demo_data.sql —— 演示数据（课程 + 报名）
--
--  用途：本地开发 / 演示 / 面试时展示时把数据补齐，让分页、搜索、首页图表
--        都能看到真实效果（原来只有 6 门课、6 条报名，图表基本是空的）。
--
--  执行方式（幂等：重复执行不会插入重复课程）：
--      cd /d D:\d-projects\online_study
--      mysql -uroot -p --default-character-set=utf8mb4 online_study < sql\demo_data.sql
--
--  ⚠️ 只做 INSERT，不删除、不修改任何现有数据。
--  ⚠️ 生产环境不要执行本脚本。
-- ============================================================================

USE online_study;

-- ----------------------------------------------------------------------------
-- 1. 课程：补充 30 门（面向企业技术培训场景）
--
--    · 大部分 audit_status = 1（已通过，学员可选）
--    · 留几门 audit_status = 0（待审核，给管理员演示审核流程）
--    · 留 1 门 audit_status = 2（已驳回，带驳回原因）
--    · max_students 各不相同：0 表示不限，小额数字用于演示"名额已满"
-- ----------------------------------------------------------------------------
INSERT INTO course
    (course_name, course_intro, train_cycle, apply_cond, publish_teacher_id,
     max_students, audit_status, audit_admin_id, audit_time, audit_remark, publish_time)
VALUES
    -- ============ Java 后端方向 ============
    ('Java 基础语法与面向对象', '从变量、流程控制讲到类与对象、封装继承多态，配套 30 道上机练习，适合零基础入门。', '2 个月', '零基础可学，自备电脑', 1, 50, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 60 DAY)),
    ('Java 集合框架深入', 'List / Set / Map 的实现原理与源码分析，重点讲 ArrayList 扩容、HashMap 红黑树、并发安全集合。', '3 周', '需掌握 Java 基础语法', 1, 40, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 55 DAY)),
    ('Java 多线程与并发编程', '线程生命周期、锁机制、JUC 工具类、线程池参数调优，含秒杀场景的并发实战。', '1 个月', '熟悉 Java 基础与集合', 1, 30, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 50 DAY)),
    ('JVM 内存模型与性能调优', '堆栈结构、垃圾回收算法、常用 GC 收集器对比、OOM 排查与 jstack/jmap 实战。', '3 周', '有 Java 项目经验', 1, 25, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 48 DAY)),
    ('Spring 核心原理：IoC 与 AOP', '手写简易 IoC 容器理解依赖注入，讲清 AOP 动态代理的两种实现与事务的底层原理。', '1 个月', '需掌握 Java 反射与注解', 1, 45, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 45 DAY)),
    ('Spring Boot 快速开发实战', '自动配置原理、Starter 机制、参数校验、全局异常处理、多环境配置，从零搭一个 REST 服务。', '1 个月', '熟悉 Spring 基础', 1, 60, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 42 DAY)),
    ('Spring Security 与权限设计', '认证与授权流程、过滤器链原理、JWT 无状态鉴权、接口级权限控制与方法注解。', '3 周', '熟悉 Spring Boot', 1, 35, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 40 DAY)),
    ('MyBatis 与 MyBatis-Plus 实战', '动态 SQL、条件构造器、分页插件、多表关联映射、SQL 日志与性能分析。', '2 周', '熟悉 Java 与 SQL', 1, 50, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 38 DAY)),

    -- ============ 数据库与中间件 ============
    ('MySQL 索引原理与 SQL 优化', 'B+ 树索引结构、执行计划分析、慢查询定位、最左前缀原则与索引失效场景。', '3 周', '会写基本 SQL', 2, 40, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 35 DAY)),
    ('MySQL 事务与锁机制', 'ACID 与隔离级别、MVCC 实现、行锁与间隙锁、死锁分析与排查。', '2 周', '需先学 MySQL 索引课程', 2, 35, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 33 DAY)),
    ('Redis 缓存设计与实践', '五种基本数据类型与典型场景、缓存穿透/击穿/雪崩的解决方案、分布式锁、持久化策略。', '3 周', '熟悉 Java 基础', 2, 45, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 30 DAY)),
    ('消息队列入门：RabbitMQ', '交换机类型与路由策略、消息可靠投递、幂等消费、死信队列与延迟队列。', '2 周', '了解 Spring Boot', 2, 30, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 28 DAY)),
    ('Elasticsearch 全文检索入门', '倒排索引原理、分词器配置、复合查询 DSL、与 MySQL 的数据同步方案。', '2 周', '熟悉 Java 与 REST 接口', 2, 25, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 26 DAY)),

    -- ============ 运维与工程能力 ============
    ('Docker 容器化入门到实战', '镜像与容器、Dockerfile 编写、数据卷与网络、Docker Compose 编排多服务应用。', '2 周', '了解 Linux 基本命令', 2, 40, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 24 DAY)),
    ('Linux 常用命令与服务器运维', '文件与权限、进程与端口、日志排查、Shell 脚本入门、常见线上问题定位思路。', '3 周', '零基础可学', 2, 50, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 22 DAY)),
    ('Nginx 配置与反向代理', '静态资源服务、反向代理与负载均衡、HTTPS 配置、跨域与缓存策略。', '1 周', '了解 Linux 基础', 2, 30, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 20 DAY)),
    ('Git 版本控制与团队协作', '分支模型、冲突解决、rebase 与 merge 的区别、PR 流程与代码评审规范。', '1 周', '零基础可学', 2, 60, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 18 DAY)),
    ('Maven 项目构建与依赖管理', '坐标与仓库、依赖传递与冲突解决、生命周期与插件、多模块项目拆分。', '1 周', '了解 Java 基础', 2, 50, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 16 DAY)),

    -- ============ 计算机基础 ============
    ('数据结构与算法基础', '数组、链表、栈、队列、树、图，配合排序与查找算法，含 100 道手写题。', '2 个月', '掌握一门编程语言', 1, 0, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 14 DAY)),
    ('计算机网络基础', 'HTTP / TCP / IP 分层模型、三次握手与四次挥手、HTTPS 加密流程、抓包分析。', '1 个月', '零基础可学', 1, 40, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 12 DAY)),
    ('操作系统原理', '进程与线程、内存管理、文件系统、IO 模型与零拷贝，配合 Linux 实操。', '1 个月', '零基础可学', 1, 35, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 10 DAY)),
    ('设计模式 23 讲', '创建型 / 结构型 / 行为型模式逐个拆解，重点讲 Spring 与 JDK 中的实际应用。', '1 个月', '熟悉 Java 面向对象', 1, 40, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 9 DAY)),

    -- ============ 前端方向 ============
    ('前端三件套：HTML / CSS / JavaScript', '标签与语义化、盒模型与布局、DOM 操作与事件、ES6 常用语法。', '2 个月', '零基础可学', 2, 50, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 8 DAY)),
    ('Vue 3 组合式 API 实战', 'setup 语法糖、响应式原理 ref 与 reactive、组件通信、生命周期、自定义指令。', '1 个月', '需掌握 JavaScript 基础', 2, 45, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 7 DAY)),
    ('Element Plus 组件库实战', '常用组件的组合用法、表单校验、表格与分页、弹窗与消息通知、主题定制。', '2 周', '需先学 Vue 3', 2, 40, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 6 DAY)),
    ('Axios 与前后端联调实战', '请求封装与拦截器、统一响应处理、Token 携带与续期、跨域问题的排查思路。', '1 周', '了解 Vue 与 REST 接口', 2, 35, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 5 DAY)),
    ('ECharts 数据可视化实战', '图表配置项体系、柱状/折线/饼图与地图、大数据量渲染优化、大屏适配方案。', '2 周', '掌握 JavaScript 基础', 2, 30, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 4 DAY)),

    -- ============ AI 应用方向 ============
    ('Python 编程入门', '语法基础、常用数据结构、函数与模块、虚拟环境与包管理，含 20 个练习。', '1 个月', '零基础可学', 1, 60, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 3 DAY)),
    ('大模型应用开发与 RAG 入门', '提示词工程、大模型 API 调用、向量化与语义检索、RAG 检索增强问答的完整链路。', '3 周', '需掌握 Java 或 Python 基础', 1, 20, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 2 DAY)),
    ('软件测试与自动化', '测试用例设计方法、接口测试与 Postman、单元测试与覆盖率、自动化测试入门。', '2 周', '零基础可学', 2, 40, 1, 1, NOW(), NULL, DATE_SUB(NOW(), INTERVAL 1 DAY)),
    ('项目管理与敏捷开发', '需求拆解与排期、Scrum 流程、任务看板、版本发布与复盘方法。', '1 周', '零基础可学', 2, 50, 0, NULL, NULL, NULL, NOW()),

    -- 待审核的课程（给管理员演示审核流程用）
    ('Kubernetes 集群运维实战', 'Pod 与 Deployment、Service 与 Ingress、配置与密钥管理、集群监控与日志收集。', '1 个月', '需掌握 Docker 基础', 1, 25, 0, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY)),
    ('微服务架构设计入门', '服务拆分原则、注册中心与配置中心、网关与链路追踪、分布式事务方案对比。', '1 个月', '熟悉 Spring Boot 与 Docker', 2, 30, 0, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY)),

    -- 已驳回的课程（给教师演示"查看驳回原因后修改再提交"的流程）
    ('区块链原理与智能合约', '课程大纲与配套资料尚未准备完成，暂不开放。', '1 个月', '零基础可学', 2, 30, 2, 1, NOW(), '课程介绍过于简略，缺少明确的学习目标与配套资料，请补充后重新提交。', DATE_SUB(NOW(), INTERVAL 5 DAY));

-- ----------------------------------------------------------------------------
-- 2. 报名记录
--
--    数量不大不小：让"课程报名排行""报名趋势""报名审核"几个图表有真实形状。
--    报名时间用 (course_id % 7) 天前错开，这样"近 7 天报名趋势"折线图
--    不会缩成一根柱子。
--    状态混合：大部分已通过，少量待审核（给教师端"报名审核"演示用）。
-- ----------------------------------------------------------------------------

-- 学员1（王同学）：报名 10 门，其中约 1/6 为待审核
--
-- 用 NOT EXISTS 排除"已经报过的课"，而不是用 course_id 范围去猜哪些是新课程 ——
-- 后者会把旧课程也算进来，撞上 uk_student_course 唯一索引直接报错。
INSERT INTO course_apply (student_id, course_id, apply_time, audit_status, audit_teacher_id)
SELECT 1, c.course_id, DATE_SUB(NOW(), INTERVAL (c.course_id % 7) DAY),
       IF(c.course_id % 6 = 0, 0, 1), 1
FROM course c
WHERE c.audit_status = 1
  AND NOT EXISTS (SELECT 1 FROM course_apply a
                   WHERE a.student_id = 1 AND a.course_id = c.course_id)
LIMIT 10;

-- 学员2（赵同学）：报名 8 门，其中约 1/4 为待审核
INSERT INTO course_apply (student_id, course_id, apply_time, audit_status, audit_teacher_id)
SELECT 2, c.course_id, DATE_SUB(NOW(), INTERVAL (c.course_id % 5) DAY),
       IF(c.course_id % 4 = 0, 0, 1), 1
FROM course c
WHERE c.audit_status = 1
  AND NOT EXISTS (SELECT 1 FROM course_apply a
                   WHERE a.student_id = 2 AND a.course_id = c.course_id)
LIMIT 8;

-- 学员3（张三）：报名 6 门，全部已通过
INSERT INTO course_apply (student_id, course_id, apply_time, audit_status, audit_teacher_id)
SELECT 3, c.course_id, DATE_SUB(NOW(), INTERVAL (c.course_id % 3) DAY), 1, 1
FROM course c
WHERE c.audit_status = 1
  AND NOT EXISTS (SELECT 1 FROM course_apply a
                   WHERE a.student_id = 3 AND a.course_id = c.course_id)
LIMIT 6;

-- ----------------------------------------------------------------------------
-- 3. 重算已占名额
--
--    口径必须与代码一致：audit_status IN (0,1) 视为占用名额（2 已驳回不占）。
--    直接复用 migrate_v1.2.sql 的回填逻辑，保证 course.current_students
--    与 course_apply 的实际数量始终对得上。
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
-- 4. 验证
-- ----------------------------------------------------------------------------
SELECT '--- 课程总数 / 待审核数 ---' AS check_item;
SELECT COUNT(*) AS 课程总数,
       SUM(audit_status = 1) AS 已通过,
       SUM(audit_status = 0) AS 待审核,
       SUM(audit_status = 2) AS 已驳回
FROM course;

SELECT '--- 报名总数 ---' AS check_item;
SELECT COUNT(*) AS 报名总数, SUM(audit_status = 0) AS 待审核报名 FROM course_apply;

SELECT '--- 近 7 天报名趋势（应有多个日期）---' AS check_item;
SELECT DATE_FORMAT(apply_time, '%Y-%m-%d') AS d, COUNT(*) AS c
FROM course_apply
WHERE apply_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)
GROUP BY DATE_FORMAT(apply_time, '%Y-%m-%d')
ORDER BY d;

SELECT '--- 计数校验：current_students 与实际报名数不符的课程（应为空）---' AS check_item;
SELECT * FROM (
    SELECT c.course_id, c.course_name, c.current_students,
           (SELECT COUNT(*) FROM course_apply a
             WHERE a.course_id = c.course_id AND a.audit_status IN (0, 1)) AS real_count
    FROM course c
) t WHERE t.current_students <> t.real_count;

-- ----------------------------------------------------------------------------
-- 4. 制造「满员 / 即将满」的课程（用于首页「报名进度」演示）
--
--    教师首页的报名进度条有三种状态，报名率不拉开就分不出来：
--      · full      已满     —— 报名数 = 名额上限
--      · warning   即将满   —— 报名率 ≥ 80%，或只剩 ≤ 2 个名额
--      · normal    正常
--    原数据里最高报名率只有 10%，21 门课全是同一个颜色，等于没有重点。
--
--    ⚠️ 本节是 UPDATE（调整名额上限），与本脚本开头「只做 INSERT」的说明不同。
--       不想改这几门课的名额，跳过本节即可。
-- ----------------------------------------------------------------------------
UPDATE course SET max_students = 3 WHERE course_name = 'Java 集合框架深入';       -- 3/3 → 已满
UPDATE course SET max_students = 4 WHERE course_name = 'Java 多线程与并发编程';   -- 3/4 → 剩 1 个名额
UPDATE course SET max_students = 3 WHERE course_name = 'JVM 内存模型与性能调优';  -- 2/3 → 剩 1 个名额

SELECT '--- 进度条三态校验（应分别看到 已满 / 即将满 / 正常）---' AS check_item;
SELECT course_name AS 课程, current_students AS 已报, max_students AS 上限,
       CASE WHEN max_students = 0 THEN '不限'
            ELSE CONCAT(ROUND(current_students * 100 / max_students), '%') END AS 报名率,
       CASE WHEN max_students = 0 THEN 'unlimited 不限名额'
            WHEN current_students >= max_students THEN 'full 已满'
            WHEN current_students * 100 / max_students >= 80
              OR max_students - current_students <= 2 THEN 'warning 即将满'
            ELSE 'normal 正常' END AS 状态
FROM course
WHERE course_id IN (14, 15, 16)
   OR max_students = 0;
