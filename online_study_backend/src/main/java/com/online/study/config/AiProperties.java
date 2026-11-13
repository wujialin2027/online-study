package com.online.study.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 大模型能力配置（对应 {@code application.yml} 的 {@code ai.*} 段）。
 *
 * <p>为什么用 {@code @ConfigurationProperties} 而不是 {@code @Value}：
 * 四个配置项语义上是一组"AI 能力开关"，集中在一个类里，改字段即改配置，
 * 且支持 yml 的松散绑定（{@code api-key} 自动映射到 {@code apiKey}）。
 *
 * <p><b>安全约定</b>：本类只读取配置，不打印 Key。
 * 真正的 Key 值放在 {@code application.yml}（该文件已被 .gitignore 排除并有 pre-commit 钩子拦截）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /**
     * 使用的 AI 引擎：{@code legacy}（手写协议层）或 {@code spring-ai}（Spring AI 2.0）。
     *
     * <p>切换需要重启，不做运行期热切换 —— 两套引擎的初始化成本不同（Spring AI 要建
     * ChatModel / VectorStore 等一堆 Bean），热切换要维护两套同时存在的状态，
     * 收益远小于复杂度。要对比效果就重启换配置，顺序跑两轮评测即可。
     *
     * <p>写错（拼写错误）不会让服务起不来，会自动回退 legacy 并打 warn，见
     * {@code AiAssistantService#pick}。
     */
    private String engine = "legacy";

    /** 百炼 API Key（形如 sk-xxx）。为空时所有 AI 调用会抛出明确的业务异常 */
    private String apiKey;

    /** OpenAI 兼容端点。通用域名或业务空间专属域名均可 */
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    /** 对话模型 code（用于生成回答） */
    private String chatModel = "deepseek-v4.1-flash";

    /** 向量模型 code（用于把资料转成向量）。注意：入库与检索必须用同一个模型 */
    private String embeddingModel = "text-embedding-v4";

    /** 连接超时（毫秒） */
    private int connectTimeout = 10_000;

    /**
     * 读取超时（毫秒）。大模型逐字生成，比普通接口慢得多，
     * 默认给 60 秒，避免长回答被中途掐断。
     */
    private int readTimeout = 60_000;

    /**
     * 拼出完整接口地址，例如 {@code https://.../compatible-mode/v1} + {@code /embeddings}。
     *
     * <p><b>为什么不让 RestClient 自己拼</b>：给 RestClient 设了 {@code baseUrl}
     * 之后再传一个不带斜杠的相对路径（如 {@code "embeddings"}），Spring 会直接把两段粘起来、
     * <b>不补分隔斜杠</b> —— 实测结果是 {@code .../compatible-mode/v1embeddings}，
     * 服务端只能返回 404。这里手工拼成绝对地址，行为在任何 Spring 版本下都一致。
     *
     * <p>自动容忍前后多余的斜杠：{@code base-url} 结尾写不写 {@code /} 都能正常拼。
     */
    public String endpoint(String path) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (path == null || path.isEmpty()) {
            return base;
        }
        return base + (path.startsWith("/") ? path : "/" + path);
    }

    // ==================== 下面三组：给两个引擎共用的参数 ====================

    /**
     * RAG 检索参数。
     *
     * <p>为什么要把这几个数字从常量搬到配置里：它们是<b>调优对象</b>。
     * 同一份题集，top-k 从 4 改成 6、阈值从 0.28 改成 0.35，召回率和 token 成本都会变。
     * 调优时只改 yml 重启，不用改代码重新打包 —— 对应「企业级 = 可配置」。
     *
     * <p>默认值与 legacy 引擎里的常量完全一致，否则"两个引擎跑同一份题集"的前提就不成立。
     */
    @Data
    public static class Rag {
        /** 每次检索取回几个片段 */
        private int topK = 4;
        /** 相似度下限，低于它的片段直接丢弃（全被丢时至少保留最相关的一条） */
        private double minScore = 0.28;
        /** 切块大小（字） */
        private int chunkSize = 400;
        /** 相邻块重叠字数，避免答案正好被切断在两块中间 */
        private int chunkOverlap = 60;
        /** 知识库最多保留多少块，防止演示机内存被撑爆 */
        private int maxChunks = 500;
    }

    /** 多轮记忆：每次提问带回最近几轮问答（一轮 = 一问一答） */
    @Data
    public static class Memory {
        private int window = 5;
    }

    /** 工具调用 */
    @Data
    public static class Tool {
        /**
         * 一次提问最多允许模型调用几次工具。
         *
         * <p>和 legacy 的 {@code MAX_TOOL_ROUNDS} 是同一个意思，但口径略有差别：
         * legacy 限的是"来回几轮"，这里限的是"总共几次调用"。
         * 一轮里模型要两个工具时，legacy 算 1 次、这里算 2 次 —— 两者都只是护栏，
         * 目的是防止模型陷入"查了又问、问了又查"的死循环把费用烧光。
         */
        private int maxRounds = 5;
    }

    private Rag rag = new Rag();

    private Memory memory = new Memory();

    private Tool tool = new Tool();
}
