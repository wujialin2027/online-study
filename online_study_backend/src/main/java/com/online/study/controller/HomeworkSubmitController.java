package com.online.study.controller;

import com.online.study.entity.HomeworkSubmit;
import com.online.study.service.HomeworkSubmitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.online.study.utils.QueryUtil;

@RestController
@RequestMapping("/homework-submit")
public class HomeworkSubmitController {

    @Autowired
    private HomeworkSubmitService service;

    @GetMapping("/list")
    public List<HomeworkSubmit> list() {
        return service.list();
    }

    @PostMapping("/query")
    public List<HomeworkSubmit> query(@RequestBody Map<String, Object> params) {
        QueryWrapper<HomeworkSubmit> wrapper = QueryUtil.buildSafeWrapper(HomeworkSubmit.class, params);
        return service.list(wrapper);
    }

    @PostMapping("/save")
    public boolean save(@RequestBody HomeworkSubmit entity) {
        return service.saveOrUpdate(entity);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Integer id) {
        return service.removeById(id);
    }
}
