package com.online.study.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.online.study.entity.Course;
import com.online.study.service.CourseService;
import com.online.study.utils.QueryUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/course")
public class CourseController {

    @Autowired
    private CourseService service;

    @GetMapping("/list")
    public List<Course> list() {
        return service.list();
    }

    /** 条件查询：条件经 QueryUtil 白名单过滤，杜绝列名拼接注入 */
    @PostMapping("/query")
    public List<Course> query(@RequestBody Map<String, Object> params) {
        QueryWrapper<Course> wrapper = QueryUtil.buildSafeWrapper(Course.class, params);
        return service.list(wrapper);
    }

    @PostMapping("/save")
    public boolean save(@RequestBody Course entity) {
        if (!StringUtils.hasText(entity.getCourseName()) || !StringUtils.hasText(entity.getTrainCycle())) {
            throw new IllegalArgumentException("课程名称和培训周期不能为空");
        }
        return service.saveOrUpdate(entity);
    }

    /**
     * 删除课程（含级联）。
     *
     * <p>级联删除的逻辑已经移到 {@code CourseServiceImpl#removeCourseCascade}，
     * 那里加了 {@code @Transactional} 保证 5 张表的删除是一个原子操作。
     * Controller 只负责转发，不再亲自编排多个 Service —— 这样职责更清晰。
     */
    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Integer id) {
        return service.removeCourseCascade(id);
    }
}
