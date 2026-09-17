package com.online.study.controller;

import com.online.study.entity.Score;
import com.online.study.service.ScoreService;
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
@RequestMapping("/score")
public class ScoreController {

    @Autowired
    private ScoreService service;

    @GetMapping("/list")
    public List<Score> list() {
        return service.list();
    }

    @PostMapping("/query")
    public List<Score> query(@RequestBody Map<String, Object> params) {
        QueryWrapper<Score> wrapper = QueryUtil.buildSafeWrapper(Score.class, params);
        return service.list(wrapper);
    }

    /**
     * 分页查询。
     * 请求体示例：{"pageNum": 1, "pageSize": 10, "courseName": "Java"}
     * 前两个字段由 PageQuery 解析（含默认值与上限保护），其余作为查询条件经白名单校验。
     */
    @PostMapping("/page")
    public Result<PageResult<Score>> page(@RequestBody Map<String, Object> params) {
        Page<Score> page = PageQuery.of(params);
        QueryWrapper<Score> wrapper = QueryUtil.buildSafeWrapper(Score.class, params);
        return Result.success(PageResult.of(service.page(page, wrapper)));
    }

    @PostMapping("/save")
    public boolean save(@RequestBody Score entity) {
        validateScore("作业分数", entity.getHomeworkScore());
        validateScore("考试分数", entity.getExamScore());
        validateScore("总成绩", entity.getTotalScore());
        return service.saveOrUpdate(entity);
    }

    private void validateScore(String label, Integer score) {
        if (score == null) {
            return;
        }
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException(label + "必须在0到100之间");
        }
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Integer id) {
        return service.removeById(id);
    }
}
