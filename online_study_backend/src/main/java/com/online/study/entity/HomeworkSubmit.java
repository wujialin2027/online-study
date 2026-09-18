package com.online.study.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import java.util.Date;

@Data
@TableName("homework_submit")
public class HomeworkSubmit {
    @TableId(type = IdType.AUTO)
    private Integer submitId;
    private Integer studentId;
    private Integer homeworkId;
    private String submitContent;
    private String submitFile;
    private String submitPhoto;
    private Date submitTime;
    private Integer correctStatus;

    // ==================== 评分字段 ====================
    // 分数天然属于「某一次提交」，所以落在这一行上，而不是挤在 score 表
    // （学生×课程一行，一门课多次作业会互相覆盖）。
    // score 为 null 即表示「尚未批改」—— 与 correctStatus 表达同一件事，
    // 服务端批改时两者一起写，保持一致。
    /** 本次作业得分（0-100，null = 未批改） */
    private Integer score;
    /** 本次作业评语 */
    private String scoreComment;
    /** 评分教师ID */
    private Integer scoreTeacherId;
    /** 评分时间 */
    private Date scoreTime;
}
