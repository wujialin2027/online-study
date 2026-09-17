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
import com.online.study.common.PageQuery;
import com.online.study.common.PageResult;
import com.online.study.common.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

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

    /**
     * 分页查询。
     * 请求体示例：{"pageNum": 1, "pageSize": 10, "courseName": "Java"}
     * 前两个字段由 PageQuery 解析（含默认值与上限保护），其余作为查询条件经白名单校验。
     */
    @PostMapping("/page")
    public Result<PageResult<CourseResource>> page(@RequestBody Map<String, Object> params) {
        Page<CourseResource> page = PageQuery.of(params);
        QueryWrapper<CourseResource> wrapper = QueryUtil.buildSafeWrapper(CourseResource.class, params);
        return Result.success(PageResult.of(service.page(page, wrapper)));
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
