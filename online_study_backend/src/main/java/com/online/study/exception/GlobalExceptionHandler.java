package com.online.study.exception;

import com.online.study.common.Result;
import com.online.study.common.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

/**
 * 全局异常处理器
 *
 * <p>作用：把散落在各处的异常统一收敛成 {@link Result} 结构，
 * 让 Controller 里可以彻底不写 try-catch，Service 里遇到问题直接抛。
 *
 * <p>日志级别约定：
 * <ul>
 *   <li>可预期的业务异常（参数错、业务规则不满足）→ <b>warn</b>，不打印堆栈</li>
 *   <li>不可预期的异常（空指针、SQL 错）→ <b>error</b>，打印完整堆栈</li>
 * </ul>
 * 这个区分在真实项目里很重要——否则日志会被业务告警淹没。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ==================== 业务异常（可预期）====================

    /** 业务异常：由 Service 主动抛出，属于正常业务分支 */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.failure(e.getCode(), e.getMessage());
    }

    // ==================== 参数校验（400）====================

    /** @RequestBody + @Valid 校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ResultCode.PARAM_ERROR.getMessage());
        log.warn("参数校验失败: {}", message);
        return Result.failure(ResultCode.PARAM_ERROR, message);
    }

    /** 表单对象 + @Valid 校验失败 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ResultCode.PARAM_ERROR.getMessage());
        log.warn("参数绑定失败: {}", message);
        return Result.failure(ResultCode.PARAM_ERROR, message);
    }

    /** 方法参数上的 @NotBlank / @Min 等校验失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse(ResultCode.PARAM_ERROR.getMessage());
        log.warn("参数约束校验失败: {}", message);
        return Result.failure(ResultCode.PARAM_ERROR, message);
    }

    /** 参数类型不匹配，如路径变量要求 Integer 却传了 abc */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = "参数 " + e.getName() + " 类型不正确";
        log.warn("参数类型不匹配: {}", message);
        return Result.failure(ResultCode.PARAM_ERROR, message);
    }

    /** 缺少必填的请求参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        String message = "缺少必要参数：" + e.getParameterName();
        log.warn("缺少请求参数: {}", message);
        return Result.failure(ResultCode.PARAM_ERROR, message);
    }

    /** 请求体 JSON 格式错误，无法反序列化 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体格式错误: {}", e.getMessage());
        return Result.failure(ResultCode.PARAM_ERROR, "请求数据格式错误，请检查后重试");
    }

    /** 历史代码沿用的写法：直接抛 IllegalArgumentException */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return Result.failure(ResultCode.PARAM_ERROR, e.getMessage());
    }

    // ==================== 权限 / 请求方式 ====================

    /** @PreAuthorize 校验未通过时会走到这里（过滤器链抛出的由 Security 自行处理） */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        log.warn("权限不足: {}", e.getMessage());
        return Result.failure(ResultCode.FORBIDDEN);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}", e.getMessage());
        return Result.failure(ResultCode.METHOD_NOT_ALLOWED,
                "请求方法不支持，当前接口支持：" + e.getSupportedHttpMethods());
    }

    /** Boot 3 中访问不存在的路径会抛这个（替代了旧的 NoHandlerFoundException） */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("资源不存在: {}", e.getResourcePath());
        return Result.failure(ResultCode.NOT_FOUND, "接口不存在：" + e.getResourcePath());
    }

    // ==================== 数据库 / 上传 ====================

    /**
     * 唯一索引冲突。报名防重复、账号唯一等场景会命中这里。
     * 注意：不把原始 SQL 信息透给前端（可能泄露表结构），只给友好提示。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一约束冲突: {}", e.getMessage());
        return Result.failure(ResultCode.BIZ_ERROR, "数据已存在，请勿重复提交");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("上传文件过大: {}", e.getMessage());
        return Result.failure(ResultCode.PARAM_ERROR, "上传文件过大，请压缩后重试");
    }

    // ==================== 兜底 ====================

    /**
     * 兜底处理。走到这里说明是未预料到的异常，
     * <b>必须打印完整堆栈</b>，但<b>不能把异常细节返回给前端</b>（避免暴露内部实现）。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常: ", e);
        return Result.failure(ResultCode.SYSTEM_ERROR);
    }
}
