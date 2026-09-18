package com.online.study.controller;

import com.online.study.entity.Course;
import com.online.study.entity.CourseApply;
import com.online.study.entity.Homework;
import com.online.study.entity.HomeworkSubmit;
import com.online.study.entity.Score;
import com.online.study.service.CourseApplyService;
import com.online.study.service.CourseService;
import com.online.study.service.HomeworkService;
import com.online.study.service.HomeworkSubmitService;
import com.online.study.service.ScoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.online.study.utils.CurrentUserUtil;
import com.online.study.utils.QueryUtil;
import com.online.study.utils.ScoreCalculator;
import com.online.study.utils.UserNameResolver;
import com.online.study.common.PageQuery;
import com.online.study.common.PageResult;
import com.online.study.common.Result;
import com.online.study.common.ResultCode;
import com.online.study.exception.BizException;
import com.online.study.vo.CourseScoreVO;
import com.online.study.vo.HomeworkScoreVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 成绩接口
 *
 * <h3>本次改造修掉的问题</h3>
 * <ol>
 *   <li><b>总评被算成一半</b>：{@code /save} 原来直接采用前端传来的 {@code totalScore}，
 *       而前端是 {@code (作业分 + 0) / 2} —— 没录入考试分时总评平白少一半。
 *       现在总评一律由服务端 {@link ScoreCalculator#total} 计算，前端传了也不采信。</li>
 *   <li><b>成绩只按课程存，一门课多次作业互相覆盖</b>：现在新增 {@code /my-courses}
 *       与 {@code /my-course/{courseId}} 两个接口，从 homework + homework_submit
 *       聚合成「课程卡片 → 作业明细」两级结构。</li>
 * </ol>
 */
@RestController
@RequestMapping("/score")
public class ScoreController {

    @Autowired
    private ScoreService service;

    @Autowired
    private CourseApplyService courseApplyService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private HomeworkService homeworkService;

    @Autowired
    private HomeworkSubmitService homeworkSubmitService;

    @Autowired
    private UserNameResolver userNameResolver;

    @GetMapping("/list")
    public List<Score> list() {
        return service.list();
    }

    @PostMapping("/query")
    public List<Score> query(@RequestBody Map<String, Object> params) {
        QueryWrapper<Score> wrapper = QueryUtil.buildSafeWrapper(Score.class, params);
        return service.list(wrapper);
    }

    /**
     * 分页查询。
     * 请求体示例：{"pageNum": 1, "pageSize": 10, "courseName": "Java"}
     * 前两个字段由 PageQuery 解析（含默认值与上限保护），其余作为查询条件经白名单校验。
     */
    @PostMapping("/page")
    public Result<PageResult<Score>> page(@RequestBody Map<String, Object> params) {
        Page<Score> page = PageQuery.of(params);
        QueryWrapper<Score> wrapper = QueryUtil.buildSafeWrapper(Score.class, params);
        return Result.success(PageResult.of(service.page(page, wrapper)));
    }

    @PostMapping("/save")
    public boolean save(@RequestBody Score entity) {
        validateScore("作业分数", entity.getHomeworkScore());
        validateScore("考试分数", entity.getExamScore());

        // 总评是派生值，不接受前端传入 —— 前端算的是 (作业分 + 考试分) / 2，
        // 考试分没录入时会被当成 0，算出来只有一半。
        Integer total = ScoreCalculator.total(entity.getHomeworkScore(), entity.getExamScore());
        if (total == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "作业分数与考试分数不能同时为空");
        }
        entity.setTotalScore(total);
        return service.saveOrUpdate(entity);
    }

    // ==================== 学员成绩页（两级） ====================

    /**
     * 第一级：我的课程成绩卡片。
     *
     * <p>只统计「我报名且审核通过」的课程。每门课统计：作业总数、我已提交数、
     * 已批改数、待批改数、未提交数、作业平均分（<b>只算已批改的</b>）。
     *
     * <p>实现上刻意避免 N+1：报名、课程、作业、提交、总评各查一次，
     * 无论报了几门课都是固定 5 次查询，聚合在内存里做。
     */
    @GetMapping("/my-courses")
    public Result<List<CourseScoreVO>> myCourses() {
        Integer studentId = requireStudentId();

        // ① 我报名且审核通过的课程
        List<CourseApply> applies = courseApplyService.list(new QueryWrapper<CourseApply>()
                .eq("student_id", studentId)
                .eq("audit_status", 1));
        List<Integer> courseIds = applies.stream()
                .map(CourseApply::getCourseId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (courseIds.isEmpty()) {
            return Result.success(List.of());
        }

        // ② 课程名
        Map<Integer, String> courseNames = new HashMap<>();
        for (Course c : courseService.listByIds(courseIds)) {
            courseNames.put(c.getCourseId(), c.getCourseName());
        }

        // ③ 这些课程下的全部作业
        List<Homework> homeworks = homeworkService.list(
                new QueryWrapper<Homework>().in("course_id", courseIds));
        Map<Integer, List<Homework>> homeworkByCourse = new HashMap<>();
        for (Homework hw : homeworks) {
            if (hw.getCourseId() != null) {
                homeworkByCourse.computeIfAbsent(hw.getCourseId(), k -> new ArrayList<>()).add(hw);
            }
        }

        // ④ 我在这些作业下的提交（一次查完）
        Map<Integer, HomeworkSubmit> submitByHomework =
                loadMySubmits(studentId, homeworks.stream().map(Homework::getHomeworkId).toList());

        // ⑤ 课程总评
        Map<Integer, Score> scoreByCourse = new HashMap<>();
        for (Score s : service.list(new QueryWrapper<Score>()
                .eq("student_id", studentId)
                .in("course_id", courseIds))) {
            scoreByCourse.put(s.getCourseId(), s);
        }

        // ⑥ 逐门课聚合
        List<CourseScoreVO> result = new ArrayList<>();
        for (Integer courseId : courseIds) {
            List<Homework> courseHomeworks = homeworkByCourse.getOrDefault(courseId, List.of());

            int submitted = 0;
            int graded = 0;
            int pending = 0;
            Date lastGradeTime = null;
            List<Integer> gradedScores = new ArrayList<>();

            for (Homework hw : courseHomeworks) {
                HomeworkSubmit sub = submitByHomework.get(hw.getHomeworkId());
                if (sub == null) {
                    continue;
                }
                submitted++;
                if (sub.getScore() == null) {
                    pending++;
                } else {
                    graded++;
                    gradedScores.add(sub.getScore());
                    if (sub.getScoreTime() != null
                            && (lastGradeTime == null || sub.getScoreTime().after(lastGradeTime))) {
                        lastGradeTime = sub.getScoreTime();
                    }
                }
            }

            CourseScoreVO vo = new CourseScoreVO();
            vo.setCourseId(courseId);
            vo.setCourseName(courseNames.getOrDefault(courseId, "未知课程"));
            vo.setTotalCount(courseHomeworks.size());
            vo.setSubmittedCount(submitted);
            vo.setGradedCount(graded);
            vo.setPendingCount(pending);
            vo.setNotSubmitCount(courseHomeworks.size() - submitted);
            vo.setAvgScore(ScoreCalculator.average(gradedScores));
            vo.setLastGradeTime(lastGradeTime);

            Score courseScore = scoreByCourse.get(courseId);
            if (courseScore != null) {
                vo.setTotalScore(courseScore.getTotalScore());
                vo.setExamScore(courseScore.getExamScore());
            }
            result.add(vo);
        }
        return Result.success(result);
    }

    /**
     * 第二级：某门课的作业成绩明细。
     *
     * <p>包含<b>没提交的作业</b>（状态标为未提交）—— 学员需要知道这门课还有几次没交。
     * 未批改的作业返回 {@code pending} 状态与空分数，前端显示成灰色「待批改」，
     * 且不参与平均分计算。
     *
     * @param courseId 课程ID，必须是我报名通过的课程
     */
    @GetMapping("/my-course/{courseId}")
    public Result<List<HomeworkScoreVO>> myCourseDetail(@PathVariable Integer courseId) {
        Integer studentId = requireStudentId();

        // 只能看自己报名通过的课程，避免换个 ID 就能读别人的课
        long applied = courseApplyService.count(new QueryWrapper<CourseApply>()
                .eq("student_id", studentId)
                .eq("course_id", courseId)
                .eq("audit_status", 1));
        if (applied == 0) {
            throw new BizException(ResultCode.FORBIDDEN, "你不是该课程的学员，无法查看成绩");
        }

        List<Homework> homeworks = homeworkService.list(new QueryWrapper<Homework>()
                .eq("course_id", courseId)
                .orderByAsc("homework_id"));

        Map<Integer, HomeworkSubmit> submitByHomework =
                loadMySubmits(studentId, homeworks.stream().map(Homework::getHomeworkId).toList());

        // 评分教师姓名：一次批量解析，不在循环里逐条查
        Set<String> teacherRefs = new HashSet<>();
        for (HomeworkSubmit sub : submitByHomework.values()) {
            if (sub.getScoreTeacherId() != null) {
                teacherRefs.add(UserNameResolver.key(UserNameResolver.ROLE_TEACHER, sub.getScoreTeacherId()));
            }
        }
        Map<String, String> teacherNames = userNameResolver.resolve(teacherRefs);

        List<HomeworkScoreVO> list = new ArrayList<>();
        for (Homework hw : homeworks) {
            HomeworkSubmit sub = submitByHomework.get(hw.getHomeworkId());

            HomeworkScoreVO vo = new HomeworkScoreVO();
            vo.setHomeworkId(hw.getHomeworkId());
            vo.setHomeworkName(hw.getHomeworkName());
            vo.setDeadline(hw.getDeadline());

            if (sub == null) {
                vo.setStatus(HomeworkScoreVO.STATUS_NOT_SUBMITTED);
                vo.setStatusText("未提交");
            } else {
                vo.setSubmitId(sub.getSubmitId());
                vo.setSubmitTime(sub.getSubmitTime());
                if (sub.getScore() == null) {
                    vo.setStatus(HomeworkScoreVO.STATUS_PENDING);
                    vo.setStatusText("待批改");
                } else {
                    vo.setStatus(HomeworkScoreVO.STATUS_GRADED);
                    vo.setStatusText("已批改");
                    vo.setScore(sub.getScore());
                    vo.setScoreComment(sub.getScoreComment());
                    vo.setScoreTime(sub.getScoreTime());
                    vo.setTeacherName(teacherNames.get(UserNameResolver.key(
                            UserNameResolver.ROLE_TEACHER, sub.getScoreTeacherId())));
                }
            }
            list.add(vo);
        }
        return Result.success(list);
    }

    // ==================== 内部辅助 ====================

    /** 断言当前登录者是学员，并返回其学员 ID */
    private Integer requireStudentId() {
        Integer uid = CurrentUserUtil.getId();
        if (uid == null || !"student".equals(CurrentUserUtil.getRole())) {
            throw new BizException(ResultCode.FORBIDDEN, "只有学员可以查看自己的成绩");
        }
        return uid;
    }

    /**
     * 查询「我」在指定作业集合下的提交。
     *
     * <p>同一份作业如果存在多条提交记录（历史脏数据），保留提交时间较晚的那条 ——
     * 学员以最后一次交的为准。
     */
    private Map<Integer, HomeworkSubmit> loadMySubmits(Integer studentId, List<Integer> homeworkIds) {
        Map<Integer, HomeworkSubmit> map = new HashMap<>();
        if (homeworkIds == null || homeworkIds.isEmpty()) {
            return map;
        }
        List<HomeworkSubmit> submits = homeworkSubmitService.list(new QueryWrapper<HomeworkSubmit>()
                .eq("student_id", studentId)
                .in("homework_id", homeworkIds));
        for (HomeworkSubmit sub : submits) {
            HomeworkSubmit old = map.get(sub.getHomeworkId());
            if (old == null) {
                map.put(sub.getHomeworkId(), sub);
            } else if (sub.getSubmitTime() != null
                    && (old.getSubmitTime() == null || sub.getSubmitTime().after(old.getSubmitTime()))) {
                map.put(sub.getHomeworkId(), sub);
            }
        }
        return map;
    }

    private void validateScore(String label, Integer score) {
        if (score == null) {
            return;
        }
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException(label + "必须在0到100之间");
        }
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Integer id) {
        return service.removeById(id);
    }
}
