package com.online.study.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页概览数据（按角色返回不同内容）
 *
 * <p>改造背景：原首页把 4 张表全量拉到浏览器统计，三种角色看到的还是同一个页面 ——
 * 学员登录进来看到的是"系统总用户数"，而这跟他毫无关系。
 *
 * <p>现在由服务端按当前登录角色组装数据：
 * <ul>
 *   <li><b>管理员</b>：平台运营视角（用户 / 课程 / 待审核 / 今日报名）</li>
 *   <li><b>教师</b>：教学视角（我的课程 / 待批改作业 / 报名待审核）</li>
 *   <li><b>学员</b>：学习视角（我的课程 / 待完成作业 / 我的成绩）</li>
 * </ul>
 * 角色取自 JWT，前端无法指定 —— 否则学员传一个 role=admin 就能看到全站数据。
 */
@Data
public class DashboardVO {

    /** 当前角色：admin / teacher / student */
    private String role;

    /** 页面标题，如「我的学习概览」 */
    private String title;

    /** 副标题，如「王同学，欢迎回来」 */
    private String subtitle;

    /** 顶部数字卡片 */
    private List<StatCardVO> cards = new ArrayList<>();

    /** 图表 */
    private List<ChartVO> charts = new ArrayList<>();

    public DashboardVO(String role, String title, String subtitle) {
        this.role = role;
        this.title = title;
        this.subtitle = subtitle;
    }

    public DashboardVO addCard(String label, Object value, String unit, String tip) {
        this.cards.add(new StatCardVO(label, String.valueOf(value), unit, tip));
        return this;
    }

    public DashboardVO addChart(ChartVO chart) {
        this.charts.add(chart);
        return this;
    }
}
