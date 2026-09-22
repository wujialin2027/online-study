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
import com.online.study.utils.QueryUtil;
import com.online.study.common.PageQuery;
import com.online.study.common.PageResult;
import com.online.study.common.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.online.study.annotation.OperationLog;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 教师账号管理接口。
 *
 * <h3>本次权限加固（配合「开放注册」的评估）</h3>
 * 与 {@code StudentController} 同样的问题：这些接口原来只要求「已登录」，
 * 陌生人注册一个学员账号就能调用 {@code DELETE /teacher/{id}} 删教师，
 * 或者改掉任意教师的密码。现在增删改一律限管理员。
 *
 * <p>这也是「教师账号不开自助注册」的配套：教师账号由管理员在
 * 「系统管理 → 用户管理」里创建（前端 {@code Admin.vue} 调 {@code /teacher/save}），
 * 普通用户永远拿不到教师身份，自然也就拿不到教师接口的写权限。
 */
@RestController
@RequestMapping("/teacher")
public class TeacherController {

    @Autowired
    private TeacherService service;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public List<Teacher> list() {
        return service.list();
    }

    @PostMapping("/query")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public List<Teacher> query(@RequestBody Map<String, Object> params) {
        QueryWrapper<Teacher> wrapper = QueryUtil.buildSafeWrapper(Teacher.class, params);
        return service.list(wrapper);
    }

    /**
     * 分页查询。
     * 请求体示例：{"pageNum": 1, "pageSize": 10, "courseName": "Java"}
     * 前两个字段由 PageQuery 解析（含默认值与上限保护），其余作为查询条件经白名单校验。
     */
    @PostMapping("/page")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageResult<Teacher>> page(@RequestBody Map<String, Object> params) {
        Page<Teacher> page = PageQuery.of(params);
        QueryWrapper<Teacher> wrapper = QueryUtil.buildSafeWrapper(Teacher.class, params);
        return Result.success(PageResult.of(service.page(page, wrapper)));
    }

    /** 新增 / 修改教师（管理员）—— 教师账号的唯一来源 */
    @PostMapping("/save")
    @PreAuthorize("hasRole('ADMIN')")
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

    /** 重置教师密码（管理员） */
    @PostMapping("/update-password")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(module = "用户", operation = "修改密码")
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

    /** 删除教师（管理员） */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public boolean delete(@PathVariable Integer id) {
        return service.removeById(id);
    }

    private boolean isEncodedPassword(String password) {
        return password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$");
    }
}
