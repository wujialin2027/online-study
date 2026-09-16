package com.online.study.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import java.util.Date;

@Data
@TableName("score")
public class Score {
    @TableId(type = IdType.AUTO)
    private Integer scoreId;
    private Integer studentId;
    private Integer courseId;
    private Integer homeworkScore;
    private Integer examScore;
    private Integer totalScore;
    private String scoreComment;
    private Integer scoreTeacherId;
    private Date scoreTime;
}
