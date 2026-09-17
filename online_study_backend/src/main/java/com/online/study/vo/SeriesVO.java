package com.online.study.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 图表数据系列
 *
 * <p>统一对应 ECharts 的 series 概念：
 * 柱状图 / 折线图用 {@code data} 表示每个类目的值；
 * 饼图则用 {@code categories} 作为扇区名称、{@code data} 作为扇区数值。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeriesVO {

    /** 系列名称，如「报名人数」 */
    private String name;

    /** 数据值 */
    private List<Long> data;
}
