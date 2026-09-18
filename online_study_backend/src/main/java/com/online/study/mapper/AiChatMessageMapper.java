package com.online.study.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.online.study.entity.AiChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 智能助教对话记录 Mapper。
 *
 * <p>只用到 BaseMapper 的 insert / selectList / delete —— 给 MyBatis-Plus 的
 * {@code LambdaQueryWrapper} 拼条件即可，不需要自定义 SQL。
 *
 * <p>为什么不写 `@Select` 手写 SQL：按用户过滤 + 按主键倒序 + LIMIT 三个条件
 * 都是有索引覆盖的简单查询，用 Wrapper 表达比手写 XML 更不容易出错，
 * 也免得以后再抄一份 mapper XML。
 */
@Mapper
public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {
}
