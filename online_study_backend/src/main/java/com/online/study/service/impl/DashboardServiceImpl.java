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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

    /** 趋势图天数 */
    private static final int TREND_DAYS = 7;

    /** 报名状态：待审核 / 已通过 / 已驳回 */
    private static final int APPLY_PENDING = 0;
    private static final int APPLY_PASSED = 1;
    private static final int APPLY_REJECTED = 2;

    /** 作业批改状态：未批改 */
    private static final int CORRECT_PENDING = 0;

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

        vo.addCard("学员总数", studentCount, "人", "教师 " + teacherCount + " 人");
        vo.addCard("课程总数", courseCount, "门", "待审核 " + pendingCourse + " 门");
        vo.addCard("今日报名", todayApply, "人次", "累计 " + totalApply + " 条");
        vo.addCard("待办事项", pendingCourse, "项", "课程审核待处理");

        // 图 1：课程报名排行 —— 直接读 course.current_students，
        // 这个字段由报名逻辑原子维护，不需要再 join 报名表做聚合
        List<Course> topCourses = courseService.list(new QueryWrapper<Course>()
                .select("course_id", "course_name", "current_students")
                .orderByDesc("current_students")
                .last("LIMIT " + TOP_COURSE_LIMIT));
        List<String> courseNames = new ArrayList<>();
        List<Long> courseApplyCounts = new ArrayList<>();
        for (Course course : topCourses) {
            courseNames.add(course.getCourseName());
            courseApplyCounts.add(course.getCurrentStudents() == null
                    ? 0L : course.getCurrentStudents().longValue());
        }
        vo.addChart(new ChartVO("bar", "课程报名排行")
                .withSeries("已报名人数", courseApplyCounts, courseNames));

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

        vo.addCard("我的课程", myCourseIds.size(), "门", "共 " + myCourseIds.size() + " 门在教");
        vo.addCard("已发布作业", myHomeworkIds.size(), "个", "累计收到 " + totalSubmit + " 份提交");
        vo.addCard("待批改作业", pendingCorrect, "份",
                pendingCorrect > 0 ? "学生等着看结果" : "暂无待批改");
        vo.addCard("报名待审核", pendingApply, "人",
                pendingApply > 0 ? "请及时处理" : "暂无待处理");

        // 图 1：我的课程报名人数
        List<String> chartCourseNames = new ArrayList<>();
        List<Long> chartCourseApplies = new ArrayList<>();
        if (!myCourseIds.isEmpty()) {
            List<Course> myCourses = courseService.list(new QueryWrapper<Course>()
                    .select("course_id", "course_name", "current_students")
                    .in("course_id", myCourseIds)
                    .orderByDesc("current_students"));
            for (Course course : myCourses) {
                chartCourseNames.add(course.getCourseName());
                chartCourseApplies.add(course.getCurrentStudents() == null
                        ? 0L : course.getCurrentStudents().longValue());
            }
        }
        vo.addChart(new ChartVO("bar", "我的课程报名人数")
                .withSeries("报名人数", chartCourseApplies, chartCourseNames));

        // 图 2：各作业的提交份数
        List<String> homeworkNames = new ArrayList<>();
        List<Long> homeworkSubmitCounts = new ArrayList<>();
        if (!myHomeworkIds.isEmpty()) {
            List<Homework> homeworks = homeworkService.listByIds(myHomeworkIds);
            for (Homework homework : homeworks) {
                homeworkNames.add(homework.getHomeworkName());
                homeworkSubmitCounts.add(homeworkSubmitService.count(new QueryWrapper<HomeworkSubmit>()
                        .eq("homework_id", homework.getHomeworkId())));
            }
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
                pendingCount > 0 ? "另有 " + pendingCount + " 门待审核" : "全部审核完成");
        vo.addCard("待完成作业", todoHomework, "个",
                "已提交 " + submittedCount + " / " + totalHomework + " 份");
        vo.addCard("我的平均成绩", avgScoreText, "分",
                myScores.isEmpty() ? "暂未出成绩" : "共 " + myScores.size() + " 门课程");
        vo.addCard("报名被驳回", rejectedCount, "门",
                rejectedCount > 0 ? "可查看审核意见" : "无");

        // 图 1：我的成绩（按课程）
        List<String> scoreCourseNames = new ArrayList<>();
        List<Long> scoreValues = new ArrayList<>();
        if (!myScores.isEmpty()) {
            List<Integer> scoreCourseIds = myScores.stream()
                    .map(Score::getCourseId).collect(Collectors.toList());
            Map<Integer, String> courseNameMap = courseService.listByIds(scoreCourseIds).stream()
                    .collect(Collectors.toMap(Course::getCourseId, Course::getCourseName, (a, b) -> a));
            for (Score score : myScores) {
                scoreCourseNames.add(courseNameMap.getOrDefault(score.getCourseId(), "未知课程"));
                scoreValues.add(score.getTotalScore() == null ? 0L : score.getTotalScore().longValue());
            }
        }
        vo.addChart(new ChartVO("bar", "我的各科成绩")
                .withSeries("总评成绩", scoreValues, scoreCourseNames));

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
