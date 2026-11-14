package com.online.study.mapper;

import lombok.Data;

/**
 * 成本看板的聚合查询结果（一天 × 一个引擎 × 一个模型 一行）。
 *
 * <p><b>为什么这个类放在 mapper 包而不是 vo 包</b>：它是数据库分组的中间结果，
 * 不是给前端的形状 —— 前端要的是按天/按引擎/按模型三种切法（见 {@code AiUsageVO}）。
 * 把中间结果和对外契约分开，以后改展示方式不会牵动 SQL。
 *
 * <p>字段名与 SQL 里的列别名一一对应，靠 MyBatis 的下划线转驼峰自动映射，
 * 所以别名必须写成 {@code prompt_tokens} 这种下划线形式。
 */
@Data
public class AiUsageRow {

    /** 日期，格式 yyyy-MM-dd */
    private String day;

    /** 引擎：legacy / spring-ai；历史数据为 null */
    private String engine;

    /** 模型 code；历史数据为 null */
    private String model;

    /** 这一桶的问答轮数 */
    private Long rounds;

    /** 输入 token 合计 */
    private Long promptTokens;

    /** 输出 token 合计 */
    private Long completionTokens;
}
