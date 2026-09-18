package com.online.study.vo;

import lombok.Data;

import java.util.List;

/**
 * 某门课程的作业提交统计（教师端作业列表用）
 *
 * <h3>为什么不让前端自己算</h3>
 * 「谁交了、谁没交」需要两个数据源：课程的在读学员数（分母）与每份作业的提交数（分子）。
 * 前端要算出来，就得对每份作业各发一次 {@code /homework-submit/query} ——
 * 3 份作业 = 3 次请求，10 份就是 10 次，典型的 N+1 挪到了浏览器侧。
 * 这里一次请求把整门课的统计一起返回，页面拿到的是可以直接渲染的数字。
 *
 * <h3>分母为什么取「已通过审核的报名数」</h3>
 * 待审核的报名还没确定能不能上课，把他们算进分母会让「未交」永远大于 0；
 * 已驳回的更不该算。只有 {@code audit_status = 1} 的学员才是真正要交作业的人。
 */
@Data
public class HomeworkSubmitStatsVO {

    /** 在读学员数（已通过审核的报名数），即「应交作业」的分母 */
    private Integer studentCount;

    /** 每份作业的统计，顺序与作业列表一致 */
    private List<Item> items;

    /** 单份作业的提交统计 */
    @Data
    public static class Item {

        private Integer homeworkId;

        /** 已提交人数 */
        private Integer submitCount;

        /** 已批改份数 */
        private Integer gradedCount;

        /** 待批改份数（已提交但还没打分） */
        private Integer pendingCount;
    }
}
