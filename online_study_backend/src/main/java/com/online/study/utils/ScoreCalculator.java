package com.online.study.utils;

import java.util.Collection;

/**
 * 分数计算工具。
 *
 * <h3>为什么要有这个类</h3>
 * 原代码在前端这样算总评（{@code Homework.vue} 的旧 {@code doGrade}）：
 * <pre>
 *   totalScore = round((homeworkScore + examScore) / 2)
 * </pre>
 * 而新建成绩记录时 {@code examScore} 被写死成 {@code 0}，于是：
 * <pre>
 *   作业打了 90 分  →  总成绩 = (90 + 0) / 2 = 45
 * </pre>
 * 学员会看到一个平白少了一半的分数。<b>根因是把"没录入"当成了"考了 0 分"。
 * 现在改为：没录入考试分时直接取作业分；录入了才按各占一半加权。</b>
 *
 * <p>把这段规则收进工具类，是为了让「教师批改」和「成绩接口」用同一套算法 ——
 * 两边各写一份，迟早会算出两个不一样的数字。
 */
public final class ScoreCalculator {

    private ScoreCalculator() {
        // 工具类不允许实例化
    }

    /**
     * 课程总评。
     *
     * <p><b>关于 0 的含义</b>：本项目的 {@code score.exam_score} 用 {@code 0} 表示
     * 「尚未录入考试分」（最初的成绩录入界面就是这么写库的，历史数据里全是 0）。
     * 因此这里把 {@code null} 与 {@code 0} 一并当作「未录入」，直接取作业分 ——
     * 否则 90 分的作业会被算成总评 45 分。
     *
     * @param homeworkScore 作业平均分（可为 null = 还没有任何作业被批改）
     * @param examScore     考试分（null 或 0 = 尚未录入）
     * @return 两者都为空时返回 null，由调用方决定是否落库
     */
    public static Integer total(Integer homeworkScore, Integer examScore) {
        if (homeworkScore == null) {
            return (examScore == null || examScore <= 0) ? null : examScore;
        }
        if (examScore == null || examScore <= 0) {
            return homeworkScore;
        }
        return (int) Math.round(homeworkScore * 0.5 + examScore * 0.5);
    }

    /**
     * 平均分（四舍五入到整数）。
     *
     * @param scores 参与计算的分值，null 元素会被跳过
     * @return 没有有效分值时返回 null —— 而不是 0，避免"一次没批 = 0 分"
     */
    public static Integer average(Collection<Integer> scores) {
        if (scores == null || scores.isEmpty()) {
            return null;
        }
        int sum = 0;
        int count = 0;
        for (Integer s : scores) {
            if (s != null) {
                sum += s;
                count++;
            }
        }
        return count == 0 ? null : (int) Math.round((double) sum / count);
    }
}
