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

    private Date createTime;
}
