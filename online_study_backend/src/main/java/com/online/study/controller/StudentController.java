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
import com.online.study.utils.QueryUtil;
import com.online.study.common.PageQuery;
import com.online.study.common.PageResult;
import com.online.study.common.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.online.study.annotation.OperationLog;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 学员账号管理接口。
 *
 * <h3>本次权限加固（配合「开放注册」的评估）</h3>
 * 这些接口原来全部只要求「已登录」—— 也就是说<b>任何登录用户（包括刚注册的陌生人）</b>
 * 都能调用 {@code /student/update-password} 改掉任意学员的密码，
 * 或者 {@code DELETE /student/{id}} 把学员删掉。
 *
 * <p>现在按用途分级：
 * <ul>
 *   <li>{@code /list}、{@code /query}：教师也要用（作业批改、报名审核要把
 *       studentId 映射成姓名），所以放开到 TEACHER；</li>
 *   <li>增删改 / 改密：只有管理员能碰 —— 账号由管理员统一开通，
 *       这也是「注册只能注册学员、教师账号由管理员建」的落点。</li>
 * </ul>
 */
@RestController
@RequestMapping("/student")
public class StudentController {

    @Autowired
    private StudentService service;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** 教师端要把 studentId 映射成姓名（批改作业、审核报名），所以教师也需要能读 */
    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public List<Student> list() {
        return service.list();
    }

    @PostMapping("/query")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public List<Student> query(@RequestBody Map<String, Object> params) {
        QueryWrapper<Student> wrapper = QueryUtil.buildSafeWrapper(Student.class, params);
        return service.list(wrapper);
    }

    /**
     * 分页查询。
     * 请求体示例：{"pageNum": 1, "pageSize": 10, "courseName": "Java"}
     * 前两个字段由 PageQuery 解析（含默认值与上限保护），其余作为查询条件经白名单校验。
     */
    @PostMapping("/page")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageResult<Student>> page(@RequestBody Map<String, Object> params) {
        Page<Student> page = PageQuery.of(params);
        QueryWrapper<Student> wrapper = QueryUtil.buildSafeWrapper(Student.class, params);
        return Result.success(PageResult.of(service.page(page, wrapper)));
    }

    /** 新增 / 修改学员（管理员）。密码在这里统一 BCrypt 加密 */
    @PostMapping("/save")
    @PreAuthorize("hasRole('ADMIN')")
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

    /**
     * 重置学员密码（管理员）。
     *
     * <p><b>加固点</b>：这个接口原来只要求「已登录」，意味着任何登录用户
     * 传一个 studentId 就能把别人账号的密码改掉 —— 拿到 ID 就等于拿到账号。
     * 现在限管理员。学员自己改密码走的是另一条路（个人中心），不走这个接口。
     */
    @PostMapping("/update-password")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(module = "用户", operation = "修改密码")
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

    /** 删除学员（管理员） */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public boolean delete(@PathVariable Integer id) {
        return service.removeById(id);
    }

    private boolean isEncodedPassword(String password) {
        return password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$");
    }
}
