package com.online.study.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import java.util.Date;

@Data
@TableName("course")
public class Course {
    @TableId(type = IdType.AUTO)
    private Integer courseId;
    private String courseName;
    private String courseIntro;
    private String trainCycle;
    private String applyCond;
    private Integer publishTeacherId;
    private Integer auditStatus;
    private Date publishTime;
    private Date offlineTime;
}
