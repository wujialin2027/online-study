package com.online.study.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 首页「报名进度」条目（一行 = 一门课程）
 *
 * <p>为什么不用柱状图表示报名情况：
 * 柱状图回答的是「谁比谁多」，而柱状图的刻度是<b>相对</b>的（最大值占满全格），
 * 于是「报名 3 人」这种数字看起来并不小，教师无法判断这门课到底是热还是冷。
 *
 * <p>教师看到报名数据后真正要做的决定是：
 * <b>哪门课该加开 / 哪门课该推广 / 哪门课考虑停掉</b>。
 * 支撑这个决定的指标不是绝对人数，而是<b>报名率（已报名 / 名额上限）</b> ——
 * 进度条的刻度是绝对的（满格 = 名额上限），一眼就能看出谁快满、谁冷清。
 *
 * <p>状态与颜色由服务端算好，前端只做渲染：
 * <ul>
 *   <li>{@code normal} —— 报名率 &lt; 80%，正常</li>
 *   <li>{@code warning} —— 报名率 ≥ 80%，即将满，建议加开</li>
 *   <li>{@code full} —— 已满，无法继续报名</li>
 *   <li>{@code unlimited} —— 名额上限为 0，表示不限名额</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProgressItemVO {

    /** 课程名称 */
    private String label;

    /** 已报名人数 */
    private Long current;

    /** 名额上限（0 表示不限名额） */
    private Long total;

    /** 报名率百分比 0~100（服务端算好，前端不做除法，避免 0 除与取整差异） */
    private Integer percent;

    /** 状态：normal / warning / full / unlimited，前端据此着色 */
    private String status;

    /** 右侧文字说明，如「剩 12 个名额」「已满」「不限名额」 */
    private String tip;
}
