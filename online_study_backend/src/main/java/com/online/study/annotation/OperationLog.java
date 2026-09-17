package com.online.study.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 *
 * <p>标在需要留痕的接口方法上，由 {@link com.online.study.aspect.OperationLogAspect}
 * 环绕拦截，把「谁、什么时候、对哪个模块做了什么、成功与否、耗时多久」写进
 * {@code sys_operation_log} 表。
 *
 * <p>用法：
 * <pre>
 *   &#64;OperationLog(module = "课程", operation = "审核课程")
 *   &#64;PostMapping("/audit")
 *   public Result&lt;Void&gt; audit(...) { ... }
 * </pre>
 *
 * <h3>为什么用注解而不是在每个方法里手写一行保存日志</h3>
 * 日志属于横切关注点：它和业务逻辑没有关系，却要在很多方法里重复。
 * 写在方法里会污染业务代码（每个方法多 5~10 行），而且容易漏、容易不一致。
 * AOP 的价值就体现在这里 —— <b>业务代码零侵入，加日志只要加一行注解</b>。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /** 业务模块，例如「课程」「报名」「作业」 */
    String module();

    /** 操作描述，例如「审核课程」「删除作业」 */
    String operation();

    /**
     * 是否记录请求参数（默认记录）。
     * 个别接口参数很长或含敏感信息时，可以设为 false 不记。
     * 注意：无论是否记录，切面都会对 password / token 等字段做脱敏处理。
     */
    boolean saveParams() default true;
}
