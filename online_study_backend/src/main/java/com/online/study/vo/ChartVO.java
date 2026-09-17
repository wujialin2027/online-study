package com.online.study.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页图表数据
 *
 * <p>刻意设计成「与 ECharts 选项一一对应」的结构，
 * 让前端只做渲染、不做计算：
 * <pre>
 *   categories → xAxis.data 或饼图的扇区名称
 *   series     → series
 * </pre>
 *
 * <p>为什么不让前端自己算：原首页是把 4 张表（学员、教师、课程、报名）
 * 全量拉到浏览器再统计，数据量一上来就会卡，而且密码等字段也会顺着接口出去。
 * 改成后端聚合后，接口只返回几十个数字。
 */
@Data
public class ChartVO {

    /** 图表类型：bar 柱状 / line 折线 / pie 饼图 */
    private String type;

    /** 图表标题 */
    private String title;

    /** 类目轴数据（饼图用作扇区名称） */
    private List<String> categories = new ArrayList<>();

    /** 数据系列 */
    private List<SeriesVO> series = new ArrayList<>();

    /**
     * 参考线数值（可选）。为 null 表示不画参考线。
     *
     * <p>为什么需要它：只有柱子时读者无法判断「这个值算高还是算低」——
     * 「报名 3 人」是好是坏？答案取决于参照系。
     * 画一条参考线（平均分 / 及格线 / 平均报名数）就补上了这个参照系，
     * 这是把「图表」变成「结论」最省力的一步。
     */
    private Long markLineValue;

    /** 参考线文字，如「及格线 60」「平均 12 人」 */
    private String markLineLabel;

    public ChartVO() {
    }

    public ChartVO(String type, String title) {
        this.type = type;
        this.title = title;
    }

    /** 追加一个单系列（最常用）：柱状 / 折线 / 饼图都只有一个系列 */
    public ChartVO withSeries(String name, List<Long> data, List<String> categories) {
        this.categories = categories;
        this.series.add(new SeriesVO(name, data));
        return this;
    }

    /** 追加一条水平/垂直参考线（纵向柱状用 yAxis，横向柱状用 xAxis，前端自动判断） */
    public ChartVO withMarkLine(Long value, String label) {
        this.markLineValue = value;
        this.markLineLabel = label;
        return this;
    }
}
