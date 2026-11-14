package com.online.study.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 一个知识块 + 它的向量（RAG 检索的最小单位）。
 *
 * <p>表和类的对应关系见 {@code sql/migrate_v1.8.sql}。一段资料（课程介绍、作业要求、
 * 课件正文）会被切成若干块，每块一行。
 *
 * <p><b>为什么 {@link #vector} 是字符串而不是 {@code float[]}</b>：
 * MySQL 没有原生数组类型。可选方案有三种 ——
 * <ol>
 *   <li>逗号分隔的字符串（本项目的选择）：零依赖、可读、写入最简单</li>
 *   <li>{@code JSON} 类型：能查，但本项目从不按向量内容做条件查询，只是整行读出来算相似度</li>
 *   <li>专门的向量库（pgvector / Milvus / Redis Stack）：几万条以上才值得</li>
 * </ol>
 * 本项目知识块上限 500 条、检索是"全量读进内存再算余弦"，所以选最朴素的 ①，
 * 代价是多一次 {@code Float.parseFloat} 的解析，实测 500 条约 10ms 量级，可以接受。
 * <b>知识块涨到几万条就该换 ③</b> —— 到那时瓶颈不在解析，而在"每次都要全表读一遍"。
 *
 * <p>{@link #dim} 与 {@link #embedModel} 两个字段是给"模型换了但库没重灌"这类事故兜底的：
 * 维度对不上就直接报错，而不是算出一堆 0 分然后假装检索不到。
 */
@Data
@TableName("ai_vector_chunk")
public class VectorChunk {

    @TableId(type = IdType.AUTO)
    private Long chunkId;

    /** 资料出处，例如「课程：Java 基础」「作业：Java 基础 / 第 3 次作业」 */
    private String source;

    /** 该资料内部的第几块，从 1 开始 */
    private Integer chunkIndex;

    /** 知识块正文 */
    private String content;

    /** 向量，逗号分隔的浮点数 */
    private String vector;

    /** 向量维度 */
    private Integer dim;

    /** 生成该向量用的模型 code */
    private String embedModel;

    private Date createTime;
}
