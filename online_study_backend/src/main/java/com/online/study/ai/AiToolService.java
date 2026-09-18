package com.online.study.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.online.study.entity.Course;
import com.online.study.entity.CourseApply;
import com.online.study.entity.Homework;
import com.online.study.entity.HomeworkSubmit;
import com.online.study.entity.Score;
import com.online.study.entity.Student;
import com.online.study.mapper.CourseApplyMapper;
import com.online.study.mapper.CourseMapper;
import com.online.study.mapper.HomeworkMapper;
import com.online.study.mapper.HomeworkSubmitMapper;
import com.online.study.mapper.ScoreMapper;
import com.online.study.mapper.StudentMapper;
import com.online.study.utils.CurrentUserUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 智能助教的「工具」——让大模型能查数据库。
 *
 * <h3>它和 RAG 检索的区别</h3>
 * <pre>
 *   RAG 检索   ：翻小抄    —— 查静态资料（课件、作业要求），内容是"快照"
 *   工具调用   ：打电话问人 —— 查动态数据（我还有哪几门作业没交），每次都是实时结果
 * </pre>
 * 学生问「作业什么时候交」，RAG 能答（截止时间写在作业要求里）；
 * 学生问「我交了没」，RAG 答不了 —— 那是这个学生的个人数据，向量库里根本没有，
 * 而工具会带着当前登录身份去数据库里现查。
 *
 * <h3>模型怎么知道该调哪个工具</h3>
 * 我们把每个工具的「名字 + 用途说明 + 参数格式」随请求一起发给模型（见 {@link #definitions()}），
 * 模型自己判断要不要调、调哪个、参数填什么，返回一个 {@code tool_calls} 结构。
 * 我们执行完把结果回传，模型再据此生成最终回答。整个过程对用户是透明的。
 *
 * <h3>🔒 安全设计（这一段是面试重点）</h3>
 * <ul>
 *   <li><b>身份只从 JWT 取，绝不接受模型传入的 ID。</b> 工具的参数里没有 {@code studentId}
 *       这一项 —— 否则学生只要问"帮我查一下 studentId=7 的作业"，模型就可能带上别人的 ID，
 *       等于把越权查询的口子交给了自然语言。这里一律用 {@link CurrentUserUtil} 取当前登录人，
 *       模型只能决定"查什么"，不能决定"查谁"。</li>
 *   <li><b>按角色裁剪工具清单。</b> 学员根本收不到教师工具的定义，谈不上滥用。</li>
 *   <li><b>工具全是只读查询。</b> 不提供任何"写"能力（改成绩、交作业），
 *       所以即便模型被诱导，最坏结果也只是多查一次自己本来就看得见的数据。</li>
 *   <li><b>教师只能查自己的课。</b> 传别人的课程名会被拒绝。</li>
 * </ul>
 */
@Slf4j
@Service
public class AiToolService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 角色：学员 */
    private static final String ROLE_STUDENT = "student";

    // ==================== 工具名（同时作为日志与前端展示的标识） ====================

    public static final String TOOL_MY_COURSES = "query_my_courses";
    public static final String TOOL_MY_HOMEWORK = "query_my_homework";
    public static final String TOOL_MY_SCORES = "query_my_scores";
    public static final String TOOL_COURSE_STATS = "query_course_homework_stats";
    public static final String TOOL_COURSE_STUDENTS = "query_course_students";

    private final CourseMapper courseMapper;
    private final CourseApplyMapper courseApplyMapper;
    private final HomeworkMapper homeworkMapper;
    private final HomeworkSubmitMapper homeworkSubmitMapper;
    private final ScoreMapper scoreMapper;
    private final StudentMapper studentMapper;
    private final ObjectMapper objectMapper;

    public AiToolService(CourseMapper courseMapper,
                         CourseApplyMapper courseApplyMapper,
                         HomeworkMapper homeworkMapper,
                         HomeworkSubmitMapper homeworkSubmitMapper,
                         ScoreMapper scoreMapper,
                         StudentMapper studentMapper,
                         ObjectMapper objectMapper) {
        this.courseMapper = courseMapper;
        this.courseApplyMapper = courseApplyMapper;
        this.homeworkMapper = homeworkMapper;
        this.homeworkSubmitMapper = homeworkSubmitMapper;
        this.scoreMapper = scoreMapper;
        this.studentMapper = studentMapper;
        this.objectMapper = objectMapper;
    }

    // ==================== 工具定义（发给模型） ====================

    /**
     * 按当前登录角色返回可用的工具定义（OpenAI 兼容格式）。
     * <p>学员拿不到教师工具 —— 模型连"有这个工具"都不知道。
     */
    public List<Map<String, Object>> definitions() {
        List<Map<String, Object>> tools = new ArrayList<>();
        if (isStudent()) {
            tools.add(tool(TOOL_MY_COURSES, "查询当前登录学员自己报名的课程清单，包含课程名称、培训周期、报名审核状态。", Map.of(), List.of()));
            tools.add(tool(TOOL_MY_HOMEWORK,
                    "查询当前登录学员自己的作业提交情况：哪些作业已提交、哪些还未提交、各自的截止时间。"
                            + "当用户问『我还有哪几门作业没交』『我的作业交了吗』这类涉及个人作业数据的问题时调用。",
                    Map.of("courseName", Map.of(
                            "type", "string",
                            "description", "可选。只查某一门课程时传入课程名（支持部分匹配），例如『Java 基础』。不传则查询全部课程。")),
                    List.of()));
            tools.add(tool(TOOL_MY_SCORES,
                    "查询当前登录学员自己的课程成绩，包含平时作业分、考试分、总分与教师评语。"
                            + "当用户问『我考了多少分』『我的成绩怎么样』时调用。",
                    Map.of("courseName", Map.of(
                            "type", "string",
                            "description", "可选。只查某一门课程时传入课程名（支持部分匹配）。不传则查询全部课程。")),
                    List.of()));
        } else {
            tools.add(tool(TOOL_MY_COURSES,
                    "查询当前登录教师自己发布的课程清单（管理员则返回全部课程），包含课程名称、审核状态、报名人数与名额上限。",
                    Map.of(), List.of()));
            tools.add(tool(TOOL_COURSE_STATS,
                    "查询指定课程的作业提交统计：有多少名学员、每项作业已交人数与未交人数、截止时间。"
                            + "当教师问『这门课的作业交得怎么样』『提交率多少』时调用。",
                    Map.of("courseName", Map.of(
                            "type", "string",
                            "description", "课程名称（支持部分匹配），例如『Java 基础』。")),
                    List.of("courseName")));
            tools.add(tool(TOOL_COURSE_STUDENTS,
                    "查询指定课程的报名学员名单，包含学员姓名、账号与报名审核状态。"
                            + "当教师问『这门课有多少学生选了』『选课的学生都叫什么名字』『谁报名了』"
                            + "『有哪些人还在待审核』时调用。教师只能查询自己发布的课程。",
                    Map.of("courseName", Map.of(
                            "type", "string",
                            "description", "课程名称（支持部分匹配），例如『Java 基础』。")),
                    List.of("courseName")));
        }
        return tools;
    }

    /** 工具的中文展示名，用于前端告诉用户"助教刚查了什么" */
    public static String label(String toolName) {
        return switch (toolName) {
            case TOOL_MY_COURSES -> "我的课程";
            case TOOL_MY_HOMEWORK -> "我的作业提交情况";
            case TOOL_MY_SCORES -> "我的成绩";
            case TOOL_COURSE_STATS -> "课程作业统计";
            case TOOL_COURSE_STUDENTS -> "课程学员名单";
            default -> toolName;
        };
    }

    // ==================== 工具执行 ====================

    /**
     * 执行一个工具调用。
     *
     * <p>注意参数里<b>没有</b>用户 ID —— 身份一律取自 JWT。
     *
     * @param name          模型选择的工具名
     * @param argumentsJson 模型给出的参数（JSON 字符串，可能是空字符串或 {@code "{}"}）
     * @return 给模型看的纯文本结果
     */
    public String execute(String name, String argumentsJson) {
        Map<String, Object> args = parseArgs(argumentsJson);
        String courseName = str(args.get("courseName"));

        log.info("智能助教调用工具：{} 参数 courseName={}", name, courseName);
        return switch (name) {
            case TOOL_MY_COURSES -> myCourses();
            case TOOL_MY_HOMEWORK -> myHomework(courseName);
            case TOOL_MY_SCORES -> myScores(courseName);
            case TOOL_COURSE_STATS -> courseHomeworkStats(courseName);
            case TOOL_COURSE_STUDENTS -> courseStudents(courseName);
            default -> "未知工具：" + name;
        };
    }

    // ==================== 各工具的实现 ====================

    /** 我的课程：学员 = 已报名的；教师 = 自己发布的；管理员 = 全部 */
    private String myCourses() {
        String role = CurrentUserUtil.getRole();
        Integer uid = CurrentUserUtil.getId();
        if (uid == null) {
            return "无法识别当前登录用户，请重新登录后再试。";
        }

        List<Course> courses;
        if (ROLE_STUDENT.equals(role)) {
            // 学员：从报名表反查课程（含待审核的，让学员知道有一门还在等审核）
            List<CourseApply> applies = courseApplyMapper.selectList(
                    Wrappers.<CourseApply>lambdaQuery().eq(CourseApply::getStudentId, uid));
            if (applies.isEmpty()) {
                return "该学员当前没有报名任何课程。";
            }
            List<Integer> courseIds = applies.stream().map(CourseApply::getCourseId).toList();
            Map<Integer, Integer> statusByCourse = applies.stream()
                    .collect(Collectors.toMap(CourseApply::getCourseId, CourseApply::getAuditStatus, (a, b) -> a));
            courses = courseMapper.selectList(
                    Wrappers.<Course>lambdaQuery().in(Course::getCourseId, courseIds));
            StringBuilder sb = new StringBuilder("该学员共报名 ").append(courses.size()).append(" 门课程：\n");
            for (Course c : courses) {
                sb.append("- ").append(c.getCourseName())
                        .append("（报名状态：").append(applyStatusText(statusByCourse.get(c.getCourseId())))
                        .append("，培训周期：").append(dash(c.getTrainCycle()))
                        .append("）\n");
            }
            return sb.toString();
        }

        // 教师：自己发布的；管理员：全部
        if (CurrentUserUtil.isAdmin()) {
            courses = courseMapper.selectList(Wrappers.<Course>lambdaQuery().orderByAsc(Course::getCourseId));
            if (courses.isEmpty()) {
                return "平台当前没有任何课程。";
            }
        } else {
            courses = courseMapper.selectList(
                    Wrappers.<Course>lambdaQuery().eq(Course::getPublishTeacherId, uid));
            if (courses.isEmpty()) {
                return "该教师当前没有发布任何课程。";
            }
        }
        StringBuilder sb = new StringBuilder("共 ").append(courses.size()).append(" 门课程：\n");
        for (Course c : courses) {
            sb.append("- ").append(c.getCourseName())
                    .append("（状态：").append(auditStatusText(c.getAuditStatus()))
                    .append("，已报名 ").append(c.getCurrentStudents() == null ? 0 : c.getCurrentStudents())
                    .append(" 人 / 名额 ").append(c.getMaxStudents() == null || c.getMaxStudents() <= 0
                            ? "不限" : c.getMaxStudents() + " 人")
                    .append("）\n");
        }
        return sb.toString();
    }

    /** 我的作业提交情况（仅学员） */
    private String myHomework(String courseName) {
        if (!isStudent()) {
            return "该工具只对学员开放。教师如需查看作业提交情况，请使用课程作业统计功能。";
        }
        Integer sid = CurrentUserUtil.getId();
        if (sid == null) {
            return "无法识别当前登录用户，请重新登录后再试。";
        }

        // 只统计已通过审核的课程 —— 还在审核中的课不该有作业压力
        List<CourseApply> applies = courseApplyMapper.selectList(
                Wrappers.<CourseApply>lambdaQuery()
                        .eq(CourseApply::getStudentId, sid)
                        .eq(CourseApply::getAuditStatus, 1));
        if (applies.isEmpty()) {
            return "该学员当前没有已通过审核的课程，因此没有作业记录。";
        }

        List<Integer> courseIds = applies.stream().map(CourseApply::getCourseId).toList();
        Map<Integer, String> courseNames = nameMap();

        // 课程名过滤（支持部分匹配，模型可能只记得半截名字）
        if (StringUtils.hasText(courseName)) {
            List<Integer> matched = courseIds.stream()
                    .filter(id -> courseNames.getOrDefault(id, "").contains(courseName))
                    .toList();
            if (matched.isEmpty()) {
                return "该学员已通过的课程中没有名称包含「" + courseName + "」的课程，未做统计。"
                        + "该学员已通过的课程为：" + courseIds.stream()
                        .map(id -> courseNames.getOrDefault(id, "课程#" + id))
                        .collect(Collectors.joining("、"));
            }
            courseIds = matched;
        }

        List<Homework> homeworks = homeworkMapper.selectList(
                Wrappers.<Homework>lambdaQuery().in(Homework::getCourseId, courseIds).orderByAsc(Homework::getDeadline));
        if (homeworks.isEmpty()) {
            return "所选课程下还没有布置任何作业。";
        }

        Set<Integer> submittedIds = homeworkSubmitMapper.selectList(
                        Wrappers.<HomeworkSubmit>lambdaQuery().eq(HomeworkSubmit::getStudentId, sid))
                .stream().map(HomeworkSubmit::getHomeworkId).collect(Collectors.toSet());

        List<Homework> missing = homeworks.stream()
                .filter(hw -> !submittedIds.contains(hw.getHomeworkId())).toList();
        List<Homework> done = homeworks.stream()
                .filter(hw -> submittedIds.contains(hw.getHomeworkId())).toList();

        Date now = new Date();
        StringBuilder sb = new StringBuilder();
        sb.append("该学员在所选课程范围内共 ").append(homeworks.size()).append(" 项作业：")
                .append("已提交 ").append(done.size()).append(" 项，未提交 ").append(missing.size()).append(" 项。\n");

        if (missing.isEmpty()) {
            sb.append("全部作业均已提交。\n");
        } else {
            sb.append("\n【未提交的作业】\n");
            for (Homework hw : missing) {
                sb.append("- ").append(courseNames.getOrDefault(hw.getCourseId(), "未知课程"))
                        .append(" / ").append(hw.getHomeworkName())
                        .append("（截止 ").append(format(hw.getDeadline()))
                        .append(hw.getDeadline() != null && hw.getDeadline().before(now) ? "，⚠️ 已过期" : "")
                        .append("）\n");
            }
        }
        if (!done.isEmpty()) {
            sb.append("\n【已提交的作业】\n");
            for (Homework hw : done) {
                sb.append("- ").append(courseNames.getOrDefault(hw.getCourseId(), "未知课程"))
                        .append(" / ").append(hw.getHomeworkName()).append('\n');
            }
        }
        // 注意：这里刻意不追加"以上为实时数据"之类的说明。
        // 工具返回值是直接喂给模型的，任何说明性文字都会被模型当成答案原文抄出来，
        // 最终用户会看到莫名其妙的括号备注。时效性的约束改在 SYSTEM_PROMPT 里声明。
        return sb.toString();
    }

    /** 我的成绩（仅学员） */
    private String myScores(String courseName) {
        if (!isStudent()) {
            return "该工具只对学员开放。";
        }
        Integer sid = CurrentUserUtil.getId();
        if (sid == null) {
            return "无法识别当前登录用户，请重新登录后再试。";
        }

        List<Score> scores = scoreMapper.selectList(
                Wrappers.<Score>lambdaQuery().eq(Score::getStudentId, sid));
        if (scores.isEmpty()) {
            return "该学员目前还没有任何成绩记录（教师可能尚未录入）。";
        }

        Map<Integer, String> courseNames = nameMap();
        if (StringUtils.hasText(courseName)) {
            List<Score> filtered = scores.stream()
                    .filter(s -> courseNames.getOrDefault(s.getCourseId(), "").contains(courseName))
                    .toList();
            if (filtered.isEmpty()) {
                return "该学员没有名称包含「" + courseName + "」的课程成绩记录。已出成绩的课程为："
                        + scores.stream().map(s -> courseNames.getOrDefault(s.getCourseId(), "课程#" + s.getCourseId()))
                        .distinct().collect(Collectors.joining("、"));
            }
            scores = filtered;
        }

        StringBuilder sb = new StringBuilder("该学员共 ").append(scores.size()).append(" 条成绩记录：\n");
        int sum = 0;
        int counted = 0;
        for (Score s : scores) {
            sb.append("- ").append(courseNames.getOrDefault(s.getCourseId(), "未知课程"))
                    .append("：作业分 ").append(nullToDash(s.getHomeworkScore()))
                    .append("、考试分 ").append(nullToDash(s.getExamScore()))
                    .append("、总分 ").append(nullToDash(s.getTotalScore()));
            if (StringUtils.hasText(s.getScoreComment())) {
                sb.append("、教师评语：").append(s.getScoreComment());
            }
            sb.append('\n');
            if (s.getTotalScore() != null) {
                sum += s.getTotalScore();
                counted++;
            }
        }
        if (counted > 0) {
            sb.append("\n平均总分：").append(String.format("%.1f", (double) sum / counted)).append(" 分。");
        }
        return sb.toString();
    }

    /** 课程作业提交统计（教师 / 管理员） */
    private String courseHomeworkStats(String courseName) {
        if (!StringUtils.hasText(courseName)) {
            return "请提供课程名称，例如「Java 基础」。";
        }

        List<Course> matched = matchOwnCourses(courseName);
        if (matched.isEmpty()) {
            return noCourseHint(courseName);
        }

        StringBuilder sb = new StringBuilder();
        for (Course course : matched) {
            List<Homework> homeworks = homeworkMapper.selectList(
                    Wrappers.<Homework>lambdaQuery()
                            .eq(Homework::getCourseId, course.getCourseId())
                            .orderByAsc(Homework::getDeadline));
            int studentCount = countPassedStudents(course.getCourseId());

            sb.append("课程：").append(course.getCourseName())
                    .append("（已通过审核的学员 ").append(studentCount).append(" 人）\n");
            if (homeworks.isEmpty()) {
                sb.append("  该课程还没有布置作业。\n\n");
                continue;
            }

            List<Integer> hwIds = homeworks.stream().map(Homework::getHomeworkId).toList();
            // 一次查出所有作业的提交记录，按作业分组计数（避免 N+1 查询）
            Map<Integer, Long> submitCount = homeworkSubmitMapper.selectList(
                            Wrappers.<HomeworkSubmit>lambdaQuery().in(HomeworkSubmit::getHomeworkId, hwIds))
                    .stream().collect(Collectors.groupingBy(HomeworkSubmit::getHomeworkId, Collectors.counting()));

            for (Homework hw : homeworks) {
                long submitted = submitCount.getOrDefault(hw.getHomeworkId(), 0L);
                long missing = Math.max(0, studentCount - submitted);
                sb.append("  - ").append(hw.getHomeworkName())
                        .append("：已交 ").append(submitted).append(" 人")
                        .append("，未交 ").append(missing).append(" 人");
                if (studentCount > 0) {
                    sb.append("（提交率 ")
                            .append(Math.round(submitted * 100.0 / studentCount)).append("%）");
                }
                sb.append("，截止 ").append(format(hw.getDeadline())).append('\n');
            }
            sb.append('\n');
        }
        // 同 myHomework()：说明性文字会被模型抄进最终答案，这里不追加
        return sb.toString();
    }

    /**
     * 课程报名学员名单（教师 / 管理员）。
     *
     * <h3>为什么教师可以看，学生不能</h3>
     * <pre>
     *   教师：报名审核本来就是他的工作内容（课程管理页有「报名审核」页签），
     *         名单对他本来就是可见信息，助教只是把同一个视图搬到对话里。
     *   学生：查不到别人的报名情况 —— 这个工具压根不在学生的工具清单里，
     *         模型连"有这么一个功能"都不知道。
     * </pre>
     * 名单里只输出<b>姓名与账号</b>：这两项是教师审核时要用来核对身份的信息；
     * 密码哈希、手机号、邮箱一律不取，且 {@code Student#studentPwd} 上有
     * {@code WRITE_ONLY} 兜底，即便将来有人误传整个实体也不会泄露。
     */
    private String courseStudents(String courseName) {
        if (!StringUtils.hasText(courseName)) {
            return "请提供课程名称，例如「Java 基础」。";
        }

        List<Course> matched = matchOwnCourses(courseName);
        if (matched.isEmpty()) {
            return noCourseHint(courseName);
        }

        StringBuilder sb = new StringBuilder();
        for (Course course : matched) {
            List<CourseApply> applies = courseApplyMapper.selectList(
                    Wrappers.<CourseApply>lambdaQuery()
                            .eq(CourseApply::getCourseId, course.getCourseId())
                            .orderByAsc(CourseApply::getApplyId));

            sb.append("课程：").append(course.getCourseName())
                    .append("（报名 ").append(applies.size()).append(" 人）\n");
            if (applies.isEmpty()) {
                sb.append("  暂无学员报名。\n\n");
                continue;
            }

            Map<Integer, String> names = studentLabels(
                    applies.stream().map(CourseApply::getStudentId).distinct().toList());

            // 按审核状态分组输出：教师最关心的是"谁还在等我审核"
            appendApplyGroup(sb, "已通过", 1, applies, names);
            appendApplyGroup(sb, "待审核", 0, applies, names);
            appendApplyGroup(sb, "已驳回", 2, applies, names);
            sb.append('\n');
        }
        return sb.toString();
    }

    /** 按审核状态输出一组学员（该状态没有记录时整组跳过） */
    private void appendApplyGroup(StringBuilder sb, String title, int status,
                                  List<CourseApply> applies, Map<Integer, String> names) {
        List<CourseApply> group = applies.stream()
                .filter(a -> a.getAuditStatus() != null && a.getAuditStatus() == status)
                .toList();
        if (group.isEmpty()) {
            return;
        }
        sb.append("  ").append(title).append("（").append(group.size()).append(" 人）：");
        sb.append(group.stream()
                .map(a -> names.getOrDefault(a.getStudentId(), "学员#" + a.getStudentId()))
                .collect(Collectors.joining("、")));
        sb.append('\n');
    }

    /**
     * 学员 ID → 展示名，形如 {@code 王同学（student1）}。
     *
     * <p>带上账号是因为真名会重名 —— 教师看到两个「张伟」无法分辨谁是谁，
     * 而账号是唯一的。批量查避免逐条 {@code selectById} 的 N+1。
     */
    private Map<Integer, String> studentLabels(List<Integer> ids) {
        Map<Integer, String> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        for (Student s : studentMapper.selectBatchIds(ids)) {
            String name = StringUtils.hasText(s.getStudentName()) ? s.getStudentName() : "未填写姓名";
            map.put(s.getStudentId(), s.getStudentAccount() == null
                    ? name : name + "（" + s.getStudentAccount() + "）");
        }
        return map;
    }

    /**
     * 按课程名模糊匹配课程，并套用「教师只能看自己的课」这条规则（管理员不受限）。
     *
     * <p>抽出来给作业统计和学员名单共用 —— 这条规则是安全边界，
     * 两处各写一遍迟早会走偏（改了一处忘了另一处 = 越权）。
     *
     * @return 命中的课程；空列表表示没有可用课程
     */
    private List<Course> matchOwnCourses(String courseName) {
        List<Course> matched = courseMapper.selectList(
                Wrappers.<Course>lambdaQuery().like(Course::getCourseName, courseName));
        if (matched.isEmpty() || CurrentUserUtil.isAdmin()) {
            return matched;
        }
        Integer uid = CurrentUserUtil.getId();
        return matched.stream()
                .filter(c -> Objects.equals(c.getPublishTeacherId(), uid))
                .toList();
    }

    /** 课程名没匹配到时的提示语：区分「压根没有这门课」和「有但不是你的课」 */
    private String noCourseHint(String courseName) {
        if (CurrentUserUtil.isAdmin()) {
            return "没有找到名称包含「" + courseName + "」的课程。";
        }
        return "没有找到你发布的名称包含「" + courseName + "」的课程（只能查询自己发布的课程）。";
    }

    // ==================== 辅助方法 ====================

    /** 课程 ID → 课程名 */
    private Map<Integer, String> nameMap() {
        Map<Integer, String> map = new HashMap<>();
        for (Course c : courseMapper.selectList(null)) {
            map.put(c.getCourseId(), c.getCourseName());
        }
        return map;
    }

    /** 某课程下「已通过审核」的学员数 —— 与课程报名人数口径一致 */
    private int countPassedStudents(Integer courseId) {
        Long count = courseApplyMapper.selectCount(
                Wrappers.<CourseApply>lambdaQuery()
                        .eq(CourseApply::getCourseId, courseId)
                        .eq(CourseApply::getAuditStatus, 1));
        return count == null ? 0 : count.intValue();
    }

    private boolean isStudent() {
        return ROLE_STUDENT.equals(CurrentUserUtil.getRole());
    }

    private static Map<String, Object> tool(String name, String description,
                                            Map<String, Object> properties, List<String> required) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", required);

        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", parameters);

        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("type", "function");
        wrapper.put("function", function);
        return wrapper;
    }

    /** 解析模型给的参数 JSON。模型偶尔会返回空串或非法 JSON，这里一律降级为空参数 */
    private Map<String, Object> parseArgs(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {
            });
            return parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            log.warn("工具参数解析失败，按空参数处理：{}", json);
            return Map.of();
        }
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String applyStatusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 0 -> "待审核";
            case 1 -> "已通过";
            case 2 -> "已驳回";
            default -> "未知";
        };
    }

    private static String auditStatusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 0 -> "待审核";
            case 1 -> "已通过";
            case 2 -> "已驳回";
            default -> "未知";
        };
    }

    private static String dash(String value) {
        return StringUtils.hasText(value) ? value : "未填写";
    }

    private static String nullToDash(Integer value) {
        return value == null ? "未录入" : String.valueOf(value);
    }

    private static String format(Date date) {
        if (date == null) {
            return "未设置";
        }
        return DATE_TIME.format(date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
    }
}
