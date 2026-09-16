package com.online.study.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import java.util.Date;

@Data
@TableName("course_apply")
public class CourseApply {
    @TableId(type = IdType.AUTO)
    private Integer applyId;
    private Integer studentId;
    private Integer courseId;
    private Date applyTime;
    private Integer auditStatus;
    private Integer auditTeacherId;
}
