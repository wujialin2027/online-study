package com.online.study.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import java.util.Date;

@Data
@TableName("teacher")
public class Teacher {
    @TableId(type = IdType.AUTO)
    private Integer teacherId;
    private String teacherAccount;
    private String teacherPwd;
    private String teacherName;
    private String teacherPhone;
    private String teacherEmail;
    private String teacherOrg;
    private Date registerTime;
    private Integer accountStatus;
}
