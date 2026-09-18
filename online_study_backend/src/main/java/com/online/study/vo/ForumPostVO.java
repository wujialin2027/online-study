package com.online.study.vo;

import com.online.study.entity.ForumPost;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 论坛帖子视图对象
 *
 * <p>在帖子实体之上补三个「跟当前登录用户有关」的字段：
 * <ul>
 *   <li>{@code liked} / {@code collected} —— 我是否点过赞 / 收藏过。
 *       列表页的点赞按钮需要显示选中态，如果只有总数，按钮就只能是"哑"的。</li>
 *   <li>{@code replyNum} —— 该帖的回复条数，列表和详情都要显示。</li>
 *   <li>{@code publisherName} —— 发帖人姓名。原来只有 {@code publisherRole}，
 *       界面上只能显示一个"学员"标签，看不出是谁发的帖子。</li>
 * </ul>
 *
 * <p>这三个字段不是数据库列，而是查询时批量补上的（见
 * {@code ForumPostController#toVOList}）—— 一次查完当前页所有帖子的互动状态，
 * 而不是每条帖子查一次。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ForumPostVO extends ForumPost {

    /** 当前登录用户是否已点赞 */
    private boolean liked;

    /** 当前登录用户是否已收藏 */
    private boolean collected;

    /** 回复条数 */
    private Integer replyNum;

    /** 发帖人姓名（按 publisherRole 去 student / teacher / admin 表批量取） */
    private String publisherName;
}
