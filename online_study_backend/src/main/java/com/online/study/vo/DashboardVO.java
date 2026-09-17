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

    /** 进度列表标题，如「我的课程报名进度」。为空表示该角色不需要这一块 */
    private String progressTitle;

    /**
     * 进度列表（一行 = 一门课程）。
     *
     * <p>用来表达「比率 / 状态」这类指标 —— 这不是图表能表达好的东西：
     * 柱状图的刻度是相对的，看不出「3 人报名」是多是少；
     * 进度条的刻度是绝对的（满格 = 名额上限），一眼就能判断。
     */
    private List<ProgressItemVO> progressList = new ArrayList<>();

    public DashboardVO(String role, String title, String subtitle) {
        this.role = role;
        this.title = title;
        this.subtitle = subtitle;
    }

    public DashboardVO addCard(String label, Object value, String unit, String tip) {
        return addCard(label, value, unit, tip, null);
    }

    /**
     * 带跳转的卡片 —— 点一下就能到对应的列表页。
     *
     * @param link 前端路由，如 {@code "/homework?tab=score"}；传 null 表示卡片不可点击。
     *             ⚠️ 必须给**当前角色有权限访问**的路径，否则会被前端路由守卫拦回首页
     *             （例如管理员不能被指到 /courses，那条路由只开放给学员和教师）。
     */
    public DashboardVO addCard(String label, Object value, String unit, String tip, String link) {
        this.cards.add(new StatCardVO(label, String.valueOf(value), unit, tip, link));
        return this;
    }

    public DashboardVO addChart(ChartVO chart) {
        this.charts.add(chart);
        return this;
    }

    /** 设置进度列表整块内容 */
    public DashboardVO setProgress(String title, List<ProgressItemVO> items) {
        this.progressTitle = title;
        this.progressList = items;
        return this;
    }
}
