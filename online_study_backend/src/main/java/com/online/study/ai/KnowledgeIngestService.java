package com.online.study.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.online.study.common.ResultCode;
import com.online.study.entity.Course;
import com.online.study.entity.CourseResource;
import com.online.study.entity.Homework;
import com.online.study.entity.KnowledgeDoc;
import com.online.study.exception.BizException;
import com.online.study.mapper.CourseMapper;
import com.online.study.mapper.CourseResourceMapper;
import com.online.study.mapper.HomeworkMapper;
import com.online.study.mapper.KnowledgeDocMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识入库服务 —— 把平台里已有的数据「翻译」成可以喂给大模型的资料。
 *
 * <p><b>它解决的问题</b>：RAG 要有资料才能检索。这个项目没有专门的"知识库表"，
 * 但课程介绍、作业要求、课程资源这些本来就在数据库里 —— 直接复用它们，
 * 不用另外让教师上传一遍。
 *
 * <h3>资料的粒度：一份资料 = 一个知识来源</h3>
 * <pre>
 *   课程：Java 基础          ← 介绍 / 周期 / 报名条件
 *   作业：Java 基础 / 第 3 次作业   ← 要求 / 截止时间
 *   课程资料：Java 基础 / 第 2 章课件   ← 名称 / 类型
 * </pre>
 * 为什么不把一门课的全部内容合成一份？因为那样切块后每块的"出处"都只能标到课程，
 * 学生看到引用时不知道具体来自哪份作业。粒度细，引用才精确。
 *
 * <h3>为什么还要支持手工补充资料</h3>
 * 上面三类数据都是"元信息"（课程名、截止时间），知识点本身（讲义正文、
 * 课件内容）不在数据库里。所以额外提供 {@link #addDoc}，把课件正文这类
 * 长文本直接灌进去 —— 否则助教只能回答"什么时候交作业"，
 * 回答不了"String 和 StringBuilder 有什么区别"。
 *
 * <h3>与「工具调用」的分工</h3>
 * 这里灌进去的人数、截止时间都是<b>快照</b>：灌完就固定了，之后数据库变了它不会更新。
 * 所以静态知识适合走向量检索，动态数据（"我还有几门作业没交"）适合走工具调用直接查库
 * （见 {@link AiToolService}）。判断标准一句话：<b>换个人问，答案会不会变</b> ——
 * 会变就必须实时查（工具），不会变就可以提前灌（检索）。
 */
@Slf4j
@Service
public class KnowledgeIngestService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CourseMapper courseMapper;
    private final CourseResourceMapper courseResourceMapper;
    private final HomeworkMapper homeworkMapper;
    private final KnowledgeDocMapper knowledgeDocMapper;
    private final DbVectorStore vectorStore;

    /**
     * 手工补充的资料（课件正文等）—— 内存里只放一份<b>缓存</b>，真相在 {@code ai_knowledge_doc} 表。
     *
     * <p>S6 之前这里就是一个 {@code CopyOnWriteArrayList}，服务重启后手工补的资料全丢，
     * 得让教师重新上传一遍。现在改成：启动时从表里读进这个列表，
     * 增删都先写表、再刷新列表。之所以还留这份列表，
     * 是因为 {@link #rebuildAll()} 要把"数据库资料 + 手工资料"一起送进向量库，
     * 每次都查表没必要。
     */
    private volatile List<DbVectorStore.SourceDoc> extraDocs = List.of();

    public KnowledgeIngestService(CourseMapper courseMapper,
                                  CourseResourceMapper courseResourceMapper,
                                  HomeworkMapper homeworkMapper,
                                  KnowledgeDocMapper knowledgeDocMapper,
                                  DbVectorStore vectorStore) {
        this.courseMapper = courseMapper;
        this.courseResourceMapper = courseResourceMapper;
        this.homeworkMapper = homeworkMapper;
        this.knowledgeDocMapper = knowledgeDocMapper;
        this.vectorStore = vectorStore;
    }

    /**
     * 启动时把手工资料读进缓存。读失败只打 warn、按"没有手工资料"继续 ——
     * 常见原因是没执行 {@code sql/migrate_v1.8.sql}；这不该让整个服务起不来。
     */
    @PostConstruct
    void hydrateExtraDocs() {
        try {
            extraDocs = knowledgeDocMapper.selectList(Wrappers.<KnowledgeDoc>lambdaQuery()
                            .orderByAsc(KnowledgeDoc::getDocId))
                    .stream()
                    .map(doc -> new DbVectorStore.SourceDoc(doc.getSource(), doc.getContent()))
                    .toList();
            if (!extraDocs.isEmpty()) {
                log.info("已加载手工补充资料 {} 份（来自 ai_knowledge_doc）", extraDocs.size());
            }
        } catch (Exception e) {
            log.warn("加载手工补充资料失败（sql/migrate_v1.8.sql 是否已执行？），本次按无手工资料处理：{}",
                    e.getMessage());
        }
    }


    /**
     * 重建向量库：数据库资料 + 手工资料一起重新切块、向量化。
     *
     * <p>加 {@code synchronized} 是必要的 —— 灌库要重调向量接口（几十次网络往返，
     * 数秒级），期间若有另一个请求也触发灌库，会双倍烧 token 并把库写乱。
     *
     * @return 实际入库的知识块数量
     */
    public synchronized int rebuildAll() {
        List<DbVectorStore.SourceDoc> docs = new ArrayList<>(collectFromDatabase());
        docs.addAll(extraDocs);
        log.info("开始重建知识库：数据库资料 {} 份 + 手工资料 {} 份",
                docs.size() - extraDocs.size(), extraDocs.size());
        return vectorStore.rebuild(docs);
    }

    /**
     * 从数据库收集资料。课程只取审核通过的（待审核 / 已驳回的课程不该被学生问到）。
     */
    public List<DbVectorStore.SourceDoc> collectFromDatabase() {
        List<DbVectorStore.SourceDoc> docs = new ArrayList<>();

        // 课程 ID -> 课程名。作业和资源都用它来标注所属课程，所以要取全量（不限审核状态）
        List<Course> allCourses = courseMapper.selectList(null);
        Map<Integer, String> courseNames = new HashMap<>();
        for (Course course : allCourses) {
            courseNames.put(course.getCourseId(), course.getCourseName());
        }

        // ① 课程（仅审核通过）
        List<Course> published = courseMapper.selectList(
                Wrappers.<Course>lambdaQuery()
                        .eq(Course::getAuditStatus, 1)
                        .orderByAsc(Course::getCourseId));
        for (Course course : published) {
            docs.add(new DbVectorStore.SourceDoc(
                    "课程：" + course.getCourseName(),
                    buildCourseText(course)));
        }

        // ② 作业要求（全量：学生问"作业什么时候交"时，历史作业也常常需要）
        for (Homework homework : homeworkMapper.selectList(null)) {
            String courseName = courseNameOf(courseNames, homework.getCourseId());
            docs.add(new DbVectorStore.SourceDoc(
                    "作业：" + courseName + " / " + homework.getHomeworkName(),
                    buildHomeworkText(courseName, homework)));
        }

        // ③ 课程资源清单（只有名称与类型，没有正文 —— 正文要靠手工补充资料）
        for (CourseResource resource : courseResourceMapper.selectList(null)) {
            String courseName = courseNameOf(courseNames, resource.getCourseId());
            docs.add(new DbVectorStore.SourceDoc(
                    "课程资料：" + courseName + " / " + resource.getResourceName(),
                    buildResourceText(courseName, resource)));
        }

        log.info("数据库资料收集完成：课程 {} 份、作业 {} 份、资源 {} 份",
                published.size(),
                docs.stream().filter(d -> d.source().startsWith("作业：")).count(),
                docs.stream().filter(d -> d.source().startsWith("课程资料：")).count());
        return docs;
    }

    /**
     * 追加（或覆盖）一份手工资料，并立即重建向量库。
     *
     * <p><b>先写表、再重建</b>：重建要调向量接口（几秒），万一中途失败，
     * 资料本身已经进库了，下次重建还在。反过来（先重建后落库）则会丢掉这次上传。
     *
     * <p>同名视为覆盖 —— 由表上的 {@code uk_source} 唯一索引兜底，
     * 语言层面也先查一次：同一个标题出现两份，检索结果里就会有重复片段。
     *
     * @param source 资料标题，会作为回答的出处展示，例如「Java 基础 - 第 2 章讲义」
     * @param text   资料正文，可以很长（入库时自动切块）
     * @return 重建后的知识块总数
     */
    public synchronized int addDoc(String source, String text) {
        if (!StringUtils.hasText(source)) {
            throw new BizException(ResultCode.PARAM_ERROR, "资料标题不能为空");
        }
        if (!StringUtils.hasText(text)) {
            throw new BizException(ResultCode.PARAM_ERROR, "资料正文不能为空");
        }
        String trimmedSource = source.trim();
        String trimmedText = text.trim();

        KnowledgeDoc existing = knowledgeDocMapper.selectOne(Wrappers.<KnowledgeDoc>lambdaQuery()
                .eq(KnowledgeDoc::getSource, trimmedSource)
                .last("limit 1"));
        if (existing == null) {
            KnowledgeDoc doc = new KnowledgeDoc();
            doc.setSource(trimmedSource);
            doc.setContent(trimmedText);
            doc.setUpdateTime(new Date());
            knowledgeDocMapper.insert(doc);
        } else {
            existing.setContent(trimmedText);
            existing.setUpdateTime(new Date());
            knowledgeDocMapper.updateById(existing);
        }
        hydrateExtraDocs();
        return rebuildAll();
    }

    /** 清空手工资料并重建（数据库资料保留） */
    public synchronized int clearExtraDocs() {
        // 条件 doc_id > 0 是"全表删除"的写法，见 VectorChunkStore#clear 的注释：
        // 项目里注册了 BlockAttackInnerInterceptor，无条件的 delete 会被拦下
        knowledgeDocMapper.delete(Wrappers.<KnowledgeDoc>lambdaQuery().gt(KnowledgeDoc::getDocId, 0));
        extraDocs = List.of();
        return rebuildAll();
    }

    /** 已补充的手工资料数量 */
    public int extraDocCount() {
        return extraDocs.size();
    }

    /** 手工资料的标题清单，供前端展示"当前补充了哪些资料" */
    public List<String> extraDocTitles() {
        return extraDocs.stream().map(DbVectorStore.SourceDoc::source).toList();
    }

    // ==================== 文本拼装 ====================

    private static String buildCourseText(Course course) {
        StringBuilder sb = new StringBuilder();
        sb.append("课程名称：").append(course.getCourseName()).append('\n');
        sb.append("课程介绍：").append(nullToDash(course.getCourseIntro())).append('\n');
        sb.append("培训周期：").append(nullToDash(course.getTrainCycle())).append('\n');
        sb.append("报名条件：").append(nullToDash(course.getApplyCond())).append('\n');
        sb.append("名额上限：").append(course.getMaxStudents() == null || course.getMaxStudents() <= 0
                ? "不限" : course.getMaxStudents() + " 人").append('\n');
        sb.append("当前已报名人数：").append(course.getCurrentStudents() == null
                ? 0 : course.getCurrentStudents()).append(" 人\n");
        sb.append("发布时间：").append(format(course.getPublishTime()));
        return sb.toString();
    }

    private static String buildHomeworkText(String courseName, Homework homework) {
        StringBuilder sb = new StringBuilder();
        sb.append("所属课程：").append(courseName).append('\n');
        sb.append("作业名称：").append(homework.getHomeworkName()).append('\n');
        sb.append("作业要求：").append(nullToDash(homework.getHomeworkContent())).append('\n');
        sb.append("提交截止时间：").append(format(homework.getDeadline())).append('\n');
        sb.append("发布时间：").append(format(homework.getPublishTime()));
        return sb.toString();
    }

    private static String buildResourceText(String courseName, CourseResource resource) {
        StringBuilder sb = new StringBuilder();
        sb.append("所属课程：").append(courseName).append('\n');
        sb.append("资源名称：").append(resource.getResourceName()).append('\n');
        sb.append("资源类型：").append(nullToDash(resource.getResourceType())).append('\n');
        sb.append("说明：该资源可在「课程资源」页面查看或下载。");
        return sb.toString();
    }

    private static String courseNameOf(Map<Integer, String> courseNames, Integer courseId) {
        if (courseId == null) {
            return "未知课程";
        }
        return courseNames.getOrDefault(courseId, "课程 #" + courseId);
    }

    private static String nullToDash(String value) {
        return StringUtils.hasText(value) ? value : "（未填写）";
    }

    private static String format(Date date) {
        if (date == null) {
            return "（未设置）";
        }
        return DATE_TIME.format(date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
    }
}
