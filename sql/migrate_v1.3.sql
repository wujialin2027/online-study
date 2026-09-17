-- ============================================================
-- 迁移 v1.3 —— 论坛互动（点赞 / 收藏）
--
-- 背景：forum_post 表里原本就有 like_num / collect_num 两个字段，
--       但代码里既没有点赞接口，也没有记录「谁点过赞」的地方 ——
--       这两个字段只能靠初始化脚本写死，是永远不变的「死字段」。
--
-- 本次新增 forum_interaction 表，把「谁 对 哪个帖子 做过 哪类互动」落库：
--   · 唯一索引 uk_post_user_type 保证同一用户对同一帖子只能点赞一次（防刷）
--   · 取消点赞 = 删掉这条记录，可反复切换
--   · like_num / collect_num 保留为冗余计数，用原子的 UPDATE ... +1 / -1 维护，
--     避免每次列表查询都去 COUNT(*) 统计（列表接口调用频率远高于点赞）
--
-- ⚠️ 为什么不直接 COUNT(*) 而要多存一份计数：
--     列表分页一次返回 10 条帖子，若每条都关联统计互动表，
--     就是一次查询里带 2 个子查询，帖子多了会明显拖慢。
--     冗余字段 + 事务内原子自增，是读多写少场景的常规做法。
--
-- 历史帖子的 like_num / collect_num 保留原值（当作历史数据），
-- 新建的互动会在此基础上继续累加。
--
-- 执行方式：
--   mysql -uroot -p123456 online_study < sql/migrate_v1.3.sql
--   或在 IDEA 的 Database 面板里打开本文件直接运行
-- ============================================================

USE online_study;

CREATE TABLE IF NOT EXISTS forum_interaction (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '互动记录ID',
    post_id INT NOT NULL COMMENT '帖子ID',
    user_role VARCHAR(10) NOT NULL COMMENT '互动者角色（student/teacher/admin）',
    user_id INT NOT NULL COMMENT '互动者ID（对应各角色表主键）',
    type VARCHAR(10) NOT NULL COMMENT '互动类型（like-点赞 / collect-收藏）',
    create_time DATETIME NOT NULL COMMENT '互动时间',
    UNIQUE KEY uk_post_user_type (post_id, user_role, user_id, type),
    KEY idx_post_type (post_id, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛互动记录表（点赞/收藏）';
