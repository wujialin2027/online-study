package com.online.study.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.online.study.common.ResultCode;
import com.online.study.config.AiProperties;
import com.online.study.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量化客户端 —— 把一段文字变成一串数字（向量）。
 *
 * <p><b>它在 RAG 里的角色</b>：这是"入库"和"检索"两个环节共用的翻译官。
 * 资料入库时把每个知识块翻译成向量，学生提问时把问题也翻译成向量，
 * 然后比较两串数字的距离 —— 距离近就说明意思相近。
 *
 * <p><b>铁律</b>：入库用哪个向量模型，检索就必须用同一个。
 * 不同模型的向量空间不可比（换个模型得出的坐标完全对不上），换模型必须全量重新灌库。
 *
 * <p><b>为什么直接调 HTTP 而不用 Spring AI</b>：见 {@link com.online.study.ai} 包说明。
 * 这里走的是百炼的「OpenAI 兼容」端点，请求体格式与 OpenAI 官方一致。
 */
@Slf4j
@Component
public class AiEmbeddingClient {

    /**
     * 单次请求最多提交多少条文本。
     * 百炼的向量接口对单次批量有条数限制，分批提交比一次性塞进去更稳。
     */
    private static final int BATCH_SIZE = 10;

    private final AiProperties props;
    private final RestClient client;

    public AiEmbeddingClient(AiProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.getConnectTimeout()));
        factory.setReadTimeout(Duration.ofMillis(props.getReadTimeout()));
        // 注意：这里刻意不设 baseUrl —— 改由 props.endpoint(...) 拼绝对地址后传给 .uri(URI)，
        // 否则 Spring 拼接相对路径时不会补斜杠（见 AiProperties#endpoint 的说明）。
        this.client = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 批量向量化。返回的向量顺序与入参文本顺序<b>一一对应</b>。
     *
     * @param texts 待向量化的文本列表
     * @return 与 texts 等长的向量列表
     */
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        requireConfigured();
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += BATCH_SIZE) {
            List<String> batch = texts.subList(i, Math.min(i + BATCH_SIZE, texts.size()));
            vectors.addAll(embedBatch(batch));
            if (texts.size() > BATCH_SIZE) {
                log.debug("向量化进度：{}/{}", Math.min(i + BATCH_SIZE, texts.size()), texts.size());
            }
        }
        return vectors;
    }

    /** 单条文本向量化 */
    public float[] embedOne(String text) {
        List<float[]> vectors = embed(List.of(text));
        if (vectors.isEmpty()) {
            throw new BizException(ResultCode.SYSTEM_ERROR.getCode(), "向量化返回结果为空：" + truncate(text));
        }
        return vectors.get(0);
    }

    /** 是否已在配置里填好 Key（供上层给出手把手的提示，而不是抛 401） */
    public boolean isConfigured() {
        return StringUtils.hasText(props.getApiKey());
    }

    private List<float[]> embedBatch(List<String> batch) {
        String url = props.endpoint("/embeddings");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getEmbeddingModel());
        body.put("input", batch);

        EmbeddingResponse response;
        try {
            response = client.post()
                    .uri(URI.create(url))
                    .body(body)
                    .retrieve()
                    .body(EmbeddingResponse.class);
        } catch (RestClientResponseException e) {
            log.error("向量化调用失败：POST {} -> HTTP {}，响应体={}", url, e.getStatusCode(), e.getResponseBodyAsString());
            throw new BizException(ResultCode.SYSTEM_ERROR.getCode(),
                    "向量化调用失败（HTTP " + e.getStatusCode().value() + "）：" + describe(e, url));
        }

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new BizException(ResultCode.SYSTEM_ERROR.getCode(),
                    "向量化返回为空，请确认模型 code 是否正确：" + props.getEmbeddingModel());
        }

        // 接口不保证返回顺序，按 index 排一次，确保与入参一一对应
        List<EmbeddingResponse.Item> items = new ArrayList<>(response.data());
        items.sort(Comparator.comparingInt(EmbeddingResponse.Item::index));

        List<float[]> vectors = new ArrayList<>(items.size());
        for (EmbeddingResponse.Item item : items) {
            if (item.embedding() == null || item.embedding().length == 0) {
                throw new BizException(ResultCode.SYSTEM_ERROR.getCode(), "向量化返回了空向量");
            }
            vectors.add(item.embedding());
        }
        return vectors;
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new BizException(ResultCode.SYSTEM_ERROR.getCode(),
                    "尚未配置 AI API Key。请在 application.yml 的 ai.api-key 填写百炼 Key 后重启服务。");
        }
    }

    private static String truncate(String text) {
        if (text == null) {
            return "null";
        }
        return text.length() <= 30 ? text : text.substring(0, 30) + "...";
    }

    /**
     * 把异常整理成一句能直接照着排查的话。
     * 服务器返回空响应体时（常见于路径写错导致的 404），补上实际请求地址，
     * 否则界面上只会看到"HTTP 404："后面一片空白，无从下手。
     */
    private static String describe(RestClientResponseException e, String url) {
        String body = e.getResponseBodyAsString();
        if (!StringUtils.hasText(body)) {
            return "服务端未返回内容，请核对 ai.base-url 是否正确（实际请求：" + url + "）";
        }
        return body;
    }

    // ==================== 响应体结构（OpenAI 兼容格式） ====================

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmbeddingResponse(String object, List<Item> data, Usage usage) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Item(int index, float[] embedding) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Usage(Integer prompt_tokens, Integer total_tokens) {
        }
    }
}
