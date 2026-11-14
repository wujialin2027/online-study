package com.online.study.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 智能助教的一条对话消息。
 *
 * <p>表和类的对应关系见 {@code sql/migrate_v1.4.sql}。一条 {@code user} 记录
 * 加一条 {@code assistant} 记录 = 一轮完整问答。
 *
 * <p>{@link #messageId} 用 {@code Long}：消息是持续累加的，不像课程、作业那样
 * 有天然上限，用 BIGINT 给它留足空间（对应建表语句里的 BIGINT）。
 */
@Data
@TableName("ai_chat_message")
public class AiChatMessage {

    @TableId(type = IdType.AUTO)
    private Long messageId;

    /** 提问者角色：student / teacher / admin */
    private String userRole;

    /** 提问者 ID（对应 student / teacher / admin 各自表的主键） */
    private Integer userId;

    /** 消息角色：user-用户提问 / assistant-助教回答 */
    private String role;

    /** 消息正文 */
    private String content;

    /**
     * 生成这条消息的引擎：{@code legacy} / {@code spring-ai}。
     *
     * <p>user 行（用户提问）也可能带这个值 —— 记录的是"这个问题交给哪套引擎处理的"。
     * 历史数据为 null，看板里归成「未记录」。
     */
    private String engine;

    /** 使用的对话模型 code，如 {@code deepseek-v4.1-flash-1017}。换模型时成本口径会变，必须分开存 */
    private String model;

    /**
     * 本次问答的输入 token（仅 assistant 行有效，user 行固定 0）。
     *
     * <p>为什么 user 行不记：一轮问答的输入 token 已经把用户问题算在里面了，
     * 两边都记等于把同一个问题算两遍，成本会凭空翻倍。
     */
    private Integer promptTokens;

    /** 本次问答的输出 token（仅 assistant 行有效） */
    private Integer completionTokens;

    private Date createTime;
}
