package com.online.study.ai;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 教师 / 管理员可用的三个工具（spring-ai 引擎）。
 *
 * <p>和 {@link StudentToolFunctions} 对称：这里<b>没有</b> "我的作业提交情况" 和 "我的成绩"。
 * 那两件事对教师没有意义（"我交作业了吗"），给了只会让模型在教师提问时挑错工具。
 *
 * <p>注意 {@code query_my_courses} 这个工具名两边都有，只是描述不同 ——
 * 学员版说"你报名的课程"，教师版说"你发布的课程"。这不是重复定义，
 * 而是同一个查询接口在两种角色下的两种语义，描述必须跟着角色走。
 */
public class TeacherToolFunctions extends AbstractToolFunctions {

    public TeacherToolFunctions(AiToolService toolService) {
        super(toolService);
    }

    /** 我的课程（教师视角：自己发布的课；管理员：全部课程） */
    @Tool(name = ToolSpecs.TOOL_MY_COURSES, description = ToolSpecs.DESC_MY_COURSES_TEACHER)
    public String queryMyCourses() {
        return run(ToolSpecs.TOOL_MY_COURSES, "", () ->
                toolService.execute(ToolSpecs.TOOL_MY_COURSES, "{}"));
    }

    /**
     * 课程作业提交统计。
     *
     * @param courseName 必填。只能查自己发布的课程（管理员不受限），
     *                   {@code AiToolService#matchOwnCourses} 里做了这层过滤
     */
    @Tool(name = ToolSpecs.TOOL_COURSE_STATS, description = ToolSpecs.DESC_COURSE_STATS)
    public String queryCourseHomeworkStats(
            @ToolParam(description = ToolSpecs.PARAM_COURSE_NAME_REQUIRED) String courseName) {
        return run(ToolSpecs.TOOL_COURSE_STATS, courseNameSummary(courseName), () ->
                toolService.execute(ToolSpecs.TOOL_COURSE_STATS, courseNameJson(courseName)));
    }

    /**
     * 课程报名学员名单。
     *
     * @param courseName 必填。输出只有姓名 + 账号（教师审核要用），不含密码、手机、邮箱
     */
    @Tool(name = ToolSpecs.TOOL_COURSE_STUDENTS, description = ToolSpecs.DESC_COURSE_STUDENTS)
    public String queryCourseStudents(
            @ToolParam(description = ToolSpecs.PARAM_COURSE_NAME_REQUIRED) String courseName) {
        return run(ToolSpecs.TOOL_COURSE_STUDENTS, courseNameSummary(courseName), () ->
                toolService.execute(ToolSpecs.TOOL_COURSE_STUDENTS, courseNameJson(courseName)));
    }
}
