package com.online.study.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.online.study.common.ResultCode;
import com.online.study.entity.Course;
import com.online.study.entity.CourseApply;
import com.online.study.entity.Homework;
import com.online.study.entity.HomeworkSubmit;
import com.online.study.entity.Score;
import com.online.study.entity.Student;
import com.online.study.entity.Teacher;
import com.online.study.exception.BizException;
import com.online.study.service.AdminService;
import com.online.study.service.CourseApplyService;
import com.online.study.service.CourseService;
import com.online.study.service.DashboardService;
import com.online.study.service.HomeworkService;
import com.online.study.service.HomeworkSubmitService;
import com.online.study.service.ScoreService;
import com.online.study.service.StudentService;
import com.online.study.service.TeacherService;
import com.online.study.utils.CurrentUserUtil;
import com.online.study.vo.ChartVO;
import com.online.study.vo.DashboardVO;
import com.online.study.vo.ProgressItemVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 首页数据概览实现
 *
 * <h3>为什么全部改成后端算</h3>
 * 原实现是前端调 4 个 /list 接口把「学员、教师、课程、报名」四张表全量拉到浏览器里，
 * 再用 JS 做 filter 统计。三个问题：
 * <ol>
 *   <li>数据量上千就卡（每次进首页都全表拉一遍）</li>
 *   <li>学员/教师的密码等字段也跟着接口一起出去了</li>
 *   <li>只能做最简单的计数，做不出"近 7 天趋势""待批改作业"这类加工</li>
 * </ol>
 * 现在改成后端聚合，接口只返回几十个数字。
 *
 * <h3>三类角色的口径</h3>
 * <ul>
 *   <li><b>管理员</b>：全站运营数据</li>
 *   <li><b>教师</b>：只看"我的"——我的课程、我的作业提交、我的待审核报名</li>
 *   <li><b>学员</b>：只看"我的"——我的报名（含进度）、待完成作业、我的成绩</li>
 * </ul>
 * 所有"我的"都由 JWT 里的用户 ID 决定，不接受前端传参。
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    /** 报名排行取前几名 */
    private static final int TOP_COURSE_LIMIT = 6;

    /**
     * 图表最多展示多少个类目。
     *
     * <p>两个原因：① 类目太多时 X 轴标签会挤成一片（课程名、作业名都是长中文）；
     * ② 值为 0 的类目（没人报名 / 没有提交）画在图上只是占地方，没有信息量，
     * 所以下面各处都会把 0 值过滤掉。前端还会根据类目数量自动切成横向柱状图。
     */
    private static final int TOP_CHART_ITEMS = 10;

    /** 趋势图天数 */
    private static final int TREND_DAYS = 7;

    /** 报名状态：待审核 / 已通过 / 已驳回 */
    private static final int APPLY_PENDING = 0;
    private static final int APPLY_PASSED = 1;
    private static final int APPLY_REJECTED = 2;

    /** 作业批改状态：未批改 */
    private static final int CORRECT_PENDING = 0;

    /** 报名率达到该百分比即视为「即将满」，进度条变色提醒教师加开 */
    private static final int PROGRESS_WARN_PERCENT = 80;

    /**
     * 剩余名额不超过该值时同样视为「即将满」。
     *
     * <p>为什么不能只看百分比：名额 4 人的小班课只剩 1 个（75%）是该提醒的，
     * 而名额 50 人的大课剩 13 个（74%）反而一点都不急 —— 百分比对班型不敏感，
     * 但要处理的问题（快没位置了）本质是「还剩几个」。
     */
    private static final int NEARLY_FULL_REMAIN = 2;

    /** 及格线：成绩图的参考线（低于这条线的科目一眼可见） */
    private static final int PASS_SCORE = 60;

    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");

    @Autowired
    private StudentService studentService;
    @Autowired
    private TeacherService teacherService;
    @Autowired
    private AdminService adminService;
    @Autowired
    private CourseService courseService;
    @Autowired
    private CourseApplyService courseApplyService;
    @Autowired
    private HomeworkService homeworkService;
    @Autowired
    private HomeworkSubmitService homeworkSubmitService;
    @Autowired
    private ScoreService scoreService;

    @Override
    public DashboardVO overview() {
        String role = CurrentUserUtil.getRole();
        Integer userId = CurrentUserUtil.getId();
        if (userId == null || role == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        switch (role) {
            case "admin":
                return adminView();
            case "teacher":
                return teacherView(userId);
            case "student":
                return studentView(userId);
            default:
                throw new BizException(ResultCode.FORBIDDEN, "当前角色暂不支持数据概览");
        }
    }

    // ========================================================================
    // 管理员视角：平台运营
    // ========================================================================
    private DashboardVO adminView() {
        DashboardVO vo = new DashboardVO("admin", "平台运营概览", "全站数据总览");

        long studentCount = studentService.count();
        long teacherCount = teacherService.count();
        long adminCount = adminService.count();
        long courseCount = courseService.count();
        long pendingCourse = courseService.count(new QueryWrapper<Course>().eq("audit_status", APPLY_PENDING));
        long totalApply = courseApplyService.count();
        long todayApply = courseApplyService.count(new QueryWrapper<CourseApply>()
                .ge("apply_time", LocalDate.now().atStartOfDay()));

        // 管理员只能进 /admin（/courses 那条路由只开放给学员和教师）
        vo.addCard("学员总数", studentCount, "人", "教师 " + teacherCount + " 人", "/admin");
        vo.addCard("课程总数", courseCount, "门", "待审核 " + pendingCourse + " 门", "/admin?tab=course");
        vo.addCard("今日报名", todayApply, "人次", "累计 " + totalApply + " 条", "/admin?tab=course");
        vo.addCard("待办事项", pendingCourse, "项", "课程审核待处理", "/admin?tab=course");

        // 图 1：课程报名排行 —— 直接读 course.current_students，
        // 这个字段由报名逻辑原子维护，不需要再 join 报名表做聚合
        List<Course> topCourses = courseService.list(new QueryWrapper<Course>()
                .select("course_id", "course_name", "current_students")
                .orderByDesc("current_students")
                .last("LIMIT " + TOP_COURSE_LIMIT));
        List<String> courseNames = new ArrayList<>();
        List<Long> courseApplyCounts = new ArrayList<>();
        for (Course course : topCourses) {
            long count = course.getCurrentStudents() == null ? 0L : course.getCurrentStudents().longValue();
            if (count <= 0) {
                // 没人报名的课程不进图：0 高度的柱子只会把图表挤乱
                continue;
            }
            courseNames.add(course.getCourseName());
            courseApplyCounts.add(count);
        }
        // 加一条「上榜平均」参考线：只有柱子的时候，看不出某门课的报名量算高还是算低
        long avgApply = courseApplyCounts.isEmpty() ? 0L : Math.round(
                courseApplyCounts.stream().mapToLong(Long::longValue).average().orElse(0));
        vo.addChart(new ChartVO("bar", "课程报名排行")
                .withSeries("已报名人数", courseApplyCounts, courseNames)
                .withMarkLine(avgApply, "平均 " + avgApply + " 人"));

        // 图 2：近 7 天报名趋势
        vo.addChart(buildApplyTrendChart());

        // 图 3：用户角色分布
        vo.addChart(new ChartVO("pie", "用户角色分布")
                .withSeries("人数", List.of(studentCount, teacherCount, adminCount),
                        List.of("学员", "教师", "管理员")));

        // 图 4：课程审核状态分布
        long passed = courseService.count(new QueryWrapper<Course>().eq("audit_status", APPLY_PASSED));
        long rejected = courseService.count(new QueryWrapper<Course>().eq("audit_status", APPLY_REJECTED));
        vo.addChart(new ChartVO("pie", "课程审核状态")
                .withSeries("课程数", List.of(passed, pendingCourse, rejected),
                        List.of("已通过", "待审核", "已驳回")));

        return vo;
    }

    /**
     * 近 7 天报名趋势。
     *
     * <p>注意必须把没有报名的日期补 0：SQL 的 group by 只会返回有数据的日期，
     * 直接用会导致折线图出现"日期跳跃"，看起来像数据丢了。
     */
    private ChartVO buildApplyTrendChart() {
        LocalDate from = LocalDate.now().minusDays(TREND_DAYS - 1L);

        QueryWrapper<CourseApply> wrapper = new QueryWrapper<>();
        // 用 DATE_FORMAT 直接让数据库返回 "yyyy-MM-dd" 字符串：
        // 若用 DATE(apply_time)，JDBC 可能映射成 java.sql.Timestamp，
        // toString() 会变成 "2026-09-17 00:00:00.0"，与下面的 LocalDate.toString() 对不上，
        // 补齐 7 天时就会全部取不到值而显示成 0
        wrapper.select("DATE_FORMAT(apply_time, '%Y-%m-%d') AS d", "COUNT(*) AS c")
                .ge("apply_time", from.atStartOfDay())
                .groupBy("DATE_FORMAT(apply_time, '%Y-%m-%d')")
                .orderByAsc("d");
        List<Map<String, Object>> rows = courseApplyService.listMaps(wrapper);

        Map<String, Long> countByDate = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object date = row.get("d");
            Object count = row.get("c");
            if (date != null && count instanceof Number number) {
                countByDate.put(date.toString(), number.longValue());
            }
        }

        List<String> categories = new ArrayList<>();
        List<Long> data = new ArrayList<>();
        for (int i = 0; i < TREND_DAYS; i++) {
            LocalDate day = from.plusDays(i);
            categories.add(day.format(DAY_FORMATTER));
            data.add(countByDate.getOrDefault(day.toString(), 0L));
        }
        return new ChartVO("line", "近 " + TREND_DAYS + " 天报名趋势")
                .withSeries("报名数", data, categories);
    }

    // ========================================================================
    // 教师视角：我的教学
    // ========================================================================
    private DashboardVO teacherView(Integer teacherId) {
        Teacher teacher = teacherService.getById(teacherId);
        String name = (teacher == null || teacher.getTeacherName() == null)
                ? "老师" : teacher.getTeacherName();
        DashboardVO vo = new DashboardVO("teacher", "我的教学概览", name + "，这是你的教学数据");

        // 我发布的课程
        List<Integer> myCourseIds = courseService.list(new QueryWrapper<Course>()
                        .select("course_id")
                        .eq("publish_teacher_id", teacherId))
                .stream().map(Course::getCourseId).collect(Collectors.toList());

        // 我发布的作业
        List<Integer> myHomeworkIds = homeworkService.list(new QueryWrapper<Homework>()
                        .select("homework_id")
                        .eq("publish_teacher_id", teacherId))
                .stream().map(Homework::getHomeworkId).collect(Collectors.toList());

        long totalSubmit = 0;
        long pendingCorrect = 0;
        if (!myHomeworkIds.isEmpty()) {
            totalSubmit = homeworkSubmitService.count(new QueryWrapper<HomeworkSubmit>()
                    .in("homework_id", myHomeworkIds));
            pendingCorrect = homeworkSubmitService.count(new QueryWrapper<HomeworkSubmit>()
                    .in("homework_id", myHomeworkIds)
                    .eq("correct_status", CORRECT_PENDING));
        }

        long pendingApply = myCourseIds.isEmpty() ? 0
                : courseApplyService.count(new QueryWrapper<CourseApply>()
                        .in("course_id", myCourseIds)
                        .eq("audit_status", APPLY_PENDING));

        vo.addCard("我的课程", myCourseIds.size(), "门", "共 " + myCourseIds.size() + " 门在教", "/courses");
        vo.addCard("已发布作业", myHomeworkIds.size(), "个", "累计收到 " + totalSubmit + " 份提交", "/homework");
        vo.addCard("待批改作业", pendingCorrect, "份",
                pendingCorrect > 0 ? "学生等着看结果" : "暂无待批改", "/homework");
        vo.addCard("报名待审核", pendingApply, "人",
                pendingApply > 0 ? "请及时处理" : "暂无待处理", "/courses?tab=apply");

        // 报名进度列表（替代原「我的课程报名人数」柱状图）
        //
        // 原图只给出绝对人数：报 3 人算多还是算少，读者无从判断 —— 因为柱状图的
        // 刻度是相对的（最大值占满全格），3 人看着也"不小"。
        // 而教师看到报名数据后真正要做的决定是「哪门课该加开 / 哪门课该推广」，
        // 支撑这个决定的指标是报名率（已报 / 名额上限），刻度绝对，一眼可判。
        vo.setProgress("我的课程报名进度", buildCourseProgress(myCourseIds));

        // 图 2：各作业的提交份数
        // 先收集所有作业的提交数，再统一排序、过滤 0、截断。
        // 原来是直接按作业 id 顺序塞进图表，结果"没人交的作业"（0 值）和有数据的混在一起，
        // 有数据的柱子被挤得几乎看不见。
        List<Map.Entry<String, Long>> submitStats = new ArrayList<>();
        if (!myHomeworkIds.isEmpty()) {
            List<Homework> homeworks = homeworkService.listByIds(myHomeworkIds);
            for (Homework homework : homeworks) {
                long submitted = homeworkSubmitService.count(new QueryWrapper<HomeworkSubmit>()
                        .eq("homework_id", homework.getHomeworkId()));
                submitStats.add(new AbstractMap.SimpleEntry<>(homework.getHomeworkName(), submitted));
            }
        }
        submitStats.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        List<String> homeworkNames = new ArrayList<>();
        List<Long> homeworkSubmitCounts = new ArrayList<>();
        for (Map.Entry<String, Long> entry : submitStats) {
            if (entry.getValue() <= 0 || homeworkNames.size() >= TOP_CHART_ITEMS) {
                continue;
            }
            homeworkNames.add(entry.getKey());
            homeworkSubmitCounts.add(entry.getValue());
        }
        vo.addChart(new ChartVO("bar", "各作业提交份数")
                .withSeries("提交数", homeworkSubmitCounts, homeworkNames));

        // 图 3：我的课程报名审核状态
        if (!myCourseIds.isEmpty()) {
            long passed = courseApplyService.count(new QueryWrapper<CourseApply>()
                    .in("course_id", myCourseIds).eq("audit_status", APPLY_PASSED));
            long rejected = courseApplyService.count(new QueryWrapper<CourseApply>()
                    .in("course_id", myCourseIds).eq("audit_status", APPLY_REJECTED));
            vo.addChart(new ChartVO("pie", "我的课程报名审核")
                    .withSeries("人数", List.of(passed, pendingApply, rejected),
                            List.of("已通过", "待审核", "已驳回")));
        }

        return vo;
    }

    /**
     * 组装「我的课程报名进度」列表。
     *
     * <p>排序规则：报名率高的排前面（要加开的先被看到）；「不限名额」的排最后
     * —— 不愁报满的课不是需要处理的事项。
     *
     * <p>⚠️ {@code max_students = 0} 的业务含义是「不限名额」（报名时用
     * {@code max_students = 0 OR current_students < max_students} 判断），
     * 所以绝不能拿它当除数算百分比 —— 这是这个字段最容易踩的坑。
     */
    private List<ProgressItemVO> buildCourseProgress(List<Integer> courseIds) {
        List<ProgressItemVO> items = new ArrayList<>();
        if (courseIds.isEmpty()) {
            return items;
        }

        List<Course> courses = courseService.list(new QueryWrapper<Course>()
                .select("course_id", "course_name", "current_students", "max_students")
                .in("course_id", courseIds));

        for (Course course : courses) {
            long current = course.getCurrentStudents() == null ? 0L : course.getCurrentStudents().longValue();
            long max = course.getMaxStudents() == null ? 0L : course.getMaxStudents().longValue();

            if (max <= 0) {
                // 不限名额：条填满但由前端用灰色表示「开放」，避免被误读成「已满」
                items.add(new ProgressItemVO(course.getCourseName(), current, 0L, 100,
                        "unlimited", "不限名额 · 已报 " + current + " 人"));
                continue;
            }

            int percent = (int) Math.min(100L, current * 100L / max);
            long remain = max - current;
            String status;
            String tip;
            if (remain <= 0) {
                status = "full";
                tip = "已满 " + current + "/" + max + "，建议加开";
            } else if (percent >= PROGRESS_WARN_PERCENT || remain <= NEARLY_FULL_REMAIN) {
                status = "warning";
                tip = current + "/" + max + "，仅剩 " + remain + " 个名额";
            } else {
                status = "normal";
                tip = current + "/" + max + "，剩 " + remain + " 个名额";
            }
            items.add(new ProgressItemVO(course.getCourseName(), current, max, percent, status, tip));
        }

        // 排序键：不限名额的记 -1（永远排最后），其余用报名率
        items.sort(Comparator
                .comparingInt((ProgressItemVO item) -> "unlimited".equals(item.getStatus())
                        ? -1 : item.getPercent())
                .reversed());
        return items;
    }

    // ========================================================================
    // 学员视角：我的学习
    // ========================================================================
    private DashboardVO studentView(Integer studentId) {
        Student student = studentService.getById(studentId);
        String name = (student == null || student.getStudentName() == null)
                ? "同学" : student.getStudentName();
        DashboardVO vo = new DashboardVO("student", "我的学习概览", name + "，这是你的学习数据");

        // ===== 报名情况 =====
        List<CourseApply> myApplies = courseApplyService.list(new QueryWrapper<CourseApply>()
                .eq("student_id", studentId));

        List<Integer> passedCourseIds = new ArrayList<>();
        long passedCount = 0;
        long pendingCount = 0;
        long rejectedCount = 0;
        for (CourseApply apply : myApplies) {
            int status = apply.getAuditStatus() == null ? APPLY_PENDING : apply.getAuditStatus();
            if (status == APPLY_PASSED) {
                passedCount++;
                passedCourseIds.add(apply.getCourseId());
            } else if (status == APPLY_PENDING) {
                pendingCount++;
            } else {
                rejectedCount++;
            }
        }

        // ===== 作业情况（只统计"审核通过的课程"下的作业）=====
        long totalHomework = 0;
        long submittedCount = 0;
        if (!passedCourseIds.isEmpty()) {
            List<Integer> courseHomeworkIds = homeworkService.list(new QueryWrapper<Homework>()
                            .select("homework_id")
                            .in("course_id", passedCourseIds))
                    .stream().map(Homework::getHomeworkId).collect(Collectors.toList());
            totalHomework = courseHomeworkIds.size();
            if (!courseHomeworkIds.isEmpty()) {
                submittedCount = homeworkSubmitService.count(new QueryWrapper<HomeworkSubmit>()
                        .eq("student_id", studentId)
                        .in("homework_id", courseHomeworkIds));
            }
        }
        long todoHomework = Math.max(0, totalHomework - submittedCount);

        // ===== 成绩情况 =====
        List<Score> myScores = scoreService.list(new QueryWrapper<Score>()
                .eq("student_id", studentId));
        String avgScoreText = "暂无";
        if (!myScores.isEmpty()) {
            double sum = 0;
            int valid = 0;
            for (Score score : myScores) {
                if (score.getTotalScore() != null) {
                    sum += score.getTotalScore();
                    valid++;
                }
            }
            if (valid > 0) {
                avgScoreText = String.format("%.1f", sum / valid);
            }
        }

        vo.addCard("我的课程", passedCount, "门",
                pendingCount > 0 ? "另有 " + pendingCount + " 门待审核" : "全部审核完成", "/courses?tab=enrolled");
        vo.addCard("待完成作业", todoHomework, "个",
                "已提交 " + submittedCount + " / " + totalHomework + " 份", "/homework");
        vo.addCard("我的平均成绩", avgScoreText, "分",
                myScores.isEmpty() ? "暂未出成绩" : "共 " + myScores.size() + " 门课程", "/homework?tab=score");
        vo.addCard("报名被驳回", rejectedCount, "门",
                rejectedCount > 0 ? "可查看审核意见" : "无", "/courses?tab=enrolled");

        // 图 1：我的各科成绩（按分数从高到低）
        // ① 过滤还没出成绩的记录 —— 把 null 当成 0 分会让人误以为"考了 0 分"；
        // ② 降序排列，最弱的科目落在末尾，一眼可见；
        // ③ 画一条及格线作参照系 —— 只有柱子的时候，没人知道"76 分"算好还是算差。
        List<String> scoreCourseNames = new ArrayList<>();
        List<Long> scoreValues = new ArrayList<>();
        List<Score> allValidScores = myScores.stream()
                .filter(score -> score.getTotalScore() != null)
                .sorted(Comparator.comparingInt(Score::getTotalScore).reversed())
                .collect(Collectors.toList());
        List<Score> validScores = allValidScores.stream()
                .limit(TOP_CHART_ITEMS)
                .collect(Collectors.toList());
        if (!validScores.isEmpty()) {
            List<Integer> scoreCourseIds = validScores.stream()
                    .map(Score::getCourseId).collect(Collectors.toList());
            Map<Integer, String> courseNameMap = courseService.listByIds(scoreCourseIds).stream()
                    .collect(Collectors.toMap(Course::getCourseId, Course::getCourseName, (a, b) -> a));
            for (Score score : validScores) {
                scoreCourseNames.add(courseNameMap.getOrDefault(score.getCourseId(), "未知课程"));
                scoreValues.add(score.getTotalScore().longValue());
            }
        }
        // 标题里写清总数：图表最多画 10 条柱子，若不说明，
        // 选了十几门课的学员会以为"我只有 10 门成绩"，其实是被截断了
        String scoreChartTitle = allValidScores.size() > TOP_CHART_ITEMS
                ? "我的各科成绩（显示前 " + TOP_CHART_ITEMS + " 门 / 共 " + allValidScores.size() + " 门）"
                : "我的各科成绩（共 " + allValidScores.size() + " 门）";
        vo.addChart(new ChartVO("bar", scoreChartTitle)
                .withSeries("总评成绩", scoreValues, scoreCourseNames)
                .withMarkLine((long) PASS_SCORE, "及格线 " + PASS_SCORE));

        // 图 2：我的报名状态分布
        vo.addChart(new ChartVO("pie", "我的报名状态")
                .withSeries("课程数", List.of(passedCount, pendingCount, rejectedCount),
                        List.of("已通过", "待审核", "已驳回")));

        // 图 3：作业提交情况
        vo.addChart(new ChartVO("pie", "作业提交情况")
                .withSeries("数量", List.of(submittedCount, todoHomework),
                        List.of("已提交", "待完成")));

        return vo;
    }
}
