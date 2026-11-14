package com.online.study.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.online.study.entity.VectorChunk;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识块（含向量）Mapper。
 *
 * <p>只用 BaseMapper 的 {@code selectList} / {@code insert} / {@code delete}：
 * 重建知识库是"整表清空再写入"，检索是"全表读出来在内存里算相似度"，
 * 都不需要自定义 SQL，也不写 mapper XML。
 *
 * <p>注意本表<b>没有按向量做查询的 SQL</b> —— 向量相似度是应用层算的。
 * 这是本项目的规模决定的（上限 500 块）；如果要做"库里直接算 top-k"，
 * 就得换成 pgvector / Milvus 这类支持向量索引的存储。
 */
@Mapper
public interface VectorChunkMapper extends BaseMapper<VectorChunk> {
}
