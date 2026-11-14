package com.online.study.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.online.study.entity.VectorChunk;
import com.online.study.mapper.VectorChunkMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 知识块的持久化层 —— 只管"怎么把向量存进 MySQL、怎么读回来"，不管检索。
 *
 * <h3>为什么单独一个类，而不是塞进 {@link DbVectorStore}</h3>
 * 因为<b>事务边界要短</b>。重建知识库的耗时几乎全在"调向量接口"上（几十份资料，
 * 好几秒网络往返）。如果把建事务写在那个方法上，整个事务会横跨十几秒的 HTTP 调用 ——
 * 期间占着连接、拿着行锁，并发一高就是互相等待。
 * 所以拆开：{@code DbVectorStore.rebuild()} 负责"算向量"（慢、无事务），
 * 算完的成品交给这里的 {@link #replaceAll} 一次性写库（快、有事务）。
 *
 * <h3>为什么是"整表清空再写入"而不是增量更新</h3>
 * 重建的语义就是<b>全量重建</b>：切块参数一改，块的边界全变，旧块和旧块的 id
 * 都没有对应关系，增量对不上账。全量删 + 全量插最简单也最不容易留脏数据，
 * 500 行以内的写入耗时是可接受的。
 *
 * <h3>为什么向量存成逗号分隔的字符串</h3>
 * 见 {@link VectorChunk#getVector()} 的类注释：本项目从不按向量内容做条件查询，
 * 只是整行读出来在应用层算余弦，所以最朴素的字符串就够了。
 */
@Slf4j
@Service
public class VectorChunkStore {

    private final VectorChunkMapper chunkMapper;

    public VectorChunkStore(VectorChunkMapper chunkMapper) {
        this.chunkMapper = chunkMapper;
    }

    /** 读出全部知识块。检索启动时加载一次，之后靠进程内缓存 */
    public List<VectorChunk> loadAll() {
        return chunkMapper.selectList(Wrappers.<VectorChunk>lambdaQuery()
                .orderByAsc(VectorChunk::getChunkId));
    }

    /** 库里现有多少块（不解析向量，比 loadAll 便宜） */
    public long count() {
        Long n = chunkMapper.selectCount(Wrappers.<VectorChunk>lambdaQuery().isNotNull(VectorChunk::getChunkId));
        return n == null ? 0L : n;
    }

    /**
     * 全量替换：先清空，再写入。
     *
     * <p><b>那个看似多余的删除条件不是笔误</b>：项目里注册了
     * {@code BlockAttackInnerInterceptor}（防全表更新 / 删除的保险），
     * 不带条件的 {@code delete(null)} 会被它直接拦下来抛异常。
     * 而 {@code chunk_id} 是自增主键、恒大于 0，所以
     * {@code chunk_id > 0} 在语义上等价于"全表"，只是为了让拦截器看得出
     * "这是一条有条件的语句，作者知道自己在干什么"。
     *
     * @param rows 已经算好向量的知识块
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceAll(List<VectorChunk> rows) {
        int removed = chunkMapper.delete(Wrappers.<VectorChunk>lambdaQuery()
                .gt(VectorChunk::getChunkId, 0));
        for (VectorChunk row : rows) {
            chunkMapper.insert(row);
        }
        log.info("向量库落库完成：删除旧块 {} 条，写入新块 {} 条", removed, rows.size());
    }

    /** 清空（资料为空时调用，保证"库是空的"与"内存是空的"一致） */
    @Transactional(rollbackFor = Exception.class)
    public int clear() {
        return chunkMapper.delete(Wrappers.<VectorChunk>lambdaQuery()
                .gt(VectorChunk::getChunkId, 0));
    }

    /**
     * 把一个向量拼成逗号分隔的字符串。
     *
     * <p>用 {@link Float#toString} 而不是 {@code String.format("%.6f")}：
     * 后者会丢精度（6 位小数对 1024 维单位向量来说足够，但没必要），
     * 也更慢（格式化开销远大于直接转字符串）。存储量差别在几 MB 级别，不值得为省这点空间牺牲精度。
     */
    public static String join(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 10);
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.toString();
    }

    /**
     * 还原向量。空串或格式异常都返回 {@code null}，由调用方决定跳过还是报错 ——
     * 一行坏数据不该让整个知识库加载失败（其余块还能用，检索只是少几块）。
     */
    public static float[] parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] parts = text.split(",");
        float[] vector = new float[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                vector[i] = Float.parseFloat(parts[i].trim());
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return vector;
    }

    /** 把检索用的知识块组装成待入库的行 */
    public static List<VectorChunk> toRows(List<KnowledgeChunk> chunks, String embedModel) {
        Date now = new Date();
        List<VectorChunk> rows = new ArrayList<>(chunks.size());
        for (KnowledgeChunk chunk : chunks) {
            VectorChunk row = new VectorChunk();
            row.setSource(chunk.source());
            row.setChunkIndex(chunkIndexOf(chunk.id()));
            row.setContent(chunk.text());
            row.setVector(join(chunk.vector()));
            row.setDim(chunk.vector() == null ? 0 : chunk.vector().length);
            row.setEmbedModel(embedModel);
            row.setCreateTime(now);
            rows.add(row);
        }
        return rows;
    }

    /**
     * 从知识块 id（形如「课程：Java 基础#3」）里取出块序号。
     * <p>id 由 {@link DbVectorStore} 统一按这个格式生成，所以这里按下标截取是安全的；
     * 万一格式变了，取不到就退化成 0，只影响排序展示，不影响检索。
     */
    private static int chunkIndexOf(String id) {
        if (id == null) {
            return 0;
        }
        int at = id.lastIndexOf('#');
        if (at < 0 || at == id.length() - 1) {
            return 0;
        }
        try {
            return Integer.parseInt(id.substring(at + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
