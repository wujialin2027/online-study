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
}
