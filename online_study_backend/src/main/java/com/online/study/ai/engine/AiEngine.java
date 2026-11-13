package com.online.study.ai.engine;

import com.online.study.vo.AiAnswerVO;

/**
 * AI 引擎 —— 一次提问背后的"大脑"。
 *
 * <h3>为什么要有这层接口</h3>
 * 项目里并存两套实现：
 * <ul>
 *   <li>{@code legacy}：手写协议层（RestClient 直连百炼 + 自己解析 tool_calls），
 *       2026 年 9 月上线时的那套，见 {@link LegacyAiEngine}</li>
 *   <li>{@code spring-ai}：用 Spring AI 2.0 的 ChatClient / Advisor / ToolCallback 重写</li>
 * </ul>
 * 两套实现必须能<b>同时活着、按配置切换</b>，原因有三个：
 * <ol>
 *   <li><b>量尺</b>：同一份 60 题评测集分别跑两套，才有"改造前 88% → 改造后 X%"这张表。
 *       把旧代码删掉，就永远失去了对照组，改造效果只能靠嘴说。</li>
 *   <li><b>降落伞</b>：新引擎答歪了、服务商接口变了、演示前一晚出问题，
 *       改一行 {@code ai.engine: legacy} 重启就回到能跑的状态。</li>
 *   <li><b>兜底</b>：新引擎初始化失败（Key 没配、SDK 加载不到）时自动落回 legacy，
 *       接口不返回 500 —— 这是"企业级 = 可降级"最具体的落点。</li>
 * </ol>
 *
 * <h3>接口为什么只有四个方法</h3>
 * 只暴露"调用方真正需要知道的"：你是谁、能不能用、不能用时怎么跟用户解释、怎么答。
 * 内部用多少轮工具、怎么拼提示词、走哪个 HTTP 客户端，全部是实现细节 ——
 * 换引擎时上层（{@code AiAssistantService} 与 Controller）一行都不用改。
 *
 * <p><b>不放进接口的东西</b>：知识库的灌库 / 状态查询（{@code status} / {@code rebuild}）。
 * 那些操作的对象是"向量库"，两套引擎共用同一份向量库，跟用哪个大脑无关。
 */
public interface AiEngine {

    /**
     * 引擎标识，取值与配置项 {@code ai.engine} 一致：{@code legacy} / {@code spring-ai}。
     * <p>用于配置校验、日志、以及状态接口回显"当前到底在用哪套"。
     */
    String type();

    /**
     * 当前引擎是否可用（Key 配了、依赖齐了、向量库能访问）。
     * <p>返回 false 时 {@link #ask} 会抛出带 {@link #unavailableReason()} 说明的业务异常。
     */
    boolean ready();

    /**
     * 不可用的具体原因，直接展示给前端用户。
     * <p>写清楚"缺什么、去哪补"，而不是"AI 不可用"——后者用户没法自救。
     */
    String unavailableReason();

    /**
     * 回答一个问题，返回答案 + 资料出处 + 工具调用轨迹 + token 用量。
     *
     * <p>入参只收问题本身：<b>身份不从参数传</b>。当前登录人一律由实现内部从 JWT 取
     * （见 {@code AiToolService} 的安全设计），这样"模型能不能查别人的数据"这个问题
     * 在架构层面就不成立 —— 不是靠每个实现自觉，而是接口根本没给这个口子。
     *
     * @param question 用户原话，不做关键词提炼
     */
    AiAnswerVO ask(String question);
}
