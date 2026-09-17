package com.online.study.mapper;

import com.online.study.entity.Course;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 课程 Mapper
 *
 * <p>这里放的是「必须由数据库一条语句原子完成」的操作。
 * 它们不能用「先查再改」的写法实现 —— 那样在并发下会出错。
 */
@Mapper
public interface CourseMapper extends BaseMapper<Course> {

    /**
     * 尝试占用一个名额（<b>防超卖的核心</b>）。
     *
     * <p>把「判断还有没有名额」和「名额加一」合并成一条 UPDATE：
     * <pre>
     *   UPDATE course
     *      SET current_students = current_students + 1
     *    WHERE course_id = ? AND (max_students = 0 OR current_students &lt; max_students)
     * </pre>
     *
     * <p>为什么这样写就安全：InnoDB 执行 UPDATE 时会对命中的行加<b>排他锁</b>，
     * 并发的第二个请求只能排队等待；等它拿到锁时，{@code current_students}
     * 已经是最新值，如果此时已满，{@code WHERE} 条件不成立，
     * <b>影响行数为 0</b> —— 调用方据此判断「名额已满」。
     *
     * <p>如果写成「先 SELECT COUNT(*) 判断、再 UPDATE」，两个并发请求会
     * 都读到「还有名额」然后各自加一 —— 这就是超卖。
     *
     * @param courseId 课程 ID
     * @return 影响行数：1 表示占位成功，0 表示名额已满或课程不存在
     */
    @Update("UPDATE course SET current_students = current_students + 1 "
            + "WHERE course_id = #{courseId} "
            + "AND (max_students = 0 OR current_students < max_students)")
    int tryOccupySeat(@Param("courseId") Integer courseId);

    /**
     * 释放一个名额（报名被驳回、学员撤销报名、报名记录被删除时调用）。
     *
     * <p>{@code current_students > 0} 是兜底条件，防止计数被减成负数 ——
     * 一旦出现负数就说明别处有 bug，先让它减不下去而不是把数据弄脏。
     *
     * @param courseId 课程 ID
     * @return 影响行数：1 表示释放成功，0 表示计数已是 0
     */
    @Update("UPDATE course SET current_students = current_students - 1 "
            + "WHERE course_id = #{courseId} AND current_students > 0")
    int releaseSeat(@Param("courseId") Integer courseId);

    /**
     * 审核课程（<b>带状态前提的条件更新，保证「待审核」只能被消费一次</b>）。
     *
     * <pre>
     *   UPDATE course
     *      SET audit_status = ?, audit_admin_id = ?, audit_time = NOW(), audit_remark = ?
     *    WHERE course_id = ? AND audit_status = 0
     * </pre>
     *
     * <p>两个管理员同时点「通过」和「驳回」时，只有先拿到行锁的那条能生效，
     * 另一条影响行数为 0 —— 调用方据此提示「该课程已被审核」。
     * 这就是把<b>状态机的前置条件写进 SQL 的 WHERE</b>，比在 Java 里
     * 「先查状态再判断」可靠得多。
     *
     * @param courseId    课程 ID
     * @param auditStatus 审核结果：1 通过 / 2 驳回
     * @param adminId     审核管理员 ID
     * @param remark      审核意见（驳回时必填）
     * @return 影响行数：1 表示审核成功，0 表示课程不存在或已被审核
     */
    @Update("UPDATE course SET audit_status = #{auditStatus}, audit_admin_id = #{adminId}, "
            + "audit_time = NOW(), audit_remark = #{remark} "
            + "WHERE course_id = #{courseId} AND audit_status = 0")
    int auditIfPending(@Param("courseId") Integer courseId,
                       @Param("auditStatus") Integer auditStatus,
                       @Param("adminId") Integer adminId,
                       @Param("remark") String remark);
}
