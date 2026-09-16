package com.online.study.common;

/**
 * 统一业务状态码
 *
 * <p>设计约定（前后端分工，很重要）：
 * <ul>
 *   <li><b>安全层</b>（未登录 / 无权限）：由 Spring Security 返回 <b>HTTP 401 / 403</b>，
 *       不走这里。前端 axios 拦截器据此自动跳登录页。</li>
 *   <li><b>业务层</b>（参数错误 / 业务规则不满足 / 系统异常）：<b>HTTP 状态码始终 200</b>，
 *       真实结果看响应体里的 {@code code} 字段。前端拦截器据此解包或提示错误。</li>
 * </ul>
 */
public enum ResultCode {

    SUCCESS(200, "操作成功"),

    PARAM_ERROR(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有权限执行该操作"),
    NOT_FOUND(404, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),

    BIZ_ERROR(500, "业务处理失败"),
    SYSTEM_ERROR(599, "系统异常，请稍后重试");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
