package com.online.study.controller;

import com.online.study.entity.Homework;
import com.online.study.entity.HomeworkSubmit;
import com.online.study.entity.Score;
import com.online.study.service.HomeworkService;
import com.online.study.service.HomeworkSubmitService;
import com.online.study.service.ScoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.online.study.utils.CurrentUserUtil;
import com.online.study.utils.QueryUtil;
import com.online.study.utils.ScoreCalculator;
import com.online.study.common.PageQuery;
import com.online.study.common.PageResult;
import com.online.study.common.Result;
import com.online.study.common.ResultCode;
import com.online.study.exception.BizException;
import com.online.study.vo.HomeworkSubmitStatsVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 作业提交接口
 *
 * <h3>本次改造的要点</h3>
 * <ol>
 *   <li><b>批改独立成 {@code /grade}</b>：原来前端"批改"是先把分数写进 score 表
 *       （学员×课程一行），再把提交记录标成已批改 —— 同一门课的第 2 次作业
 *       批改会把第 1 次的分数覆盖掉。现在分数直接落在这条提交记录上。</li>
 *   <li><b>分数只能由服务端写</b>：{@code /save} 会把前端传来的 score 等字段全部丢弃。
 *       否则学员只要在提交作业的请求里带上 {@code score: 100}，就能给自己打满分。</li>
 *   <li><b>提交者身份从 JWT 取</b>：学员提交时强制 {@code studentId = 当前登录人}，
 *       不能替别人提交；{@code correctStatus} 也不能由学员自己标成已批改。</li>
 * </ol>
 */
@RestController
@RequestMapping("/homework-submit")
public class HomeworkSubmitController {

    @Autowired
    private HomeworkSubmitService service;

    @Autowired
    private HomeworkService homeworkService;

    @Autowired
    private ScoreService scoreService;

    @GetMapping("/list")
    public List<HomeworkSubmit> list() {
        return service.list();
    }

    @PostMapping("/query")
    public List<HomeworkSubmit> query(@RequestBody Map<String, Object> params) {
        QueryWrapper<HomeworkSubmit> wrapper = QueryUtil.buildSafeWrapper(HomeworkSubmit.class, params);
        return service.list(wrapper);
    }

    /**
     * 分页查询。
     * 请求体示例：{"pageNum": 1, "pageSize": 10, "courseName": "Java"}
     * 前两个字段由 PageQuery 解析（含默认值与上限保护），其余作为查询条件经白名单校验。
     */
    @PostMapping("/page")
    public Result<PageResult<HomeworkSubmit>> page(@RequestBody Map<String, Object> params) {
        Page<HomeworkSubmit> page = PageQuery.of(params);
        QueryWrapper<HomeworkSubmit> wrapper = QueryUtil.buildSafeWrapper(HomeworkSubmit.class, params);
        return Result.success(PageResult.of(service.page(page, wrapper)));
    }

    /**
     * 提交 / 更新作业。
     *
     * <p>分数相关字段一律置空后落库：MyBatis-Plus 的更新策略是忽略 null 字段，
     * 所以更新已有提交时库里的分数不会被冲掉，而新建提交时分数天然为空
     * （= 未批改）。这样学员无论如何都写不进分数。
     */
    @PostMapping("/save")
    public boolean save(@RequestBody HomeworkSubmit entity) {
        Integer uid = CurrentUserUtil.getId();
        String role = CurrentUserUtil.getRole();

        entity.setScore(null);
        entity.setScoreComment(null);
        entity.setScoreTeacherId(null);
        entity.setScoreTime(null);

        if ("student".equals(role) && uid != null) {
            // 学员：只能以自己名义提交，且不能自己把状态标成已批改
            entity.setStudentId(uid);
            if (entity.getSubmitId() == null) {
                entity.setCorrectStatus(0);
            } else {
                entity.setCorrectStatus(null);
            }
        }

        return service.saveOrUpdate(entity);
    }

    /**
     * 批改作业（教师 / 管理员）。
     *
     * <p>请求体：{@code {"submitId": 12, "score": 88, "comment": "思路清晰"}}
     *
     * <p>做三件事，全在一个事务里：
     * <ol>
     *   <li>把分数、评语、评分教师、评分时间写进<b>这条提交记录</b>；</li>
     *   <li>重算该学员在该课程下的作业平均分，回写 score 表的课程总评；</li>
     *   <li>总评按 {@link ScoreCalculator} 的规则算 —— 没录考试分时就是作业平均分，
     *       不再出现"录了一次作业分，总评只有一半"的问题。</li>
     * </ol>
     */
    @PostMapping("/grade")
    @Transactional(rollbackFor = Exception.class)
    public Result<HomeworkSubmit> grade(@RequestBody Map<String, Object> body) {
        Integer uid = CurrentUserUtil.getId();
        String role = CurrentUserUtil.getRole();
        if (uid == null || role == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }

        Integer submitId = toInt(body.get("submitId"));
        Integer score = toInt(body.get("score"));
        Object commentObj = body.get("comment");
        String comment = commentObj == null ? null : commentObj.toString().trim();

        if (submitId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "缺少提交记录ID");
        }
        if (score == null || score < 0 || score > 100) {
            throw new BizException(ResultCode.PARAM_ERROR, "分数需要是 0~100 的整数");
        }

        HomeworkSubmit submit = service.getById(submitId);
        if (submit == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "提交记录不存在或已被删除");
        }
        Homework homework = homeworkService.getById(submit.getHomeworkId());
        if (homework == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "作业不存在或已被删除");
        }

        // 权限：管理员随便批；教师只能批自己发布的作业
        if (!CurrentUserUtil.isAdmin()) {
            if (!"teacher".equals(role)) {
                throw new BizException(ResultCode.FORBIDDEN, "只有教师可以批改作业");
            }
            if (!Objects.equals(homework.getPublishTeacherId(), uid)) {
                throw new BizException(ResultCode.FORBIDDEN, "只能批改自己发布的作业");
            }
        }

        submit.setScore(score);
        submit.setScoreComment(comment == null || comment.isEmpty() ? null : comment);
        submit.setScoreTeacherId(uid);
        submit.setScoreTime(new Date());
        submit.setCorrectStatus(1);
        service.updateById(submit);

        recalcCourseScore(submit.getStudentId(), homework.getCourseId(), comment);

        return Result.success("批改已保存", submit);
    }

    /**
     * 某门课程的作业提交统计（教师 / 管理员）。
     *
     * <p>给作业列表用：一次请求拿到「在读学员数 + 每份作业的已交 / 已批改 / 待批改」，
     * 教师不必点开每一份作业才知道谁没交。
     *
     * <p>请求示例：{@code GET /homework-submit/course-stats?courseId=47}
     */
    @GetMapping("/course-stats")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<HomeworkSubmitStatsVO> courseStats(@RequestParam(name = "courseId") Integer courseId) {
        return Result.success(service.courseStats(courseId));
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Integer id) {
        return service.removeById(id);
    }

    // ==================== 内部辅助 ====================

    /**
     * 重算某学员某课程的作业平均分与总评，写回 score 表。
     *
     * <p>score 表从"唯一的分数来源"退化为"课程总评"：
     * {@code homeworkScore} = 该课程所有已批改作业的平均分，
     * {@code totalScore} = 有考试分则各占一半，没有就直接取作业平均分。
     */
    private void recalcCourseScore(Integer studentId, Integer courseId, String latestComment) {
        if (studentId == null || courseId == null) {
            return;
        }
        List<Integer> homeworkIds = homeworkService
                .list(new QueryWrapper<Homework>().eq("course_id", courseId))
                .stream()
                .map(Homework::getHomeworkId)
                .toList();
        if (homeworkIds.isEmpty()) {
            return;
        }

        // 该学员在这门课下所有已批改作业的分数
        List<Integer> gradedScores = service.list(new QueryWrapper<HomeworkSubmit>()
                        .eq("student_id", studentId)
                        .in("homework_id", homeworkIds)
                        .isNotNull("score"))
                .stream()
                .map(HomeworkSubmit::getScore)
                .toList();
        Integer average = ScoreCalculator.average(gradedScores);

        Score record = scoreService.getOne(new QueryWrapper<Score>()
                .eq("student_id", studentId)
                .eq("course_id", courseId)
                .last("limit 1"));
        Score target = record == null ? new Score() : record;

        target.setStudentId(studentId);
        target.setCourseId(courseId);
        target.setHomeworkScore(average);

        // 考试分保持库里原值 —— 没有记录时就是 null，绝不写 0，
        // 否则总评又会变成 (作业平均分 + 0) / 2。
        Integer total = ScoreCalculator.total(average, target.getExamScore());
        if (total == null) {
            // 一次都还没批改出分数，不建总评记录
            return;
        }
        target.setTotalScore(total);
        if (latestComment != null && !latestComment.isEmpty()) {
            target.setScoreComment(latestComment);
        }
        target.setScoreTeacherId(CurrentUserUtil.getId());
        target.setScoreTime(new Date());
        scoreService.saveOrUpdate(target);
    }

    /** 请求体里的数字可能是 Integer / String / Double，统一转成 Integer */
    private Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.valueOf(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
