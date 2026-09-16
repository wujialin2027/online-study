package com.online.study.controller;

import com.online.study.entity.Student;
import com.online.study.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

@RestController
@RequestMapping("/student")
public class StudentController {

    @Autowired
    private StudentService service;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/list")
    public List<Student> list() {
        return service.list();
    }

    @PostMapping("/query")
    public List<Student> query(@RequestBody Map<String, Object> params) {
        QueryWrapper<Student> wrapper = new QueryWrapper<>();
        params.forEach((k, v) -> {
            if(v != null && !"".equals(v.toString())) {
                String column = k.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
                wrapper.eq(column, v);
            }
        });
        return service.list(wrapper);
    }

    @PostMapping("/save")
    public boolean save(@RequestBody Student entity) {
        if (StringUtils.hasText(entity.getStudentPhone()) && !entity.getStudentPhone().matches("^1\\d{10}$")) {
            throw new IllegalArgumentException("请输入正确的11位手机号");
        }
        if (StringUtils.hasText(entity.getStudentPwd()) && !isEncodedPassword(entity.getStudentPwd())) {
            if (entity.getStudentPwd().length() < 6) {
                throw new IllegalArgumentException("密码长度不能少于6位");
            }
            entity.setStudentPwd(passwordEncoder.encode(entity.getStudentPwd()));
        }
        return service.saveOrUpdate(entity);
    }

    @PostMapping("/update-password")
    public boolean updatePassword(@RequestBody Map<String, Object> params) {
        Object studentId = params.get("studentId");
        Object newPassword = params.get("newPassword");
        if (studentId == null) {
            throw new IllegalArgumentException("学员ID不能为空");
        }
        if (newPassword == null || !StringUtils.hasText(newPassword.toString())) {
            throw new IllegalArgumentException("新密码不能为空");
        }
        if (newPassword.toString().length() < 6) {
            throw new IllegalArgumentException("密码长度不能少于6位");
        }
        Student student = service.getById(Integer.valueOf(studentId.toString()));
        if (student == null) {
            throw new IllegalArgumentException("学员不存在");
        }
        student.setStudentPwd(passwordEncoder.encode(newPassword.toString()));
        return service.updateById(student);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Integer id) {
        return service.removeById(id);
    }

    private boolean isEncodedPassword(String password) {
        return password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$");
    }
}
