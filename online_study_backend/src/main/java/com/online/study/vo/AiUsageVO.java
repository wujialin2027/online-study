package com.online.study.vo;

import lombok.Data;

import java.util.List;

/**
 * AI 成本看板的一次统计结果。
 *
 * <h3>它回答什么问题</h3>
 * "这套 AI 一共被问了多少次、烧了多少 token、折算多少钱、钱花在哪个引擎和哪个模型上"。
 * 以前这些数字只在日志里滚掉，问起来只能凭感觉；现在一个接口就能拿出来。
 *
 * <h3>为什么把三路分组一次全返回</h3>
 * 按天、按引擎、按模型是同一个数据集三种切法。前端如果要切换视图还得再发三次请求，
 * 而这里的数据量很小（30 天 × 2 引擎 × 几个模型，最多几十行），
 * 一次查库、一次返回，比来回三次更省也更简单。
 *
 * <h3>成本是估算，不是账单</h3>
 * 算法是 {@code 输入token × 输入单价 + 输出token × 输出单价}，单价来自配置。
 * 服务商的实际计费还涉及缓存命中、阶梯价、免费额度抵扣等因素，所以：
 * <ul>
 *   <li>单价与币种随接口返回，前端不要写死"¥"，也不要把这个数当账单对账；</li>
 *   <li>口径说明放在 {@link #note} 里，避免看的人自行脑补一套算法。</li>
 * </ul>
 */
@Data
public class AiUsageVO {

    // ==================== 计价口径（随数据一起返回，别让前端猜） ====================

    /** 币种，如 CNY */
    private String currency;

    /** 输入每百万 token 单价 */
    private Double inputPerMillion;

    /** 输出每百万 token 单价 */
    private Double outputPerMillion;

    /** 统计窗口天数 */
    private Integer days;

    // ==================== 总计（只算 role=assistant 的行，见 note） ====================

    /** 总问答轮数 */
    private Long rounds;

    /** 总输入 token */
    private Long promptTokens;

    /** 总输出 token */
    private Long completionTokens;

    /** 总 token */
    private Long totalTokens;

    /** 估算成本 */
    private Double cost;

    // ==================== 三路分组 ====================

    /** 按天（yyyy-MM-dd），最近的在前 */
    private List<Bucket> byDay;

    /** 按引擎（legacy / spring-ai / 未记录） */
    private List<Bucket> byEngine;

    /** 按模型 code */
    private List<Bucket> byModel;

    /** 口径说明，直接展示给看板使用者 */
    private String note;

    /**
     * 一个分组桶（一天 / 一个引擎 / 一个模型各是一个桶）。
     *
     * <p>{@link #key} 的解释随分组维度不同：按天是日期、按引擎是引擎名、按模型是模型 code。
     */
    @Data
    public static class Bucket {

        /** 分组键 */
        private String key;

        /** 轮数 */
        private Long rounds;

        /** 输入 token */
        private Long promptTokens;

        /** 输出 token */
        private Long completionTokens;

        /** 合计 token */
        private Long totalTokens;

        /** 估算成本 */
        private Double cost;

        /** 平均每轮 token，用来横向比"哪个模型更费"（比总量更公平，总量受使用频率影响） */
        private Long avgTokens;
    }
}
