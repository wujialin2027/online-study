package com.online.study.utils;

import com.online.study.entity.Admin;
import com.online.study.entity.Student;
import com.online.study.entity.Teacher;
import com.online.study.service.AdminService;
import com.online.study.service.StudentService;
import com.online.study.service.TeacherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 按「角色 + ID」批量解析用户姓名。
 *
 * <h3>解决什么问题</h3>
 * 帖子、回复这类数据只存了 {@code publisherRole} + {@code publisherId}，
 * 前端拿到的就是一个"学员"标签 —— 看不出是谁。要显示姓名就得去三张表里查。
 *
 * <h3>为什么不做成循环里逐条查</h3>
 * 一个列表页 20 条帖子，逐条查就是 20 次（甚至 60 次）数据库往返，即典型的
 * <b>N+1 查询</b>。这里的做法是：先把所有 (角色,ID) 收齐、按角色分组，
 * 再对每张表各发一次 {@code IN} 查询 —— 无论多少条数据，最多 3 次查询。
 *
 * <p>调用方把需要的引用拼成 {@code "student:12"} 这种字符串集合传进来，
 * 拿回一个 {@code "student:12" -> "王小明"} 的映射，直接取值即可。
 */
@Component
public class UserNameResolver {

    public static final String ROLE_STUDENT = "student";
    public static final String ROLE_TEACHER = "teacher";
    public static final String ROLE_ADMIN = "admin";

    @Autowired
    private StudentService studentService;

    @Autowired
    private TeacherService teacherService;

    @Autowired
    private AdminService adminService;

    /** 拼引用键：{@code key("student", 12)} → {@code "student:12"} */
    public static String key(String role, Integer id) {
        return (role == null ? "?" : role) + ":" + (id == null ? "?" : id);
    }

    /**
     * 批量解析姓名。
     *
     * @param refs 形如 {@code "student:12"} 的引用集合
     * @return key 相同的引用 → 姓名；查不到的（用户被删等）不会出现在结果里，
     *         调用方用 {@code getOrDefault} 兜底即可
     */
    public Map<String, String> resolve(Collection<String> refs) {
        Map<String, String> result = new HashMap<>();
        if (refs == null || refs.isEmpty()) {
            return result;
        }

        Set<Integer> studentIds = new HashSet<>();
        Set<Integer> teacherIds = new HashSet<>();
        Set<Integer> adminIds = new HashSet<>();
        for (String ref : refs) {
            if (ref == null) {
                continue;
            }
            int idx = ref.indexOf(':');
            if (idx <= 0) {
                continue;
            }
            String role = ref.substring(0, idx);
            Integer id;
            try {
                id = Integer.valueOf(ref.substring(idx + 1));
            } catch (NumberFormatException e) {
                continue;
            }
            switch (role) {
                case ROLE_STUDENT -> studentIds.add(id);
                case ROLE_TEACHER -> teacherIds.add(id);
                case ROLE_ADMIN -> adminIds.add(id);
                default -> { /* 角色不认识就不查 */ }
            }
        }

        if (!studentIds.isEmpty()) {
            for (Student s : studentService.listByIds(studentIds)) {
                result.put(key(ROLE_STUDENT, s.getStudentId()), s.getStudentName());
            }
        }
        if (!teacherIds.isEmpty()) {
            for (Teacher t : teacherService.listByIds(teacherIds)) {
                result.put(key(ROLE_TEACHER, t.getTeacherId()), t.getTeacherName());
            }
        }
        if (!adminIds.isEmpty()) {
            for (Admin a : adminService.listByIds(adminIds)) {
                result.put(key(ROLE_ADMIN, a.getAdminId()), a.getAdminName());
            }
        }
        return result;
    }

    /** 取姓名的便捷方法：查不到时返回 null，由调用方决定兜底文案 */
    public String nameOf(Map<String, String> resolved, String role, Integer id) {
        return resolved.get(key(role, id));
    }
}
