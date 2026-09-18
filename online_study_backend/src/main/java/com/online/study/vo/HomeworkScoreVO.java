package com.online.study.vo;

import lombok.Data;

import java.util.Date;

/**
 * 学员成绩页「第二级：某门课的作业明细」的视图对象。
 *
 * <p>一行 = 该课程的一次作业。注意<b>未提交的作业也要返回</b>：
 * 学员需要看到"这门课还有 2 次作业没交"，否则会以为作业就这几次。
 *
 * <p>{@code status} 三态：
 * <ul>
 *   <li>{@code notSubmitted} —— 没交（未提交）</li>
 *   <li>{@code pending} —— 交了、老师还没批（待批改）</li>
 *   <li>{@code graded} —— 已批改，{@code score} 有值</li>
 * </ul>
 */
@Data
public class HomeworkScoreVO {

    public static final String STATUS_NOT_SUBMITTED = "notSubmitted";
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_GRADED = "graded";

    private Integer homeworkId;

    private String homeworkName;

    /** 截止时间 */
    private Date deadline;

    /** 我的提交记录 ID；没交时为 null */
    private Integer submitId;

    /** 提交时间；没交时为 null */
    private Date submitTime;

    /** notSubmitted / pending / graded，供前端配色用 */
    private String status;

    /** 状态中文文案，后端直接给，前端不必再写一层映射 */
    private String statusText;

    /** 本次得分；未批改时为 null */
    private Integer score;

    private String scoreComment;

    /** 评分教师姓名；未批改时为 null */
    private String teacherName;

    private Date scoreTime;
}
