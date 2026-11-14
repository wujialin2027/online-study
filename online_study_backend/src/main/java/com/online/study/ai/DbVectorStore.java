package com.online.study.ai;

import com.online.study.common.ResultCode;
import com.online.study.config.AiProperties;
import com.online.study.entity.VectorChunk;
import com.online.study.exception.BizException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 向量库 —— 存知识块，并按「意思相近」检索。
 *
 * <p><b>它解决什么问题</b>：学生问「作业啥时候交」，课件里写的是「提交截止日期：10 月 8 日」。
 * 用关键词搜（SQL 的 LIKE）一个字都对不上，搜不到；用向量比相似度就能找到，
 * 因为这两句话的向量坐标挨得很近。
 *
 * <h3>两层结构（S6 起）：库是真相，内存是索引</h3>
 * <pre>
 *   ai_vector_chunk 表  ← 真相。向量算一次存下来，重启还在，不再重复调向量接口
 *          ↓ 启动时读一次
 *   进程内 List&lt;KnowledgeChunk&gt;  ← 索引/缓存。检索要在内存里算余弦，读库太慢
 * </pre>
 * 改造前（v1.7 及以前）只有下面这一层，服务一重启知识库就空了，下次提问得把几十份资料
 * 重新向量化一遍（几秒到十几秒，每次都花 token）。现在只有"重建知识库"这一个动作会真正
 * 调向量接口，其余时候都是读库。
 *
 * <p><b>为什么不干脆每次检索都查库</b>：检索要把"问题向量"和库里每一条向量算余弦，
 * 是 O(n) 全量比较。读一次缓存进内存，500 块算一遍是毫秒级；每次查库再反序列化，
 * 等于把网络和解析开销乘上每题一次。所以是"库负责不丢，内存负责快"。
 *
 * <p><b>这套做法什么时候就不够用了</b>：知识块上万以后，"全表读进内存"和"内存里逐个算余弦"
 * 都会变成瓶颈，那时要换 pgvector / Milvus 这类带向量索引的库，把 top-k 交给库算。
 * 本项目上限 500 块，用不着 —— 但这是<u>规模决定的取舍</u>，不是"这样最好"。
 *
 * <p><b>检索算法</b>：余弦相似度。向量是有方向的箭头，不是有长度的尺子 ——
 * 比较两个箭头的夹角比比较长度更能反映"意思像不像"，
 * 所以用点积除以两个模长的乘积，取值 [-1, 1]，越接近 1 越相似。
 */
@Slf4j
@Component
public class DbVectorStore {

    /** 每个知识块最多多少字。切太碎会丢上下文，切太大检索不精准，400 是兼顾两者的经验值 */
    private static final int CHUNK_SIZE = 400;

    /** 相邻块之间的重叠字数。防止一句话正好被切断在两块之间，检索时两头都捞不全 */
    private static final int OVERLAP = 60;

    /** 单次灌库的最大块数保护 —— 防止误操作把海量无关内容灌进去白烧 token */
    private static final int MAX_CHUNKS = 500;

    private final AiEmbeddingClient embeddingClient;
    private final VectorChunkStore chunkStore;
    private final AiProperties props;

    /** 用 volatile + 整体替换的方式保证并发安全：读检索不加锁，写入时一次性换掉引用 */
    private volatile List<KnowledgeChunk> chunks = List.of();

    /** 向量维度，来自第一次成功返回的向量长度。用于校验"入库与检索用的是同一个模型" */
    private volatile int dimension = 0;

    /** 最近一次灌库完成的时间戳（毫秒） */
    private volatile long loadedAt = 0L;

    public DbVectorStore(AiEmbeddingClient embeddingClient, VectorChunkStore chunkStore, AiProperties props) {
        this.embeddingClient = embeddingClient;
        this.chunkStore = chunkStore;
        this.props = props;
    }

    /**
     * 启动时把库里的向量读进内存 —— <b>这是"重启不再丢"的落点</b>。
     *
     * <p>读失败不炸启动，只打 warn 并按空库处理：最常见的原因是
     * <b>忘了执行 {@code sql/migrate_v1.8.sql}</b>。这种情况下服务仍然能起来，
     * 教师问"资料有多少块"会看到 0，点一次「重建知识库」就会把缺表的问题暴露成明确报错。
     * 相比"启动直接失败、整站不可用"，这个降级更合适 —— 向量库只是 AI 助教的一个依赖，
     * 不该拖垮课程 / 作业这些主功能。
     */
    @PostConstruct
    void hydrate() {
        List<VectorChunk> rows;
        try {
            rows = chunkStore.loadAll();
        } catch (Exception e) {
            log.warn("从数据库加载向量库失败（sql/migrate_v1.8.sql 是否已执行？），本次按空库处理：{}",
                    e.getMessage());
            return;
        }
        if (rows.isEmpty()) {
            log.info("向量库为空（ai_vector_chunk 无数据），首次提问时会自动重建");
            return;
        }

        List<KnowledgeChunk> loaded = new ArrayList<>(rows.size());
        Set<String> models = new LinkedHashSet<>();
        long latest = 0L;
        int dim = 0;
        for (VectorChunk row : rows) {
            float[] vector = VectorChunkStore.parse(row.getVector());
            if (vector == null || vector.length == 0) {
                // 单行坏数据只跳过，不让整个知识库加载失败
                log.warn("知识块 #{}（{}）向量解析失败，本次跳过", row.getChunkId(), row.getSource());
                continue;
            }
            dim = vector.length;
            loaded.add(new KnowledgeChunk(
                    row.getSource() + "#" + row.getChunkIndex(),
                    row.getSource(),
                    row.getContent(),
                    vector));
            if (row.getEmbedModel() != null) {
                models.add(row.getEmbedModel());
            }
            if (row.getCreateTime() != null) {
                latest = Math.max(latest, row.getCreateTime().getTime());
            }
        }
        if (loaded.isEmpty()) {
            log.warn("ai_vector_chunk 有 {} 行但没一行能解析出向量，按空库处理，请重建知识库", rows.size());
            return;
        }

        applyCache(loaded, dim, latest);

        // 模型换了但库没重灌 —— 这个坑必须显式提示，否则检索会集体变成"查不到"，
        // 看起来像"知识库没数据"，实际是两套向量不在同一个坐标系里。
        String current = props.getEmbeddingModel();
        if (current != null && !models.isEmpty() && !models.contains(current)) {
            log.warn("向量库是用 {} 生成的，当前配置是 {} —— 两套向量不在同一空间，请重建知识库",
                    models, current);
        }
        log.info("向量库已从数据库加载：{} 块，维度 {}，更新时间 {}",
                loaded.size(), dim, loadedAtText());
    }

    /**
     * 重建：把原始资料切块 → 向量化 → 写库 → 换缓存。
     * <p>调用前请确认资料没有重复，否则检索结果会出现重复片段。
     *
     * @param docs 原始资料列表
     * @return 实际入库的块数
     */
    public synchronized int rebuild(List<SourceDoc> docs) {
        if (docs == null || docs.isEmpty()) {
            chunkStore.clear();
            this.chunks = List.of();
            this.dimension = 0;
            this.loadedAt = System.currentTimeMillis();
            log.info("向量库已清空（没有可入库的资料）");
            return 0;
        }

        // 1) 切块，并记下每块来自哪份资料
        List<KnowledgeChunk> pending = new ArrayList<>();
        for (SourceDoc doc : docs) {
            List<String> pieces = splitIntoChunks(doc.text());
            for (int i = 0; i < pieces.size(); i++) {
                pending.add(new KnowledgeChunk(
                        doc.source() + "#" + (i + 1),
                        doc.source(),
                        pieces.get(i),
                        null
                ));
            }
        }
        if (pending.isEmpty()) {
            chunkStore.clear();
            this.chunks = List.of();
            this.loadedAt = System.currentTimeMillis();
            log.warn("切块后没有任何内容，请检查资料是否为空");
            return 0;
        }
        if (pending.size() > MAX_CHUNKS) {
            log.warn("切块数量 {} 超过上限 {}，只保留前 {} 块", pending.size(), MAX_CHUNKS, MAX_CHUNKS);
            pending = pending.subList(0, MAX_CHUNKS);
        }

        // 2) 批量向量化（客户端内部会自动分批）。最慢的一步，放在事务外面做
        List<String> texts = pending.stream().map(KnowledgeChunk::text).toList();
        long start = System.currentTimeMillis();
        List<float[]> vectors = embeddingClient.embed(texts);
        if (vectors.size() != pending.size()) {
            throw new BizException(ResultCode.SYSTEM_ERROR.getCode(),
                    "向量化结果数量与知识块数量不一致：" + vectors.size() + " vs " + pending.size());
        }

        // 3) 组装成最终的知识块（向量随块存储，检索时不再重复调用接口）
        List<KnowledgeChunk> built = new ArrayList<>(pending.size());
        for (int i = 0; i < pending.size(); i++) {
            KnowledgeChunk p = pending.get(i);
            built.add(new KnowledgeChunk(p.id(), p.source(), p.text(), vectors.get(i)));
        }

        // 4) 先落库，再换内存 —— 顺序不能反。
        // 反过来的话，写库失败时内存里已经是新的、库里还是旧的：下一次重启知识库会"退回上一版"，
        // 而用户完全不知道发生了什么。先写库再换缓存，失败最多是这次重建没生效，库里仍是完整的一版。
        chunkStore.replaceAll(VectorChunkStore.toRows(built, props.getEmbeddingModel()));

        applyCache(built, vectors.get(0).length, System.currentTimeMillis());
        log.info("向量库重建完成：{} 份资料 → {} 个知识块，维度 {}，耗时 {} ms",
                docs.size(), built.size(), dimension, System.currentTimeMillis() - start);
        return built.size();
    }

    /**
     * 按问题检索最相关的知识块。
     *
     * @param question 学生的问题（原话即可，不需要关键词）
     * @param topK     取最相似的几块。3~5 块通常够用：太少可能漏掉答案，太多会稀释提示词
     */
    public List<SearchHit> search(String question, int topK) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        if (chunks.isEmpty()) {
            return List.of();
        }
        float[] queryVector = embeddingClient.embedOne(question);
        if (dimension > 0 && queryVector.length != dimension) {
            throw new BizException(ResultCode.SYSTEM_ERROR.getCode(),
                    "问题向量维度(" + queryVector.length + ")与知识库维度(" + dimension
                            + ")不一致，说明入库与检索用了不同的向量模型，请重新灌库");
        }
        return search(queryVector, topK);
    }

    /** 用现成的向量检索（供调试或"以文搜文"场景使用） */
    public List<SearchHit> search(float[] queryVector, int topK) {
        if (queryVector == null || chunks.isEmpty()) {
            return List.of();
        }
        int limit = topK <= 0 ? 3 : topK;
        return chunks.stream()
                .map(chunk -> new SearchHit(chunk, cosine(queryVector, chunk.vector())))
                .sorted(Comparator.comparingDouble(SearchHit::score).reversed())
                .limit(limit)
                .toList();
    }

    /** 当前入库的知识块数量 */
    public int size() {
        return chunks.size();
    }

    /** 向量维度（0 表示还没灌过库） */
    public int dimension() {
        return dimension;
    }

    /** 最近一次灌库时间；0 表示从未灌库 */
    public long loadedAt() {
        return loadedAt;
    }

    /** 是否已灌库 */
    public boolean isEmpty() {
        return chunks.isEmpty();
    }

    /** 灌库时间的可读形式，供接口返回给前端展示"资料更新时间" */
    public String loadedAtText() {
        return loadedAt == 0L ? "尚未灌库" : Instant.ofEpochMilli(loadedAt).toString();
    }

    /** 缓存与元信息一起换，避免并发下读到"新块 + 旧维度"这种半截状态 */
    private void applyCache(List<KnowledgeChunk> built, int dim, long at) {
        this.chunks = List.copyOf(built);
        this.dimension = dim;
        this.loadedAt = at;
    }

    // ==================== 切块 ====================

    /**
     * 把长文本切成知识块。
     *
     * <p>策略：固定窗口（{@link #CHUNK_SIZE} 字）+ 相邻重叠（{@link #OVERLAP} 字），
     * 并且<b>优先在句末标点或换行处断开</b> —— 硬切会把一句话劈成两半，语义就断了。
     *
     * <p>这三个数字目前是常量，与 {@code ai.rag.chunk-size / chunk-overlap / max-chunks}
     * 的默认值一致。之所以还没接配置：<b>改了切块参数必须重灌整个知识库</b>，
     * 它不是"改完重启就生效"的参数，属于 P3 调优阶段要和评测一起动的对象。
     */
    private List<String> splitIntoChunks(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        String text = raw.replace("\r\n", "\n").trim();
        int length = text.length();
        int start = 0;
        while (start < length) {
            int end = Math.min(start + CHUNK_SIZE, length);
            if (end < length) {
                int cut = lastBreakIndex(text, start, end);
                if (cut > start) {
                    end = cut;
                }
            }
            String piece = text.substring(start, end).trim();
            if (!piece.isEmpty()) {
                result.add(piece);
            }
            if (end >= length) {
                break;
            }
            // 回退 OVERLAP 个字符做重叠，但至少前进 1 个字符，避免死循环
            start = Math.max(end - OVERLAP, start + 1);
        }
        return result;
    }

    /**
     * 在 [start, end) 区间里倒着找最近的一个"可以断句"的位置。
     * 只在下半段找 —— 否则块长度会被压得远小于 CHUNK_SIZE。
     */
    private int lastBreakIndex(String text, int start, int end) {
        int floor = start + CHUNK_SIZE / 2;
        for (int i = end - 1; i > floor; i--) {
            char c = text.charAt(i);
            if (c == '\n' || c == '。' || c == '！' || c == '？' || c == '；'
                    || c == '.' || c == '!' || c == '?' || c == ';') {
                return i + 1;
            }
        }
        return -1;
    }

    // ==================== 相似度 ====================

    /** 余弦相似度：点积 / (模长 × 模长)，取值 [-1, 1]，越接近 1 越相似 */
    private static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0d;
        }
        double dot = 0d;
        double normA = 0d;
        double normB = 0d;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0d || normB == 0d) {
            return 0d;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // ==================== 辅助类型 ====================

    /**
     * 一份待入库的原始资料。
     *
     * @param source 来源标题（会作为回答的出处展示给用户）
     * @param text   资料正文（可以很长，入库时自动切块）
     */
    public record SourceDoc(String source, String text) {
    }

    /**
     * 一条检索结果。
     *
     * @param chunk 命中的知识块
     * @param score 相似度得分，越接近 1 越相关
     */
    public record SearchHit(KnowledgeChunk chunk, double score) {

        /** 得分百分比，便于前端直接显示（如 0.8732 → 87.3） */
        public double percent() {
            return Math.round(score * 1000d) / 10d;
        }

        /** 调试用：入库时间不参与展示，这里只保留必要的只读信息 */
        public String debugLine() {
            return "[" + percent() + "%] " + chunk.source();
        }
    }
}
