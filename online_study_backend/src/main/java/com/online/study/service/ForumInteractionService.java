package com.online.study.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.online.study.entity.ForumInteraction;
import com.online.study.vo.ForumInteractionVO;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ForumInteractionService extends IService<ForumInteraction> {

    /**
     * 切换点赞 / 收藏状态：没点过就点上，点过就取消。
     *
     * @param postId 帖子ID
     * @param type   {@link ForumInteraction#TYPE_LIKE} 或 {@link ForumInteraction#TYPE_COLLECT}
     * @return 最新总数 + 当前用户的选中态
     */
    ForumInteractionVO toggle(Integer postId, String type);

    /**
     * 批量查询「当前登录用户」在这些帖子上做过哪些互动。
     *
     * <p>用途：列表页每条帖子都要显示点赞按钮的选中态。若逐条查就是 N 次 SQL，
     * 一次列表 10 条就是 10 次查询（N+1 问题）。这里一次性拿回来，
     * 在内存里按 postId 分组。
     *
     * @param postIds 帖子ID列表
     * @return postId → 互动类型集合（如 {1: {"like"}, 2: {"like","collect"}}）
     */
    Map<Integer, Set<String>> myInteractionMap(List<Integer> postIds);
}
