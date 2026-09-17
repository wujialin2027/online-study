package com.online.study.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.online.study.common.ResultCode;
import com.online.study.entity.ForumInteraction;
import com.online.study.entity.ForumPost;
import com.online.study.exception.BizException;
import com.online.study.mapper.ForumInteractionMapper;
import com.online.study.mapper.ForumPostMapper;
import com.online.study.service.ForumInteractionService;
import com.online.study.service.ForumPostService;
import com.online.study.utils.CurrentUserUtil;
import com.online.study.vo.ForumInteractionVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 论坛互动服务实现
 *
 * <p>点赞 / 收藏的完整链路：<b>记录表去重 → 冗余计数原子更新 → 回读真实总数</b>。
 * 三步都在同一个事务里，任何一步失败都会一起回滚，不会出现
 * 「互动记录删了但计数没减」这种对不上的情况。
 */
@Service
public class ForumInteractionServiceImpl
        extends ServiceImpl<ForumInteractionMapper, ForumInteraction>
        implements ForumInteractionService {

    private static final Logger log = LoggerFactory.getLogger(ForumInteractionServiceImpl.class);

    @Autowired
    private ForumPostService forumPostService;

    @Autowired
    private ForumPostMapper forumPostMapper;

    /**
     * 切换点赞 / 收藏。
     *
     * <h3>为什么是「先插、撞车了再删」而不是「先查、没有才插」</h3>
     * 后者的判断与写入之间有间隙：两个人同时点赞时，两个请求都查到"没点过"，
     * 于是各插一条记录、各加一次计数 —— 计数就多了。
     * 前者的插入动作由唯一索引兜底，谁先谁后由数据库裁决，不存在间隙：
     * <pre>
     *   插入成功        → 本次是「点赞」，计数 +1
     *   唯一键冲突      → 之前点过，本次是「取消」，删掉记录，计数 -1
     * </pre>
     * 换句话说，连点两次的语义是「点上再取消」，最终回到初始状态 —— 符合直觉。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ForumInteractionVO toggle(Integer postId, String type) {
        Integer uid = CurrentUserUtil.getId();
        String role = CurrentUserUtil.getRole();
        if (uid == null || role == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }

        ForumPost post = forumPostService.getById(postId);
        if (post == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "帖子不存在或已被删除");
        }

        ForumInteraction record = new ForumInteraction();
        record.setPostId(postId);
        record.setUserRole(role);
        record.setUserId(uid);
        record.setType(type);
        record.setCreateTime(new Date());

        boolean active;
        try {
            this.save(record);
            active = true;
        } catch (DuplicateKeyException e) {
            // 撞上唯一索引 uk_post_user_type → 说明之前点过，这次是取消
            this.remove(new QueryWrapper<ForumInteraction>()
                    .eq("post_id", postId)
                    .eq("user_role", role)
                    .eq("user_id", uid)
                    .eq("type", type));
            active = false;
            log.debug("用户 {}:{} 取消了对帖子 {} 的 {} 互动", role, uid, postId, type);
        }

        // 同步冗余计数：+1 或 -1（原子更新，见 ForumPostMapper 里的说明）
        int delta = active ? 1 : -1;
        if (ForumInteraction.TYPE_LIKE.equals(type)) {
            forumPostMapper.addLikeNum(postId, delta);
        } else {
            forumPostMapper.addCollectNum(postId, delta);
        }

        // 回读登记在库里的真实计数。不能拿 post 对象自己加减 —— 并发下会算歪。
        ForumPost latest = forumPostService.getById(postId);
        Integer count = ForumInteraction.TYPE_LIKE.equals(type)
                ? latest.getLikeNum()
                : latest.getCollectNum();
        return new ForumInteractionVO(postId, type, active, count);
    }

    @Override
    public Map<Integer, Set<String>> myInteractionMap(List<Integer> postIds) {
        Map<Integer, Set<String>> result = new HashMap<>();
        if (postIds == null || postIds.isEmpty()) {
            return result;
        }
        String role = CurrentUserUtil.getRole();
        Integer uid = CurrentUserUtil.getId();
        if (role == null || uid == null) {
            return result;
        }

        // 只取需要的两列，避免把整行数据捞回来
        List<ForumInteraction> mine = this.list(new QueryWrapper<ForumInteraction>()
                .select("post_id", "type")
                .in("post_id", postIds)
                .eq("user_role", role)
                .eq("user_id", uid));

        for (ForumInteraction item : mine) {
            result.computeIfAbsent(item.getPostId(), key -> new HashSet<>()).add(item.getType());
        }
        return result;
    }
}
