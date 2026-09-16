package com.online.study.controller;

import com.online.study.entity.Course;
import com.online.study.entity.Homework;
import com.online.study.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

@RestController
@RequestMapping("/course")
public class CourseController {

    @Autowired
    private CourseService service;
    
    @Autowired
    private CourseResourceService courseResourceService;
    
    @Autowired
    private CourseApplyService courseApplyService;
    
    @Autowired
    private HomeworkService homeworkService;
    
    @Autowired
    private HomeworkSubmitService homeworkSubmitService;
    
    @Autowired
    private ScoreService scoreService;

    @GetMapping("/list")
    public List<Course> list() {
        return service.list();
    }

    @PostMapping("/query")
    public List<Course> query(@RequestBody Map<String, Object> params) {
        QueryWrapper<Course> wrapper = new QueryWrapper<>();
        params.forEach((k, v) -> {
            if(v != null && !"".equals(v.toString())) {
                // convert camelCase to snake_case for mybatis plus wrapper
                String column = k.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
                wrapper.eq(column, v);
            }
        });
        return service.list(wrapper);
    }

    @PostMapping("/save")
    public boolean save(@RequestBody Course entity) {
        if (!StringUtils.hasText(entity.getCourseName()) || !StringUtils.hasText(entity.getTrainCycle())) {
            throw new IllegalArgumentException("课程名称和培训周期不能为空");
        }
        return service.saveOrUpdate(entity);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Integer id) {
        // Cascade delete related records
        
        // 1. Delete course_resource
        courseResourceService.remove(new QueryWrapper<com.online.study.entity.CourseResource>().eq("course_id", id));
        
        // 2. Delete course_apply
        courseApplyService.remove(new QueryWrapper<com.online.study.entity.CourseApply>().eq("course_id", id));
        
        // 3. Delete score
        scoreService.remove(new QueryWrapper<com.online.study.entity.Score>().eq("course_id", id));
        
        // 4. Delete homework and homework_submit
        List<Homework> homeworks = homeworkService.list(new QueryWrapper<Homework>().eq("course_id", id));
        if (homeworks != null && !homeworks.isEmpty()) {
            List<Integer> homeworkIds = homeworks.stream().map(Homework::getHomeworkId).collect(Collectors.toList());
            homeworkSubmitService.remove(new QueryWrapper<com.online.study.entity.HomeworkSubmit>().in("homework_id", homeworkIds));
            homeworkService.removeByIds(homeworkIds);
        }
        
        // 5. Delete the course itself
        return service.removeById(id);
    }
}
