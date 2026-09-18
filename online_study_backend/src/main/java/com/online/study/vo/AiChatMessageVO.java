package com.online.study.vo;

import lombok.Data;

import java.util.Date;

/**
 * 一条历史对话消息（返回给前端渲染用）。
 *
 * <p>与实体 {@code AiChatMessage} 的差别：**不带 userRole / userId**。
 * 前端本来就知道自己是谁，把身份字段回传没有意义，反而多一份数据暴露面
 * （接口响应在浏览器网络面板里是明文可见的）。
 *
 * <p>为什么不把出处（sources）和工具轨迹（usedTools）一起存下来：
 * 那两样是"当次回答的现场证据"，存进历史表会让表结构复杂一大截（要么加 JSON 列、
 * 要么再加两张子表）。历史记录的作用是"翻回去看问过什么、答了什么"，
 * 正文足够；需要看出处就重新问一次。
 */
@Data
public class AiChatMessageVO {

    private Long messageId;

    /** user-用户提问 / assistant-助教回答 */
    private String role;

    private String content;

    private Date createTime;
}
