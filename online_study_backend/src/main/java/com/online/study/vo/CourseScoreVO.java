package com.online.study.vo;

import lombok.Data;

import java.util.Date;

/**
 * 学员成绩页「第一级：课程卡片」的视图对象。
 *
 * <h3>为什么不直接用 score 表</h3>
 * score 表是「学员 × 课程」一行，只有分数，没有「这门课共几次作业、批了几次、
 * 还有几次没批」这些信息 —— 而学员最想知道的恰恰是这些。所以这里不返回实体，
 * 而是由 {@code ScoreController#myCourses} 把 homework + homework_submit + score
 * 三张表聚合出这个对象。
 *
 * <p>统计口径：平均分<b>只统计已批改的作业</b>。未批改的作业按 0 分算会让
 * 学员看到莫名其妙的低分，属于"冤案"。
 */
@Data
public class CourseScoreVO {

    private Integer courseId;

    private String courseName;

    /** 该课程作业总数 */
    private Integer totalCount;

    /** 我已提交的次数 */
    private Integer submittedCount;

    /** 已批改次数（有分数的提交数） */
    private Integer gradedCount;

    /** 已提交、等老师批改的次数 */
    private Integer pendingCount;

    /** 还没提交的次数 */
    private Integer notSubmitCount;

    /** 已批改作业的平均分；一次都没批改时为 null */
    private Integer avgScore;

    /** 课程总评（来自 score 表，由批改接口按平均分重算）；无记录时为 null */
    private Integer totalScore;

    /** 考试分（来自 score 表）；没录入过为 null */
    private Integer examScore;

    /** 最近一次批改时间 */
    private Date lastGradeTime;
}
