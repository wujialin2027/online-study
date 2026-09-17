package com.online.study.mapper;

import com.online.study.entity.ForumPost;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ForumPostMapper extends BaseMapper<ForumPost> {

    /**
     * 原子增减点赞数
     *
     * <h3>为什么必须交给数据库做</h3>
     * 常见写法是「先 getById 查出来 → setLikeNum(n+1) → updateById」，也就是"读-改-写"。
     * 两个人同时点赞时，两个请求都会读到旧值 10，各自加 1 后写回 11 ——
     * 点了两次赞，计数只涨了 1。这类 bug 本地单人测试永远复现不出来。
     *
     * <p>写成 {@code like_num = like_num + #{delta}} 后，加减动作发生在数据库内部，
     * 由行锁保证串行，不会互相覆盖。
     *
     * <h3>WHERE 里那个 {@code >= 0} 是干什么的</h3>
     * 取消点赞时 delta = -1。如果因为数据被手工改过、或重复的取消请求到达，
     * 计数就会变成负数（页面上显示 "-1 赞" 很难看）。加这个条件后，
     * 减到 0 时语句影响行数为 0，计数停在 0 不会继续往下掉。
     *
     * <h3>为什么分成两个方法而不是传列名</h3>
     * MyBatis 的 {@code ${}} 是字符串直接拼进 SQL，传列名等于开了一个注入口子。
     * 拆成两个方法后列名是硬编码的，没有任何拼接口子，也顺带让调用方一眼看清意图。
     *
     * @param postId 帖子ID
     * @param delta  +1 点赞 / -1 取消
     * @return 受影响行数（0 表示帖子不存在或计数已到下限）
     */
    @Update("UPDATE forum_post SET like_num = like_num + #{delta} "
            + "WHERE post_id = #{postId} AND like_num + #{delta} >= 0")
    int addLikeNum(@Param("postId") Integer postId, @Param("delta") int delta);

    /**
     * 原子增减收藏数，语义同 {@link #addLikeNum(Integer, int)}。
     *
     * @param postId 帖子ID
     * @param delta  +1 收藏 / -1 取消收藏
     * @return 受影响行数
     */
    @Update("UPDATE forum_post SET collect_num = collect_num + #{delta} "
            + "WHERE post_id = #{postId} AND collect_num + #{delta} >= 0")
    int addCollectNum(@Param("postId") Integer postId, @Param("delta") int delta);
}
