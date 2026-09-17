package com.online.study.controller;

import com.online.study.entity.Score;
import com.online.study.service.ScoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.online.study.utils.QueryUtil;

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
