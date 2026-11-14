package com.online.study.ai;

import com.online.study.config.AiProperties;
import com.online.study.mapper.AiChatMessageMapper;
import com.online.study.mapper.AiUsageRow;
import com.online.study.vo.AiUsageVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 成本看板 —— 把落库的 token 用量汇总成"多少钱"。
 *
 * <h3>为什么这件事值得单独做</h3>
 * token 用量本来就有，但只在日志里滚掉。于是每次被问"这套 AI 一个月花多少钱"，
 * 只能回答"我没细算过"——这一句话就把"做过成本控制"变成"没做过"。
 * 有这张看板之后，答案是具体的数字加一句"一轮 60 题约 17 万 token、折算约 X 元"。
 *
 * <h3>两个必须守住的统计口径</h3>
 * <ol>
 *   <li><b>只统计 assistant 行</b>。一轮问答的输入 token 已经把用户提问包含在内，
 *       把 user 行也算一遍等于同一个问题收两次费，成本凭空翻倍。</li>
 *   <li><b>成本一律按原始 token 现算，不累加已四舍五入的小计</b>。
 *       每轮成本小到 0.000x 元，先舍入再累加会累积出可见误差；
 *       所以总计和小计各自从 token 出发算一次。</li>
 * </ol>
 *
 * <h3>SQL 与 Java 的分工</h3>
 * 数据库只做"分组求和"（{@code GROUP BY day, engine, model}），
 * 计价、排序、合并三种切法都在这里做。原因是单价是配置项 ——
 * 放进 SQL 会让一条 SQL 依赖外部配置，改价要改 SQL，出问题分不清是谁的责任。
 */
@Service
public class AiUsageService {

    private static final Logger log = LoggerFactory.getLogger(AiUsageService.class);

    /** 默认统计最近多少天 */
    private static final int DEFAULT_DAYS = 30;

    /** 天数上限：再长也是同一个量级的数据量，但没有上限就等于允许别人用 ?days=999999 打库 */
    private static final int MAX_DAYS = 365;

    /** 历史数据（v1.9 之前）没有 engine / model，统一显示成这个 */
    private static final String UNKNOWN = "未记录";

    /** 一百万的 double 常量，避免每次除法都写一遍字面量 */
    private static final double MILLION = 1_000_000d;

    private final AiChatMessageMapper mapper;
    private final AiProperties props;

    public AiUsageService(AiChatMessageMapper mapper, AiProperties props) {
        this.mapper = mapper;
        this.props = props;
    }

    /**
     * 汇总最近 {@code days} 天的用量与估算成本。
     *
     * @param days 统计天数，null 或小于 1 时用默认值；超过上限按上限算
     */
    public AiUsageVO summary(Integer days) {
        int window = clampDays(days);
        Date since = Date.from(Instant.now().minus(window, ChronoUnit.DAYS));

        List<AiUsageRow> rows;
        try {
            rows = mapper.aggregateUsage(since);
        } catch (Exception e) {
            // 看板是"锦上添花"的只读统计，表还没迁移时不该把整个请求打成 500，
            // 给一份空数据 + 口径说明，前端照样能渲染，用户也知道是"没有数据"而不是"系统坏了"
            log.warn("查询 AI 用量失败（表可能还没执行 migrate_v1.9.sql）：{}", e.getMessage());
            rows = List.of();
        }

        AiProperties.Pricing pricing = props.getPricing();

        AiUsageVO vo = new AiUsageVO();
        vo.setCurrency(pricing.getCurrency());
        vo.setInputPerMillion(pricing.getInputPerMillion());
        vo.setOutputPerMillion(pricing.getOutputPerMillion());
        vo.setDays(window);

        // ---------- 总计 ----------
        long totalPrompt = 0;
        long totalCompletion = 0;
        long totalRounds = 0;
        for (AiUsageRow row : rows) {
            totalPrompt += nz(row.getPromptTokens());
            totalCompletion += nz(row.getCompletionTokens());
            totalRounds += nz(row.getRounds());
        }
        vo.setRounds(totalRounds);
        vo.setPromptTokens(totalPrompt);
        vo.setCompletionTokens(totalCompletion);
        vo.setTotalTokens(totalPrompt + totalCompletion);
        vo.setCost(cost(totalPrompt, totalCompletion, pricing));

        // ---------- 三路分组 ----------
        vo.setByDay(bucketize(rows, pricing, GroupKey.DAY));
        vo.setByEngine(bucketize(rows, pricing, GroupKey.ENGINE));
        vo.setByModel(bucketize(rows, pricing, GroupKey.MODEL));

        vo.setNote(buildNote(window, totalRounds, pricing));
        return vo;
    }

    /** 分组维度 */
    private enum GroupKey { DAY, ENGINE, MODEL }

    /**
     * 把聚合行按某一维度合并成桶。
     *
     * <p>数据库已经按 (天, 引擎, 模型) 分好组了，这里只是再按单个维度折叠一次 ——
     * 三路视图共用同一次查库结果，不用为每种视图再打一次库。
     */
    private List<AiUsageVO.Bucket> bucketize(List<AiUsageRow> rows,
                                             AiProperties.Pricing pricing,
                                             GroupKey key) {
        // LinkedHashMap 保序，便于结果可预期；排序统一放在最后一步做
        Map<String, long[]> acc = new LinkedHashMap<>();
        for (AiUsageRow row : rows) {
            String k = switch (key) {
                case DAY -> row.getDay() == null ? UNKNOWN : row.getDay();
                case ENGINE -> blankToUnknown(row.getEngine());
                case MODEL -> blankToUnknown(row.getModel());
            };
            long[] a = acc.computeIfAbsent(k, x -> new long[3]);
            a[0] += nz(row.getRounds());
            a[1] += nz(row.getPromptTokens());
            a[2] += nz(row.getCompletionTokens());
        }

        List<AiUsageVO.Bucket> buckets = new ArrayList<>(acc.size());
        acc.forEach((k, a) -> {
            AiUsageVO.Bucket b = new AiUsageVO.Bucket();
            b.setKey(k);
            b.setRounds(a[0]);
            b.setPromptTokens(a[1]);
            b.setCompletionTokens(a[2]);
            b.setTotalTokens(a[1] + a[2]);
            b.setCost(cost(a[1], a[2], pricing));
            b.setAvgTokens(a[0] == 0 ? 0L : (a[1] + a[2]) / a[0]);
            buckets.add(b);
        });

        if (key == GroupKey.DAY) {
            // 日期按字符串倒序即时间倒序（yyyy-MM-dd 是可以直接比大小的格式），最近的在最前
            buckets.sort(Comparator.comparing(AiUsageVO.Bucket::getKey).reversed());
        } else {
            // 引擎 / 模型按 token 量倒序：谁最烧钱排最前，这才是看板要回答的问题
            buckets.sort(Comparator.comparingLong(AiUsageVO.Bucket::getTotalTokens).reversed());
        }
        return buckets;
    }

    /**
     * 估算成本 = 输入 token × 输入单价 + 输出 token × 输出单价（单价按每百万 token 计）。
     *
     * <p>保留 4 位小数：单轮成本常在 0.0003~0.03 元这个量级，只留 2 位会被抹成 0，
     * 看板上就会出现"用了 17 万 token 花了 0 元"的荒唐显示。汇总总量时金额到元级，
     * 多出来的小数位不影响阅读。
     */
    private static double cost(long promptTokens, long completionTokens, AiProperties.Pricing p) {
        double raw = (promptTokens * p.getInputPerMillion()
                + completionTokens * p.getOutputPerMillion()) / MILLION;
        return Math.round(raw * 10_000d) / 10_000d;
    }

    private static int clampDays(Integer days) {
        if (days == null || days < 1) {
            return DEFAULT_DAYS;
        }
        return Math.min(days, MAX_DAYS);
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    private static String blankToUnknown(String v) {
        return (v == null || v.isBlank()) ? UNKNOWN : v;
    }

    /**
     * 口径说明。写死在返回里而不是放在文档里 —— 看板上的人不看文档，
     * 只会盯着数字自己脑补一套算法，然后把估算值当账单。
     */
    private static String buildNote(int days, long rounds, AiProperties.Pricing p) {
        String unit = String.format("输入 %.2f 元/百万 token、输出 %.2f 元/百万 token",
                p.getInputPerMillion(), p.getOutputPerMillion());
        return String.format(
                "统计最近 %d 天的 %d 轮问答，只计 assistant 行（输入 token 已含用户提问，不重复计）。"
                        + "成本为估算值，按%s折算，不含缓存命中与免费额度抵扣，实际以服务商账单为准。",
                days, rounds, unit);
    }
}
