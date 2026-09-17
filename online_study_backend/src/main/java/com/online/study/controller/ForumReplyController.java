package com.online.study.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.online.study.common.PageQuery;
import com.online.study.common.PageResult;
import com.online.study.common.Result;
import com.online.study.common.ResultCode;
import com.online.study.entity.ForumPost;
import com.online.study.entity.ForumReply;
import com.online.study.exception.BizException;
import com.online.study.service.ForumPostService;
import com.online.study.service.ForumReplyService;
import com.online.study.utils.CurrentUserUtil;
import com.online.study.utils.QueryUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 论坛回复接口
 *
 * <h3>本次改造修掉的问题</h3>
 * <ol>
 *   <li><b>回复者可以伪造</b>：原 {@code /save} 直接使用前端传来的
 *       {@code replierId} / {@code replierRole} —— 挂谁的 ID 就是以谁的名义回复。
 *       现在一律从 JWT 取。</li>
 *   <li><b>谁都能删任何回复</b>：原 {@code /delete} 只判断"登录了没"。
 *       现在必须是回复本人或管理员。</li>
 *   <li><b>可以往已删除的帖子上回复</b>：原代码不校验帖子是否存在，
 *       帖子被删后回复会变成挂在空处的孤儿数据。现在回复前先确认帖子还在。</li>
 * </ol>
 */
@RestController
@RequestMapping("/forum-reply")
public class ForumReplyController {

    @Autowired
    private ForumReplyService service;

    @Autowired
    private ForumPostService forumPostService;

    @GetMapping("/list")
    public List<ForumReply> list() {
        return service.list();
    }

    @PostMapping("/query")
    public List<ForumReply> query(@RequestBody Map<String, Object> params) {
        QueryWrapper<ForumReply> wrapper = QueryUtil.buildSafeWrapper(ForumReply.class, params);
        return service.list(wrapper);
    }

    /**
     * 分页查询。
     *
     * <p>请求体示例：{@code {"pageNum": 1, "pageSize": 10, "courseName": "Java"}}
     * 前两个字段由 PageQuery 解析（含默认值与上限保护），其余作为查询条件经白名单校验。
     *
     * <p>按回复时间正序返回 —— 讨论串要按先后顺序读，倒序会把对话逻辑打乱。
     */
    @PostMapping("/page")
    public Result<PageResult<ForumReply>> page(@RequestBody Map<String, Object> params) {
        Page<ForumReply> page = PageQuery.of(params);
        QueryWrapper<ForumReply> wrapper = QueryUtil.buildSafeWrapper(ForumReply.class, params);
        wrapper.orderByAsc("reply_time");
        return Result.success(PageResult.of(service.page(page, wrapper)));
    }

    /** 发表回复。回复者身份与时间由服务端写入。 */
    @PostMapping("/save")
    public Result<ForumReply> save(@RequestBody ForumReply entity) {
        Integer uid = CurrentUserUtil.getId();
        String role = CurrentUserUtil.getRole();
        if (uid == null || role == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }
        if (entity.getPostId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "缺少帖子ID");
        }
        if (entity.getReplyContent() == null || entity.getReplyContent().isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "回复内容不能为空");
        }

        // 帖子必须还在，否则会产生"挂在已删除帖子上的孤儿回复"
        ForumPost post = forumPostService.getById(entity.getPostId());
        if (post == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "帖子不存在或已被删除");
        }

        entity.setReplierId(uid);
        entity.setReplierRole(role);
        entity.setReplyTime(new Date());
        service.save(entity);
        return Result.success("回复成功", entity);
    }

    /** 删除回复（回复本人或管理员）。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        ForumReply reply = service.getById(id);
        if (reply == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "回复不存在或已被删除");
        }
        if (!CurrentUserUtil.isAdmin()) {
            Integer uid = CurrentUserUtil.getId();
            String role = CurrentUserUtil.getRole();
            boolean mine = Objects.equals(reply.getReplierId(), uid)
                    && Objects.equals(reply.getReplierRole(), role);
            if (!mine) {
                throw new BizException(ResultCode.FORBIDDEN, "只能删除自己的回复");
            }
        }
        service.removeById(id);
        return Result.success();
    }
}
