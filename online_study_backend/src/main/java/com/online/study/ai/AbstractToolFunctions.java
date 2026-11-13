package com.online.study.ai;

import com.online.study.vo.AiAnswerVO;
import lombok.extern.slf4j.Slf4j;
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
 */
@Slf4j
public abstract class AbstractToolFunctions {

    /** 真正的工具实现 */
    protected final AiToolService toolService;

    /** 本次问答的工具调用轨迹，按发生顺序追加 */
    private final List<AiAnswerVO.ToolTrace> traces = new ArrayList<>();

    protected AbstractToolFunctions(AiToolService toolService) {
        this.toolService = toolService;
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
        try {
            text = action.get();
        } catch (Exception e) {
            success = false;
            log.error("工具 {} 执行失败", name, e);
            text = "工具执行失败：" + e.getMessage();
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
