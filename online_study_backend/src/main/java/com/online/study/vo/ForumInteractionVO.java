package com.online.study.vo;

import lombok.Data;

/**
 * 点赞 / 收藏的操作结果
 *
 * <p>点一下按钮就要把「最新总数」和「我现在的状态」一起返回给前端，
 * 这样页面只更新这一个按钮，不用为了刷新数字重新拉整个列表。
 */
@Data
public class ForumInteractionVO {

    /** 帖子ID */
    private Integer postId;

    /** 互动类型：like / collect */
    private String type;

    /** 本次操作后，当前用户是否处于「已点赞 / 已收藏」状态（true=已点） */
    private boolean active;

    /** 该类型互动的最新总数 */
    private Integer count;

    public ForumInteractionVO() {
    }

    public ForumInteractionVO(Integer postId, String type, boolean active, Integer count) {
        this.postId = postId;
        this.type = type;
        this.active = active;
        this.count = count;
    }
}
