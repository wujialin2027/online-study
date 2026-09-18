-- ============================================================
-- 迁移 v1.4 —— 智能助教对话记录持久化
--
-- 背景：改造前 Assistant.vue 的 messages 是前端内存里的一个数组，
--       刷新页面、退出登录、关掉标签页就全没了。表现为：
--         · 学生问了一半的问题，不小心按了 F5，整段对话消失；
--         · 退出登录再进来，之前问过的什么都没留下；
--         · 教师想回看"学生都问了助教什么"，无从查起。
--
-- 本次新增 ai_chat_message 表，把每一轮问答落库：
--   · 一行 = 一条消息，user（提问）与 assistant（回答）各占一行，
--     按 message_id 升序拼起来就是完整的对话流；
--   · user_role + user_id 记录"谁问的"，取自 JWT（服务端验签结果），
--     不接受前端传参 —— 和论坛互动表 forum_interaction 同一套身份口径；
--   · 严格按用户隔离：查询、清空都带 user_role + user_id 条件，
--     任何用户都不可能读到或删掉别人的对话。
--
-- ⚠️ 为什么存 TEXT 而不是 VARCHAR(N)：
--     助教回答里可能含 Markdown 表格，长度不固定，TEXT（64KB）留足余量。
--     真正的长度管控在业务层（提问 ≤500 字、回答由提示词约束在 400 字内）。
--
-- ⚠️ 为什么索引是 (user_role, user_id, message_id)：
--     唯一的查询场景是「拉某个用户最近 N 条」→ 等值条件两列 + 按主键倒序，
--     联合索引正好覆盖，无需回表排序。
--
-- ⚠️ 为什么不建外键：
--     user_id 指向的表随角色而变（student / teacher / admin 三张表），
--     无法用一条外键约束表达；且项目内其他表也未使用外键，保持一致。
--     孤儿数据由清理逻辑负责（见 AiChatHistoryService.KEEP_PER_USER）。
--
-- 执行方式：
--   mysql -uroot -p123456 online_study < sql/migrate_v1.4.sql
--   或在 IDEA 的 Database 面板里打开本文件直接运行
-- ============================================================

USE online_study;

CREATE TABLE IF NOT EXISTS ai_chat_message (
    message_id  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    user_role   VARCHAR(10)  NOT NULL COMMENT '提问者角色（student/teacher/admin）',
    user_id     INT          NOT NULL COMMENT '提问者ID（对应各角色表主键）',
    role        VARCHAR(16)  NOT NULL COMMENT '消息角色（user-用户提问 / assistant-助教回答）',
    content     TEXT         NOT NULL COMMENT '消息正文',
    create_time DATETIME     NOT NULL COMMENT '消息时间',
    PRIMARY KEY (message_id),
    KEY idx_user (user_role, user_id, message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能助教对话记录表';
