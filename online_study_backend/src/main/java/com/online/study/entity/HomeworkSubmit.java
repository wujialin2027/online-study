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
}
