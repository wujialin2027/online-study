package com.online.study.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.online.study.common.PageQuery;
import com.online.study.common.PageResult;
import com.online.study.common.Result;
import com.online.study.common.ResultCode;
import com.online.study.entity.ForumInteraction;
import com.online.study.entity.ForumPost;
import com.online.study.entity.ForumReply;
import com.online.study.exception.BizException;
import com.online.study.service.ForumInteractionService;
import com.online.study.service.ForumPostService;
import com.online.study.service.ForumReplyService;
import com.online.study.utils.CurrentUserUtil;
import com.online.study.utils.QueryUtil;
import com.online.study.vo.ForumInteractionVO;
import com.online.study.vo.ForumPostVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 论坛帖子接口
 *
 * <h3>本次改造修掉的问题</h3>
 * <ol>
 *   <li><b>身份可以伪造</b>：原 {@code /save} 直接把前端传来的 {@code publisherRole} /
 *       {@code publisherId} 存库 —— 前端只要改一个字段，就能以别人（包括管理员）的名义发帖。
 *       现在一律从 JWT 里取，请求体里的这两个字段被直接忽略并覆盖。</li>
 *   <li><b>谁都能删任何帖子</b>：原 {@code /delete} 只有 {@code removeById(id)}，
 *       没有任何归属判断。现在必须「本人或管理员」。</li>
 *   <li><b>撤回是假的</b>：原前端自己算 5 分钟倒计时，然后调的就是删除接口 ——
 *       绕过前端直接调接口就没有任何时限。现在新增 {@code /{id}/recall}，
 *       时限由服务端比对发布时间判定。</li>
 *   <li><b>点赞/收藏是死字段</b>：{@code like_num} / {@code collect_num} 有列无接口，
 *       页面上永远是初始化脚本写死的数字。现在补上 toggle 接口与去重记录表。</li>
 * </ol>
 */
@RestController
@RequestMapping("/forum-post")
public class ForumPostController {

    /** 撤回时限：5 分钟。计时必须在服务端，前端倒计时只用于控制按钮显隐 */
    private static final long RECALL_WINDOW_MS = 5 * 60 * 1000L;

    @Autowired
    private ForumPostService service;

    @Autowired
    private ForumReplyService forumReplyService;

    @Autowired
    private ForumInteractionService interactionService;

    // ==================== 查询 ====================

    @GetMapping("/list")
    public List<ForumPost> list() {
        return service.list();
    }

    @PostMapping("/query")
    public List<ForumPost> query(@RequestBody Map<String, Object> params) {
        QueryWrapper<ForumPost> wrapper = QueryUtil.buildSafeWrapper(ForumPost.class, params);
        return service.list(wrapper);
    }

    /**
     * 分页查询（论坛列表页用）。
     *
     * <p>请求体示例：{@code {"pageNum": 1, "pageSize": 10, "keyword": "环境配置"}}
     *
     * <p>返回的是 {@link ForumPostVO}，比实体多了三个字段：
     * {@code liked} / {@code collected}（当前用户是否点过，用于按钮选中态）、
     * {@code replyNum}（回复条数）。
     */
    @PostMapping("/page")
    public Result<PageResult<ForumPostVO>> page(@RequestBody Map<String, Object> params) {
        Page<ForumPost> page = PageQuery.of(params);
        QueryWrapper<ForumPost> wrapper = QueryUtil.buildSafeWrapper(ForumPost.class, params);

        // 搜索框：标题或内容命中任一即算。
        // QueryUtil 的 XxxLike 后缀只能做单字段模糊，这里是「或」关系，所以单独拼。
        Object keyword = params.get("keyword");
        if (keyword != null && !keyword.toString().isBlank()) {
            String kw = keyword.toString().trim();
            wrapper.and(w -> w.like("post_title", kw).or().like("post_content", kw));
        }
        wrapper.orderByDesc("publish_time");

        Page<ForumPost> result = service.page(page, wrapper);
        PageResult<ForumPostVO> pageResult = new PageResult<>(
                toVOList(result.getRecords()),
                result.getTotal(),
                result.getCurrent(),
                result.getSize());
        return Result.success(pageResult);
    }

    /**
     * 帖子详情（详情页用）。
     *
     * <p>与列表返回同一种结构，前端详情页可以复用同一套组件，
     * 不用为了详情单独写一份渲染逻辑。
     */
    @GetMapping("/{id}")
    public Result<ForumPostVO> detail(@PathVariable Integer id) {
        ForumPost post = service.getById(id);
        if (post == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "帖子不存在或已被删除");
        }
        return Result.success(toVOList(List.of(post)).get(0));
    }

    // ==================== 发布 / 修改 / 删除 ====================

    /**
     * 发布或修改帖子。
     *
     * <p>{@code postId} 为空 = 新增；不为空 = 修改。
     */
    @PostMapping("/save")
    public Result<ForumPost> save(@RequestBody ForumPost entity) {
        Integer uid = CurrentUserUtil.getId();
        String role = CurrentUserUtil.getRole();
        if (uid == null || role == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }
        if (entity.getPostTitle() == null || entity.getPostTitle().isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "帖子标题不能为空");
        }
        if (entity.getPostContent() == null || entity.getPostContent().isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "帖子内容不能为空");
        }

        if (entity.getPostId() == null) {
            // 新增：发布者与发布时间一律由服务端决定。
            // 前端传什么 publisherId / publisherRole 都不采用 —— 这是防伪造的关键。
            entity.setPublisherId(uid);
            entity.setPublisherRole(role);
            entity.setPublishTime(new Date());
            entity.setLikeNum(0);
            entity.setCollectNum(0);
            service.save(entity);
            return Result.success("发布成功", entity);
        }

        // 修改：内容可变，但发布者与发布时间不可变（不允许"改完变成别人发的"）
        ForumPost db = service.getById(entity.getPostId());
        if (db == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "帖子不存在或已被删除");
        }
        requireOwnerOrAdmin(db.getPublisherId(), db.getPublisherRole(), "修改");
        db.setPostTitle(entity.getPostTitle());
        db.setPostContent(entity.getPostContent());
        service.updateById(db);
        return Result.success("修改成功", db);
    }

    /** 删除帖子（作者本人或管理员）。连同回复与互动记录一起清理。 */
    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(@PathVariable Integer id) {
        ForumPost post = service.getById(id);
        if (post == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "帖子不存在或已被删除");
        }
        requireOwnerOrAdmin(post.getPublisherId(), post.getPublisherRole(), "删除");
        removePostDeep(id);
        return Result.success();
    }

    /**
     * 撤回帖子：仅作者本人，且发布未超过 5 分钟。
     *
     * <p>与 {@link #delete} 的区别在于语义：撤回是"发错了，我自己收回"，
     * 所以有 5 分钟窗口；管理员删违规帖走 delete，不受时限约束。
     */
    @DeleteMapping("/{id}/recall")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> recall(@PathVariable Integer id) {
        ForumPost post = service.getById(id);
        if (post == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "帖子不存在或已被删除");
        }

        Integer uid = CurrentUserUtil.getId();
        String role = CurrentUserUtil.getRole();
        if (!Objects.equals(post.getPublisherId(), uid) || !Objects.equals(post.getPublisherRole(), role)) {
            throw new BizException(ResultCode.FORBIDDEN, "只能撤回自己发布的帖子");
        }

        // 时间以数据库里的发布时间为准。前端那个倒计时只是用来控制按钮显隐，
        // 绕过前端直接调接口也没用 —— 判定发生在服务端。
        if (post.getPublishTime() != null
                && System.currentTimeMillis() - post.getPublishTime().getTime() > RECALL_WINDOW_MS) {
            throw new BizException("已超过 5 分钟撤回时限，如需删除请联系管理员");
        }

        removePostDeep(id);
        return Result.success();
    }

    // ==================== 点赞 / 收藏 ====================

    /** 点赞（再点一次取消） */
    @PostMapping("/{id}/like")
    public Result<ForumInteractionVO> toggleLike(@PathVariable Integer id) {
        return Result.success(interactionService.toggle(id, ForumInteraction.TYPE_LIKE));
    }

    /** 收藏（再点一次取消） */
    @PostMapping("/{id}/collect")
    public Result<ForumInteractionVO> toggleCollect(@PathVariable Integer id) {
        return Result.success(interactionService.toggle(id, ForumInteraction.TYPE_COLLECT));
    }

    // ==================== 内部辅助 ====================

    /**
     * 实体列表 → 视图对象列表。
     *
     * <p>这里刻意写成"批处理"：无论 10 条还是 50 条帖子，都只额外查两次库
     * （一次互动状态、一次回复数）。如果写成循环里逐条查，就是典型的 N+1 查询。
     */
    private List<ForumPostVO> toVOList(List<ForumPost> posts) {
        List<ForumPostVO> result = new ArrayList<>();
        if (posts == null || posts.isEmpty()) {
            return result;
        }

        List<Integer> ids = posts.stream().map(ForumPost::getPostId).collect(Collectors.toList());

        // ① 当前用户在这些帖子上点过哪些赞 / 收藏
        Map<Integer, Set<String>> myTypes = interactionService.myInteractionMap(ids);

        // ② 每条的回复条数
        Map<Integer, Integer> replyCounts = new HashMap<>();
        List<Map<String, Object>> rows = forumReplyService.listMaps(new QueryWrapper<ForumReply>()
                .select("post_id", "COUNT(*) AS cnt")
                .in("post_id", ids)
                .groupBy("post_id"));
        for (Map<String, Object> row : rows) {
            Object postId = row.get("post_id");
            Object cnt = row.get("cnt");
            if (postId instanceof Number n1 && cnt instanceof Number n2) {
                replyCounts.put(n1.intValue(), n2.intValue());
            }
        }

        for (ForumPost post : posts) {
            ForumPostVO vo = new ForumPostVO();
            BeanUtils.copyProperties(post, vo);
            Set<String> types = myTypes.getOrDefault(post.getPostId(), Set.of());
            vo.setLiked(types.contains(ForumInteraction.TYPE_LIKE));
            vo.setCollected(types.contains(ForumInteraction.TYPE_COLLECT));
            vo.setReplyNum(replyCounts.getOrDefault(post.getPostId(), 0));
            result.add(vo);
        }
        return result;
    }

    /** 校验操作人是否为帖子作者本人或管理员 */
    private void requireOwnerOrAdmin(Integer publisherId, String publisherRole, String action) {
        if (CurrentUserUtil.isAdmin()) {
            return;
        }
        Integer uid = CurrentUserUtil.getId();
        String role = CurrentUserUtil.getRole();
        if (!Objects.equals(publisherId, uid) || !Objects.equals(publisherRole, role)) {
            throw new BizException(ResultCode.FORBIDDEN, "只能" + action + "自己发布的帖子");
        }
    }

    /**
     * 彻底删除帖子：回复 → 互动记录 → 帖子本体。
     *
     * <p>顺序不能反：{@code forum_reply} 上有外键指向 {@code forum_post}，
     * 先删帖子会触发外键约束失败。三张表的删除都在这一个事务里，
     * 中途报错会整体回滚，不会留下"帖子没了但回复还在"的脏数据。
     */
    private void removePostDeep(Integer postId) {
        forumReplyService.remove(new QueryWrapper<ForumReply>().eq("post_id", postId));
        interactionService.remove(new QueryWrapper<ForumInteraction>().eq("post_id", postId));
        service.removeById(postId);
    }
}
