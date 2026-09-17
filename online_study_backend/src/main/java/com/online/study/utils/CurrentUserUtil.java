package com.online.study.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户工具类
 *
 * <h3>为什么可以信任这里的值</h3>
 * {@link com.online.study.config.JwtAuthenticationFilter} 在解析 JWT 之后会把三样东西写进 SecurityContext：
 * <pre>
 *   principal   → 账号（account）
 *   authorities → ROLE_STUDENT / ROLE_TEACHER / ROLE_ADMIN
 *   details     → 用户 ID（学员/教师/管理员各自的表主键）
 * </pre>
 * 这些值**全部来自服务端对 token 的验签结果**，与前端请求体里传的任何参数无关，
 * 所以可以放心当作「当前登录用户的真实身份」使用。
 *
 * <h3>为什么要专门做这个工具</h3>
 * 原代码里写的是：
 * <pre>
 * Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
 * if (principal instanceof UserDetails) { ... }   // ← 永远为 false
 * </pre>
 * 因为过滤器放进 principal 的是 {@code String}，根本不是 {@code UserDetails}，
 * 这段判断从来没生效过 —— 结果发布作业时的教师 ID 只能靠前端传，可被随意伪造。
 */
public final class CurrentUserUtil {

    private static final String ROLE_PREFIX = "ROLE_";

    private CurrentUserUtil() {
        // 工具类不允许实例化
    }

    /** 当前登录账号；未登录返回 null */
    public static String getAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        return principal instanceof String ? (String) principal : null;
    }

    /** 当前登录用户的 ID；未登录或取不到返回 null */
    public static Integer getId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object details = auth.getDetails();
        return details instanceof Integer ? (Integer) details : null;
    }

    /** 当前登录角色（小写：student / teacher / admin）；未登录返回 null */
    public static String getRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        return auth.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(name -> name.startsWith(ROLE_PREFIX))
                .map(name -> name.substring(ROLE_PREFIX.length()).toLowerCase())
                .findFirst()
                .orElse(null);
    }

    /** 是否为管理员 */
    public static boolean isAdmin() {
        return "admin".equals(getRole());
    }
}
