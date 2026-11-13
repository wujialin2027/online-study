package com.online.study.ai;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 学员可用的三个工具（spring-ai 引擎）。
 *
 * <p>这个类里<b>刻意没有</b> {@code query_course_students} / {@code query_course_homework_stats} ——
 * 那两个是教师工具。模型拿到的工具清单就是这三个，它连"平台有查全班名单的功能"都不会知道。
 *
 * <p>方法体全部只有一行转发，业务逻辑不在这里（见 {@link AiToolService}）：
 * <pre>
 *   注解负责"告诉模型有这个工具、参数长什么样"
 *   AiToolService 负责"怎么查、查谁"
 * </pre>
 * 身份这一层永远不由模型决定 —— 参数里没有 studentId，{@code AiToolService} 内部用
 * {@code CurrentUserUtil} 从 JWT 取当前登录人。模型只能选"查什么"。
 *
 * <p>为什么每个方法都要返回 {@code String} 而不是实体对象：
 * 工具返回值会原样拼进下一次请求的 {@code tool} 消息。返回 String，框架原样透传；
 * 返回实体，框架会用 Jackson 序列化成一大段 JSON（含一堆 null 字段），既浪费 token
 * 又容易让模型把字段名当成人话念出来。legacy 引擎里那些手工拼的文本也是同一个理由。
 */
public class StudentToolFunctions extends AbstractToolFunctions {

    public StudentToolFunctions(AiToolService toolService) {
        super(toolService);
    }

    /** 我的课程（学员视角：已报名的课） */
    @Tool(name = ToolSpecs.TOOL_MY_COURSES, description = ToolSpecs.DESC_MY_COURSES_STUDENT)
    public String queryMyCourses() {
        return run(ToolSpecs.TOOL_MY_COURSES, "", () ->
                toolService.execute(ToolSpecs.TOOL_MY_COURSES, "{}"));
    }

    /**
     * 我的作业提交情况。
     *
     * @param courseName 可选。不传则查全部已通过审核的课程
     */
    @Tool(name = ToolSpecs.TOOL_MY_HOMEWORK, description = ToolSpecs.DESC_MY_HOMEWORK)
    public String queryMyHomework(
            @ToolParam(required = false, description = ToolSpecs.PARAM_COURSE_NAME_OPTIONAL) String courseName) {
        return run(ToolSpecs.TOOL_MY_HOMEWORK, courseNameSummary(courseName), () ->
                toolService.execute(ToolSpecs.TOOL_MY_HOMEWORK, courseNameJson(courseName)));
    }

    /**
     * 我的成绩。
     *
     * @param courseName 可选。不传则查全部课程
     */
    @Tool(name = ToolSpecs.TOOL_MY_SCORES, description = ToolSpecs.DESC_MY_SCORES)
    public String queryMyScores(
            @ToolParam(required = false, description = ToolSpecs.PARAM_COURSE_NAME_OPTIONAL_SCORE) String courseName) {
        return run(ToolSpecs.TOOL_MY_SCORES, courseNameSummary(courseName), () ->
                toolService.execute(ToolSpecs.TOOL_MY_SCORES, courseNameJson(courseName)));
    }
}
