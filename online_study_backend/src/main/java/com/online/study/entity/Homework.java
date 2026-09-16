package com.online.study.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import java.util.Date;

@Data
@TableName("homework")
public class Homework {
    @TableId(type = IdType.AUTO)
    private Integer homeworkId;
    private Integer courseId;
    private String homeworkName;
    private String homeworkContent;
    private Date deadline;
    private Integer publishTeacherId;
    private Date publishTime;
}
