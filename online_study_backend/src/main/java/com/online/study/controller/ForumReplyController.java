package com.online.study.controller;

import com.online.study.entity.ForumReply;
import com.online.study.service.ForumReplyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.online.study.utils.QueryUtil;
import com.online.study.common.PageQuery;
import com.online.study.common.PageResult;
import com.online.study.common.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

@RestController
@RequestMapping("/forum-reply")
public class ForumReplyController {

    @Autowired
    private ForumReplyService service;

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
     * 请求体示例：{"pageNum": 1, "pageSize": 10, "courseName": "Java"}
     * 前两个字段由 PageQuery 解析（含默认值与上限保护），其余作为查询条件经白名单校验。
     */
    @PostMapping("/page")
    public Result<PageResult<ForumReply>> page(@RequestBody Map<String, Object> params) {
        Page<ForumReply> page = PageQuery.of(params);
        QueryWrapper<ForumReply> wrapper = QueryUtil.buildSafeWrapper(ForumReply.class, params);
        return Result.success(PageResult.of(service.page(page, wrapper)));
    }

    @PostMapping("/save")
    public boolean save(@RequestBody ForumReply entity) {
        return service.saveOrUpdate(entity);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Integer id) {
        return service.removeById(id);
    }
}
