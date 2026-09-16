package com.online.study.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import java.util.Date;

@Data
@TableName("course_resource")
public class CourseResource {
    @TableId(type = IdType.AUTO)
    private Integer resourceId;
    private Integer courseId;
    private String resourceName;
    private String resourceType;
    private String resourcePath;
    private String resourcePhoto;
    private Date uploadTime;
    private Integer uploadTeacherId;
}
