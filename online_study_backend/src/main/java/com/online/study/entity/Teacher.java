package com.online.study.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.Date;

@Data
@TableName("teacher")
public class Teacher {
    @TableId(type = IdType.AUTO)
    private Integer teacherId;
    private String teacherAccount;

    /** 密码（BCrypt 哈希）：只许写入、永不序列化返回，详见 {@link Student#getStudentPwd()} */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String teacherPwd;

    private String teacherName;
    private String teacherPhone;
    private String teacherEmail;
    private String teacherOrg;
    private Date registerTime;
    private Integer accountStatus;
}
