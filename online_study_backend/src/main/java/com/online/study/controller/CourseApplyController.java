package com.online.study.controller;

import com.online.study.entity.CourseApply;
import com.online.study.service.CourseApplyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

@RestController
@RequestMapping("/course-apply")
public class CourseApplyController {

    @Autowired
    private CourseApplyService service;

    @GetMapping("/list")
    public List<CourseApply> list() {
        return service.list();
    }

    @PostMapping("/query")
    public List<CourseApply> query(@RequestBody Map<String, Object> params) {
        QueryWrapper<CourseApply> wrapper = new QueryWrapper<>();
        params.forEach((k, v) -> {
            if(v != null && !"".equals(v.toString())) {
                String column = k.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
                wrapper.eq(column, v);
            }
        });
        return service.list(wrapper);
    }

    @PostMapping("/save")
    public boolean save(@RequestBody CourseApply entity) {
        return service.saveOrUpdate(entity);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Integer id) {
        return service.removeById(id);
    }
}
