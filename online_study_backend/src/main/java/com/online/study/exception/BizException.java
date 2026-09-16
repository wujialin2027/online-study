package com.online.study.exception;

import com.online.study.common.ResultCode;

/**
 * 业务异常
 *
 * <p>用法：在 Service 里遇到"业务规则不允许"的情况直接抛，例如
 * <pre>
 * if (course.getMaxStudents() &lt;= enrolled) {
 *     throw new BizException("课程名额已满");
 * }
 * </pre>
 * 由 {@link GlobalExceptionHandler} 统一捕获，转成 {@code Result.failure(...)} 返回前端，
 * <b>不需要在 Controller 里写 try-catch</b>。
 *
 * <p>对比 {@code IllegalArgumentException}：那个语义是"参数写错了"（编程错误），
 * 这个是"业务不允许"（正常业务分支），两者状态码与日志级别都应当区分。
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 业务状态码 */
    private final Integer code;

    public BizException(String message) {
        super(message);
        this.code = ResultCode.BIZ_ERROR.getCode();
    }

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
