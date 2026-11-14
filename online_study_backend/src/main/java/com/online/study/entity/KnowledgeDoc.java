package com.online.study.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 手工补充的一份知识资料（课件讲义正文、教师整理的知识点等）。
 *
 * <p>为什么单独一张表、而不是直接存切好的知识块：
 * <b>重建知识库要能重切</b>。切块参数（{@code ai.rag.chunk-size} / {@code chunk-overlap}）
 * 是调优对象，改了参数就得把原文重新切一遍 —— 只存切好的块，改参数时原文就回不来了。
 * 所以原文存这里（一份一行），切出来的块存 {@code ai_vector_chunk}（一份多行），
 * 前者是源，后者是派生物。
 *
 * <p>表结构见 {@code sql/migrate_v1.8.sql}。改造前这份数据存在
 * {@code KnowledgeIngestService} 的一个 {@code CopyOnWriteArrayList} 里，重启即丢。
 */
@Data
@TableName("ai_knowledge_doc")
public class KnowledgeDoc {

    @TableId(type = IdType.AUTO)
    private Integer docId;

    /** 资料标题，同时作为检索结果的出处 */
    private String source;

    /** 资料正文，可以很长（入库时自动切块） */
    private String content;

    private Date updateTime;
}
