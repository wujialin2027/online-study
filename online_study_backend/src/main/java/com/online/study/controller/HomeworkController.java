package com.online.study.controller;

import com.online.study.entity.Homework;
import com.online.study.entity.HomeworkSubmit;
import com.online.study.service.HomeworkService;
import com.online.study.service.HomeworkSubmitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.online.study.mapper.TeacherMapper;
import com.online.study.entity.Teacher;

@RestController
@RequestMapping("/homework")
public class HomeworkController {

    @Autowired
    private HomeworkService service;

    @Autowired
    private HomeworkSubmitService homeworkSubmitService;

    @Autowired
    private TeacherMapper teacherMapper;   // 新增：直接注入 TeacherMapper

    @GetMapping("/list")
    public List<Homework> list() {
        return service.list();
    }

    @PostMapping("/query")
    public List<Homework> query(@RequestBody Map<String, Object> params) {
        QueryWrapper<Homework> wrapper = new QueryWrapper<>();
        params.forEach((k, v) -> {
            if(v != null && !"".equals(v.toString())) {
                String column = k.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
                wrapper.eq(column, v);
            }
        });
        return service.list(wrapper);
    }

    @PostMapping("/save")
    public boolean save(@RequestBody Homework entity) {
        // 获取当前登录用户的教师ID
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetails) {
            String teacherAccount = ((UserDetails) principal).getUsername();
            // 根据教师账号查询 teacher_id
            QueryWrapper<Teacher> wrapper = new QueryWrapper<>();
            wrapper.eq("teacher_account", teacherAccount);
            Teacher teacher = teacherMapper.selectOne(wrapper);
            if (teacher != null) {
                entity.setPublishTeacherId(teacher.getTeacherId());
            }
        }
        return service.saveOrUpdate(entity);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Integer id) {
        homeworkSubmitService.remove(new QueryWrapper<HomeworkSubmit>().eq("homework_id", id));
        return service.removeById(id);
    }
}