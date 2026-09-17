package com.online.study.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.online.study.annotation.OperationLog;
import com.online.study.entity.SysOperationLog;
import com.online.study.service.SysOperationLogService;
import com.online.study.utils.CurrentUserUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * 操作日志切面
 *
 * <p>环绕拦截所有标注了 {@link OperationLog} 的方法，把操作留痕写入
 * {@code sys_operation_log} 表。
 *
 * <h3>四个关键设计（面试可讲）</h3>
 * <ol>
 *   <li><b>用 {@code @Around} 而不是 {@code @AfterReturning}</b>：
 *       只有环绕通知能同时覆盖「正常返回」和「抛异常」两条路径。
 *       这里的实现是在 {@code finally} 里落库 —— <b>失败的操作同样要被记录下来</b>，
 *       因为「谁试图删别人的课程被拦住」这类信息往往比成功操作更有价值。</li>
 *
 *   <li><b>记录日志失败绝不能影响业务</b>：落库被 try-catch 包住，只打 warn 日志。
 *       否则一旦日志表出问题，正常的业务接口会跟着报错 —— 这是"非侵入"的底线。</li>
 *
 *   <li><b>敏感字段脱敏</b>：把参数序列化成 JSON 树后递归遍历，
 *       命中 {@code password / token / secret} 等字段名就把值替换成 {@code ***}，
 *       避免把明文密码写进日志表（日志的读取权限通常比业务表宽）。</li>
 *   <li><b>兼容反向代理</b>：客户端 IP 优先从 {@code X-Forwarded-For} 等请求头取，
 *       因为将来部署到 Nginx 后面时 {@code getRemoteAddr()} 拿到的是 Nginx 的地址。</li>
 * </ol>
 *
 * <p>关于性能：日志写入是同步的，一次 INSERT 通常在毫秒级，对接口影响可以忽略。
 * 若将来日志量很大，可以给 {@code save} 加 {@code @Async} 改成异步落库 ——
 * 但要注意异步线程里拿不到 {@code SecurityContext} 和 {@code RequestContextHolder}，
 * 必须先把需要的字段提取成局部变量再传进去。
 */
@Aspect
@Component
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

    /** 命中则值替换为 ***（小写比较） */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "pwd", "oldpassword", "newpassword", "confirmpassword",
            "studentpwd", "teacherpwd", "adminpwd",
            "secret", "token", "accesstoken", "apikey", "api_key"
    );

    /** 请求参数落库前的长度上限 */
    private static final int MAX_PARAM_LENGTH = 2000;

    /** 异常信息落库前的长度上限（对应字段 varchar(500)） */
    private static final int MAX_ERROR_LENGTH = 500;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private final SysOperationLogService logService;

    public OperationLogAspect(SysOperationLogService logService) {
        this.logService = logService;
    }

    /**
     * 环绕通知：方法执行前后各做一点事。
     *
     * <p>切点表达式用注解的全限定名，注解实例则通过 {@link MethodSignature}
     * 从目标方法上反射取得 —— 而不用 {@code @annotation(参数名)} 做参数绑定。
     * 后者依赖编译时保留形参名（需要 {@code -parameters} 编译参数），
     * 显式反射获取不依赖任何编译选项，更稳妥。
     *
     * @param joinPoint 连接点，可拿到目标方法与入参
     */
    @Around("@annotation(com.online.study.annotation.OperationLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        OperationLog annotation = resolveAnnotation(joinPoint);
        if (annotation == null) {
            // 切点已限定只匹配带注解的方法，这里只是保底
            return joinPoint.proceed();
        }

        long start = System.currentTimeMillis();
        boolean success = true;
        String errorMsg = null;

        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            success = false;
            errorMsg = e.getMessage();
            throw e;    // 异常继续向上抛，业务行为不受影响（交给 GlobalExceptionHandler）
        } finally {
            try {
                saveLog(joinPoint, annotation, success, errorMsg, System.currentTimeMillis() - start);
            } catch (Exception e) {
                // 关键：记录日志失败不能影响业务
                log.warn("写入操作日志失败，模块={}，操作={}，原因={}",
                        annotation.module(), annotation.operation(), e.getMessage());
            }
        }
    }

    /** 从目标方法上取出 @OperationLog 注解 */
    private OperationLog resolveAnnotation(ProceedingJoinPoint joinPoint) {
        if (joinPoint.getSignature() instanceof MethodSignature signature) {
            return signature.getMethod().getAnnotation(OperationLog.class);
        }
        return null;
    }

    /** 组装并落库一条操作日志 */
    private void saveLog(ProceedingJoinPoint joinPoint, OperationLog annotation,
                         boolean success, String errorMsg, long costMs) {
        HttpServletRequest request = currentRequest();

        SysOperationLog entity = new SysOperationLog();
        // 操作人取自 JWT 解析出的可信身份，不是前端传值
        entity.setOperatorId(CurrentUserUtil.getId());
        entity.setOperatorName(CurrentUserUtil.getAccount());
        entity.setOperatorRole(CurrentUserUtil.getRole());

        entity.setModule(annotation.module());
        entity.setOperation(annotation.operation());

        if (request != null) {
            entity.setRequestUri(request.getRequestURI());
            entity.setRequestMethod(request.getMethod());
            entity.setIp(getClientIp(request));
        }

        if (annotation.saveParams()) {
            entity.setRequestParams(truncate(buildParamsJson(joinPoint.getArgs()), MAX_PARAM_LENGTH));
        }

        entity.setSuccess(success ? 1 : 0);
        entity.setErrorMsg(truncate(errorMsg, MAX_ERROR_LENGTH));
        entity.setCostMs(costMs);
        entity.setCreateTime(new Date());

        logService.save(entity);
    }

    /** 取当前请求对象；非 Web 线程（如定时任务）里返回 null */
    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }

    /**
     * 把方法入参序列化成 JSON 并脱敏。
     *
     * <p>两类参数会被跳过：
     * <ul>
     *   <li>Servlet 对象（request / response）—— 无法序列化，会直接抛异常</li>
     *   <li>文件流（MultipartFile / InputStream / OutputStream）—— 内容太大且无意义</li>
     * </ul>
     */
    private String buildParamsJson(Object[] args) {
        List<Object> loggable = new ArrayList<>();
        if (args != null) {
            for (Object arg : args) {
                if (arg == null) {
                    continue;
                }
                if (arg instanceof HttpServletRequest || arg instanceof HttpServletResponse
                        || arg instanceof MultipartFile || arg instanceof InputStream
                        || arg instanceof OutputStream) {
                    continue;
                }
                loggable.add(arg);
            }
        }
        if (loggable.isEmpty()) {
            return null;
        }

        try {
            JsonNode node = OBJECT_MAPPER.valueToTree(loggable);
            maskSensitiveFields(node);
            return OBJECT_MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            // 序列化失败时退化成字符串，不影响日志记录本身
            return String.valueOf(loggable);
        }
    }

    /** 递归把敏感字段的值替换成 *** */
    private void maskSensitiveFields(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node instanceof ObjectNode objectNode) {
            // 先收集字段名再修改，避免遍历过程中修改导致的并发修改异常
            List<String> fieldNames = new ArrayList<>();
            objectNode.fieldNames().forEachRemaining(fieldNames::add);

            for (String name : fieldNames) {
                JsonNode child = objectNode.get(name);
                if (child != null && child.isValueNode() && SENSITIVE_KEYS.contains(name.toLowerCase())) {
                    objectNode.put(name, "***");
                } else {
                    maskSensitiveFields(child);
                }
            }
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::maskSensitiveFields);
        }
    }

    /**
     * 获取客户端真实 IP。
     *
     * <p>部署到 Nginx 反向代理后面之后，{@code getRemoteAddr()} 拿到的是代理服务器的地址，
     * 真实来源在 {@code X-Forwarded-For} 头里（格式是「客户端IP, 代理1, 代理2」），
     * 所以取第一段。
     */
    private String getClientIp(HttpServletRequest request) {
        String[] candidateHeaders = {
                "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"
        };
        for (String header : candidateHeaders) {
            String value = request.getHeader(header);
            if (StringUtils.hasText(value) && !"unknown".equalsIgnoreCase(value)) {
                int commaIndex = value.indexOf(',');
                return commaIndex > 0 ? value.substring(0, commaIndex).trim() : value.trim();
            }
        }
        return request.getRemoteAddr();
    }

    /** 超长截断，避免写库报「Data too long」 */
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
