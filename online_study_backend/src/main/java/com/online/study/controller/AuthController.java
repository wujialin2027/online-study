package com.online.study.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.online.study.entity.Admin;
import com.online.study.entity.Student;
import com.online.study.entity.Teacher;
import com.online.study.service.AdminService;
import com.online.study.service.StudentService;
import com.online.study.service.TeacherService;
import com.online.study.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private StudentService studentService;

    @Autowired
    private TeacherService teacherService;

    @Autowired
    private AdminService adminService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> params) {
        String account = params.get("account");
        String password = params.get("password");
        String role = params.get("role");

        Map<String, Object> result = new HashMap<>();
        result.put("success", false);

        if (!StringUtils.hasText(account) || !StringUtils.hasText(password)) {
            result.put("message", "账号密码不能为空");
            return result;
        }

        account = account.trim();

        if (role == null || role.isEmpty()) {
            Student student = studentService.getOne(new QueryWrapper<Student>().eq("student_account", account));
            if (student != null) {
                role = "student";
            } else {
                Teacher teacher = teacherService.getOne(new QueryWrapper<Teacher>().eq("teacher_account", account));
                if (teacher != null) {
                    role = "teacher";
                } else {
                    Admin admin = adminService.getOne(new QueryWrapper<Admin>().eq("admin_account", account));
                    if (admin != null) {
                        role = "admin";
                    } else {
                        result.put("message", "账号不存在");
                        return result;
                    }
                }
            }
        }

        if ("student".equals(role)) {
            Student student = studentService.getOne(new QueryWrapper<Student>().eq("student_account", account));
            if (student != null && passwordEncoder.matches(password, student.getStudentPwd())) {
                if (student.getAccountStatus() == 0) {
                    result.put("message", "账号已被禁用");
                    return result;
                }
                result.put("success", true);
                result.put("token", jwtUtils.generateToken(account, role, student.getStudentId()));
                result.put("user", student);
                result.put("role", role);
            } else {
                result.put("message", "账号或密码错误");
            }
        } else if ("teacher".equals(role)) {
            Teacher teacher = teacherService.getOne(new QueryWrapper<Teacher>().eq("teacher_account", account));
            if (teacher != null && passwordEncoder.matches(password, teacher.getTeacherPwd())) {
                if (teacher.getAccountStatus() == 0) {
                    result.put("message", "账号已被禁用");
                    return result;
                }
                result.put("success", true);
                result.put("token", jwtUtils.generateToken(account, role, teacher.getTeacherId()));
                result.put("user", teacher);
                result.put("role", role);
            } else {
                result.put("message", "账号或密码错误");
            }
        } else if ("admin".equals(role)) {
            Admin admin = adminService.getOne(new QueryWrapper<Admin>().eq("admin_account", account));
            if (admin != null && passwordEncoder.matches(password, admin.getAdminPwd())) {
                result.put("success", true);
                result.put("token", jwtUtils.generateToken(account, role, admin.getAdminId()));
                result.put("user", admin);
                result.put("role", role);
            } else {
                result.put("message", "账号或密码错误");
            }
        }

        return result;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> params) {
        String account = params.get("account");
        String password = params.get("password");
        String name = params.get("name");
        String phone = params.get("phone");
        String role = params.get("role"); // 'student' or 'teacher'

        Map<String, Object> result = new HashMap<>();
        result.put("success", false);

        if (!StringUtils.hasText(account) || !StringUtils.hasText(password) || !StringUtils.hasText(name) || !StringUtils.hasText(phone)) {
            throw new IllegalArgumentException("账号、密码、姓名和手机号不能为空");
        }

        account = account.trim();
        name = name.trim();

        if (password.length() < 6) {
            throw new IllegalArgumentException("密码长度不能少于6位");
        }

        if (!phone.matches("^1\\d{10}$")) {
            throw new IllegalArgumentException("请输入正确的11位手机号");
        }

        if ("student".equals(role)) {
            if (studentService.count(new QueryWrapper<Student>().eq("student_account", account)) > 0) {
                result.put("message", "账号已存在");
                return result;
            }
            Student student = new Student();
            student.setStudentAccount(account);
            student.setStudentPwd(passwordEncoder.encode(password));
            student.setStudentName(name);
            student.setStudentPhone(phone);
            student.setRegisterTime(new java.util.Date());
            student.setAccountStatus(1);
            studentService.save(student);
            result.put("success", true);
        } else if ("teacher".equals(role)) {
            if (teacherService.count(new QueryWrapper<Teacher>().eq("teacher_account", account)) > 0) {
                result.put("message", "账号已存在");
                return result;
            }
            Teacher teacher = new Teacher();
            teacher.setTeacherAccount(account);
            teacher.setTeacherPwd(passwordEncoder.encode(password));
            teacher.setTeacherName(name);
            teacher.setTeacherPhone(phone);
            teacher.setTeacherOrg("未填写");
            teacher.setRegisterTime(new java.util.Date());
            teacher.setAccountStatus(1);
            teacherService.save(teacher);
            result.put("success", true);
        } else {
            result.put("message", "不支持的角色注册");
        }

        return result;
    }
}
