package com.online.study.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.online.study.entity.Homework;
import com.online.study.service.HomeworkService;
import com.online.study.utils.CurrentUserUtil;
import com.online.study.utils.QueryUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.online.study.common.PageQuery;
import com.online.study.common.PageResult;
import com.online.study.common.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

@RestController
@RequestMapping("/homework")
public class HomeworkController {

    @Autowired
    private HomeworkService service;

    @GetMapping("/list")
    public List<Homework> list() {
        return service.list();
    }

    /** 条件查询：条件经 QueryUtil 白名单过滤，杜绝列名拼接注入 */
    @PostMapping("/query")
    public List<Homework> query(@RequestBody Map<String, Object> params) {
        QueryWrapper<Homework> wrapper = QueryUtil.buildSafeWrapper(Homework.class, params);
        return service.list(wrapper);
    }

    /**
     * 分页查询。
     * 请求体示例：{"pageNum": 1, "pageSize": 10, "courseName": "Java"}
     * 前两个字段由 PageQuery 解析（含默认值与上限保护），其余作为查询条件经白名单校验。
     */
    @PostMapping("/page")
    public Result<PageResult<Homework>> page(@RequestBody Map<String, Object> params) {
        Page<Homework> page = PageQuery.of(params);
        QueryWrapper<Homework> wrapper = QueryUtil.buildSafeWrapper(Homework.class, params);
        return Result.success(PageResult.of(service.page(page, wrapper)));
    }

    /**
     * 发布 / 更新作业。
     *
     * <p><b>本次改造的核心</b>：发布教师 ID 改为从 JWT 解析出的「当前登录用户」里取，
     * <b>不再相信前端传来的 publishTeacherId</b>。
     *
     * <p>原代码是这样写的：
     * <pre>
     * Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
     * if (principal instanceof UserDetails) { ... }   // 永远为 false
     * </pre>
     * 而过滤器放进 principal 的是 {@code String}（账号），不是 {@code UserDetails}，
     * 所以这段判断<b>从来没有执行过</b> —— 实际效果是「前端传谁就是谁」，
     * 学员完全可以伪造成教师去发布作业。
     */
    @PostMapping("/save")
    public boolean save(@RequestBody Homework entity) {
        Integer teacherId = CurrentUserUtil.getId();
        if (teacherId != null) {
            entity.setPublishTeacherId(teacherId);
        }
        return service.saveOrUpdate(entity);
    }

    /** 删除作业（含其提交记录）。级联 + 事务在 Service 层完成 */
    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Integer id) {
        return service.removeHomeworkCascade(id);
    }
}
