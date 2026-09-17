package com.online.study.controller;

import com.online.study.entity.CourseResource;
import com.online.study.service.CourseResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.online.study.utils.QueryUtil;

@RestController
@RequestMapping("/course-resource")
public class CourseResourceController {

    @Autowired
    private CourseResourceService service;

    @GetMapping("/list")
    public List<CourseResource> list() {
        return service.list();
    }

    @PostMapping("/query")
    public List<CourseResource> query(@RequestBody Map<String, Object> params) {
        QueryWrapper<CourseResource> wrapper = QueryUtil.buildSafeWrapper(CourseResource.class, params);
        return service.list(wrapper);
    }

    @PostMapping("/save")
    public boolean save(@RequestBody CourseResource entity) {
        if (!StringUtils.hasText(entity.getResourceName()) || !StringUtils.hasText(entity.getResourceType())) {
            throw new IllegalArgumentException("资源名称和资源类型不能为空");
        }
        if (!StringUtils.hasText(entity.getResourcePath())) {
            throw new IllegalArgumentException("请先上传资源文件");
        }
        return service.saveOrUpdate(entity);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Integer id) {
        return service.removeById(id);
    }
}
