package com.online.study.ai;

/**
 * 工具规格：工具名、描述、参数说明的<b>唯一出处</b>。
 *
 * <h3>为什么要单独抽一个类</h3>
 * 同一批工具现在有两种"描述方式"，但只有<b>一份实现</b>：
 * <pre>
 *   legacy 引擎    ：{@link AiToolService#definitions()} 手工拼 OpenAI 的 JSON schema
 *   spring-ai 引擎 ：{@link StudentToolFunctions} / {@link TeacherToolFunctions} 上的 {@code @Tool} 注解
 * </pre>
 * 如果两边各写一份中文描述，改一处忘另一处，两个引擎对同一个问题的判断就会不一样 ——
 * 那时 60 题的分数差异到底是"引擎换人了"还是"描述写歪了"，完全说不清。
 * 所以描述文本放这里，两边都引用它。
 *
 * <h3>为什么这些常量必须写成 {@code static final String} 字面量</h3>
 * 注解的值只能是<b>编译期常量</b>：{@code @Tool(description = ...)} 里的东西在编译时
 * 就要定下来写进 class 文件。所以这些字符串不能是方法调用、不能读配置、不能拼运行期变量 ——
 * 连 {@code String.format} 都不行。字面量之间用 {@code +} 连接仍算常量，可以照用。
 *
 * <h3>同一个工具名，为什么有两套描述</h3>
 * {@code query_my_courses} 这个工具名在两种角色下语义不同：学员查"我报了什么课"、
 * 教师查"我发布了什么课"。模型靠描述决定"要不要调、参数怎么填"，描述必须跟着角色走，
 * 否则教师会看到"你报名的课程"这种错位说法。所以拆成 STUDENT / TEACHER 两套常量，
 * 分别落在两个类上 —— 学员的类里<b>根本没有</b>教师工具，比"运行时过滤"更彻底。
 */
public final class ToolSpecs {

    private ToolSpecs() {
    }

    // ==================== 工具名 ====================

    /** 我的课程（学员 / 教师共用同一个工具名，描述不同） */
    public static final String TOOL_MY_COURSES = "query_my_courses";

    /** 我的作业提交情况（仅学员） */
    public static final String TOOL_MY_HOMEWORK = "query_my_homework";

    /** 我的成绩（仅学员） */
    public static final String TOOL_MY_SCORES = "query_my_scores";

    /** 课程作业提交统计（教师 / 管理员） */
    public static final String TOOL_COURSE_STATS = "query_course_homework_stats";

    /** 课程报名学员名单（教师 / 管理员） */
    public static final String TOOL_COURSE_STUDENTS = "query_course_students";

    // ==================== 工具描述：学员 ====================

    public static final String DESC_MY_COURSES_STUDENT =
            "查询当前登录学员自己报名的课程清单，包含课程名称、培训周期、报名审核状态。";

    public static final String DESC_MY_HOMEWORK =
            "查询当前登录学员自己的作业提交情况：哪些作业已提交、哪些还未提交、各自的截止时间。"
                    + "当用户问『我还有哪几门作业没交』『我的作业交了吗』这类涉及个人作业数据的问题时调用。";

    public static final String DESC_MY_SCORES =
            "查询当前登录学员自己的课程成绩，包含平时作业分、考试分、总分与教师评语。"
                    + "当用户问『我考了多少分』『我的成绩怎么样』时调用。";

    /** 只查某一门课时用的课程名参数说明（作业、成绩两处措辞略有差别，故分开） */
    public static final String PARAM_COURSE_NAME_OPTIONAL =
            "可选。只查某一门课程时传入课程名（支持部分匹配），例如『Java 基础』。不传则查询全部课程。";

    /** 成绩工具的课程名参数：原实现措辞更短，没有"例如" */
    public static final String PARAM_COURSE_NAME_OPTIONAL_SCORE =
            "可选。只查某一门课程时传入课程名（支持部分匹配）。不传则查询全部课程。";

    // ==================== 工具描述：教师 / 管理员 ====================

    public static final String DESC_MY_COURSES_TEACHER =
            "查询当前登录教师自己发布的课程清单（管理员则返回全部课程），包含课程名称、审核状态、报名人数与名额上限。";

    public static final String DESC_COURSE_STATS =
            "查询指定课程的作业提交统计：有多少名学员、每项作业已交人数与未交人数、截止时间。"
                    + "当教师问『这门课的作业交得怎么样』『提交率多少』时调用。";

    public static final String DESC_COURSE_STUDENTS =
            "查询指定课程的报名学员名单，包含学员姓名、账号与报名审核状态。"
                    + "当教师问『这门课有多少学生选了』『选课的学生都叫什么名字』『谁报名了』"
                    + "『有哪些人还在待审核』时调用。教师只能查询自己发布的课程。";

    /** 教师侧课程名参数：必填 */
    public static final String PARAM_COURSE_NAME_REQUIRED =
            "课程名称（支持部分匹配），例如『Java 基础』。";

    // ==================== 中文展示名 ====================

    /**
     * 工具的中文展示名，用于前端告诉用户"助教刚查了什么"。
     *
     * <p>放在这里而不是各自引擎里：两个引擎写出的 {@code usedTools} 要能被同一套前端渲染，
     * 名字不一致就会出现"legacy 显示『我的成绩』、spring-ai 显示『query_my_scores』"。
     */
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
}
