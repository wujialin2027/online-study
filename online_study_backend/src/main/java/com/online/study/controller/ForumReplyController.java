package com.online.study.controller;

import com.online.study.entity.ForumReply;
import com.online.study.service.ForumReplyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

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
        QueryWrapper<ForumReply> wrapper = new QueryWrapper<>();
        params.forEach((k, v) -> {
            if(v != null && !"".equals(v.toString())) {
                String column = k.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
                wrapper.eq(column, v);
            }
        });
        return service.list(wrapper);
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
