package com.online.study.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.online.study.common.Result;
import com.online.study.common.ResultCode;
import com.online.study.entity.Admin;
import com.online.study.entity.Student;
import com.online.study.entity.Teacher;
import com.online.study.service.AdminService;
import com.online.study.service.StudentService;
import com.online.study.service.TeacherService;
import com.online.study.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证接口：登录 / 注册
 *
 * <p>本次改造点：
 * <ol>
 *   <li>返回值从「裸 Map」改为统一 {@link Result} 结构，前端只需一处解包</li>
 *   <li>参数错误从「抛 IllegalArgumentException」改为显式返回失败结果，语义更清晰</li>
 *   <li>登录成功返回的 user 对象<b>不再包含密码字段</b> ——
 *       实体上加了 {@code @JsonProperty(WRITE_ONLY)}，这里的代码一行都不用改，
 *       序列化时密码会被自动剔除</li>
 * </ol>
 */
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

    /**
     * 自助注册开关，默认开放。
     * <p>部署到公网做演示时，在服务器的配置里设 {@code app.register-enabled: false}
     * 关闭自助注册 —— 演示密码写在公开的 README 里，任何人都能登录演示环境，
     * 关注册可以防止陌生人灌垃圾数据；新账号由管理员/教师在库内开通。
     */
    @Value("${app.register-enabled:true}")
    private boolean registerEnabled;

    /**
     * 登录。成功时 data 形如：
     * <pre>
     * { "token": "eyJ...", "role": "teacher", "user": { "teacherId": 1, "teacherAccount": "teacher1", ... } }
     * </pre>
     * 注意 user 里<b>没有</b> teacherPwd 字段。
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> params) {
        String account = params.get("account");
        String password = params.get("password");
        String role = params.get("role");

        if (!StringUtils.hasText(account) || !StringUtils.hasText(password)) {
            return Result.failure(ResultCode.PARAM_ERROR, "账号密码不能为空");
        }
        account = account.trim();

        // 前端没指定角色时，依次在三张表里查，推断出角色
        if (!StringUtils.hasText(role)) {
            role = resolveRole(account);
            if (role == null) {
                return Result.failure("账号不存在");
            }
        }

        if ("student".equals(role)) {
            Student student = studentService.getOne(
                    new QueryWrapper<Student>().eq("student_account", account));
            if (student == null || !passwordEncoder.matches(password, student.getStudentPwd())) {
                return Result.failure("账号或密码错误");
            }
            if (student.getAccountStatus() != null && student.getAccountStatus() == 0) {
                return Result.failure("账号已被禁用");
            }
            return Result.success(buildLoginData(account, role, student.getStudentId(), student));
        }

        if ("teacher".equals(role)) {
            Teacher teacher = teacherService.getOne(
                    new QueryWrapper<Teacher>().eq("teacher_account", account));
            if (teacher == null || !passwordEncoder.matches(password, teacher.getTeacherPwd())) {
                return Result.failure("账号或密码错误");
            }
            if (teacher.getAccountStatus() != null && teacher.getAccountStatus() == 0) {
                return Result.failure("账号已被禁用");
            }
            return Result.success(buildLoginData(account, role, teacher.getTeacherId(), teacher));
        }

        if ("admin".equals(role)) {
            Admin admin = adminService.getOne(
                    new QueryWrapper<Admin>().eq("admin_account", account));
            if (admin == null || !passwordEncoder.matches(password, admin.getAdminPwd())) {
                return Result.failure("账号或密码错误");
            }
            return Result.success(buildLoginData(account, role, admin.getAdminId(), admin));
        }

        return Result.failure(ResultCode.PARAM_ERROR, "不支持的角色：" + role);
    }

    /**
     * 注册（仅支持 student / teacher）。
     */
    @PostMapping("/register")
    public Result<Void> register(@RequestBody Map<String, String> params) {
        String account = params.get("account");
        String password = params.get("password");
        String name = params.get("name");
        String phone = params.get("phone");
        String role = params.get("role");

        // 部署到公网后的防灌水开关：关闭后前端注册页会收到明确提示（服务端兜底，
        // 前端只是隐藏入口 —— 接口层必须自己挡，这是「不信任前端」原则的一部分）
        if (!registerEnabled) {
            return Result.failure("系统已关闭自助注册，请联系管理员开通账号");
        }

        if (!StringUtils.hasText(account) || !StringUtils.hasText(password)
                || !StringUtils.hasText(name) || !StringUtils.hasText(phone)) {
            return Result.failure(ResultCode.PARAM_ERROR, "账号、密码、姓名和手机号不能为空");
        }
        account = account.trim();
        name = name.trim();

        if (password.length() < 6) {
            return Result.failure(ResultCode.PARAM_ERROR, "密码长度不能少于6位");
        }
        if (!phone.matches("^1\\d{10}$")) {
            return Result.failure(ResultCode.PARAM_ERROR, "请输入正确的11位手机号");
        }

        if ("student".equals(role)) {
            if (studentService.count(new QueryWrapper<Student>().eq("student_account", account)) > 0) {
                return Result.failure("账号已存在");
            }
            Student student = new Student();
            student.setStudentAccount(account);
            student.setStudentPwd(passwordEncoder.encode(password));
            student.setStudentName(name);
            student.setStudentPhone(phone);
            student.setRegisterTime(new Date());
            student.setAccountStatus(1);
            studentService.save(student);
            return Result.success();
        }

        if ("teacher".equals(role)) {
            if (teacherService.count(new QueryWrapper<Teacher>().eq("teacher_account", account)) > 0) {
                return Result.failure("账号已存在");
            }
            Teacher teacher = new Teacher();
            teacher.setTeacherAccount(account);
            teacher.setTeacherPwd(passwordEncoder.encode(password));
            teacher.setTeacherName(name);
            teacher.setTeacherPhone(phone);
            teacher.setTeacherOrg("未填写");
            teacher.setRegisterTime(new Date());
            teacher.setAccountStatus(1);
            teacherService.save(teacher);
            return Result.success();
        }

        return Result.failure(ResultCode.PARAM_ERROR, "不支持的角色注册");
    }

    /** 依次在三张用户表里找这个账号，推断角色；找不到返回 null */
    private String resolveRole(String account) {
        if (studentService.getOne(new QueryWrapper<Student>().eq("student_account", account)) != null) {
            return "student";
        }
        if (teacherService.getOne(new QueryWrapper<Teacher>().eq("teacher_account", account)) != null) {
            return "teacher";
        }
        if (adminService.getOne(new QueryWrapper<Admin>().eq("admin_account", account)) != null) {
            return "admin";
        }
        return null;
    }

    /**
     * 组装登录成功的返回数据。
     * 注意：user 直接放的是实体对象，密码字段靠实体上的 WRITE_ONLY 注解自动屏蔽，
     * 这里<b>不需要</b>手写一堆 setXxxPwd(null)，避免以后新增字段时漏掉。
     */
    private Map<String, Object> buildLoginData(String account, String role, Integer userId, Object user) {
        Map<String, Object> data = new HashMap<>();
        data.put("token", jwtUtils.generateToken(account, role, userId));
        data.put("user", user);
        data.put("role", role);
        return data;
    }
}
