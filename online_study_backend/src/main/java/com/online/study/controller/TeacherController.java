package com.online.study.controller;

import com.online.study.entity.Teacher;
import com.online.study.service.TeacherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

@RestController
@RequestMapping("/teacher")
public class TeacherController {

    @Autowired
    private TeacherService service;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/list")
    public List<Teacher> list() {
        return service.list();
    }

    @PostMapping("/query")
    public List<Teacher> query(@RequestBody Map<String, Object> params) {
        QueryWrapper<Teacher> wrapper = new QueryWrapper<>();
        params.forEach((k, v) -> {
            if(v != null && !"".equals(v.toString())) {
                String column = k.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
                wrapper.eq(column, v);
            }
        });
        return service.list(wrapper);
    }

    @PostMapping("/save")
    public boolean save(@RequestBody Teacher entity) {
        if (StringUtils.hasText(entity.getTeacherPhone()) && !entity.getTeacherPhone().matches("^1\\d{10}$")) {
            throw new IllegalArgumentException("请输入正确的11位手机号");
        }
        if (StringUtils.hasText(entity.getTeacherPwd()) && !isEncodedPassword(entity.getTeacherPwd())) {
            if (entity.getTeacherPwd().length() < 6) {
                throw new IllegalArgumentException("密码长度不能少于6位");
            }
            entity.setTeacherPwd(passwordEncoder.encode(entity.getTeacherPwd()));
        }
        return service.saveOrUpdate(entity);
    }

    @PostMapping("/update-password")
    public boolean updatePassword(@RequestBody Map<String, Object> params) {
        Object teacherId = params.get("teacherId");
        Object newPassword = params.get("newPassword");
        if (teacherId == null) {
            throw new IllegalArgumentException("教师ID不能为空");
        }
        if (newPassword == null || !StringUtils.hasText(newPassword.toString())) {
            throw new IllegalArgumentException("新密码不能为空");
        }
        if (newPassword.toString().length() < 6) {
            throw new IllegalArgumentException("密码长度不能少于6位");
        }
        Teacher teacher = service.getById(Integer.valueOf(teacherId.toString()));
        if (teacher == null) {
            throw new IllegalArgumentException("教师不存在");
        }
        teacher.setTeacherPwd(passwordEncoder.encode(newPassword.toString()));
        return service.updateById(teacher);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Integer id) {
        return service.removeById(id);
    }

    private boolean isEncodedPassword(String password) {
        return password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$");
    }
}
