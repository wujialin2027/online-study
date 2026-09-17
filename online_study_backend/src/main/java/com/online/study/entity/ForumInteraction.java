package com.online.study.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 论坛互动记录（点赞 / 收藏）
 *
 * <h3>为什么需要这张表</h3>
 * {@code forum_post} 里本来就有 {@code like_num} / {@code collect_num} 两个字段，
 * 但既没有点赞接口、也没有任何地方记录「谁点过赞」——
 * 这两个字段只能靠初始化脚本写死，页面上永远是同一个数字，属于典型的「死字段」。
 *
 * <p>有了本表，计数才是真实可维护的：
 * <pre>
 *   点赞   → 往本表插一条 (post_id, user_role, user_id, 'like')，同时 like_num + 1
 *   取消   → 删掉这条记录，同时 like_num - 1
 *   列表页 → 用本表反查「当前用户是否已点赞」，让按钮显示正确的选中态
 * </pre>
 *
 * <h3>去重为什么交给数据库</h3>
 * 唯一索引 {@code uk_post_user_type} 是这里的核心。如果写成「先 SELECT 判断有没有、
 * 没有才 INSERT」，两个请求同时进来时都会查到"没点过"，然后各插一条 —— 计数就多算了。
 * 交给唯一索引，第二条会直接抛 DuplicateKey，捕获后按「取消点赞」处理即可。
 */
@Data
@TableName("forum_interaction")
public class ForumInteraction {

    /** 互动类型：点赞 */
    public static final String TYPE_LIKE = "like";

    /** 互动类型：收藏 */
    public static final String TYPE_COLLECT = "collect";

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 帖子ID */
    private Integer postId;

    /** 互动者角色（student / teacher / admin） */
    private String userRole;

    /** 互动者ID（对应各角色表主键） */
    private Integer userId;

    /** 互动类型，取值见 {@link #TYPE_LIKE} / {@link #TYPE_COLLECT} */
    private String type;

    /** 互动时间 */
    private Date createTime;
}
