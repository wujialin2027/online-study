package com.online.study.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.online.study.entity.KnowledgeDoc;
import org.apache.ibatis.annotations.Mapper;

/**
 * 手工补充资料 Mapper。
 *
 * <p>只用到 BaseMapper：按标题覆盖（先删后插或 update）、全量读出、整表清空。
 * 唯一性由表上的 {@code uk_source} 保证 —— 同名资料只应存在一份，
 * 否则重建后同一段讲义会变成两份知识块，检索结果里出现重复片段。
 */
@Mapper
public interface KnowledgeDocMapper extends BaseMapper<KnowledgeDoc> {
}
