package com.online.study.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.online.study.entity.AiChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

/**
 * 智能助教对话记录 Mapper。
 *
 * <p>日常读写的 insert / selectList / delete 全部走 BaseMapper，
 * 用 {@code LambdaQueryWrapper} 拼条件即可，不写自定义 SQL ——
 * "按用户过滤 + 按主键倒序 + LIMIT"三个条件都有索引覆盖，用 Wrapper 表达更不容易出错。
 *
 * <h3>唯一一处手写 SQL：成本看板的聚合</h3>
 * {@link #aggregateUsage} 用了 {@code DATE_FORMAT} + {@code GROUP BY} + 两个 {@code SUM}。
 * 这类查询用 Wrapper 也能拼，但结果要落进一个自定义对象时，别名映射比 selectMaps
 * 拿一堆 {@code Map<String,Object>} 再手动转类型清楚得多（后者的 key 大小写、
 * SUM 返回的 BigDecimal，都是容易踩的坑）。
 *
 * <p>SQL 里只做"分组求和"，<b>不做计价</b> —— 单价是配置项，塞进 SQL 会让
 * 一条 SQL 依赖外部配置：改价要改 SQL，出错时分不清是数据问题还是配置问题。
 */
@Mapper
public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {

    /**
     * 按「天 × 引擎 × 模型」聚合 token 用量。
     *
     * <p>只统计 {@code role = 'assistant'} 的行：一轮问答的输入 token 已经把
     * 用户提问算在里面，把 user 行也算进来等于同一个问题算两遍，成本直接翻倍。
     *
     * <p>历史行（v1.9 之前）的 {@code engine} / {@code model} 是 null，
     * {@code GROUP BY} 会把它们归成一组，由上层显示成「未记录」。
     *
     * @param since 统计起点（含）
     */
    @Select("""
            SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS day,
                   engine,
                   model,
                   COUNT(*)               AS rounds,
                   SUM(prompt_tokens)     AS prompt_tokens,
                   SUM(completion_tokens) AS completion_tokens
            FROM ai_chat_message
            WHERE role = 'assistant'
              AND create_time >= #{since}
            GROUP BY day, engine, model
            ORDER BY day DESC
            """)
    List<AiUsageRow> aggregateUsage(@Param("since") Date since);
}
