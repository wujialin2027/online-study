-- ============================================================
-- v1.9 智能助教：对话记录补「成本核算」四列（S9-1 token 成本看板）
--
-- 背景：v1.4 建 ai_chat_message 时只存了"谁问的、问了什么、什么时候"，
-- token 用量只在日志里打一行，没有任何地方能聚合。
-- 于是"这个月 AI 花了多少钱"只能靠翻日志，被问起来答不出数字。
--
-- 这次补四列：
--   engine            哪套引擎答的（legacy / spring-ai）—— 双引擎对比要按引擎分组
--   model             用的哪个模型 code —— 换模型时成本口径不同，必须分开算
--   prompt_tokens     输入 token
--   completion_tokens 输出 token
--
-- 口径约定：
--   1) 只有 role='assistant' 的行有真实 token；user 行的两列固定 0。
--      原因是一轮问答的输入 token 已经包含了用户问题，给 user 行也记一遍会翻倍。
--   2) engine / model 两列 assistant 行必填、user 行可空 —— 查询按 role='assistant' 过滤即可。
--
-- 执行：mysql -uroot -p online_study < sql/migrate_v1.9.sql
-- ============================================================

ALTER TABLE `ai_chat_message`
    ADD COLUMN `engine`            VARCHAR(16) NULL     COMMENT '生成该条消息的引擎（legacy / spring-ai）' AFTER `content`,
    ADD COLUMN `model`             VARCHAR(64) NULL     COMMENT '使用的对话模型 code'                     AFTER `engine`,
    ADD COLUMN `prompt_tokens`     INT         NOT NULL DEFAULT 0 COMMENT '输入 token（仅 assistant 行有效）' AFTER `model`,
    ADD COLUMN `completion_tokens` INT         NOT NULL DEFAULT 0 COMMENT '输出 token（仅 assistant 行有效）' AFTER `prompt_tokens`;

-- 成本看板的主查询是 "WHERE role='assistant' AND create_time >= ? GROUP BY DATE(create_time), engine, model"，
-- create_time 在最前能直接吃掉范围过滤，再回表取后两列做分组。
ALTER TABLE `ai_chat_message`
    ADD INDEX `idx_time_engine` (`create_time`, `engine`);

-- 历史数据（约 925 条）不回填，统一留 NULL，看板里归成「未记录」一类。
--
-- 为什么不回填成 'legacy'：这段时间里既跑过 legacy 也跑过 spring-ai（P2 改造期），
-- 一刀切成 legacy 会凭空造出一段"legacy 成本曲线"，数字是编的。
-- token 同理 —— 历史日志已经滚掉，留空能说明"这段没有数据"，
-- 编出来的数字会让人以为成本曲线在那段时间是真实的。宁可空着。
