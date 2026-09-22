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
     *
     * <p>部署到公网后的两种策略，按需在服务器的配置里切换：
     * <ul>
     *   <li>{@code true}（当前线上采用）：开放注册，但只放行学员（见 {@link #register}），
     *       方便面试官 / HR 自己注册一个号进来体验；</li>
     *   <li>{@code false}：完全关闭自助注册，新账号只能由管理员在库内开通，
     *       适合不想被任何人写数据的场景。</li>
     * </ul>
     * 注意这个开关是<b>服务端兜底</b>：前端隐藏入口只是体验问题，
     * 接口层必须自己挡。</p>
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
     * 注册。
     *
     * <p><b>开放给公网时只放行「学员」</b>。原因不是"学员更友好"，而是
     * <b>教师角色有写公共数据的权限</b>：{@code POST /course/save} 是 TEACHER
     * 就能调，陌生人注册一个教师号就能建垃圾课程、发作业、传课件，
     * 演示环境会变成广告板。学员角色只能写自己的报名 / 提交 / 发帖记录，
     * 灌不脏公共数据，风险可控。
     *
     * <p>所以这里的判断是<b>「不是学员就拒绝」而不是「默认按学员处理」</b>：
     * 如果请求体里带了 {@code role=teacher} 却静默降级成学员，调用方会以为
     * 教师号注册成功了，实际拿到一个学员号，后续报错更难查。显式拒绝更清楚。
     *
     * <p>教师账号改为由管理员在库内开通（或后续做成"申请 + 审核"）。
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

        // 只放行学员。role 为空时按学员处理（前端不传就是没得选），传了别的值一律拒绝。
        if (StringUtils.hasText(role) && !"student".equals(role)) {
            return Result.failure(ResultCode.PARAM_ERROR, "当前仅开放学员注册，教师账号请联系管理员开通");
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

        // 账号在【三张表】里任意一张存在就拒绝。
        //
        // 为什么不能只查自己那张表：前端登录时**不传 role**，角色由后端
        // resolveRole() 按 student → teacher → admin 的顺序推断。于是
        // 「学员表里存在一个叫 teacher 的账号」会把教师账号 teacher 整个遮住 ——
        // 查询先命中学员，密码按学员那条记录校验，真正的教师从此登不进来。
        // 演示账号就叫 student / teacher / admin，开放注册后谁都能抢注同名账号
        // 把演示账号顶掉，所以这里必须做成跨表唯一的「登录名空间」。
        if (accountExists(account)) {
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

    /**
     * 账号是否已被占用 —— 在学员 / 教师 / 管理员三张表里任意一张查到就算占用。
     *
     * <p>这三个账号字段在业务上是同一个「登录名空间」：登录时前端只传账号和密码，
     * 角色由 {@link #resolveRole} 推断，所以重名会让先命中的那张表把后面的遮住。
     */
    private boolean accountExists(String account) {
        return studentService.count(new QueryWrapper<Student>().eq("student_account", account)) > 0
                || teacherService.count(new QueryWrapper<Teacher>().eq("teacher_account", account)) > 0
                || adminService.count(new QueryWrapper<Admin>().eq("admin_account", account)) > 0;
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
