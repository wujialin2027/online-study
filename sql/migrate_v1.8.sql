-- ============================================================
-- migrate_v1.8.sql
-- 向量落库：把「知识块 + 向量」从进程内存搬到数据库（P2-S6）
--
-- 改造前的行为（v1.7 及以前）：
--   知识块和向量只存在 InMemoryVectorStore 的一个 List 里，
--   服务一重启就全丢，下次提问要重新调一遍向量接口把几十份资料重新向量化
--   （耗时几秒到十几秒，且每次都花 token）。
--
-- 改造后的行为：
--   ① ai_vector_chunk  —— 向量库的「真相」。重建知识库时写进来，启动时从这儿加载，
--      不再重复调向量接口。进程内的 List 退化成「索引/缓存」，只为检索快。
--   ② ai_knowledge_doc —— 手工补充的资料正文（课件讲义等）。原来也存在内存里，
--      重启一样丢；现在落表，否则"重启不丢"只对了半句话。
--
-- 两张都是纯新增表，不改动任何已有表结构，可重复执行。
-- ============================================================

-- ---------- 1. 知识块（含向量） ----------
CREATE TABLE IF NOT EXISTS ai_vector_chunk
(
    chunk_id     BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '知识块 ID',
    source       VARCHAR(200) NOT NULL COMMENT '资料出处，回答里展示给用户看的那一行，例如「课程：Java 基础」',
    chunk_index  INT          NOT NULL COMMENT '该资料内部的第几块（从 1 开始），用于还原顺序与去重',
    content      MEDIUMTEXT   NOT NULL COMMENT '知识块正文',
    vector       LONGTEXT     NOT NULL COMMENT '向量：逗号分隔的浮点数，1024 维约 10KB',
    dim          INT          NOT NULL COMMENT '向量维度，检索前用来校验"入库与检索是同一个模型"',
    embed_model  VARCHAR(64)  NOT NULL COMMENT '生成该向量用的模型 code（text-embedding-v4）',
    create_time  DATETIME     NOT NULL COMMENT '入库时间；整表最后一次写入时间 = 知识库更新时间',
    -- 注意这里是普通索引、不是唯一索引。
    -- 第一版写的是 UNIQUE (source, chunk_index)，实测灌库直接失败：
    -- 课程/作业里有重名数据（例如两门课都有名为「123」的作业），
    -- 拼出来的 source 一模一样，块序号也一样 → 唯一键冲突。
    -- 内存版能容忍这种重复（代价只是检索时可能捞到两块相同内容），
    -- 落库这一步不该顺手改掉检索行为，所以降级为普通索引。
    -- 真要治本得去重名（业务侧问题），不是靠索引拦。
    KEY idx_source_chunk (source(100), chunk_index),
    KEY idx_model (embed_model)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='知识块与向量（RAG 检索用）';

-- ---------- 2. 手工补充的资料正文 ----------
CREATE TABLE IF NOT EXISTS ai_knowledge_doc
(
    doc_id      INT AUTO_INCREMENT PRIMARY KEY COMMENT '资料 ID',
    source      VARCHAR(200) NOT NULL COMMENT '资料标题，同时作为检索结果的出处，例如「Java 基础 - 第 2 章讲义」',
    content     MEDIUMTEXT   NOT NULL COMMENT '资料正文（入库时自动切块）',
    update_time DATETIME     NOT NULL COMMENT '最后修改时间',
    UNIQUE KEY uk_source (source)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='手工补充的知识资料（课件正文等）';

-- ---------- 核对 ----------
SELECT TABLE_NAME AS 已就绪的表, TABLE_COMMENT AS 说明
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('ai_vector_chunk', 'ai_knowledge_doc');
