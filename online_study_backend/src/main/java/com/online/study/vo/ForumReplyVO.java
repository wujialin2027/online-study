package com.online.study.vo;

import com.online.study.entity.ForumReply;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 论坛回复视图对象。
 *
 * <p>在回复实体之上补一个 {@code replierName}：原来接口只返回
 * {@code replierRole}（student / teacher / admin），前端只能显示一个"学员"标签，
 * 根本看不出是谁回复的 —— 一个讨论区连"谁在说话"都要猜，是体验硬伤。
 *
 * <p>姓名不是数据库列，而是查询后按角色批量补齐的（见
 * {@code ForumReplyController#toVOList}），一次请求只多查 1~3 次库，不做逐条查询。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ForumReplyVO extends ForumReply {

    /** 回复者姓名（按角色去 student / teacher / admin 表取） */
    private String replierName;
}
