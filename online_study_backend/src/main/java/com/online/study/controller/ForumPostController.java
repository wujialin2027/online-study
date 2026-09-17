package com.online.study.controller;

import com.online.study.entity.ForumPost;
import com.online.study.service.ForumPostService;
import com.online.study.service.ForumReplyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.online.study.utils.QueryUtil;

@RestController
@RequestMapping("/forum-post")
public class ForumPostController {

    @Autowired
    private ForumPostService service;

    @Autowired
    private ForumReplyService forumReplyService;

    @GetMapping("/list")
    public List<ForumPost> list() {
        return service.list();
    }

    @PostMapping("/query")
    public List<ForumPost> query(@RequestBody Map<String, Object> params) {
        QueryWrapper<ForumPost> wrapper = QueryUtil.buildSafeWrapper(ForumPost.class, params);
        return service.list(wrapper);
    }

    @PostMapping("/save")
    public boolean save(@RequestBody ForumPost entity) {
        return service.saveOrUpdate(entity);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Integer id) {
        forumReplyService.remove(new QueryWrapper<com.online.study.entity.ForumReply>().eq("post_id", id));
        return service.removeById(id);
    }
}
