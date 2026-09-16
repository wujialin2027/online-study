package com.online.study.common;

import java.io.Serializable;

/**
 * 统一响应结果
 *
 * <p>所有 Controller 的返回值都包一层，前端只需在一处（axios 响应拦截器）统一解包，
 * 不用每个页面判断成功失败。
 *
 * <p>JSON 形态：
 * <pre>
 * { "code": 200, "message": "操作成功", "data": {...} }
 * </pre>
 *
 * @param <T> 业务数据类型
 */
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务状态码，见 {@link ResultCode} */
    private Integer code;

    /** 提示信息，前端可直接展示 */
    private String message;

    /** 业务数据，失败时为 null */
    private T data;

    public Result() {
    }

    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ==================== 成功 ====================

    /** 成功且无数据（新增 / 修改 / 删除用） */
    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /** 成功并携带数据 */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /** 成功并自定义提示语（如"报名成功，已加入我的课程"） */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    // ==================== 失败 ====================

    /** 按枚举返回失败 */
    public static <T> Result<T> failure(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /** 按枚举返回失败，并覆盖提示语 */
    public static <T> Result<T> failure(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null);
    }

    /** 自定义状态码与提示语 */
    public static <T> Result<T> failure(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    /** 业务失败（最常用），等价于 failure(ResultCode.BIZ_ERROR, message) */
    public static <T> Result<T> failure(String message) {
        return new Result<>(ResultCode.BIZ_ERROR.getCode(), message, null);
    }

    // ==================== 便捷判断 ====================

    /** 是否成功，供内部逻辑使用 */
    public boolean isSuccess() {
        return ResultCode.SUCCESS.getCode() == (this.code == null ? -1 : this.code);
    }

    // ==================== Getter / Setter ====================

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
