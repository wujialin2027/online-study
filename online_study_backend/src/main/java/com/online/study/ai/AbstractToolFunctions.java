package com.online.study.ai;

import com.online.study.vo.AiAnswerVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 带 {@code @Tool} 注解的工具对象 —— spring-ai 引擎专用（S4）。
 *
 * <h3>它和 {@link AiToolService} 的分工</h3>
 * <pre>
 *   AiToolService    ：真正干活的那层 —— 查数据库、按角色裁剪、只读、身份从 JWT 取
 *   本类（两套子类）：<b>协议适配层</b> —— 把"工具"这件事按 Spring AI 的规矩重新声明一遍
 * </pre>
 * 一句话：<b>实现只有一份，入口有两个</b>。legacy 引擎走 {@code definitions() + execute(name, json)}
 * （自己拼 OpenAI 的 JSON schema），spring-ai 引擎走 {@code @Tool} 注解（框架读注解生成 schema）。
 * 子类里的方法体几乎都只有一行 {@code toolService.execute(...)}，故意不写任何业务逻辑 ——
 * 一旦两边逻辑分叉，60 题的对照实验就没有意义了。
 *
 * <h3>为什么按角色拆成两个子类，而不是一个类里用 if 判断</h3>
 * Spring AI 扫描的是<b>对象上所有</b> {@code @Tool} 方法，扫到什么就把什么发给模型。
 * 一个类装 5 个工具、再在方法里写"你是学员不许用"，等于把教师工具的定义也发给了学员，
 * 模型仍然知道"有这么一个功能"，只是调了会被拒绝 —— 安全靠"拦"而不是靠"看不见"。
 * 拆成两个类，学员手里那个对象<b>物理上没有</b>教师工具，这才是
 * {@code AiToolService} 里写的"学员根本收不到教师工具的定义"。
 *
 * <h3>这个对象是有状态的，每个请求新建一个</h3>
 * {@code traces} 记录本次问答调过哪些工具。ChatClient 是全局单例（构造一次反复用），
 * 但工具对象<b>不能</b>共用 —— 两人同时提问，轨迹会混到一起。所以由
 * {@code SpringAiEngine#ask} 每次 new 一个，问答结束即丢弃，天然不串。
 *
 * <h3>为什么还要在工具对象里"携带身份"</h3>
 * {@link AiToolService} 判断"我是谁"靠 {@link com.online.study.utils.CurrentUserUtil}，
 * 而它读的是 {@code SecurityContextHolder} 的 <b>ThreadLocal</b>。
 * 同步问答时调用链一直在请求线程上，读得到；<b>但流式不行</b> ——
 * 工具是 Spring AI 在生成过程中回调的，执行线程已经不是当初提交任务的那个线程，
 * ThreadLocal 是空的。现象极具迷惑性：
 * <pre>
 *   学员提问 → 模型答"你当前登录的是教师账号"，或"该工具只对学员开放"
 *   而同一轮问答的对话记录却正确落库为 student / 12
 * </pre>
 * （落库发生在业务线程上、身份正确；工具在别的线程上、身份为空。
 * 空身份在 {@code toolFunctions()} 里走 else 分支被当成教师，于是学员眼睁睁被"降级"。）
 *
 * <p>所以这里在<b>构造时</b>（还在业务线程、上下文一定有效）把身份快照下来，
 * 每次执行工具前绑回当前线程、执行完还原。一处兜住，同步 / 流式 / 未来任何线程模型都不用再操心。
 */
@Slf4j
public abstract class AbstractToolFunctions {

    /** 真正的工具实现 */
    protected final AiToolService toolService;

    /**
     * 构造时捕获的身份快照，执行工具前绑回当前线程。
     * <p>只取 {@code Authentication} 不可变对象复制一份，不共享整个 {@code SecurityContext}，
     * 避免多线程读写同一个上下文实例。
     */
    private final SecurityContext identity;

    /** 本次问答的工具调用轨迹，按发生顺序追加 */
    private final List<AiAnswerVO.ToolTrace> traces = new ArrayList<>();

    protected AbstractToolFunctions(AiToolService toolService) {
        this.toolService = toolService;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SecurityContext snapshot = SecurityContextHolder.createEmptyContext();
        snapshot.setAuthentication(auth);
        this.identity = snapshot;
    }

    /** 本次问答实际调用过的工具（拷贝出去，防止调用方改到内部列表） */
    public List<AiAnswerVO.ToolTrace> traces() {
        return List.copyOf(traces);
    }

    /**
     * 统一执行入口：记轨迹 + 兜异常，然后交给 {@link AiToolService}。
     *
     * <p>为什么要自己 catch：Spring AI 默认会把工具抛出的异常转成一段错误文本回给模型，
     * 但那样 {@code success} 永远是 true，前端"哪次查询失败了"就看不出来。
     * 这里先兜一层，把成功与否记进轨迹，再把同样的错误文本返回给模型 ——
     * 模型照旧能继续组织回答，前端也能显示"这次查询失败"。
     *
     * @param name        工具名（与 {@link ToolSpecs} 的常量一致）
     * @param argsSummary 参数摘要，如 {@code courseName=Java 基础}；无参数传空串
     * @param action      真正要执行的动作
     * @return 给模型看的纯文本结果
     */
    protected String run(String name, String argsSummary, Supplier<String> action) {
        boolean success = true;
        String text;
        // 工具可能在别的线程上执行（流式就是），先把这个请求的身份绑到当前线程，
        // 执行完还原 —— 不能只 clear()，因为线程会被复用，清掉别人的上下文同样是事故。
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContextHolder.setContext(identity);
        try {
            text = action.get();
        } catch (Exception e) {
            success = false;
            log.error("工具 {} 执行失败", name, e);
            text = "工具执行失败：" + e.getMessage();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
        traces.add(AiAnswerVO.ToolTrace.of(name, ToolSpecs.label(name), argsSummary, success));
        return text;
    }

    /**
     * 拼出 {@code AiToolService} 要的那段参数 JSON，如 {@code {"courseName":"Java 基础"}}。
     *
     * <p>为什么手工拼而不是注入 ObjectMapper：这里只有一个可选字符串参数，
     * 拼两行比拉一个依赖简单；但<b>引号必须转义</b> —— 课程名里带双引号时，
     * 不转义就会拼出非法 JSON，{@code AiToolService} 解析失败后按空参数处理，
     * 结果是"过滤条件静默消失、返回全部课程"，那种错最难查。
     */
    protected static String courseNameJson(String courseName) {
        if (!StringUtils.hasText(courseName)) {
            return "{}";
        }
        return "{\"courseName\":\"" + courseName.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }

    /** 参数摘要，与 legacy 引擎里 {@code summarizeArgs} 的写法保持一致 */
    protected static String courseNameSummary(String courseName) {
        return StringUtils.hasText(courseName) ? "courseName=" + courseName : "";
    }
}
